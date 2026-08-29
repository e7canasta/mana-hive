package com.manahive.hub.nats

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.manahive.contracts.EventEnvelope
import com.manahive.hub.ledger.EventStore
import com.manahive.messaging.BusEvents
import com.manahive.messaging.Subjects
import io.nats.client.Connection
import io.nats.client.Dispatcher
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy

/**
 * Subscribes to all engine event streams (SCENE, SENTINEL, ALARM) and
 * stores events in the Hub ledger.
 *
 * Fowler: "Adapter" — converts NATS messages to domain events.
 *
 * Vernon: "Infrastructure layer" — handles NATS connectivity.
 */
@Component
@ConditionalOnProperty(name = ["nats.enabled"], havingValue = "true", matchIfMissing = true)
public class NatsIngestListener(
    private val events: BusEvents,
    private val eventStore: EventStore,
    private val mapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val dispatchers = mutableListOf<Dispatcher>()

    @PostConstruct
    public fun start() {
        // No bloquea: se suscribe cuando el bus aparece, y otra vez en cada
        // reconexión. El hub tiene que poder arrancar antes que NATS.
        events.onConnected { subscribeSafely() }
        if (events.connected) subscribeSafely()
    }

    private fun subscribeSafely() {
        try {
            subscribeToStreams()
            log.info("NATS ingest listener started")
        } catch (e: Exception) {
            log.warn("No se pudo suscribir al bus: {}", e.message)
        }
    }

    @PreDestroy
    public fun stop() {
        // Note: NATS Dispatcher doesn't have a clean shutdown method.
        // The connection.close() in Spring will handle cleanup.
        dispatchers.clear()
        log.info("NATS ingest listener stopped")
    }

    private fun subscribeToStreams() {
        val streams = mapOf(
            "SCENE" to Subjects.SCENE_WILDCARD,
            "SENTINEL" to Subjects.SENTINEL_WILDCARD,
            "ALARM" to Subjects.ALARM_WILDCARD,
        )

        streams.forEach { (name, subject) ->
            try {
                val connection = events.connection ?: return
                val dispatcher = connection.createDispatcher { msg ->
                    try {
                        val payload = String(msg.data)
                        val envelope = mapper.readValue<EventEnvelope>(payload)
                        val streamName = msg.subject.substringBefore(".")
                        eventStore.store(streamName, listOf(envelope))
                        log.debug("Ingested event from {}: {}", msg.subject, envelope.eventId)
                    } catch (e: Exception) {
                        log.error("Failed to ingest event from {}: {}", msg.subject, e.message)
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
}
