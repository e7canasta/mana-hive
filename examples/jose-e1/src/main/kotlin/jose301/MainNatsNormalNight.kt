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
 * Noche NORMAL — lo que el director médico espera ver.
 *
 * No es la noche completa de 7 episodios del blueprint.
 * Es una noche tranquila con 1 episodio y varios falsos positivos:
 *
 *  22:00 IN_BED (acostado)
 *  23:30 SITTING 4m → vuelve solo 23:34 (corto, NO episodio) → valida que no alertamos de más
 *  02:30 SITTING 15m → ComeBackExceeded 02:38 (PT8M) → EPISODE_OPENED
 *         02:40 STAFF_ENTERED → 02:42 IN_BED → EPISODE_CLOSED (cierre STAFF_OR_SAFE)
 *  05:00 SITTING 3m → vuelve 05:03 (corto, NO episodio)
 *
 * Dos noches seguidas quedan limpias: cada episodio cierra antes del siguiente.
 * Si a las 3h vuelve a pasar algo corto, no abre. Si vuelve a ser largo, abre nuevo.
 *
 * Run:
 *   ./gradlew :examples:jose-e1:run -Pmain=jose301.MainNatsNormalNightKt       # noche 2024-01-19
 *   ./gradlew :examples:jose-e1:run -Pmain=jose301.MainNatsNormalNightKt -Pnight=2024-01-20  # segunda noche
 */
fun main() {
    val mapper = NatsObjectMapper.mapper
    val nightStr = System.getProperty("night") ?: "2024-01-19"
    val START = Instant.parse("${nightStr}T22:00:00Z")

    val bed = "bed-4"
    val monitor = "m1"

    println("═══════════════════════════════════════════════════════════════")
    println("  Noche NORMAL de José — $nightStr 22:00-06:00 (v3 PT5M/PT8M)")
    println("═══════════════════════════════════════════════════════════════")
    println()

    val conn = NatsConfig.createConnection()
    println("Connected to NATS: ${conn.status}")
    println()

    fun publishEnveloped(subject: String, type: String, at: Instant, payload: Any) {
        val envelope = EventEnvelope(
            eventId = UUID.randomUUID().toString(),
            type = type, version = 1, occurredAt = at, source = "normal-night",
            payloadJson = mapper.writeValueAsString(payload),
        )
        conn.publish(subject, mapper.writeValueAsBytes(envelope))
    }
    fun pubTime(cmd: TimeCommand) {
        publishEnveloped("test.time.v1", "TimeCommand", Instant.now(), cmd)
        println("  → TIME: ${cmd.action} ${cmd.duration ?: cmd.startAt ?: ""}")
    }
    fun pubObservation(offset: Duration, kind: String, conf: Double = 0.92) {
        val at = START + offset
        val obs = Observation(
            monitor = com.manahive.kernel.MonitorId(monitor),
            bed = com.manahive.kernel.BedId(bed),
            kind = com.manahive.contracts.perception.ObservationKind.valueOf(kind),
            confidence = conf, observedAt = at,
        )
        publishEnveloped(Subjects.perceptionObservation(com.manahive.kernel.BedId(bed)), "Observation", at, obs)
        println("  → OBS: $kind at +$offset → $at")
    }

    println("── 22:00 acostado ──")
    pubTime(TimeCommand(action = "useManual", startAt = START.toString())); Thread.sleep(200)
    pubObservation(Duration.ZERO, "IN_BED", 0.95); Thread.sleep(200)
    println()

    println("── 23:30 se sienta 4m (corto, NO episodio) ──")
    pubTime(TimeCommand(action = "advance", duration = "PT1H30M")); Thread.sleep(200)
    pubObservation(Duration.ofMinutes(90), "SITTING_IN_BED"); Thread.sleep(200)
    pubTime(TimeCommand(action = "advance", duration = "PT4M")); Thread.sleep(200)
    pubObservation(Duration.ofMinutes(94), "IN_BED"); Thread.sleep(300)
    println("  → 4m < PT8M → no debe abrir episodio")
    println()

    println("── 02:30 se sienta 15m (largo, SÍ episodio) ──")
    pubTime(TimeCommand(action = "advance", duration = "PT176M")); Thread.sleep(200) // 94m -> 270m =02:30
    pubObservation(Duration.ofMinutes(270), "SITTING_IN_BED"); Thread.sleep(400)
    println("  → mina armada 02:30")
    pubTime(TimeCommand(action = "advance", duration = "PT8M")); Thread.sleep(600)
    println("  → 02:38 ComeBackExceeded PT8M → debe abrir EPISODE_OPENED")
    println()

    println("── 02:40 entra enfermera + 02:42 lo acuesta ──")
    pubTime(TimeCommand(action = "advance", duration = "PT2M")); Thread.sleep(200)
    pubObservation(Duration.ofMinutes(280), "STAFF_ENTERED", 0.95); Thread.sleep(300)
    pubTime(TimeCommand(action = "advance", duration = "PT2M")); Thread.sleep(200)
    pubObservation(Duration.ofMinutes(282), "IN_BED"); Thread.sleep(600)
    println("  → con STAFF_OR_SAFE debe cerrar EPISODE_CLOSED")
    println()

    println("── 05:00 se sienta 3m (corto, NO episodio) ──")
    pubTime(TimeCommand(action = "advance", duration = "PT138M")); Thread.sleep(200) // 282->420=05:00
    pubObservation(Duration.ofMinutes(420), "SITTING_IN_BED"); Thread.sleep(200)
    pubTime(TimeCommand(action = "advance", duration = "PT3M")); Thread.sleep(200)
    pubObservation(Duration.ofMinutes(423), "IN_BED"); Thread.sleep(300)
    println("  → 3m < PT8M → no debe abrir")
    println()

    println("── 06:00 fin de noche ──")
    pubTime(TimeCommand(action = "useSystem")); Thread.sleep(500)
    println()

    println("═══════════════════════════════════════════════════════════════")
    println("  Noche normal completa")
    println("  Esperado: 1 episodio (02:38-02:42) + 2 sentadas cortas sin episodio")
    println("  Ver: curl http://localhost:8080/api/v1/episodes?residentId=jose | jq")
    println("═══════════════════════════════════════════════════════════════")
    conn.close()
}
