package com.manahive.politica.service.nats

import com.fasterxml.jackson.module.kotlin.readValue
import com.manahive.contracts.EventEnvelope
import com.manahive.contracts.policy.PolicyChangeDetected
import com.manahive.messaging.NatsObjectMapper
import com.manahive.politica.PolicyChangeProcessor
import io.nats.client.Connection
import io.nats.client.Dispatcher
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import java.time.Instant

/**
 * Subscribes to hub.policy.change.v1 and feeds PolicyChangeDetected
 * to the PolicyChangeProcessor.
 *
 * Fowler: "Driving adapter" — initiates domain logic from external input.
 * Vernon: "Inbound port" — the entry point for policy changes.
 */
@Component
@ConditionalOnProperty(name = ["nats.enabled"], havingValue = "true", matchIfMissing = true)
public class PoliticaNatsIngest(
    private val connection: Connection,
    private val processor: PolicyChangeProcessor,
    private val egress: PoliticaNatsEgress,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val mapper = NatsObjectMapper.mapper
    private val dispatchers = mutableListOf<Dispatcher>()

    @PostConstruct
    public fun start() {
        try {
            subscribeToPolicyChanges()
            log.info("Politica NATS ingest listener started")
        } catch (e: Exception) {
            log.warn("NATS not available, ingest listener disabled: {}", e.message)
        }
    }

    @PreDestroy
    public fun stop() {
        dispatchers.clear()
        log.info("Politica NATS ingest listener stopped")
    }

    private fun subscribeToPolicyChanges() {
        try {
            val dispatcher = connection.createDispatcher { msg ->
                try {
                    val payload = String(msg.data)
                    val envelope = mapper.readValue<EventEnvelope>(payload)
                    handlePolicyChange(envelope)
                } catch (e: Exception) {
                    log.error("Failed to process policy change from {}: {}", msg.subject, e.message)
                }
            }
            dispatcher.subscribe("hub.policy.change.v1")
            dispatchers.add(dispatcher)
            log.info("Subscribed to hub.policy.change.v1")
        } catch (e: Exception) {
            log.warn("Failed to subscribe to hub.policy.change.v1: {}", e.message)
        }
    }

    private fun handlePolicyChange(envelope: EventEnvelope) {
        val change = try {
            mapper.readValue<PolicyChangeDetected>(envelope.payloadJson)
        } catch (e: Exception) {
            log.warn("Failed to deserialize PolicyChangeDetected from envelope {}: {}", envelope.eventId, e.message)
            return
        }

        val now = Instant.now()

        // Process the policy change
        val result = processor.process(change, now)

        // Publish emitted events to their respective subjects
        for (event in result.emittedEvents) {
            egress.publishPolicyEvent(event)
            log.debug(
                "Processed policy change for resident {}: {} events emitted",
                result.residentId.value,
                result.emittedEvents.size,
            )
        }
    }
}
