package com.manahive.sentinel.service.nats

import com.manahive.serialization.SentinelSignalSerializer
import com.manahive.contracts.EventEnvelope
import com.manahive.contracts.sentinel.SentinelSignal
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
 * Publishes SentinelSignals to sentinel.signal.v1.<bed> JetStream.
 *
 * Fowler: "Driving adapter" — outputs domain results to external systems.
 * Vernon: "Publishing domain events" — the sentinel's output becomes bus messages.
 */
@Component
@ConditionalOnProperty(name = ["nats.enabled"], havingValue = "true", matchIfMissing = true)
public class SentinelNatsEgress(
    private val connection: Connection,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val mapper = NatsObjectMapper.mapper
    private lateinit var jetStream: JetStream

    @PostConstruct
    public fun start() {
        try {
            jetStream = connection.jetStream()
            log.info("Sentinel NATS egress started")
        } catch (e: Exception) {
            log.warn("NATS not available, egress disabled: {}", e.message)
        }
    }

    /**
     * Publish a sentinel signal to sentinel.signal.v1.<bed>.
     */
    public fun publishSignal(signal: SentinelSignal) {
        try {
            val subject = Subjects.sentinelSignal(signal.bed)
            val envelope = EventEnvelope(
                eventId = UUID.randomUUID().toString(),
                type = signal::class.simpleName ?: "SentinelSignal",
                version = 1,
                occurredAt = signal.at,
                source = "sentinel",
                payloadJson = SentinelSignalSerializer.toJson(signal),
            )
            val data = mapper.writeValueAsBytes(envelope)
            jetStream.publish(subject, data)
            log.debug("Published sentinel signal to {}: {}", subject, signal::class.simpleName)
        } catch (e: Exception) {
            log.error("Failed to publish sentinel signal: {}", e.message)
        }
    }
}
