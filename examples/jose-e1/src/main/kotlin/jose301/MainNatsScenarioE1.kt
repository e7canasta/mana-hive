package jose301

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.manahive.contracts.EventEnvelope
import com.manahive.contracts.perception.Observation
import com.manahive.messaging.NatsConfig
import com.manahive.messaging.NatsObjectMapper
import com.manahive.messaging.Subjects
import com.manahive.runtime.TimeCommand
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * José E1 via NATS — full integration test.
 *
 * Publishes observations and time commands to NATS.
 * The running NightWatchService processes them.
 * The FileEventWriter saves everything to .out + events.jsonl.
 *
 * Prerequisites:
 * - NATS running (docker run -d --name nats -p 4222:4222 nats:latest)
 * - NightWatchApplication started (./gradlew :engines:night-watch-runtime:bootRun)
 *
 * Run:
 *   ./gradlew :examples:jose-e1:run -Pmain=jose301.MainNatsScenarioE1Kt
 */
fun main() {
    val mapper = NatsObjectMapper.mapper
    val START = Instant.parse("2024-01-15T22:00:00Z")

    val bed = "bed-4"
    val resident = "jose"
    val night = "night-jose-301"
    val monitor = "m1"

    println("═══════════════════════════════════════════════════════════════")
    println("  José E1 — NATS Integration Test")
    println("═══════════════════════════════════════════════════════════════")
    println()

    val conn = NatsConfig.createConnection()
    println("Connected to NATS: ${conn.status}")
    println()

    fun publishEnveloped(subject: String, type: String, at: Instant, payload: Any) {
        val envelope = EventEnvelope(
            eventId = UUID.randomUUID().toString(),
            type = type,
            version = 1,
            occurredAt = at,
            source = "test-client",
            payloadJson = mapper.writeValueAsString(payload),
        )
        conn.publish(subject, mapper.writeValueAsBytes(envelope))
    }

    fun pubTime(cmd: TimeCommand) {
        publishEnveloped("test.time.v1", "TimeCommand", Instant.now(), cmd)
        println("  → TIME: ${cmd.action} ${cmd.duration ?: cmd.instant ?: cmd.startAt ?: ""}")
    }

    fun pubObservation(offset: Duration, kind: String, confidence: Double) {
        val at = START + offset
        val obs = Observation(
            sourceEventId = "nats-test-${offset}",
            monitor = com.manahive.kernel.MonitorId(monitor),
            bed = com.manahive.kernel.BedId(bed),
            kind = com.manahive.contracts.perception.ObservationKind.valueOf(kind),
            confidence = confidence,
            observedAt = at,
        )
        publishEnveloped(Subjects.perceptionObservation(com.manahive.kernel.BedId(bed)), "Observation", at, obs)
        println("  → OBS: $kind at t=$offset (confidence=$confidence)")
    }

    // ── Step 1: Switch to ManualClock ──
    println("── Step 1: useManual(START) ──")
    pubTime(TimeCommand(action = "useManual", startAt = START.toString()))
    Thread.sleep(200)
    println()

    // ── Step 2: obs(IN_BED) at t=0 ──
    println("── Step 2: obs(IN_BED) at t=0s ──")
    pubObservation(Duration.ZERO, "IN_BED", 0.95)
    Thread.sleep(200)
    println()

    // ── Step 3: advance 75m, obs(SITTING_IN_BED) ──
    println("── Step 3: advance(75m) + obs(SITTING_IN_BED) ──")
    pubTime(TimeCommand(action = "advance", duration = "PT75M"))
    Thread.sleep(200)
    pubObservation(Duration.ofMinutes(75), "SITTING_IN_BED", 0.92)
    Thread.sleep(200)
    println()

    // ── Step 4: advance 17m → sweep detects comeback ──
    println("── Step 4: advance(17m) → sweep ──")
    pubTime(TimeCommand(action = "advance", duration = "PT17M"))
    Thread.sleep(200)
    println()

    // ── Step 5: obs(IN_BED) at t=92m ──
    println("── Step 5: obs(IN_BED) at t=92m ──")
    pubObservation(Duration.ofMinutes(92), "IN_BED", 0.94)
    Thread.sleep(200)
    println()

    // ── Step 6: advance to 350m, obs(SITTING_IN_BED) ──
    println("── Step 6: advance(258m) + obs(SITTING_IN_BED) ──")
    pubTime(TimeCommand(action = "advance", duration = "PT258M"))
    Thread.sleep(200)
    pubObservation(Duration.ofMinutes(350), "SITTING_IN_BED", 0.90)
    Thread.sleep(200)
    println()

    // ── Step 7: advance 4m, obs(IN_BED) at t=354m ──
    println("── Step 7: advance(4m) + obs(IN_BED) ──")
    pubTime(TimeCommand(action = "advance", duration = "PT4M"))
    Thread.sleep(200)
    pubObservation(Duration.ofMinutes(354), "IN_BED", 0.93)
    Thread.sleep(200)
    println()

    // ── Step 8: Back to SystemClock ──
    println("── Step 8: useSystem() ──")
    pubTime(TimeCommand(action = "useSystem"))
    Thread.sleep(500)
    println()

    // ── Done ──
    println("═══════════════════════════════════════════════════════════════")
    println("  All events published to NATS")
    println("  Check: events.jsonl in output-jose-e1/")
    println("═══════════════════════════════════════════════════════════════")

    conn.close()
}
