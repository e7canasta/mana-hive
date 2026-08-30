package jose301

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.manahive.contracts.EventEnvelope
import com.manahive.messaging.NatsConfig
import com.manahive.messaging.NatsObjectMapper
import java.time.Instant
import java.util.UUID

fun main() {
    val mapper = NatsObjectMapper.mapper
    val conn = NatsConfig.createConnection()
    val js = conn.jetStream()

    val bed = "bed-4"
    val subject = "recorder.command.v1.$bed"

    println("═══════════════════════════════════════════════════")
    println("  Test NVR — Sending RecordingStarted + Stopped")
    println("═══════════════════════════════════════════════════")

    // 1. RecordingStarted
    val started = mapOf(
        "type" to "RecordingStarted",
        "target" to mapOf(
            "bed" to mapOf("value" to bed),
            "monitor" to mapOf("value" to "m1"),
        ),
        "config" to mapOf("start" to "2024-01-15T23:15:00Z"),
    )
    val startedEnvelope = EventEnvelope(
        eventId = UUID.randomUUID().toString(),
        type = "RecordingStarted",
        version = 1,
        occurredAt = Instant.parse("2024-01-15T23:15:00Z"),
        source = "test-nvr",
        payloadJson = mapper.writeValueAsString(started),
    )
    js.publish(subject, mapper.writeValueAsBytes(startedEnvelope))
    println("→ Sent RecordingStarted to $subject")
    Thread.sleep(1000)

    // 2. RecordingStopped
    val stopped = mapOf(
        "type" to "RecordingStopped",
        "target" to mapOf(
            "bed" to mapOf("value" to bed),
            "monitor" to mapOf("value" to "m1"),
        ),
        "end" to "2024-01-15T23:20:00Z",
        "config" to mapOf("start" to "2024-01-15T23:15:00Z"),
        "context" to mapOf("episode" to mapOf("value" to "ep-test-001")),
    )
    val stoppedEnvelope = EventEnvelope(
        eventId = UUID.randomUUID().toString(),
        type = "RecordingStopped",
        version = 1,
        occurredAt = Instant.parse("2024-01-15T23:20:00Z"),
        source = "test-nvr",
        payloadJson = mapper.writeValueAsString(stopped),
    )
    js.publish(subject, mapper.writeValueAsBytes(stoppedEnvelope))
    println("→ Sent RecordingStopped to $subject")

    Thread.sleep(500)
    println("Done")
    conn.close()
}
