package jose301

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.manahive.contracts.EventEnvelope
import com.manahive.contracts.perception.Observation
import com.manahive.contracts.perception.ObservationKind
import com.manahive.messaging.NatsConfig
import com.manahive.messaging.NatsObjectMapper
import com.manahive.messaging.Subjects
import com.manahive.profile.api.ResidentProfileChanged
import com.manahive.profile.api.ResidentProfileDto
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Cold boot → profile change test.
 *
 * 1. jose starts with STANDARD profile (cold boot from disk)
 * 2. Send full E1 profile via NATS (with comeBack 12/15m)
 * 3. obs(IN_BED) → resident enters LYING
 * 4. obs(SITTING_IN_BED) → resident leaves LYING
 * 5. advance + sweep → COMEBACK_WARNING at 12m
 * 6. advance + sweep → COMEBACK_EXCEEDED at 15m
 * 7. obs(IN_BED) → resident returns, episode closes
 */
fun main() {
    val mapper = NatsObjectMapper.mapper
    val START = Instant.parse("2024-01-15T22:00:00Z")
    val bed = "bed-4"
    val monitor = "m1"

    println("═══════════════════════════════════════════════════════════════")
    println("  Cold Boot → Profile Change → ComeBack Test")
    println("  jose: STANDARD → E1 (comeBack 12/15m)")
    println("═══════════════════════════════════════════════════════════════")
    println()

    val conn = NatsConfig.createConnection()

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

    fun pubTime(action: String, value: String = "") {
        val cmd = when (action) {
            "useManual" -> mapOf("action" to "useManual", "startAt" to START.toString())
            "advance" -> mapOf("action" to "advance", "duration" to value)
            "useSystem" -> mapOf("action" to "useSystem")
            "sweep" -> mapOf("action" to "sweep")
            else -> emptyMap()
        }
        publishEnveloped("test.time.v1", "TimeCommand", Instant.now(), cmd)
        println("  → TIME: $action $value")
    }

    fun pubObservation(offset: Duration, kind: ObservationKind) {
        val at = START + offset
        val obs = Observation(
            sourceEventId = "cold-boot-${offset}",
            monitor = com.manahive.kernel.MonitorId(monitor),
            bed = com.manahive.kernel.BedId(bed),
            kind = kind,
            confidence = 0.95,
            observedAt = at,
        )
        publishEnveloped(Subjects.perceptionObservation(com.manahive.kernel.BedId(bed)), "Observation", at, obs)
        println("  → OBS: $kind at t=$offset")
    }

    // ── Step 1: Switch to ManualClock ──
    println("── Step 1: useManual(START) ──")
    pubTime("useManual")
    Thread.sleep(200)
    println()

    // ── Step 2: Load and send full E1 profile ──
    println("── Step 2: Publish full E1 profile via NATS ──")
    val cpStream = object {}::class.java.getResourceAsStream("/profiles/jose-e1-full.json")
        ?: error("No se encontró profiles/jose-e1-full.json")
    val fullProfile = mapper.readValue<ResidentProfileDto>(cpStream)
    cpStream.close()
    val change = ResidentProfileChanged(
        at = Instant.now().toString(),
        profile = fullProfile,
    )
    publishEnveloped(Subjects.residentProfile(), "ResidentProfileChanged", Instant.now(), change)
    println("  → Profile sent: jose v2 (comeBack 12/15m)")
    Thread.sleep(500)
    println()

    // ── Step 3: obs(IN_BED) at t=0 → resident enters LYING ──
    println("── Step 3: obs(IN_BED) at t=0s → resident in LYING ──")
    pubObservation(Duration.ZERO, ObservationKind.IN_BED)
    Thread.sleep(200)
    println()

    // ── Step 4: obs(SITTING_IN_BED) at t=1m → leaves LYING ──
    println("── Step 4: obs(SITTING_IN_BED) at t=1m → leaves LYING ──")
    pubObservation(Duration.ofMinutes(1), ObservationKind.SITTING_IN_BED)
    Thread.sleep(200)
    println()

    // ── Step 5: advance 11m → sweep: COMEBACK_WARNING (12m from leaving at t=1m) ──
    println("── Step 5: advance(11m) → sweep: COMEBACK_WARNING ──")
    pubTime("advance", "PT11M")
    Thread.sleep(200)
    pubTime("sweep")
    Thread.sleep(200)
    println()

    // ── Step 6: advance 5m → sweep: COMEBACK_EXCEEDED (16m from leaving, >15m threshold) ──
    println("── Step 6: advance(5m) → sweep: COMEBACK_EXCEEDED ──")
    pubTime("advance", "PT5M")
    Thread.sleep(200)
    pubTime("sweep")
    Thread.sleep(200)
    println()

    // ── Step 7: obs(IN_BED) at t=17m → resident returns, episode closes ──
    println("── Step 7: obs(IN_BED) at t=17m → episode closes ──")
    pubObservation(Duration.ofMinutes(17), ObservationKind.IN_BED)
    Thread.sleep(200)
    println()

    // ── Step 8: Back to SystemClock ──
    println("── Step 8: useSystem() ──")
    pubTime("useSystem")
    Thread.sleep(500)
    println()

    println("═══════════════════════════════════════════════════════════════")
    println("  Done — Check logs for:")
    println("    1. Alta de jose con perfil v1 (STANDARD from disk)")
    println("    2. Recalibrado jose con perfil v2 (E1 via NATS)")
    println("    3. COMEBACK_WARNING at 12m")
    println("    4. COMEBACK_EXCEEDED at 15m")
    println("    5. EPISODE_OPENED + EPISODE_CLOSED")
    println("═══════════════════════════════════════════════════════════════")

    conn.close()
}
