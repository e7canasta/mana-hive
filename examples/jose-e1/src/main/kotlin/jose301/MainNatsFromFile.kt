package jose301

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.manahive.contracts.EventEnvelope
import com.manahive.contracts.perception.Observation
import com.manahive.messaging.NatsConfig
import com.manahive.messaging.NatsObjectMapper
import com.manahive.messaging.Subjects
import com.manahive.runtime.TimeCommand
import java.io.File
import java.time.Instant

/**
 * Corre una secuencia definida en un archivo YAML/JSON por NATS.
 *
 * Formato (ejemplo en examples/jose-e1/sequences/jose-e1.yaml):
 * ```yaml
 * start: "2024-01-15T22:00:00Z"
 * bed: "bed-4"
 * monitor: "m1"
 * steps:
 *   - useManual: "2024-01-15T22:00:00Z"
 *   - obs: { kind: IN_BED, conf: 0.95 }
 *   - advance: "PT5M"
 *   - obs: { kind: SITTING_IN_BED }
 * ```
 *
 * Run:
 *   ./gradlew :examples:jose-e1:run -Pmain=jose301.MainNatsFromFileKt --args="sequences/jose-e1.yaml"
 *   ./gradlew :examples:jose-e1:run -Pmain=jose301.MainNatsFromFileKt --args="sequences/jose-e2-staff.yaml"
 */
fun main(args: Array<String>) {
    val filePath = args.firstOrNull()
        ?: System.getProperty("sequence")
        ?: "sequences/catalog/01-e1-vuelve-solo.yaml"
    val file = File(filePath).let { if (it.exists()) it else File("examples/jose-e1/$filePath") }.let { if (it.exists()) it else File("/home/visiona/workspace/mana-hive/examples/jose-e1/$filePath") }
    require(file.exists()) { "No existe secuencia: $filePath (${file.absolutePath}) — args=${args.toList()} sysProps=${System.getProperties().stringPropertyNames().filter { it.contains("sequence") }}" }

    val yamlMapper = YAMLMapper().apply { findAndRegisterModules() }
    val data: Map<String, Any> = yamlMapper.readValue(file)

    val start = Instant.parse(data["start"] as String)
    val bed = data["bed"] as? String ?: "bed-4"
    val monitor = data["monitor"] as? String ?: "m1"
    @Suppress("UNCHECKED_CAST")
    val steps = data["steps"] as List<Map<String, Any>>

    val natsMapper = NatsObjectMapper.mapper
    val conn = NatsConfig.createConnection()
    println("═══════════════════════════════════════════════════════════════")
    println("  FromFile — ${file.name} → NATS")
    println("  start=$start bed=$bed")
    println("═══════════════════════════════════════════════════════════════")
    println()

    var now = start

    fun publishEnveloped(subject: String, type: String, at: Instant, payload: Any) {
        val envelope = EventEnvelope(eventId = java.util.UUID.randomUUID().toString(), type = type, version = 1, occurredAt = at, source = "from-file", payloadJson = natsMapper.writeValueAsString(payload))
        conn.publish(subject, natsMapper.writeValueAsBytes(envelope))
    }

    for ((i, step) in steps.withIndex()) {
        when {
            step.containsKey("useManual") -> {
                val at = step["useManual"] as String
                publishEnveloped("test.time.v1", "TimeCommand", Instant.now(), TimeCommand(action = "useManual", startAt = at))
                now = Instant.parse(at)
                println("${i+1}. → useManual $at")
            }
            step.containsKey("useSystem") -> {
                publishEnveloped("test.time.v1", "TimeCommand", Instant.now(), TimeCommand(action = "useSystem"))
                println("${i+1}. → useSystem")
            }
            step.containsKey("advance") -> {
                val d = step["advance"] as String
                publishEnveloped("test.time.v1", "TimeCommand", Instant.now(), TimeCommand(action = "advance", duration = d))
                now = now.plus(java.time.Duration.parse(d))
                println("${i+1}. → advance $d → $now")
            }
            step.containsKey("obs") -> {
                @Suppress("UNCHECKED_CAST")
                val obsMap = step["obs"] as Map<String, Any>
                val kind = obsMap["kind"] as String
                val conf = (obsMap["conf"] as? Number)?.toDouble() ?: 0.92
                val obs = Observation(monitor = com.manahive.kernel.MonitorId(monitor), bed = com.manahive.kernel.BedId(bed), kind = com.manahive.contracts.perception.ObservationKind.valueOf(kind), confidence = conf, observedAt = now)
                publishEnveloped(Subjects.perceptionObservation(com.manahive.kernel.BedId(bed)), "Observation", now, obs)
                println("${i+1}. → OBS $kind at $now")
            }
            else -> println("  ⚠ paso desconocido $step")
        }
        Thread.sleep(200)
    }

    println()
    println("═══════════════════════════════════════════════════════════════")
    println("  Secuencia ${file.name} publicada")
    println("═══════════════════════════════════════════════════════════════")
    conn.close()
}
