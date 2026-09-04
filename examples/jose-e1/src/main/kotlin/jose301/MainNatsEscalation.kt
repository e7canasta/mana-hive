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
 * Escalado: SITTING WARNING → STANDING dwell 3m CRITICAL
 *
 *  22:00 IN_BED
 *  22:05 SITTING_IN_BED → Entry WARNING → EPISODE_OPENED WARNING
 *  22:06 STANDING → umbrella
 *  22:09 (3m en STANDING) → DwellExceeded → EPISODE_COMPLICATED CRITICAL
 *  22:10 STAFF_ENTERED → no cierra aún (STAFF_AND_SAFE necesita SAFE)
 *  22:12 LYING → EPISODE_CLOSED
 *
 * Requiere perfil jose@v6 (rampa SITTING→STANDING).
 * Run: ./gradlew :examples:jose-e1:run -Pmain=jose301.MainNatsEscalationKt
 */
fun main() {
    val mapper = NatsObjectMapper.mapper
    val START = Instant.parse("2024-01-27T22:00:00Z")
    val bed = "bed-4"
    val monitor = "m1"

    println("═══════════════════════════════════════════════════════════════")
    println("  Escalado SITTING→STANDING — WARNING → CRITICAL (v6 rampa)")
    println("═══════════════════════════════════════════════════════════════")
    println()
    val conn = NatsConfig.createConnection()
    println("Connected: ${conn.status}")
    println()

    fun publishEnveloped(subject: String, type: String, at: Instant, payload: Any) {
        val envelope = EventEnvelope(eventId = UUID.randomUUID().toString(), type = type, version = 1, occurredAt = at, source = "escalation", payloadJson = mapper.writeValueAsString(payload))
        conn.publish(subject, mapper.writeValueAsBytes(envelope))
    }
    fun pubTime(cmd: TimeCommand) {
        publishEnveloped("test.time.v1", "TimeCommand", Instant.now(), cmd)
        println("  → TIME: ${cmd.action} ${cmd.duration ?: cmd.startAt ?: ""}")
    }
    fun pubObs(at: Instant, kind: String) {
        val obs = Observation(monitor = com.manahive.kernel.MonitorId(monitor), bed = com.manahive.kernel.BedId(bed), kind = com.manahive.contracts.perception.ObservationKind.valueOf(kind), confidence = 0.95, observedAt = at)
        publishEnveloped(Subjects.perceptionObservation(com.manahive.kernel.BedId(bed)), "Observation", at, obs)
        println("  → OBS: $kind at $at")
    }

    pubTime(TimeCommand(action = "useManual", startAt = START.toString())); Thread.sleep(200)
    pubObs(START, "IN_BED"); Thread.sleep(200)

    val tSitting = START.plus(Duration.ofMinutes(5))
    pubTime(TimeCommand(action = "advance", duration = Duration.between(START, tSitting).toString())); Thread.sleep(200)
    pubObs(tSitting, "SITTING_IN_BED"); Thread.sleep(400)
    println("  → espera EPISODE_OPENED WARNING (SITTING)")

    val tStanding = tSitting.plus(Duration.ofMinutes(1))
    pubTime(TimeCommand(action = "advance", duration = Duration.ofMinutes(1).toString())); Thread.sleep(200)
    pubObs(tStanding, "STANDING"); Thread.sleep(400)
    println("  → STANDING bajo paraguas")

    val tDwell = tStanding.plus(Duration.ofMinutes(3))
    pubTime(TimeCommand(action = "advance", duration = Duration.ofMinutes(3).toString())); Thread.sleep(600)
    println("  → 22:09 Dwell STANDING 3m → debe escalar a CRITICAL (EpisodeComplicated)")

    val tStaff = tDwell.plus(Duration.ofMinutes(2))
    pubTime(TimeCommand(action = "advance", duration = Duration.ofMinutes(2).toString())); Thread.sleep(200)
    pubObs(tStaff, "STAFF_ENTERED"); Thread.sleep(300)
    println("  → staff entra, aún parado → no cierra (STAFF_AND_SAFE)")

    val tLying = tStaff.plus(Duration.ofMinutes(2))
    pubTime(TimeCommand(action = "advance", duration = Duration.ofMinutes(2).toString())); Thread.sleep(200)
    pubObs(tLying, "IN_BED"); Thread.sleep(400)
    println("  → vuelve a LYING → debe cerrar EPISODE_CLOSED")

    pubTime(TimeCommand(action = "useSystem")); Thread.sleep(400)
    println()
    println("═══════════════════════════════════════════════════════════════")
    println("  Ver: docker logs mana-hive-dev | grep -E \"signals|EPISODE\"")
    println("       curl http://localhost:8080/api/v1/episodes?residentId=jose | jq")
    println("═══════════════════════════════════════════════════════════════")
    conn.close()
}
