package jose301

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.manahive.contracts.EventEnvelope
import com.manahive.contracts.perception.Observation
import com.manahive.contracts.perception.ObservationKind
import com.manahive.kernel.BedId
import com.manahive.kernel.MonitorId
import com.manahive.messaging.NatsConfig
import com.manahive.messaging.NatsObjectMapper
import com.manahive.messaging.Subjects
import com.manahive.profile.api.ResidentProfileChanged
import com.manahive.profile.api.ResidentProfileDto
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Policy change test — sends full ResidentProfileDto via NATS.
 *
 * Reads jose-e1-full.json and publishes it as ResidentProfileChanged.
 * NightWatchService receives it and recalibrates.
 */
fun main() {
    val mapper = NatsObjectMapper.mapper
    val START = Instant.parse("2024-01-15T22:00:00Z")
    val bed = BedId("bed-4")
    val monitor = MonitorId("m1")

    println("═══════════════════════════════════════════════════════════════")
    println("  Policy Change Test — Full ResidentProfileDto")
    println("═══════════════════════════════════════════════════════════════")
    println()

    // Load full profile from JSON
    val cpStream = object {}::class.java.getResourceAsStream("/profiles/jose-e1-full.json")
        ?: error("No se encontró profiles/jose-e1-full.json")
    val profile = mapper.readValue<ResidentProfileDto>(cpStream)
    cpStream.close()
    println("  Profile loaded: ${profile.profileId} (v${profile.version})")
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
            else -> emptyMap()
        }
        publishEnveloped("test.time.v1", "TimeCommand", Instant.now(), cmd)
        println("  → TIME: $action $value")
    }

    fun pubObservation(offset: Duration, kind: ObservationKind) {
        val at = START + offset
        val obs = Observation(
            monitor = monitor,
            bed = bed,
            kind = kind,
            confidence = 0.95,
            observedAt = at,
        )
        publishEnveloped(Subjects.perceptionObservation(bed), "Observation", at, obs)
        println("  → OBS: $kind at t=$offset")
    }

    fun pubProfileChange() {
        val change = ResidentProfileChanged(
            at = Instant.now().toString(),
            profile = profile,
        )
        publishEnveloped(Subjects.residentProfile(), "ResidentProfileChanged", Instant.now(), change)
        println("  → PROFILE: ${profile.profileId} published")
    }

    // ── Step 1: Switch to ManualClock ──
    println("── Step 1: useManual(START) ──")
    pubTime("useManual")
    Thread.sleep(200)
    println()

    // ── Step 2: obs(SITTING_IN_BED) at t=0 ──
    println("── Step 2: obs(SITTING_IN_BED) at t=0s ──")
    pubObservation(Duration.ZERO, ObservationKind.SITTING_IN_BED)
    Thread.sleep(200)
    println()

    // ── Step 3: advance 15m → sweep: NADA (STANDARD) ──
    println("── Step 3: advance(15m) → sweep: NADA (STANDARD no alerta) ──")
    pubTime("advance", "PT15M")
    Thread.sleep(200)
    println()

    // ── Step 4: Send full profile ──
    println("── Step 4: publish full ResidentProfileDto ──")
    pubProfileChange()
    Thread.sleep(200)
    println()

    // ── Step 5: advance 5m → sweep: DWELL_WARNING ──
    println("── Step 5: advance(5m) → sweep: DWELL_WARNING (12m threshold) ──")
    pubTime("advance", "PT5M")
    Thread.sleep(200)
    println()

    // ── Step 6: advance 5m → sweep: DWELL_EXCEEDED ──
    println("── Step 6: advance(5m) → sweep: DWELL_EXCEEDED (15m threshold) ──")
    pubTime("advance", "PT5M")
    Thread.sleep(200)
    println()

    // ── Step 7: obs(LYING) → episode closes ──
    println("── Step 7: obs(IN_BED) → episode closes ──")
    pubObservation(Duration.ofMinutes(25), ObservationKind.IN_BED)
    Thread.sleep(200)
    println()

    // ── Step 8: Back to SystemClock ──
    println("── Step 8: useSystem() ──")
    pubTime("useSystem")
    Thread.sleep(500)
    println()

    println("═══════════════════════════════════════════════════════════════")
    println("  Done — Check events.jsonl for:")
    println("    - NO dwell events at t=15m (STANDARD)")
    println("    - DWELL_WARNING at t=20m (after profile change)")
    println("    - DWELL_EXCEEDED at t=25m")
    println("    - EPISODE_OPENED + EPISODE_CLOSED")
    println("═══════════════════════════════════════════════════════════════")

    conn.close()
}
