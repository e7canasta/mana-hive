package simulator.report

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import simulator.Http
import java.io.File
import java.time.Instant

object ReportComponent {
    private val mapper = ObjectMapper().apply {
        registerModule(JavaTimeModule())
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        enable(SerializationFeature.INDENT_OUTPUT)
    }

    fun generate(
        scenarioFile: File,
        hubUrl: String,
        natsUrl: String,
        residentId: String,
        bed: String,
        profile: String?,
        from: Instant,
        to: Instant,
        steps: List<Map<String, Any>>,
        expect: Map<String, Any>?,
        verified: Boolean
    ): File {
        val episodesJson = try { Http.get("$hubUrl/api/v1/episodes?residentId=$residentId&from=$from&to=$to") } catch (e: Exception) { "[]" }
        val scenesJson = try { Http.get("$hubUrl/api/v1/admin/scene-events?residentId=$residentId&from=$from&to=$to&bedId=$bed") } catch (e: Exception) { "[]" }
        // hub no expone /admin/signals -> fallback a episodes como proxy (ExpectVerifier.kt:60)
        val signalsJson = try {
            val s = Http.get("$hubUrl/api/v1/admin/signals?residentId=$residentId&from=$from&to=$to&bedId=$bed")
            if (s.contains("\"status\":404") || s.contains("Not Found")) throw IllegalStateException("admin/signals 404")
            s
        } catch (e: Exception) {
            try { Http.get("$hubUrl/api/v1/episodes?residentId=$residentId&from=$from&to=$to") } catch (_: Exception) { "[]" }
        }

        fun parseList(s: String): List<Map<String, Any>> = try {
            @Suppress("UNCHECKED_CAST")
            mapper.readValue(s, List::class.java) as List<Map<String, Any>>
        } catch (e: Exception) { listOf(mapOf("raw" to s.take(2000))) }

        val dataset = ReportDataset(
            meta = ReportDataset.Meta(scenarioFile.nameWithoutExtension, profile, hubUrl, natsUrl, residentId, bed, from, to),
            steps = steps,
            episodes = parseList(episodesJson),
            sceneEvents = parseList(scenesJson),
            signals = parseList(signalsJson),
            expect = expect,
            verified = verified
        )
        val outDir = File("examples/jose-e1/reports").let { if (it.exists()) it else File("/home/visiona/workspace/mana-hive/examples/jose-e1/reports") }
        outDir.mkdirs()
        val out = File(outDir, "${scenarioFile.nameWithoutExtension}.llm.json")
        mapper.writeValue(out, dataset)
        println("  → ReportComponent: escrito ${out.absolutePath} (${dataset.episodes.size} episodes, ${dataset.sceneEvents.size} scenes, ${dataset.signals.size} signals)")
        return out
    }
}
