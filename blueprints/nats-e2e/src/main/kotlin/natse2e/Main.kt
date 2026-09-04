package natse2e

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.manahive.contracts.EventEnvelope
import com.manahive.contracts.perception.Observation
import com.manahive.contracts.perception.ObservationKind
import com.manahive.contracts.policy.AlarmProfile
import com.manahive.contracts.policy.CatalogVersion
import com.manahive.contracts.policy.FALL_RISK_CATALOG
import com.manahive.contracts.policy.MobilityAid
import com.manahive.contracts.policy.PolicyChangeDetected
import com.manahive.contracts.policy.PolicyMode
import com.manahive.contracts.policy.RiskLevel
import com.manahive.contracts.policy.TemplateId
import com.manahive.contracts.scene.SceneEvent
import com.manahive.contracts.sentinel.SentinelSignal
import com.manahive.contracts.alarm.AlarmEvent
import com.manahive.contracts.alarm.AlertKey
import com.manahive.kernel.AlertId
import com.manahive.kernel.BedId
import com.manahive.kernel.MonitorId
import com.manahive.kernel.EventRef
import com.manahive.kernel.NightId
import com.manahive.kernel.ResidentId
import com.manahive.messaging.NatsTopology
import com.manahive.messaging.Subjects
import com.manahive.politica.PolicyResolver
import com.manahive.runtime.EngineCalibrations
import com.manahive.runtime.ResidentRuntime
import com.manahive.serialization.SceneEventSerializer
import com.manahive.serialization.SentinelSignalSerializer
import io.nats.client.Connection
import io.nats.client.Nats
import io.nats.client.Options
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * El contrato del bus, probado contra un NATS de verdad.
 *
 * Los demas blueprints prueban el dominio llamando funciones. Este prueba la
 * otra mitad —la que se rompe en produccion y no en los tests— : que lo que
 * publicamos se pueda volver a leer del otro lado, con los serializadores
 * reales, los subjects reales y JetStream de por medio.
 *
 * Tres roles, un proceso, todo hablando por el bus:
 *
 * ```
 *   Sistema externo  ──hub.policy.change.v1──────────▶  Motor
 *                    ──perception.observation.v1.*───▶  Motor
 *                                                        │
 *   Verificador  ◀── scene.fact / sentinel.signal / alarm.event / recorder.command
 * ```
 *
 * Requiere un NATS con JetStream:  `nats-server -js`
 */

private val BED = BedId("bed-301")
private val JOSE = ResidentId("jose")
private val NIGHT = NightId("night-nats-e2e")
private val CAM = MonitorId("CAMERA_MAIN")
private val T0: Instant = Instant.parse("2026-01-15T22:00:00Z")

private val mapper = ObjectMapper()
    .registerKotlinModule()
    .registerModule(JavaTimeModule())

private var checks = 0
private var failed = 0

private fun check(label: String, ok: Boolean) {
    checks++
    if (ok) {
        println("    ✅ $label")
    } else {
        failed++
        println("    ❌ $label")
    }
}

private fun envelope(type: String, at: Instant, payload: String) = EventEnvelope(
    eventId = UUID.randomUUID().toString(),
    type = type,
    version = 1,
    occurredAt = at,
    source = "nats-e2e-blueprint",
    payloadJson = payload,
)

fun main() {
    val url = System.getenv("NATS_URL") ?: "nats://127.0.0.1:4222"

    println("═══════════════════════════════════════════════════════════════")
    println("  Contrato del bus — ida y vuelta contra NATS real")
    println("  $url")
    println("═══════════════════════════════════════════════════════════════")
    println()

    val nc = try {
        Nats.connect(Options.Builder().server(url).connectionTimeout(Duration.ofSeconds(5)).build())
    } catch (e: Exception) {
        println("  ❌ No hay NATS en $url — levantalo con:  nats-server -js")
        println("     ${e.message}")
        kotlin.system.exitProcess(1)
    }

    nc.use { conn ->
        NatsTopology(conn.jetStreamManagement()).ensureAll()
        println("  Streams declarados: PERCEPTION SCENE SENTINEL ALARM POLICY RECORDER EVIDENCE")
        println()

        politicaVaYVuelve(conn)
        pipelineVaYVuelve(conn)
    }

    println()
    println("═══════════════════════════════════════════════════════════════")
    if (failed == 0) {
        println("  ✅ CONTRATO DEL BUS OK — $checks checks, 0 fallidos")
    } else {
        println("  ❌ CONTRATO DEL BUS ROTO — $checks checks, $failed fallidos")
    }
    println("═══════════════════════════════════════════════════════════════")

    // Salida explicita, y no caerse del final de main.
    //
    // `nc.use { }` cierra la conexion, pero el cliente de NATS deja hilos
    // no-daemon vivos y el JVM no termina solo: este blueprint quedaba colgado
    // *despues* de imprimir su veredicto, y como es el ultimo de la lista
    // colgaba a `scripts/blueprints.sh` entero. El sintoma era peor que el bug:
    // el runner parecia lento, no roto, y a los diez minutos alguien lo mataba
    // y perdia el resultado de los once.
    kotlin.system.exitProcess(if (failed > 0) 1 else 0)
}

// ── 1. Politica: entra un cambio, sale una calibracion ──────────────────────

private fun politicaVaYVuelve(conn: Connection) {
    println("  ── 1. Politica ──────────────────────────────────────────────")
    println("     el sistema externo avisa un cambio; nosotros resolvemos y publicamos")
    println()

    val js = conn.jetStream()
    val recibidas = ConcurrentLinkedQueue<PolicyChangeDetected>()
    val latch = CountDownLatch(1)

    // El motor escucha cambios de politica, como hace el runtime.
    val motor = conn.createDispatcher { msg ->
        val env = mapper.readValue<EventEnvelope>(String(msg.data))
        recibidas += mapper.readValue<PolicyChangeDetected>(env.payloadJson)
        latch.countDown()
    }
    motor.subscribe(Subjects.policyChangeDetected())

    val profile = AlarmProfile(
        residentId = JOSE,
        riskLevel = RiskLevel.HIGH,
        mobilityAid = MobilityAid.WALKER,
        autopilot = false,
        mode = PolicyMode.PRESET,
        templateId = TemplateId("fall-risk"),
        overrides = emptyMap(),
        catalogVersion = CatalogVersion("2.1.0"),
        validFrom = T0,
    )
    val cambio = PolicyChangeDetected(residentId = JOSE, at = T0, snapshot = profile)

    js.publish(
        Subjects.policyChangeDetected(),
        mapper.writeValueAsBytes(envelope("PolicyChangeDetected", T0, mapper.writeValueAsString(cambio))),
    )

    val llego = latch.await(10, TimeUnit.SECONDS)
    check("el cambio de politica cruza el bus", llego)
    conn.closeDispatcher(motor)
    if (!llego) return

    val leido = recibidas.first()
    check("el residente sobrevive la serializacion", leido.snapshot.residentId == JOSE)
    check("la version del catalogo sobrevive", leido.snapshot.catalogVersion.value == "2.1.0")

    // Se resuelve con lo que llego del bus, no con lo que teniamos en memoria.
    val calibracion = PolicyResolver.resolve(FALL_RISK_CATALOG, leido.snapshot).value
    check("resuelve umbrales para el residente", calibracion.scene.dwellThresholds.isNotEmpty())
    check("resuelve reglas de alerta", calibracion.sentinel.alertRules.isNotEmpty())
    check(
        "la caida llega como CRITICAL desde la plantilla, no desde el codigo",
        calibracion.sentinel.alertRules[com.manahive.contracts.scene.StateKind.ON_FLOOR]
            ?.severity == com.manahive.contracts.policy.Severity.CRITICAL,
    )
    println()
}

// ── 2. Pipeline: entran observaciones, salen hechos, señales y ordenes ──────

private fun pipelineVaYVuelve(conn: Connection) {
    println("  ── 2. Pipeline ──────────────────────────────────────────────")
    println("     entran observaciones por el bus; salen hechos, episodios y ordenes")
    println()

    val js = conn.jetStream()

    val profile = AlarmProfile(
        residentId = JOSE, riskLevel = RiskLevel.HIGH, mobilityAid = MobilityAid.WALKER,
        autopilot = false, mode = PolicyMode.PRESET, templateId = TemplateId("fall-risk"),
        overrides = emptyMap(), catalogVersion = CatalogVersion("2.1.0"), validFrom = T0,
    )
    val calibracion = PolicyResolver.resolve(FALL_RISK_CATALOG, profile).value
    val runtime = ResidentRuntime(
        residentId = JOSE, bed = BED, night = NIGHT, monitor = CAM,
        calibrations = EngineCalibrations.from(calibracion),
    )

    // ── el verificador: escucha todo lo que emitimos ──
    val hechos = ConcurrentLinkedQueue<SceneEvent>()
    val señales = ConcurrentLinkedQueue<SentinelSignal>()
    val alarmas = ConcurrentLinkedQueue<String>()
    val grabaciones = ConcurrentLinkedQueue<String>()

    val verificador = conn.createDispatcher { msg ->
        val env = mapper.readValue<EventEnvelope>(String(msg.data))
        when (env.type) {
            "SceneEvent" -> hechos += SceneEventSerializer.fromJson(env.payloadJson)
            "SentinelSignal" -> señales += SentinelSignalSerializer.fromJson(env.payloadJson)
            "AlarmEvent" -> alarmas += env.payloadJson
            "RecordingCommand" -> grabaciones += env.payloadJson
        }
    }
    verificador.subscribe(Subjects.SCENE_WILDCARD)
    verificador.subscribe(Subjects.SENTINEL_WILDCARD)
    verificador.subscribe(Subjects.ALARM_WILDCARD)
    verificador.subscribe(Subjects.RECORDER_WILDCARD)

    // ── el motor: consume observaciones y publica lo que produce ──
    val procesadas = CountDownLatch(5)
    val motor = conn.createDispatcher { msg ->
        try {
            val env = mapper.readValue<EventEnvelope>(String(msg.data))
            val obs = mapper.readValue<Observation>(env.payloadJson)
            val out = runtime.onObservation(obs)
            for (f in out.sceneFacts) {
                js.publish(
                    Subjects.sceneEvent(BED),
                    mapper.writeValueAsBytes(envelope("SceneEvent", f.at, SceneEventSerializer.toJson(f))),
                )
            }
            for (s in out.signals) {
                js.publish(
                    Subjects.sentinelSignal(BED),
                    mapper.writeValueAsBytes(envelope("SentinelSignal", s.at, SentinelSignalSerializer.toJson(s))),
                )
            }
            for (nf in out.harborCommands) {
                val s = nf.signal
                if (nf.command is com.manahive.harbor.NoticeCommand.Dispatch &&
                    s is SentinelSignal.EpisodeOpened
                ) {
                    val ev = AlarmEvent.AlertRaised(
                        alert = AlertId("alert-${s.episode.value}-${s.at.epochSecond}"),
                        at = s.at,
                        key = AlertKey(bed = s.bed, rule = s.rule, episode = s.episode),
                        severity = s.severity,
                        origin = EventRef(stream = Subjects.sentinelSignal(BED), seq = 0),
                    )
                    js.publish(
                        Subjects.alarmEvent(ev.alert),
                        mapper.writeValueAsBytes(
                            envelope("AlarmEvent", ev.at, mapper.writeValueAsString(ev)),
                        ),
                    )
                }
            }
            for (c in out.recorderCommands) {
                js.publish(
                    Subjects.recordingCommand(BED),
                    mapper.writeValueAsBytes(
                        envelope("RecordingCommand", Instant.now(), mapper.writeValueAsString(c)),
                    ),
                )
            }
        } finally {
            procesadas.countDown()
        }
    }
    motor.subscribe(Subjects.PERCEPTION_WILDCARD)

    // ── el sistema externo: la noche de José ──
    val noche = listOf(
        ObservationKind.IN_BED to T0,
        ObservationKind.SITTING_IN_BED to T0.plusSeconds(30),
        ObservationKind.BED_EDGE to T0.plusSeconds(90),
        ObservationKind.STANDING to T0.plusSeconds(150),
        // Y se cae. Es el desenlace que todo esto existe para detectar, y hasta
        // hoy no habia forma de reportarlo: ObservationKind no tenia ON_FLOOR.
        ObservationKind.ON_FLOOR to T0.plusSeconds(160),
    )
    for ((kind, at) in noche) {
        val obs = Observation(
            monitor = CAM, bed = BED, kind = kind, confidence = 0.95, observedAt = at,
        )
        js.publish(
            Subjects.perceptionObservation(BED),
            mapper.writeValueAsBytes(envelope("Observation", at, mapper.writeValueAsString(obs))),
        )
    }

    val todas = procesadas.await(15, TimeUnit.SECONDS)
    check("las 5 observaciones cruzan el bus y se procesan", todas)
    Thread.sleep(1200)

    // El reloj. Una regla por permanencia no se ve nunca si nadie pregunta
    // "cuanto lleva asi": el runtime real lo hace en cada tick, y sin esto el
    // blueprint probaria solo la mitad del pipeline.
    val tick = runtime.onTick(T0.plusSeconds(600))
    for (f in tick.sceneFacts) {
        js.publish(
            Subjects.sceneEvent(BED),
            mapper.writeValueAsBytes(envelope("SceneEvent", f.at, SceneEventSerializer.toJson(f))),
        )
    }
    for (s in tick.signals) {
        js.publish(
            Subjects.sentinelSignal(BED),
            mapper.writeValueAsBytes(envelope("SentinelSignal", s.at, SentinelSignalSerializer.toJson(s))),
        )
    }
    Thread.sleep(1500) // que el verificador termine de recibir lo emitido

    conn.closeDispatcher(motor)
    conn.closeDispatcher(verificador)

    check("salieron hechos de escena", hechos.isNotEmpty())
    check(
        "los hechos vuelven tipados, no como texto suelto",
        hechos.any { it is SceneEvent.TransitionDetected },
    )
    check(
        "una transicion identifica su cama del otro lado",
        hechos.filterIsInstance<SceneEvent.TransitionDetected>().all { it.bed == BED },
    )
    check("salieron señales de Sentinel", señales.isNotEmpty())
    check(
        "las señales vuelven tipadas",
        señales.any { it is SentinelSignal.EpisodeOpened },
    )
    check(
        "la caida abre un episodio CRITICAL",
        señales.filterIsInstance<SentinelSignal.EpisodeOpened>()
            .any { it.severity == com.manahive.contracts.policy.Severity.CRITICAL },
    )
    check("la alarma llega al bus", alarmas.isNotEmpty())

    println()
    println("     hechos de escena: ${hechos.size}   señales: ${señales.size}   " +
        "alarmas: ${alarmas.size}   grabaciones: ${grabaciones.size}")
    hechos.take(6).forEach { println("       · ${it::class.simpleName}") }
    señales.take(4).forEach { println("       ⚑ ${it::class.simpleName}") }
    println()
}
