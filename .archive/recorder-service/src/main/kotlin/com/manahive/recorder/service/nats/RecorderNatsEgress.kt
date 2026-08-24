package com.manahive.recorder.service.nats

import com.manahive.contracts.EventEnvelope
import com.manahive.recorder.EvidenceRecord
import com.manahive.recorder.RecordingCommand
import com.manahive.messaging.NatsObjectMapper
import io.nats.client.Connection
import io.nats.client.JetStream
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Publishes RecordingCommands and EvidenceRecords to NATS JetStream.
 *
 * Fowler: "Driving adapter" — outputs domain results to external systems.
 * Vernon: "Publishing domain events" — the engine's output becomes bus messages.
 *
 * Subjects:
 *  - recorder.command.v1.<bed>  — recording commands for NVR adapter
 *  - evidence.record.v1.<bed>   — evidence records for tracking
 */
@Component
@ConditionalOnProperty(name = ["nats.enabled"], havingValue = "true", matchIfMissing = true)
public class RecorderNatsEgress(
    private val connection: Connection,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val mapper = NatsObjectMapper.mapper
    private lateinit var jetStream: JetStream

    @jakarta.annotation.PostConstruct
    public fun start() {
        try {
            jetStream = connection.jetStream()
            log.info("Recorder NATS egress started")
        } catch (e: Exception) {
            log.warn("NATS not available, egress disabled: {}", e.message)
        }
    }

    /**
     * Publish recording commands to recorder.command.v1.<bed>.
     */
    public fun publishCommands(commands: List<RecordingCommand>) {
        for (command in commands) {
            try {
                val subject = "recorder.command.v1.${command.bed.value}"
                val envelope = EventEnvelope(
                    eventId = UUID.randomUUID().toString(),
                    type = command::class.simpleName ?: "RecordingCommand",
                    version = 1,
                    occurredAt = command.at,
                    source = "recorder-engine",
                    payloadJson = mapper.writeValueAsString(command),
                )
                val data = mapper.writeValueAsBytes(envelope)
                jetStream.publish(subject, data)
                log.debug("Published recording command to {}: {}", subject, command::class.simpleName)
            } catch (e: Exception) {
                log.error("Failed to publish recording command: {}", e.message)
            }
        }
    }

    /**
     * Publish evidence records to evidence.record.v1.<bed>.
     */
    public fun publishEvidenceRecords(records: List<EvidenceRecord>) {
        for (record in records) {
            try {
                val subject = "evidence.record.v1.${record.bed.value}"
                val envelope = EventEnvelope(
                    eventId = UUID.randomUUID().toString(),
                    type = record::class.simpleName ?: "EvidenceRecord",
                    version = 1,
                    occurredAt = record.at,
                    source = "recorder-engine",
                    payloadJson = mapper.writeValueAsString(record),
                )
                val data = mapper.writeValueAsBytes(envelope)
                jetStream.publish(subject, data)
                log.debug("Published evidence record to {}: {}", subject, record::class.simpleName)
            } catch (e: Exception) {
                log.error("Failed to publish evidence record: {}", e.message)
            }
        }
    }
}
