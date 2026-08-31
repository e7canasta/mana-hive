package com.manahive.runtime.control

import com.manahive.contracts.EventEnvelope
import com.manahive.messaging.BusEvents
import com.manahive.messaging.NatsObjectMapper
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Adapter — publishes HiveControlEvent to `hive.control.v1`.
 */
class NatsControlEventPublisher(
    private val events: BusEvents,
) : ControlEventPublisher {
    private val log = LoggerFactory.getLogger(javaClass)
    private val mapper = NatsObjectMapper.mapper

    override fun publish(event: HiveControlEvent) {
        val conn = events.connection ?: run {
            log.warn("No NATS connection, control event not published {}", event.type)
            return
        }
        try {
            val payload = mapper.writeValueAsString(event)
            val envelope = EventEnvelope(
                eventId = UUID.randomUUID().toString(),
                type = "HiveControlEvent",
                version = 1,
                occurredAt = event.at,
                source = "night-watch-runtime",
                payloadJson = payload,
            )
            // Plain publish (no JetStream stream needed for control)
            conn.publish("hive.control.v1", mapper.writeValueAsBytes(envelope))
            log.info("Published HiveControlEvent {} for {} on hive.control.v1", event.type, event.residentId)
        } catch (e: Exception) {
            log.error("Failed to publish HiveControlEvent: {}", e.message, e)
        }
    }
}
