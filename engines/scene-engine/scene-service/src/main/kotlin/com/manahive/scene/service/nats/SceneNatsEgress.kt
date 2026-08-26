package com.manahive.scene.service.nats

import com.manahive.serialization.SceneEventSerializer
import com.manahive.contracts.EventEnvelope
import com.manahive.contracts.scene.SceneFact
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
 * Publishes SceneFacts to scene.fact.v1.<bed> JetStream.
 *
 * Fowler: "Driving adapter" — outputs domain results to external systems.
 * Vernon: "Publishing domain events" — the twin's output becomes bus messages.
 */
@Component
@ConditionalOnProperty(name = ["nats.enabled"], havingValue = "true", matchIfMissing = true)
public class SceneNatsEgress(
    private val connection: Connection,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val mapper = NatsObjectMapper.mapper
    private lateinit var jetStream: JetStream

    @PostConstruct
    public fun start() {
        try {
            jetStream = connection.jetStream()
            log.info("Scene NATS egress started")
        } catch (e: Exception) {
            log.warn("NATS not available, egress disabled: {}", e.message)
        }
    }

    /**
     * Publish a scene fact to scene.fact.v1.<bed>.
     */
    public fun publishFact(fact: SceneFact) {
        try {
            val subject = Subjects.sceneFact(fact.bed)
            val envelope = EventEnvelope(
                eventId = UUID.randomUUID().toString(),
                type = fact::class.simpleName ?: "SceneFact",
                version = 1,
                occurredAt = fact.at,
                source = "scene-engine",
                payloadJson = SceneEventSerializer.toJson(fact),
            )
            val data = mapper.writeValueAsBytes(envelope)
            jetStream.publish(subject, data)
            log.debug("Published scene fact to {}: {}", subject, fact::class.simpleName)
        } catch (e: Exception) {
            log.error("Failed to publish scene fact: {}", e.message)
        }
    }
}
