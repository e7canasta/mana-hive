package com.manahive.runtime

import com.fasterxml.jackson.module.kotlin.readValue
import com.manahive.contracts.perception.Observation
import com.manahive.contracts.policy.PolicyChangeDetected
import com.manahive.messaging.BusEvents
import com.manahive.messaging.NatsObjectMapper
import com.manahive.messaging.NatsTopology
import com.manahive.messaging.Subjects
import com.manahive.profile.api.ResidentProfileChanged
import io.nats.client.Connection
import io.nats.client.Dispatcher
import io.nats.client.JetStream
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import java.io.File

/**
 * NATS adapter for [NightWatchServiceCore].
 *
 * Thin layer: subscribes to NATS subjects, routes to the core,
 * publishes results back via [NatsEventPublisher].
 *
 * No business logic here — all domain decisions happen in the core.
 */
@Component
class NightWatchService(
    private val core: NightWatchServiceCore,
    private val timeSink: TimeSink,
    private val status: RuntimeStatusHolder,
    private val events: BusEvents,
    @org.springframework.beans.factory.annotation.Value("\${manahive.profiles.dir:profiles}")
    private val profilesDir: String = "profiles",
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val mapper = NatsObjectMapper.mapper
    private val dispatchers = mutableListOf<Dispatcher>()
    private lateinit var jetStream: JetStream

    @PostConstruct
    fun start() {
        ProfileSeed(core.calibrator, File(profilesDir)).load()

        status.transition(RuntimeState.WAITING_FOR_BUS, "esperando al bus")
        events.onConnected { onBusAvailable() }
        events.onLost { onBusLost(it) }
        onBusAvailable()
    }

    @Synchronized
    fun onBusAvailable() {
        val connection = events.connection
        if (connection == null || connection.status != Connection.Status.CONNECTED) {
            status.transition(RuntimeState.WAITING_FOR_BUS, "bus no disponible")
            return
        }
        try {
            NatsTopology(connection.jetStreamManagement()).ensureAll()
            jetStream = connection.jetStream()
            dispatchers.forEach { d -> runCatching { connection.closeDispatcher(d) } }
            dispatchers.clear()
            subscribeToObservations(connection)
            subscribeToPolicyChanges(connection)
            subscribeToProfiles(connection)
            subscribeToTimeControl(connection)
            status.transition(RuntimeState.RUNNING, "consumiendo del bus")
            log.info("Night-watch runtime consumiendo: {} residentes activos", core.residentCount)
        } catch (e: Exception) {
            status.transition(RuntimeState.DEGRADED, "no se pudo suscribir: ${e.message}")
            log.error("No se pudo suscribir al bus: {}", e.message)
        }
    }

    fun onBusLost(detail: String) {
        status.transition(RuntimeState.DEGRADED, detail)
        log.warn("Bus perdido ({}): no se reciben observaciones hasta reconectar", detail)
    }

    @PreDestroy
    fun stop() {
        dispatchers.clear()
        log.info("Night-watch runtime stopped")
    }

    @Scheduled(fixedRate = 30_000)
    fun sweep() {
        core.sweep()
    }

    private fun subscribeToObservations(connection: Connection) {
        val dispatcher = connection.createDispatcher { msg ->
            try {
                val raw = String(msg.data)
                log.debug("Observation received: {} bytes", raw.length)
                val envelope = mapper.readValue<com.manahive.contracts.EventEnvelope>(raw)
                val obs = mapper.readValue<Observation>(envelope.payloadJson)
                core.onObservation(obs)
            } catch (e: Exception) {
                log.error("Failed to process observation: {}", e.message, e)
            }
        }
        dispatcher.subscribe(Subjects.PERCEPTION_WILDCARD)
        dispatchers.add(dispatcher)
        log.info("Subscribed to PERCEPTION stream")
    }

    private fun subscribeToPolicyChanges(connection: Connection) {
        val dispatcher = connection.createDispatcher { msg ->
            try {
                val envelope = mapper.readValue<com.manahive.contracts.EventEnvelope>(String(msg.data))
                val change = mapper.readValue<PolicyChangeDetected>(envelope.payloadJson)
                core.onPolicyChange(change)
            } catch (e: Exception) {
                log.error("Failed to process policy change: {}", e.message)
            }
        }
        dispatcher.subscribe(Subjects.policyChangeDetected())
        dispatchers.add(dispatcher)
        log.info("Subscribed to policy changes")
    }

    private fun subscribeToProfiles(connection: Connection) {
        val dispatcher = connection.createDispatcher { msg ->
            try {
                val envelope = mapper.readValue<com.manahive.contracts.EventEnvelope>(String(msg.data))
                val change = mapper.readValue<ResidentProfileChanged>(envelope.payloadJson)
                core.onProfileChanged(change.profile)
            } catch (e: Exception) {
                log.error("No se pudo procesar el perfil: {}", e.message)
            }
        }
        dispatcher.subscribe(Subjects.residentProfile())
        dispatchers += dispatcher
        log.info("Suscripto a perfiles en {}", Subjects.residentProfile())
    }

    /**
     * Time control — test only.
     *
     * Subject: `test.time.v1`
     * Advance: `{ "action": "advance", "duration": "PT12M" }`
     * Set:     `{ "action": "setTo", "instant": "2024-01-15T23:00:00Z" }`
     */
    private fun subscribeToTimeControl(connection: Connection) {
        val dispatcher = connection.createDispatcher { msg ->
            try {
                val envelope = mapper.readValue<com.manahive.contracts.EventEnvelope>(String(msg.data))
                val cmd = mapper.readValue<TimeCommand>(envelope.payloadJson)
                when (cmd.action) {
                    "advance" -> {
                        val d = java.time.Duration.parse(cmd.duration ?: "PT0S")
                        timeSink.advanceTime(d)
                        log.info("Time advanced by {}", d)
                    }
                    "setTo" -> {
                        val t = java.time.Instant.parse(cmd.instant ?: return@createDispatcher)
                        timeSink.setTime(t)
                        log.info("Time set to {}", t)
                    }
                    "useManual" -> {
                        val t = java.time.Instant.parse(cmd.startAt ?: return@createDispatcher)
                        timeSink.useManual(t)
                        log.info("Switched to ManualClock at {}", t)
                    }
                    "useSystem" -> {
                        timeSink.useSystem()
                        log.info("Switched to SystemClock")
                    }
                    "sweep" -> {
                        core.sweep()
                        log.info("Manual sweep triggered")
                    }
                }
            } catch (e: Exception) {
                log.error("Failed to process time command: {}", e.message)
            }
        }
        dispatcher.subscribe("test.time.v1")
        dispatchers += dispatcher
        log.info("Subscribed to time control on test.time.v1")
    }
}
