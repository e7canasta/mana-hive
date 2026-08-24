package com.manahive.sentinel.service.nats

import com.fasterxml.jackson.module.kotlin.readValue
import com.manahive.contracts.EventEnvelope
import com.manahive.contracts.scene.SceneFact
import com.manahive.kernel.ResidentId
import com.manahive.messaging.NatsObjectMapper
import com.manahive.messaging.Subjects
import com.manahive.sentinel.EpisodeLedger
import com.manahive.sentinel.SentinelCalibration
import com.manahive.sentinel.SentinelEvaluator
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
 * Subscribes to scene.fact.v1.> stream and feeds SceneFacts
 * to the SentinelEvaluator, maintaining EpisodeLedger state per resident.
 *
 * Fowler: "Driving adapter" — initiates domain logic from external input.
 * Vernon: "Inbound port" — the entry point for scene facts.
 */
@Component
@ConditionalOnProperty(name = ["nats.enabled"], havingValue = "true", matchIfMissing = true)
public class SentinelNatsIngest(
    private val connection: Connection,
    private val evaluator: SentinelEvaluator,
    private val calibration: SentinelCalibration,
    private val egress: SentinelNatsEgress,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val mapper = NatsObjectMapper.mapper
    private val dispatchers = mutableListOf<Dispatcher>()

    /** EpisodeLedger state per resident — maintained across facts. */
    private val ledgers = ConcurrentHashMap<ResidentId, EpisodeLedger>()

    @PostConstruct
    public fun start() {
        try {
            subscribeToSceneFacts()
            log.info("Sentinel NATS ingest listener started")
        } catch (e: Exception) {
            log.warn("NATS not available, ingest listener disabled: {}", e.message)
        }
    }

    @PreDestroy
    public fun stop() {
        dispatchers.clear()
        log.info("Sentinel NATS ingest listener stopped")
    }

    private fun subscribeToSceneFacts() {
        try {
            val dispatcher = connection.createDispatcher { msg ->
                try {
                    val payload = String(msg.data)
                    val envelope = mapper.readValue<EventEnvelope>(payload)
                    handleSceneFact(envelope)
                } catch (e: Exception) {
                    log.error("Failed to process scene fact from {}: {}", msg.subject, e.message)
                }
            }
            dispatcher.subscribe(Subjects.SCENE_WILDCARD)
            dispatchers.add(dispatcher)
            log.info("Subscribed to SCENE stream ({})", Subjects.SCENE_WILDCARD)
        } catch (e: Exception) {
            log.warn("Failed to subscribe to SCENE stream: {}", e.message)
        }
    }

    private fun handleSceneFact(envelope: EventEnvelope) {
        val fact = try {
            mapper.readValue<SceneFact>(envelope.payloadJson)
        } catch (e: Exception) {
            log.warn("Failed to deserialize SceneFact from envelope {}: {}", envelope.eventId, e.message)
            return
        }

        val now = Instant.now()
        val residentId = calibration.residentId

        // Get or create EpisodeLedger for this resident
        val ledger = ledgers.getOrPut(residentId) {
            EpisodeLedger.empty(residentId, calibration.fatigue)
        }

        // Evaluate the fact
        val explained = evaluator.evaluate(fact, ledger, now)
        val verdict = explained.value

        // Update ledger state
        ledgers[residentId] = verdict.episodes

        // Publish generated signals
        for (signal in verdict.signals) {
            egress.publishSignal(signal)
            log.debug(
                "Evaluated fact: {} -> {} signals, explanation: {}",
                fact::class.simpleName,
                verdict.signals.size,
                explained.explanation.map { it.conclusion },
            )
        }
    }
}
