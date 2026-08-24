package com.manahive.politica.service.nats

import com.manahive.contracts.EventEnvelope
import com.manahive.contracts.policy.CalibrationChanged
import com.manahive.contracts.policy.PolicyEvent
import com.manahive.contracts.policy.RecordingChanged
import com.manahive.contracts.policy.ResponseChanged
import com.manahive.contracts.policy.EscalationChanged
import com.manahive.messaging.NatsObjectMapper
import com.manahive.messaging.Subjects
import io.nats.client.Connection
import io.nats.client.JetStream
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import jakarta.annotation.PostConstruct
import java.util.UUID

/**
 * Publishes PolicyEvents to their respective JetStream subjects:
 *  - CalibrationChanged -> hub.policy.calibration.v1.<resident>
 *  - ResponseChanged -> hub.policy.response.v1.<resident>
 *  - EscalationChanged -> hub.policy.escalation.v1.<resident>
 *  - RecordingChanged -> hub.policy.recording.v1.<resident>
 *
 * Fowler: "Driving adapter" — outputs domain results to external systems.
 * Vernon: "Publishing domain events" — the politica output becomes bus messages.
 */
@Component
@ConditionalOnProperty(name = ["nats.enabled"], havingValue = "true", matchIfMissing = true)
public class PoliticaNatsEgress(
    private val connection: Connection,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val mapper = NatsObjectMapper.mapper
    private lateinit var jetStream: JetStream

    @PostConstruct
    public fun start() {
        try {
            jetStream = connection.jetStream()
            log.info("Politica NATS egress started")
        } catch (e: Exception) {
            log.warn("NATS not available, egress disabled: {}", e.message)
        }
    }

    /**
     * Publish a PolicyEvent to the appropriate subject based on its type.
     */
    public fun publishPolicyEvent(event: PolicyEvent) {
        val subject = when (event) {
            is CalibrationChanged -> "hub.policy.calibration.v1.${event.residentId.value}"
            is ResponseChanged -> "hub.policy.response.v1.${event.residentId.value}"
            is EscalationChanged -> "hub.policy.escalation.v1.${event.residentId.value}"
            is RecordingChanged -> "hub.policy.recording.v1.${event.residentId.value}"
        }

        try {
            val envelope = EventEnvelope(
                eventId = UUID.randomUUID().toString(),
                type = event::class.simpleName ?: "PolicyEvent",
                version = 1,
                occurredAt = event.at,
                source = "politica-engine",
                payloadJson = mapper.writeValueAsString(event),
            )
            val data = mapper.writeValueAsBytes(envelope)
            jetStream.publish(subject, data)
            log.debug("Published policy event to {}: {}", subject, event::class.simpleName)
        } catch (e: Exception) {
            log.error("Failed to publish policy event to {}: {}", subject, e.message)
        }
    }
}
