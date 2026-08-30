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
 * 48h mixto — prueba de robustez.
 *
 * Mix determinístico:
 *  - cortas 3-4m (no episodio) — no debe dejar pending
 *  - largas 15m → episodio + STAFF → cierre
 *  - STAFF sola sin episodio (ronda) — no debe romper ledger
 *
 * Timeline base 2024-01-22T22:00:00Z → 2024-01-24T22:00:00Z (48h)
 *
 * Run:
 *   ./gradlew :examples:jose-e1:run -Pmain=jose301.MainNats48hMixedKt --console=plain
 */
fun main() {
    val mapper = NatsObjectMapper.mapper
    val START = Instant.parse("2024-01-22T22:00:00Z")
    val bed = "bed-4"
    val monitor = "m1"

    println("═══════════════════════════════════════════════════════════════")
    println("  48h mixto — José (v3 PT5M/PT8M) — cortas / largas / staff sola")
    println("═══════════════════════════════════════════════════════════════")
    println()

    val conn = NatsConfig.createConnection()
    println("Connected to NATS: ${conn.status}")
    println()

    var now = START

    fun publishEnveloped(subject: String, type: String, at: Instant, payload: Any) {
        val envelope = EventEnvelope(
            eventId = UUID.randomUUID().toString(),
            type = type, version = 1, occurredAt = at, source = "48h-mixed",
            payloadJson = mapper.writeValueAsString(payload),
        )
        conn.publish(subject, mapper.writeValueAsBytes(envelope))
    }
    fun pubTime(cmd: TimeCommand) {
        publishEnveloped("test.time.v1", "TimeCommand", Instant.now(), cmd)
        println("  → TIME: ${cmd.action} ${cmd.duration ?: cmd.startAt ?: ""} (now=$now)")
    }
    fun pubObservation(at: Instant, kind: String, conf: Double = 0.92) {
        val obs = Observation(
            sourceEventId = "48h-${at}-${UUID.randomUUID().toString().take(4)}",
            monitor = com.manahive.kernel.MonitorId(monitor),
            bed = com.manahive.kernel.BedId(bed),
            kind = com.manahive.contracts.perception.ObservationKind.valueOf(kind),
            confidence = conf, observedAt = at,
        )
        publishEnveloped(Subjects.perceptionObservation(com.manahive.kernel.BedId(bed)), "Observation", at, obs)
        println("  → OBS: $kind at $at")
    }
    fun advanceTo(target: Instant) {
        val d = Duration.between(now, target)
        if (d.isNegative || d.isZero) return
        publishEnveloped("test.time.v1", "TimeCommand", Instant.now(), TimeCommand(action = "advance", duration = d.toString()))
        println("  → ADVANCE ${d} → $target")
        now = target
        Thread.sleep(150)
    }
    fun useManual(at: Instant) {
        publishEnveloped("test.time.v1", "TimeCommand", Instant.now(), TimeCommand(action = "useManual", startAt = at.toString()))
        now = at
        println("  → useManual $at")
        Thread.sleep(200)
    }

    useManual(START)
    pubObservation(START, "IN_BED", 0.95); Thread.sleep(200)

    // ── Día 1 ──
    // 23:30 corta 4m (no episodio)
    advanceTo(Instant.parse("2024-01-22T23:30:00Z"))
    pubObservation(now, "SITTING_IN_BED"); Thread.sleep(200)
    advanceTo(Instant.parse("2024-01-22T23:34:00Z"))
    pubObservation(now, "IN_BED"); Thread.sleep(300)

    // 02:30 larga 15m → episodio + staff
    advanceTo(Instant.parse("2024-01-23T02:30:00Z"))
    pubObservation(now, "SITTING_IN_BED"); Thread.sleep(200)
    advanceTo(Instant.parse("2024-01-23T02:38:00Z")) // ComeBack 8m
    Thread.sleep(400)
    advanceTo(Instant.parse("2024-01-23T02:40:00Z"))
    pubObservation(now, "STAFF_ENTERED", 0.95); Thread.sleep(300)
    advanceTo(Instant.parse("2024-01-23T02:42:00Z"))
    pubObservation(now, "IN_BED"); Thread.sleep(400)

    // 05:00 staff sola sin episodio (ronda)
    advanceTo(Instant.parse("2024-01-23T05:00:00Z"))
    pubObservation(now, "STAFF_ENTERED", 0.90); Thread.sleep(200)
    advanceTo(Instant.parse("2024-01-23T05:02:00Z"))
    pubObservation(now, "STAFF_LEFT", 0.90); Thread.sleep(300)

    // 10:00 corta 3m
    advanceTo(Instant.parse("2024-01-23T10:00:00Z"))
    pubObservation(now, "SITTING_IN_BED"); Thread.sleep(200)
    advanceTo(Instant.parse("2024-01-23T10:03:00Z"))
    pubObservation(now, "IN_BED"); Thread.sleep(300)

    // 14:00 larga 12m → episodio
    advanceTo(Instant.parse("2024-01-23T14:00:00Z"))
    pubObservation(now, "SITTING_IN_BED"); Thread.sleep(200)
    advanceTo(Instant.parse("2024-01-23T14:08:00Z")); Thread.sleep(400) // ComeBack
    advanceTo(Instant.parse("2024-01-23T14:10:00Z"))
    pubObservation(now, "STAFF_ENTERED", 0.95); Thread.sleep(200)
    advanceTo(Instant.parse("2024-01-23T14:12:00Z"))
    pubObservation(now, "IN_BED"); Thread.sleep(400)

    // ── Día 2 ──
    // 19:00 staff sola
    advanceTo(Instant.parse("2024-01-23T19:00:00Z"))
    pubObservation(now, "STAFF_ENTERED", 0.90); Thread.sleep(200)
    advanceTo(Instant.parse("2024-01-23T19:02:00Z"))
    pubObservation(now, "STAFF_LEFT", 0.90); Thread.sleep(300)

    // 23:30 corta 4m
    advanceTo(Instant.parse("2024-01-23T23:30:00Z"))
    pubObservation(now, "SITTING_IN_BED"); Thread.sleep(200)
    advanceTo(Instant.parse("2024-01-23T23:34:00Z"))
    pubObservation(now, "IN_BED"); Thread.sleep(300)

    // 02:30 larga 15m → episodio (segunda noche)
    advanceTo(Instant.parse("2024-01-24T02:30:00Z"))
    pubObservation(now, "SITTING_IN_BED"); Thread.sleep(200)
    advanceTo(Instant.parse("2024-01-24T02:38:00Z")); Thread.sleep(400)
    advanceTo(Instant.parse("2024-01-24T02:40:00Z"))
    pubObservation(now, "STAFF_ENTERED", 0.95); Thread.sleep(200)
    advanceTo(Instant.parse("2024-01-24T02:42:00Z"))
    pubObservation(now, "IN_BED"); Thread.sleep(400)

    // 10:00 corta 3m
    advanceTo(Instant.parse("2024-01-24T10:00:00Z"))
    pubObservation(now, "SITTING_IN_BED"); Thread.sleep(200)
    advanceTo(Instant.parse("2024-01-24T10:03:00Z"))
    pubObservation(now, "IN_BED"); Thread.sleep(300)

    pubTime(TimeCommand(action = "useSystem")); Thread.sleep(500)

    println()
    println("═══════════════════════════════════════════════════════════════")
    println("  48h mixto completo — 3 episodios largos + 4 cortas + 2 staff solas")
    println("  Ver: curl http://localhost:8080/api/v1/episodes?residentId=jose | jq")
    println("═══════════════════════════════════════════════════════════════")
    conn.close()
}
