package com.manahive.hub.nats

import com.manahive.contracts.EventEnvelope
import com.manahive.contracts.policy.AlarmProfile
import com.manahive.contracts.policy.EffectiveRules
import com.manahive.hub.policy.PolicyEventPublisher
import com.manahive.kernel.ResidentId
import com.manahive.messaging.NatsObjectMapper
import com.manahive.messaging.Subjects
import io.nats.client.Connection
import io.nats.client.JetStream
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import jakarta.annotation.PostConstruct
import java.time.Instant
import java.util.UUID

/**
 * Publishes policy events to JetStream.
 *
 * Implements [PolicyEventPublisher] for PolicyChangeDetected events
 * (hub → Politica Engine) and publishes EffectiveRules events
 * (hub → Sentinel).
 *
 * Fowler: "Driving adapter" — outputs policy resolution results to the bus.
 * Vernon: "Publishing domain events" — the hub's policy output becomes bus
 * messages consumed by downstream engines.
 */
@Component
@ConditionalOnProperty(name = ["nats.enabled"], havingValue = "true", matchIfMissing = true)
public class PolicyNatsEgress(
    private val connection: Connection,
) : PolicyEventPublisher {

    private val log = LoggerFactory.getLogger(javaClass)
    private val mapper = NatsObjectMapper.mapper
    private lateinit var jetStream: JetStream

    @PostConstruct
    public fun start() {
        try {
            jetStream = connection.jetStream()
            log.info("Policy NATS egress started")
        } catch (e: Exception) {
            log.warn("NATS not available, egress disabled: {}", e.message)
        }
    }

    /**
     * Publish a PolicyChangeDetected event to hub.policy.change.v1.
     *
     * Politica Engine subscribes to this subject, resolves the profile
     * against the catalog, and publishes per-category events to downstream
     * engines.
     */
    override fun publishPolicyChange(
        residentId: ResidentId,
        snapshot: AlarmProfile,
        at: Instant,
    ) {
        try {
            val subject = Subjects.policyChangeDetected()
            val change = com.manahive.contracts.policy.PolicyChangeDetected(
                residentId = residentId,
                at = at,
                snapshot = snapshot,
            )
            val envelope = EventEnvelope(
                eventId = UUID.randomUUID().toString(),
                type = "PolicyChangeDetected",
                version = 1,
                occurredAt = at,
                source = "hub",
                payloadJson = mapper.writeValueAsString(change),
            )
            val data = mapper.writeValueAsBytes(envelope)
            jetStream.publish(subject, data)
            log.debug("Published PolicyChangeDetected to {} for resident {}", subject, residentId.value)
        } catch (e: Exception) {
            log.error("Failed to publish PolicyChangeDetected for resident {}: {}", residentId.value, e.message)
        }
    }

    /**
     * Publish effective rules for a resident to hub.policy.effective-rules.v1.<resident>.
     */
    public fun publishEffectiveRules(residentId: ResidentId, rules: EffectiveRules, at: Instant = Instant.now()) {
        try {
            val subject = Subjects.effectiveRules(residentId)
            val envelope = EventEnvelope(
                eventId = UUID.randomUUID().toString(),
                type = "EffectiveRules",
                version = 1,
                occurredAt = at,
                source = "hub",
                payloadJson = mapper.writeValueAsString(rules),
            )
            val data = mapper.writeValueAsBytes(envelope)
            jetStream.publish(subject, data)
            log.debug("Published effective rules to {} for resident {}", subject, residentId.value)
        } catch (e: Exception) {
            log.error("Failed to publish effective rules for resident {}: {}", residentId.value, e.message)
        }
    }
}
