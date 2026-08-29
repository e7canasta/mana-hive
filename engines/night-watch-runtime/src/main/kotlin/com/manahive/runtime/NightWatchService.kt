package com.manahive.runtime

import com.manahive.kernel.EventRef
import com.manahive.kernel.AlertId
import com.manahive.harbor.NoticeCommand
import com.manahive.contracts.sentinel.SentinelSignal
import com.manahive.contracts.alarm.AlertKey
import com.manahive.contracts.alarm.AlarmEvent
import com.manahive.serialization.SentinelSignalSerializer
import com.manahive.serialization.SceneEventSerializer
import com.manahive.kernel.BedId
import com.manahive.contracts.scene.SceneEvent
import com.fasterxml.jackson.module.kotlin.readValue
import com.manahive.contracts.EventEnvelope
import com.manahive.contracts.perception.Observation
import com.manahive.contracts.policy.PolicyChangeDetected
import com.manahive.contracts.policy.WatchLevel
import com.manahive.contracts.policy.catalogFor
import com.manahive.hub.policy.LevelTemplate
import com.manahive.hub.policy.PolicyLayers
import com.manahive.hub.policy.toAlarmProfile
import com.manahive.kernel.ResidentId
import com.manahive.messaging.BusEvents
import com.manahive.messaging.NatsObjectMapper
import com.manahive.messaging.NatsTopology
import com.manahive.messaging.Subjects
import com.manahive.politica.PolicyResolver
import io.nats.client.Connection
import io.nats.client.Dispatcher
import io.nats.client.JetStream
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import java.time.Instant

/**
 * Wires the [NightWatchRuntime] to NATS.
 *
 * - Ingests perception observations, routes to the correct resident via [Census].
 * - Subscribes to policy changes, recalibrates the affected resident.
 * - Runs a sweep every 30 seconds (dwell, come-back, signal lost).
 */
@Component
class NightWatchService(
    private val runtime: NightWatchRuntime,
    private val census: Census,
    private val status: RuntimeStatusHolder,
    private val events: BusEvents,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val mapper = NatsObjectMapper.mapper
    private val dispatchers = mutableListOf<Dispatcher>()
    private lateinit var jetStream: JetStream

    @PostConstruct
    fun start() {
        // No bloquea. La conexión puede estar todavía cayéndose de un lado a
        // otro: nos suscribimos cuando el bus aparece, y otra vez cada vez que
        // vuelve. Antes esto llamaba a jetStream() y subscribe() acá mismo, así
        // que el servicio no arrancaba si NATS no estaba — y en un sistema 24/7
        // el orden de arranque no puede ser una precondición.
        status.transition(RuntimeState.WAITING_FOR_BUS, "esperando al bus")
        // Recién ahora el servicio existe: se engancha a los eventos del bus.
        events.onConnected { onBusAvailable() }
        events.onLost { onBusLost(it) }
        // Y si el bus ya estaba arriba, el evento CONNECTED pudo haber pasado
        // antes de este enganche: se intenta una vez de entrada.
        onBusAvailable()
    }

    /**
     * Se llama al arrancar y en cada (re)conexión. Es idempotente: volver a
     * suscribirse tras un reconnect es exactamente lo que hay que hacer.
     */
    @Synchronized
    fun onBusAvailable() {
        val connection = events.connection
        if (connection == null || connection.status != Connection.Status.CONNECTED) {
            status.transition(RuntimeState.WAITING_FOR_BUS, "bus no disponible")
            return
        }
        try {
            // Los streams se declaran cuando el bus aparece, no al arrancar:
            // ensureAll() necesita una conexión viva. Es idempotente.
            NatsTopology(connection.jetStreamManagement()).ensureAll()
            jetStream = connection.jetStream()
            dispatchers.forEach { d -> runCatching { connection.closeDispatcher(d) } }
            dispatchers.clear()
            subscribeToObservations(connection)
            subscribeToPolicyChanges(connection)
            status.transition(RuntimeState.RUNNING, "consumiendo del bus")
            log.info("Night-watch runtime consumiendo: {} residentes activos", runtime.size)
        } catch (e: Exception) {
            status.transition(RuntimeState.DEGRADED, "no se pudo suscribir: ${e.message}")
            log.error("No se pudo suscribir al bus: {}", e.message)
        }
    }

    /** El bus se cayó. Seguimos vivos; el cliente NATS reintenta solo. */
    fun onBusLost(detail: String) {
        status.transition(RuntimeState.DEGRADED, detail)
        log.warn("Bus perdido ({}): no se reciben observaciones hasta reconectar", detail)
    }

    @PreDestroy
    fun stop() {
        dispatchers.clear()
        log.info("Night-watch runtime stopped")
    }

    /**
     * Sweep every 30 seconds. Uses wall clock time.
     */
    @Scheduled(fixedRate = 30_000)
    fun sweep() {
        if (runtime.size == 0) return
        val now = Instant.now()
        val results = runtime.tickAll(now)
        for ((residentId, out) in results) {
            runtime.get(residentId)?.let { publish(it.bed, out) }
            for (signal in out.signals) {
                log.info("Sweep signal for {}: {}", residentId.value, signal::class.simpleName)
            }
            for (cmd in out.harborCommands) {
                log.info("Sweep harbor command for {}: {}", residentId.value, cmd.command::class.simpleName)
            }
        }
    }

    private fun subscribeToObservations(connection: Connection) {
        val dispatcher = connection.createDispatcher { msg ->
            try {
                val envelope = mapper.readValue<EventEnvelope>(String(msg.data))
                val obs = mapper.readValue<Observation>(envelope.payloadJson)
                handleObservation(obs)
            } catch (e: Exception) {
                log.error("Failed to process observation: {}", e.message)
            }
        }
        dispatcher.subscribe(Subjects.PERCEPTION_WILDCARD)
        dispatchers.add(dispatcher)
        log.info("Subscribed to PERCEPTION stream")
    }

    private fun handleObservation(obs: Observation) {
        val entry = census.lookup(obs.bed)
        if (entry == null) {
            log.debug("No census entry for bed {}, ignoring", obs.bed.value)
            return
        }
        val out = runtime.onObservation(entry.resident, obs)
        publish(obs.bed, out)
        for (signal in out.signals) {
            log.info("Signal for {}: {}", entry.resident.value, signal::class.simpleName)
        }
        for (cmd in out.harborCommands) {
            log.info("Harbor command for {}: {}", entry.resident.value, cmd.command::class.simpleName)
        }
    }

    private fun subscribeToPolicyChanges(connection: Connection) {
        val dispatcher = connection.createDispatcher { msg ->
            try {
                val envelope = mapper.readValue<EventEnvelope>(String(msg.data))
                val change = mapper.readValue<PolicyChangeDetected>(envelope.payloadJson)
                handlePolicyChange(change)
            } catch (e: Exception) {
                log.error("Failed to process policy change: {}", e.message)
            }
        }
        dispatcher.subscribe(Subjects.policyChangeDetected())
        dispatchers.add(dispatcher)
        log.info("Subscribed to policy changes")
    }

    /**
     * Publica al bus lo que el runtime produjo.
     *
     * Sin esto el runtime es un lazo cerrado que loguea: el hub —que es el
     * System of Record— nunca ve los hechos, y la historia clínica queda vacía.
     * Va con los serializadores del lenguaje publicado, no con Jackson crudo:
     * `SceneEvent` y `SentinelSignal` son interfaces selladas y sin
     * discriminador no se pueden reconstruir del otro lado.
     */
    private fun publish(bed: BedId, out: Outbound) {
        // Los hechos de escena van SIEMPRE, haya episodio o no. Es lo unico que
        // recibe un residente en nivel STANDARD —"solo observar, sin alertas"— y
        // es lo que alimenta su historial en el hub.
        for (fact in out.sceneFacts) {
            emit(Subjects.sceneEvent(bed), "SceneEvent", fact.at, SceneEventSerializer.toJson(fact))
        }

        // La señal se publica primero para poder citar su secuencia real en el
        // `origin` de la alarma. El egress viejo ponia seq = 0.
        val seqOf = mutableMapOf<SentinelSignal, EventRef>()
        for (signal in out.signals) {
            val ref = emit(
                Subjects.sentinelSignal(bed), "SentinelSignal", signal.at,
                SentinelSignalSerializer.toJson(signal),
            )
            if (ref != null) seqOf[signal] = ref
        }

        for ((signal, command) in out.harborCommands.map { it.signal to it.command }) {
            val event = toAlarmEvent(signal, command, seqOf[signal]) ?: continue
            emit(
                Subjects.alarmEvent(event.alert), "AlarmEvent", event.at,
                mapper.writeValueAsString(event),
            )
        }

        for (command in out.recorderCommands) {
            emit(
                Subjects.recordingCommand(bed), "RecordingCommand", Instant.now(),
                mapper.writeValueAsString(command),
            )
        }
    }

    /**
     * Traduce un aviso de Harbor a un hecho de alarma.
     *
     * Los identificadores son los **de verdad**: `EpisodeOpened` trae su
     * `EpisodeId` y su `RuleId`, asi que la alarma se puede rastrear hasta el
     * episodio que la origino. El egress de harbor los inventaba con UUIDs
     * random porque solo recibia el comando y habia perdido la señal.
     *
     * El `AlertId` es determinista —episodio + instante— para que un replay
     * produzca la misma alarma y no una nueva.
     */
    private fun toAlarmEvent(
        signal: SentinelSignal,
        command: NoticeCommand,
        origin: EventRef?,
    ): AlarmEvent.AlertRaised? {
        if (command !is NoticeCommand.Dispatch) return null
        if (signal !is SentinelSignal.EpisodeOpened) return null
        return AlarmEvent.AlertRaised(
            alert = AlertId("alert-${signal.episode.value}-${signal.at.epochSecond}"),
            at = signal.at,
            key = AlertKey(bed = signal.bed, rule = signal.rule, episode = signal.episode),
            severity = signal.severity,
            origin = origin ?: EventRef(stream = Subjects.sentinelSignal(signal.bed), seq = 0),
        )
    }

    /** Publica y devuelve dónde quedó, para que otro hecho pueda citarlo. */
    private fun emit(subject: String, type: String, at: Instant, payload: String): EventRef? = try {
        val envelope = EventEnvelope(
            eventId = java.util.UUID.randomUUID().toString(),
            type = type,
            version = 1,
            occurredAt = at,
            source = "night-watch-runtime",
            payloadJson = payload,
        )
        val ack = jetStream.publish(subject, mapper.writeValueAsBytes(envelope))
        EventRef(stream = ack.stream, seq = ack.seqno)
    } catch (e: Exception) {
        log.error("No se pudo publicar {} en {}: {}", type, subject, e.message)
        null
    }

    private fun handlePolicyChange(change: PolicyChangeDetected) {
        // El nivel viaja en templateId, no en catalogVersion: el fold del hub hace
        // `LevelTemplate(id = level.label)`. Leerlo de catalogVersion daba "2.1.0",
        // que no es un nivel, y todos los residentes caían al default — el director
        // ponía FALL_RISK y el motor vigilaba con STANDARD, en silencio.
        val raw = change.snapshot.templateId?.value.orEmpty()
        val level = parseWatchLevel(raw)
        if (level == null) {
            log.error(
                "Nivel irreconocible '{}' para {}: no se recalibra. " +
                    "Vigilar con un default sería vigilar con reglas que nadie eligió.",
                raw, change.residentId.value,
            )
            return
        }
        val catalog = catalogFor(level)
        val layers = PolicyLayers(
            level = level,
            template = LevelTemplate(id = change.snapshot.templateId?.value ?: level.label, level = level),
            adjustments = emptyList(),
            windows = emptyList(),
        )
        val profile = layers.toAlarmProfile(change.residentId, change.at)
        val calibration = PolicyResolver.resolve(catalog, profile.value).value
        val calibrations = EngineCalibrations.from(calibration)
        // Una política para alguien que todavía no tiene runtime **lo da de alta**.
        // La política siempre llega antes que la primera observación —el director
        // configura al residente cuando ingresa—, así que errar acá dejaba al
        // residente sin vigilancia hasta que alguien lo recalibrara de nuevo.
        val existing = runtime.get(change.residentId)
        if (existing == null) {
            val bed = census.bedFor(change.residentId)
            if (bed == null) {
                log.error(
                    "Política para {} pero no está en el censo: ninguna cama le corresponde",
                    change.residentId.value,
                )
                return
            }
            runtime.register(change.residentId, bed.bed, bed.night, bed.monitor, calibrations)
            log.info("Alta de {} en nivel {}", change.residentId.value, level)
        } else {
            runtime.recalibrate(change.residentId, calibrations)
            log.info("Recalibrated resident {} to level {}", change.residentId.value, level)
        }
    }

    /**
     * El nivel, tal como viaja en `templateId`.
     *
     * El fold del hub hace `LevelTemplate(id = level.label)`, así que lo que
     * llega es el **label** ("fall-risk"), no el nombre del enum ("FALL_RISK").
     * Un `valueOf` sobre el label no matchea nunca y caía al default: el
     * director ponía FALL_RISK y el residente quedaba vigilado como STANDARD,
     * sin que nada lo dijera.
     *
     * Un nivel que no se reconoce es un error operativo, no un STANDARD
     * silencioso — mismo criterio que `NoPolicyForResident` en el hub.
     */
    private fun parseWatchLevel(value: String): WatchLevel? =
        WatchLevel.entries.firstOrNull { it.label == value || it.name == value }

}
