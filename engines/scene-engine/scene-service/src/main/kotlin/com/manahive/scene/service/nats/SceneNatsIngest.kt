package com.manahive.scene.service.nats

import com.fasterxml.jackson.module.kotlin.readValue
import com.manahive.contracts.EventEnvelope
import com.manahive.contracts.perception.Observation
import com.manahive.kernel.BedId
import com.manahive.kernel.NightId
import com.manahive.messaging.NatsObjectMapper
import com.manahive.messaging.Subjects
import com.manahive.scene.calibration.SceneCalibration
import com.manahive.scene.core.DigitalTwin
import com.manahive.scene.interpreter.SceneInterpreter
import com.manahive.scene.sweeper.ClockSweeper
import com.manahive.scene.sweeper.DwellMarks
import com.manahive.scene.calibration.toDwellCatalog
import io.nats.client.Connection
import io.nats.client.Dispatcher
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Subscribes to perception.observation.v1.> stream and feeds observations
 * to the SceneInterpreter, maintaining DigitalTwin state per bed.
 *
 * Fowler: "Driving adapter" — initiates domain logic from external input.
 * Vernon: "Inbound port" — the entry point for perception data.
 */
@Component
@ConditionalOnProperty(name = ["nats.enabled"], havingValue = "true", matchIfMissing = true)
public class SceneNatsIngest(
    private val connection: Connection,
    private val interpreter: SceneInterpreter,
    private val sweeper: ClockSweeper,
    private val calibration: SceneCalibration,
    private val egress: SceneNatsEgress,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val mapper = NatsObjectMapper.mapper
    private val dispatchers = mutableListOf<Dispatcher>()

    /** DigitalTwin state per bed — maintained across observations. */
    private val twins = ConcurrentHashMap<BedId, DigitalTwin>()

    /** Dwell marks for sweep engine — synchronized for thread safety. */
    @Volatile
    private var dwellMarks = DwellMarks.NONE
    private val dwellMarksLock = Any()

    @PostConstruct
    public fun start() {
        try {
            subscribeToObservations()
            log.info("Scene NATS ingest listener started")
        } catch (e: Exception) {
            log.warn("NATS not available, ingest listener disabled: {}", e.message)
        }
    }

    @PreDestroy
    public fun stop() {
        dispatchers.clear()
        log.info("Scene NATS ingest listener stopped")
    }

    /**
     * Get all active twins for sweep.
     */
    public fun activeTwins(): Collection<DigitalTwin> = twins.values

    /**
     * Run the sweep engine on all active twins.
     * Called by the sweep scheduler.
     */
    public fun sweep(now: Instant) {
        val activeTwins = twins.values
        if (activeTwins.isEmpty()) return

        synchronized(dwellMarksLock) {
            val explained = sweeper.sweep(activeTwins, now, calibration.toDwellCatalog(), dwellMarks)
            val result = explained.value

            dwellMarks = result.marks
            for (fact in result.facts) {
                egress.publishFact(fact)
                log.debug("Sweep emitted fact: {} for bed {}", fact::class.simpleName, fact.bed.value)
            }
        }
    }

    private fun subscribeToObservations() {
        try {
            val dispatcher = connection.createDispatcher { msg ->
                try {
                    val payload = String(msg.data)
                    val envelope = mapper.readValue<EventEnvelope>(payload)
                    handleObservation(envelope)
                } catch (e: Exception) {
                    log.error("Failed to process observation from {}: {}", msg.subject, e.message)
                }
            }
            dispatcher.subscribe(Subjects.PERCEPTION_WILDCARD)
            dispatchers.add(dispatcher)
            log.info("Subscribed to PERCEPTION stream ({})", Subjects.PERCEPTION_WILDCARD)
        } catch (e: Exception) {
            log.warn("Failed to subscribe to PERCEPTION stream: {}", e.message)
        }
    }

    private fun handleObservation(envelope: EventEnvelope) {
        val observation = try {
            mapper.readValue<Observation>(envelope.payloadJson)
        } catch (e: Exception) {
            log.warn("Failed to deserialize Observation from envelope {}: {}", envelope.eventId, e.message)
            return
        }

        val now = Instant.now()
        val bed = observation.bed

        // Get or create DigitalTwin for this bed (thread-safe via ConcurrentHashMap.compute)
        val twin = twins.compute(bed) { _, existing ->
            existing ?: DigitalTwin(
                bed = bed,
                night = NightId("default"),
                occupant = null,
                state = com.manahive.contracts.scene.PersonState.Unknown(
                    com.manahive.contracts.scene.UnknownCause.SIGNAL_LOST
                ),
                stateSince = now,
                signal = com.manahive.scene.core.SignalHealth(
                    monitor = observation.monitor,
                    lastHeartbeat = observation.observedAt,
                    lost = false,
                ),
                calibration = calibration,
            )
        }!!

        // Interpret the observation
        val explained = interpreter.interpret(twin, observation, now)
        val verdict = explained.value

        // Update twin state (thread-safe via ConcurrentHashMap.put)
        twins[bed] = verdict.twin

        // Publish generated facts
        for (fact in verdict.facts) {
            egress.publishFact(fact)
            log.debug(
                "Interpreted observation: {} -> {} facts, explanation: {}",
                observation.kind,
                verdict.facts.size,
                explained.explanation.map { it.conclusion },
            )
        }
    }
}
