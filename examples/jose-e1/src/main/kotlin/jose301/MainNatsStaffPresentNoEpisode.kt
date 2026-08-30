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
 * Staff presente desde el inicio → sentada larga NO debe abrir episodio.
 *
 *  22:00 IN_BED
 *  22:02 STAFF_ENTERED (enfermera ya está)
 *  22:05 SITTING 15m → 22:13 ComeBack PT8M pero con staff → SuppressedWithRecord
 *  22:20 IN_BED (siguen juntos)
 *
 * Run:
 *   ./gradlew :examples:jose-e1:run -Pmain=jose301.MainNatsStaffPresentNoEpisodeKt
 */
fun main() {
    val mapper = NatsObjectMapper.mapper
    val START = Instant.parse("2024-01-25T22:00:00Z")
    val bed = "bed-4"
    val monitor = "m1"

    println("═══════════════════════════════════════════════════════════════")
    println("  Staff presente → sentada larga NO abre episodio (suprimido)")
    println("═══════════════════════════════════════════════════════════════")
    println()
    val conn = NatsConfig.createConnection()
    println("Connected: ${conn.status}")
    println()

    fun publishEnveloped(subject: String, type: String, at: Instant, payload: Any) {
        val envelope = EventEnvelope(eventId = UUID.randomUUID().toString(), type = type, version = 1, occurredAt = at, source = "staff-suppress-test", payloadJson = mapper.writeValueAsString(payload))
        conn.publish(subject, mapper.writeValueAsBytes(envelope))
    }
    fun pubTime(cmd: TimeCommand) {
        publishEnveloped("test.time.v1", "TimeCommand", Instant.now(), cmd)
        println("  → TIME: ${cmd.action} ${cmd.duration ?: cmd.startAt ?: ""}")
    }
    fun pubObservation(at: Instant, kind: String, conf: Double = 0.92) {
        val obs = Observation(sourceEventId = "staff-test-${at}", monitor = com.manahive.kernel.MonitorId(monitor), bed = com.manahive.kernel.BedId(bed), kind = com.manahive.contracts.perception.ObservationKind.valueOf(kind), confidence = conf, observedAt = at)
        publishEnveloped(Subjects.perceptionObservation(com.manahive.kernel.BedId(bed)), "Observation", at, obs)
        println("  → OBS: $kind at $at")
    }

    pubTime(TimeCommand(action = "useManual", startAt = START.toString())); Thread.sleep(200)
    pubObservation(START, "IN_BED", 0.95); Thread.sleep(200)
    pubObservation(START.plusSeconds(120), "STAFF_ENTERED", 0.95); Thread.sleep(200)
    println("  → enfermera ya está presente")
    println()

    val sittingAt = START.plus(Duration.ofMinutes(5))
    pubTime(TimeCommand(action = "advance", duration = "PT5M")); Thread.sleep(200)
    pubObservation(sittingAt, "SITTING_IN_BED"); Thread.sleep(200)

    // Avanzar 8m → debería suprimirse, no abrir
    val comeBackAt = sittingAt.plus(Duration.ofMinutes(8))
    val d = Duration.between(sittingAt, comeBackAt)
    pubTime(TimeCommand(action = "advance", duration = d.toString())); Thread.sleep(600)
    println("  → 02:13 ComeBack PT8M con staff → debe suprimirse (SuppressedWithRecord)")

    val backAt = sittingAt.plus(Duration.ofMinutes(15))
    pubTime(TimeCommand(action = "advance", duration = Duration.between(comeBackAt, backAt).toString())); Thread.sleep(200)
    pubObservation(backAt, "IN_BED"); Thread.sleep(400)

    pubTime(TimeCommand(action = "useSystem")); Thread.sleep(400)
    println()
    println("═══════════════════════════════════════════════════════════════")
    println("  Esperado: 0 episodios, 1 SuppressedWithRecord STAFF_PRESENT")
    println("═══════════════════════════════════════════════════════════════")
    conn.close()
}
