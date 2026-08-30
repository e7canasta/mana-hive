package jose301

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.manahive.contracts.EventEnvelope
import com.manahive.contracts.perception.Observation
import com.manahive.contracts.perception.ObservationKind
import com.manahive.contracts.policy.AlarmProfile
import com.manahive.contracts.policy.CatalogVersion
import com.manahive.contracts.policy.MobilityAid
import com.manahive.contracts.policy.PolicyChangeDetected
import com.manahive.contracts.policy.PolicyMode
import com.manahive.contracts.policy.RiskLevel
import com.manahive.contracts.policy.TemplateId
import com.manahive.kernel.BedId
import com.manahive.kernel.MonitorId
import com.manahive.kernel.NightId
import com.manahive.kernel.ResidentId
import com.manahive.messaging.NatsConfig
import com.manahive.messaging.NatsObjectMapper
import com.manahive.messaging.Subjects
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Policy change test — jose STANDARD → FALL_RISK.
 *
 * Shows that:
 * - STANDARD: no dwell alerts (pure observation)
 * - FALL_RISK: dwell warning at 15m, alert at 20m
 *
 * Prerequisites:
 * - NATS running
 * - NightWatchApplication started with census.json + jose.json
 * - FileEventWriter configured to write events.jsonl
 */
fun main() {
    val mapper = NatsObjectMapper.mapper
    val START = Instant.parse("2024-01-15T22:00:00Z")
    val bed = BedId("bed-4")
    val monitor = MonitorId("m1")

    println("═══════════════════════════════════════════════════════════════")
    println("  Policy Change Test — jose STANDARD → FALL_RISK")
    println("  dwell: SittingInBed warning=15m, alert=20m")
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
            else -> emptyMap()
        }
        publishEnveloped("test.time.v1", "TimeCommand", Instant.now(), cmd)
        println("  → TIME: $action $value")
    }

    fun pubObservation(offset: Duration, kind: ObservationKind) {
        val at = START + offset
        val obs = Observation(
            sourceEventId = "policy-test-${offset}",
            monitor = monitor,
            bed = bed,
            kind = kind,
            confidence = 0.95,
            observedAt = at,
        )
        publishEnveloped(Subjects.perceptionObservation(bed), "Observation", at, obs)
        println("  → OBS: $kind at t=$offset")
    }

    fun pubPolicyChange(level: String) {
        val change = PolicyChangeDetected(
            residentId = ResidentId("jose"),
            at = Instant.now(),
            snapshot = AlarmProfile(
                residentId = ResidentId("jose"),
                riskLevel = RiskLevel.HIGH,
                mobilityAid = MobilityAid.NONE,
                autopilot = true,
                mode = PolicyMode.PRESET,
                templateId = TemplateId(level),
                overrides = emptyMap(),
                catalogVersion = CatalogVersion("1.0.0"),
                validFrom = Instant.now(),
            ),
        )
        publishEnveloped(Subjects.policyChangeDetected(), "PolicyChangeDetected", Instant.now(), change)
        println("  → POLICY: jose → $level")
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

    // ── Step 4: Change policy to FALL_RISK ──
    println("── Step 4: policyChange → FALL_RISK ──")
    pubPolicyChange("fall-risk")
    Thread.sleep(200)
    println()

    // ── Step 5: advance 5m → sweep: DWELL_WARNING ──
    println("── Step 5: advance(5m) → sweep: DWELL_WARNING (15m threshold) ──")
    pubTime("advance", "PT5M")
    Thread.sleep(200)
    println()

    // ── Step 6: advance 5m → sweep: DWELL_EXCEEDED ──
    println("── Step 6: advance(5m) → sweep: DWELL_EXCEEDED (20m threshold) ──")
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
    println("    - DWELL_WARNING at t=20m (FALL_RISK)")
    println("    - DWELL_EXCEEDED at t=25m (FALL_RISK)")
    println("    - EPISODE_OPENED + EPISODE_CLOSED")
    println("═══════════════════════════════════════════════════════════════")

    conn.close()
}
