package com.manahive.harbor.service.nats

import com.manahive.serialization.SentinelSignalSerializer
import com.fasterxml.jackson.module.kotlin.readValue
import com.manahive.contracts.EventEnvelope
import com.manahive.contracts.sentinel.SentinelSignal
import com.manahive.harbor.HarborEngine
import com.manahive.harbor.HarborState
import com.manahive.messaging.NatsObjectMapper
import com.manahive.messaging.Subjects
import io.nats.client.Connection
import io.nats.client.Dispatcher
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import java.time.Instant

/**
 * Subscribes to sentinel.signal.v1.> stream and feeds SentinelSignals
 * to the HarborEngine, maintaining HarborState (notices + budget).
 *
 * Fowler: "Driving adapter" — initiates domain logic from external input.
 * Vernon: "Inbound port" — the entry point for sentinel signals.
 */
@Component
@ConditionalOnProperty(name = ["nats.enabled"], havingValue = "true", matchIfMissing = true)
public class HarborNatsIngest(
    private val connection: Connection,
    private val engine: HarborEngine,
    private val egress: HarborNatsEgress,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val mapper = NatsObjectMapper.mapper
    private val dispatchers = mutableListOf<Dispatcher>()

    /** HarborState — synchronized for thread safety. */
    @Volatile
    private var state = HarborState()
    private val stateLock = Any()

    @PostConstruct
    public fun start() {
        try {
            subscribeToSentinelSignals()
            log.info("Harbor NATS ingest listener started")
        } catch (e: Exception) {
            log.warn("NATS not available, ingest listener disabled: {}", e.message)
        }
    }

    @PreDestroy
    public fun stop() {
        dispatchers.clear()
        log.info("Harbor NATS ingest listener stopped")
    }

    private fun subscribeToSentinelSignals() {
        try {
            val dispatcher = connection.createDispatcher { msg ->
                try {
                    val payload = String(msg.data)
                    val envelope = mapper.readValue<EventEnvelope>(payload)
                    handleSentinelSignal(envelope)
                } catch (e: Exception) {
                    log.error("Failed to process sentinel signal from {}: {}", msg.subject, e.message)
                }
            }
            dispatcher.subscribe(Subjects.SENTINEL_WILDCARD)
            dispatchers.add(dispatcher)
            log.info("Subscribed to SENTINEL stream ({})", Subjects.SENTINEL_WILDCARD)
        } catch (e: Exception) {
            log.warn("Failed to subscribe to SENTINEL stream: {}", e.message)
        }
    }

    private fun handleSentinelSignal(envelope: EventEnvelope) {
        val signal = try {
            SentinelSignalSerializer.fromJson(envelope.payloadJson)
        } catch (e: Exception) {
            log.warn("Failed to deserialize SentinelSignal from envelope {}: {}", envelope.eventId, e.message)
            return
        }

        val now = signal.at

        synchronized(stateLock) {
            val explained = engine.evaluate(signal, state, now)
            val verdict = explained.value

            // Update state
            state = verdict.state

            // Publish alarm events for dispatch commands
            for (command in verdict.commands) {
                egress.publishAlarmEvent(command, signal, now)
                log.debug(
                    "Evaluated signal: {} -> {} commands, explanation: {}",
                    signal::class.simpleName,
                    verdict.commands.size,
                    explained.explanation.map { it.conclusion },
                )
            }
        }
    }
}
