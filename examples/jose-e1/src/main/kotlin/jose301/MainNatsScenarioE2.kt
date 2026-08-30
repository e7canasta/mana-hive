package jose301

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
 * José E2 via NATS — se queda sentado y viene la enfermera.
 *
 * Diferencia con E1: acá José NO vuelve solo. Se queda sentado
 * 15 min y la enfermera lo acuesta (STAFF_ENTERED + IN_BED).
 *
 *  - 22:00 IN_BED (acostado)
 *  - 22:05 SITTING_IN_BED (se sienta)
 *  - 22:13 → ComeBackExceeded PT8M (v3) → EPISODE_OPENED
 *  - 22:18 STAFF_ENTERED (enfermera entra, scene staff=Present)
 *  - 22:20 IN_BED (enfermera lo acuesta, closure STAFF_OR_SAFE)
 *
 * Run:
 *   ./gradlew :examples:jose-e1:run -Pmain=jose301.MainNatsScenarioE2Kt
 */
fun main() {
    val mapper = NatsObjectMapper.mapper
    val START = Instant.parse("2024-01-18T22:00:00Z")

    val bed = "bed-4"
    val monitor = "m1"

    println("═══════════════════════════════════════════════════════════════")
    println("  José E2 — Se queda sentado y viene la enfermera (v3 PT5M/PT8M)")
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
            source = "test-client-e2",
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
            sourceEventId = "nats-e2-${offset}",
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

    // ── Step 3: advance 5m, obs(SITTING_IN_BED) ──
    println("── Step 3: advance(5m) + obs(SITTING_IN_BED) ──")
    pubTime(TimeCommand(action = "advance", duration = "PT5M"))
    Thread.sleep(200)
    pubObservation(Duration.ofMinutes(5), "SITTING_IN_BED", 0.92)
    Thread.sleep(200)
    println()

    // ── Step 4: advance 8m → dispara ComeBack PT8M (v3) ──
    println("── Step 4: advance(8m) → debe disparar ComeBackExceeded ──")
    pubTime(TimeCommand(action = "advance", duration = "PT8M"))
    Thread.sleep(800)
    println()

    // ── Step 5: enfermera entra mientras sigue sentado (cierre STAFF_PRESENT) ──
    println("── Step 5: advance(1m) + obs(STAFF_ENTERED) — entra enfermera mientras sigue sentado ──")
    pubTime(TimeCommand(action = "advance", duration = "PT1M"))
    Thread.sleep(200)
    pubObservation(Duration.ofMinutes(14), "STAFF_ENTERED", 0.95)
    Thread.sleep(400)
    println()

    // ── Step 6: enfermera lo acuesta 6m después (ya debería estar cerrado) ──
    println("── Step 6: advance(6m) + obs(IN_BED) — enfermera lo acuesta ──")
    pubTime(TimeCommand(action = "advance", duration = "PT6M"))
    Thread.sleep(200)
    pubObservation(Duration.ofMinutes(20), "IN_BED", 0.94)
    Thread.sleep(400)
    println()

    // ── Step 7: Back to SystemClock ──
    println("── Step 7: useSystem() ──")
    pubTime(TimeCommand(action = "useSystem"))
    Thread.sleep(500)
    println()

    println("═══════════════════════════════════════════════════════════════")
    println("  All events published to NATS")
    println("  Ver: docker compose -f compose.dev.yml logs mana-hive -f")
    println("  Hub: curl http://localhost:8080/api/v1/episodes?residentId=jose | jq")
    println("═══════════════════════════════════════════════════════════════")

    conn.close()
}
