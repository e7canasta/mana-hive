@file:Suppress("UNCHECKED_CAST")

package simulator

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.manahive.contracts.EventEnvelope
import com.manahive.contracts.perception.Observation
import com.manahive.messaging.NatsConfig
import com.manahive.messaging.NatsObjectMapper
import com.manahive.messaging.Subjects
import com.manahive.runtime.TimeCommand
import java.io.File
import java.time.Duration
import java.time.Instant
import java.util.UUID

fun main(args: Array<String>) {
    val seqPath = args.firstOrNull() ?: System.getProperty("sequence") ?: System.getenv("CATALOG") ?: "examples/jose-e1/sequences/catalog/01-e1-vuelve-solo.yaml"
    val file = File(seqPath).let { if (it.exists()) it else File("/home/visiona/workspace/mana-hive/$seqPath") }.let { if (it.exists()) it else File("/home/visiona/workspace/mana-hive/examples/jose-e1/$seqPath") }
    require(file.exists()) { "Secuencia no encontrada: $seqPath" }

    val yaml = YAMLMapper().apply { findAndRegisterModules() }
    val data: Map<String, Any> = yaml.readValue(file)
    // ENV override para docker prod
    val hubUrl = System.getenv("HUB_URL") ?: (data["hubUrl"] as? String) ?: "http://localhost:8080"
    val natsUrl = System.getenv("NATS_URL") ?: (data["natsUrl"] as? String) ?: "nats://localhost:4222"
    val residentId = (data["residentId"] as? String) ?: "jose"
    val bed = (data["bed"] as? String) ?: "bed-4"
    val monitor = (data["monitor"] as? String) ?: "m1"

    val profile = data["profile"] as? String
    val clean = data["clean"] as? Boolean ?: false
    val autoDate = data["autoDate"] as? Boolean ?: false

    var start = (data["start"] as? String)?.let { Instant.parse(it) } ?: Instant.parse("2024-01-15T22:00:00Z")
    if (autoDate) {
        start = DateResolver.resolveAutoDate(hubUrl, residentId)
        println("  → autoDate: usando start=$start (último episodio +1d)")
    }

    if (clean) {
        println("  → clean=true → limpiando hub+hive por API")
        val hiveUrl = System.getenv("HIVE_URL") ?: hubUrl.replace("8080", "18081")
        Cleaner.clean(hubUrl, residentId, hiveUrl)
    }
    if (profile != null) {
        println("  → profile=$profile → aplicando")
        ProfileApplier.apply(hubUrl, profile)
    }

    println("═══════════════════════════════════════════════════════════════")
    println("  Simulador — ${file.name} — $residentId bed=$bed start=$start hub=$hubUrl nats=$natsUrl")
    println("═══════════════════════════════════════════════════════════════")
    println()

    val natsMapper = NatsObjectMapper.mapper
    val conn = try { NatsConfig.createConnection(natsUrl) } catch (e: Exception) { NatsConfig.createConnection() }
    println("Conectado NATS: ${conn.status} url=$natsUrl")
    println()

    var now = start
    @Suppress("UNCHECKED_CAST")
    val steps = data["steps"] as? List<Map<String, Any>> ?: emptyList()

    fun publishEnveloped(subject: String, type: String, at: Instant, payload: Any) {
        val envelope = EventEnvelope(eventId = UUID.randomUUID().toString(), type = type, version = 1, occurredAt = at, source = "simulator", payloadJson = natsMapper.writeValueAsString(payload))
        conn.publish(subject, natsMapper.writeValueAsBytes(envelope))
    }

    for ((i, step) in steps.withIndex()) {
        when {
            step.containsKey("useManual") -> {
                val at = step["useManual"] as String
                publishEnveloped("test.time.v1", "TimeCommand", Instant.now(), TimeCommand(action = "useManual", startAt = at))
                now = Instant.parse(at)
                if (autoDate && i == 0) {
                    publishEnveloped("test.time.v1", "TimeCommand", Instant.now(), TimeCommand(action = "useManual", startAt = start.toString()))
                    now = start
                }
                println("${i+1}. useManual $now")
            }
            step.containsKey("useSystem") -> {
                publishEnveloped("test.time.v1", "TimeCommand", Instant.now(), TimeCommand(action = "useSystem"))
                println("${i+1}. useSystem")
            }
            step.containsKey("advance") -> {
                val d = step["advance"] as String
                val dur = Duration.parse(d)
                publishEnveloped("test.time.v1", "TimeCommand", Instant.now(), TimeCommand(action = "advance", duration = d))
                now = now.plus(dur)
                println("${i+1}. advance $d → $now")
            }
            step.containsKey("obs") -> {
                @Suppress("UNCHECKED_CAST")
                val obsMap = step["obs"] as Map<String, Any>
                val kind = obsMap["kind"] as String
                val conf = (obsMap["conf"] as? Number)?.toDouble() ?: 0.92
                val obs = Observation(sourceEventId = "sim-${i}", monitor = com.manahive.kernel.MonitorId(monitor), bed = com.manahive.kernel.BedId(bed), kind = com.manahive.contracts.perception.ObservationKind.valueOf(kind), confidence = conf, observedAt = now)
                publishEnveloped(Subjects.perceptionObservation(com.manahive.kernel.BedId(bed)), "Observation", now, obs)
                println("${i+1}. OBS $kind at $now")
            }
        }
        Thread.sleep(220)
    }

    Thread.sleep(2000)
    @Suppress("UNCHECKED_CAST")
    val expectMap = data["expect"] as? Map<String, Any>
    var verified = true
    var from = start
    var to = now.plusSeconds(3600)
    if (expectMap != null) {
        println()
        println("── Verificación ──")
        val episodes = (expectMap["episodes"] as? List<Map<String, Any>>)?.map { ExpectedEpisode(status = it["status"] as? String, severity = it["severity"] as? String, count = (it["count"] as? Number)?.toInt()) } ?: emptyList()
        val minScene = (expectMap["minSceneEvents"] as? Number)?.toInt()
        val minSignals = (expectMap["minSignals"] as? Number)?.toInt()
        val expected = Expected(episodes = episodes, minSceneEvents = minScene, minSignals = minSignals)
        from = start
        to = now.plusSeconds(3600)
        verified = ExpectVerifier.verify(hubUrl, residentId, from, to, expected)
        println(if (verified) "═══════════ ✅ SIMULACIÓN OK ═══════════" else "═══════════ ❌ SIMULACIÓN FALLÓ ═══════════")
    } else {
        println()
        println("  (sin expect → solo se publicó, verificar manual con GET /api/v1/episodes)")
    }

    // Componente reporte LLM-friendly (Fowler: componente separado)
    try {
        simulator.report.ReportComponent.generate(file, hubUrl, natsUrl, residentId, bed, profile, from, to, steps, expectMap, verified)
    } catch (e: Exception) {
        println("  ⚠ ReportComponent falló: ${e.message}")
    }

    if (expectMap != null && !verified) System.exit(1)

    publishEnveloped("test.time.v1", "TimeCommand", Instant.now(), TimeCommand(action = "useSystem"))
    conn.close()
}
