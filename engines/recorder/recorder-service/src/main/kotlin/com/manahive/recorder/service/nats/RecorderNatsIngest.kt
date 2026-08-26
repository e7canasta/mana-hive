package com.manahive.recorder.service.nats

import com.fasterxml.jackson.module.kotlin.readValue
import com.manahive.contracts.EventEnvelope
import com.manahive.contracts.scene.SceneFact
import com.manahive.contracts.sentinel.SentinelSignal
import com.manahive.messaging.NatsObjectMapper
import com.manahive.messaging.Subjects
import com.manahive.recorder.RecordingLedger
import com.manahive.recorder.RecordingTrigger
import com.manahive.recorder.RecorderEngine
import com.manahive.recorder.SceneFactTrigger
import com.manahive.recorder.SentinelSignalTrigger
import io.nats.client.Connection
import io.nats.client.Dispatcher
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import java.time.Instant

/**
 * Subscribes to scene.fact.v1.> and sentinel.signal.v1.> streams,
 * converts incoming events to RecordingTriggers, and feeds them to the RecorderEngine.
 *
 * Fowler: "Adapter" — converts NATS messages to domain triggers.
 * Vernon: "Driving adapter" — initiates domain logic from external input.
 */
@Component
@ConditionalOnProperty(name = ["nats.enabled"], havingValue = "true", matchIfMissing = true)
public class RecorderNatsIngest(
    private val connection: Connection,
    private val engine: RecorderEngine,
    private val egress: RecorderNatsEgress,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val mapper = NatsObjectMapper.mapper
    private val dispatchers = mutableListOf<Dispatcher>()
    private var ledger = RecordingLedger()

    @PostConstruct
    public fun start() {
        try {
            subscribeToStreams()
            log.info("Recorder NATS ingest listener started")
        } catch (e: Exception) {
            log.warn("NATS not available, ingest listener disabled: {}", e.message)
        }
    }

    @PreDestroy
    public fun stop() {
        dispatchers.clear()
        log.info("Recorder NATS ingest listener stopped")
    }

    private fun subscribeToStreams() {
        val streams = mapOf(
            "SCENE" to Subjects.SCENE_WILDCARD,
            "SENTINEL" to Subjects.SENTINEL_WILDCARD,
        )

        streams.forEach { (name, subject) ->
            try {
                val dispatcher = connection.createDispatcher { msg ->
                    try {
                        val payload = String(msg.data)
                        val envelope = mapper.readValue<EventEnvelope>(payload)
                        handleEvent(name, envelope)
                    } catch (e: Exception) {
                        log.error("Failed to process recorder event from {}: {}", msg.subject, e.message)
                    }
                }
                dispatcher.subscribe(subject)
                dispatchers.add(dispatcher)
                log.info("Subscribed to {} stream ({})", name, subject)
            } catch (e: Exception) {
                log.warn("Failed to subscribe to {} stream: {}", name, e.message)
            }
        }
    }

    private fun handleEvent(streamName: String, envelope: EventEnvelope) {
        val trigger = when (streamName) {
            "SCENE" -> deserializeSceneFactTrigger(envelope)
            "SENTINEL" -> deserializeSentinelSignalTrigger(envelope)
            else -> {
                log.debug("Ignoring event from unknown stream: {}", streamName)
                return
            }
        }

        if (trigger == null) {
            log.warn("Failed to deserialize trigger from envelope {}", envelope.eventId)
            return
        }

        val now = trigger.at
        val explained = engine.evaluate(trigger, ledger, now)
        val verdict = explained.value

        // Update local ledger
        ledger = verdict.ledger

        // Publish commands and evidence records
        egress.publishCommands(verdict.commands)
        egress.publishEvidenceRecords(verdict.evidenceRecords)

        log.debug(
            "Processed trigger from {}: {} commands, {} evidence records, explanation: {}",
            streamName,
            verdict.commands.size,
            verdict.evidenceRecords.size,
            explained.explanation.map { it.conclusion },
        )
    }

    private fun deserializeSceneFactTrigger(envelope: EventEnvelope): RecordingTrigger? {
        return try {
            val fact = mapper.readValue<SceneFact>(envelope.payloadJson)
            SceneFactTrigger(
                fact = fact,
                bed = fact.bed,
                at = fact.at,
            )
        } catch (e: Exception) {
            log.warn("Failed to deserialize SceneFact: {}", e.message)
            null
        }
    }

    private fun deserializeSentinelSignalTrigger(envelope: EventEnvelope): RecordingTrigger? {
        return try {
            val signal = mapper.readValue<SentinelSignal>(envelope.payloadJson)
            SentinelSignalTrigger(
                signal = signal,
                bed = signal.bed,
                at = signal.at,
            )
        } catch (e: Exception) {
            log.warn("Failed to deserialize SentinelSignal: {}", e.message)
            null
        }
    }
}
