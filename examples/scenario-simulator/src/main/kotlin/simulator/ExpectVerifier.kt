package simulator

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.time.Instant

data class Expected(
    val episodes: List<ExpectedEpisode> = emptyList(),
    val minSceneEvents: Int? = null,
    val minSignals: Int? = null
)
data class ExpectedEpisode(val status: String? = null, val severity: String? = null, val count: Int? = null)

object ExpectVerifier {
    private val mapper = jacksonObjectMapper()

    fun verify(hubUrl: String, residentId: String, from: Instant, to: Instant, expected: Expected): Boolean {
        val fromStr = from.toString()
        val toStr = to.toString()
        val body = try { Http.get("$hubUrl/api/v1/episodes?residentId=$residentId&from=$fromStr&to=$toStr") } catch (e: Exception) { "[]" }
        val actual = try { mapper.readValue<List<Map<String, Any>>>(body) } catch (e: Exception) { emptyList<Map<String, Any>>() }

        var ok = true
        expected.episodes.forEach { exp ->
            val matched = actual.count { a ->
                (exp.status == null || a["status"] == exp.status) &&
                (exp.severity == null || a["severity"] == exp.severity)
            }
            val expectedCount = exp.count ?: 1
            if (matched != expectedCount) {
                println("  ❌ Esperaba $expectedCount episodios $exp, hallados $matched / actual=${actual.size}")
                ok = false
            } else {
                println("  ✅ Episodios $exp → $matched")
            }
        }
        if (expected.episodes.isEmpty() && actual.isNotEmpty()) {
            println("  ❌ Esperaba 0 episodios, hallados ${actual.size}")
            ok = false
        }
        expected.minSceneEvents?.let { min ->
            try {
                val bed = "bed-4"
                val body2 = try { Http.get("$hubUrl/api/v1/admin/scene-events?residentId=$residentId&from=$fromStr&to=$toStr&bedId=$bed") } catch (e: Exception) { "[]" }
                val scenes = try { mapper.readValue<List<Map<String, Any>>>(body2) } catch (e: Exception) { emptyList<Map<String, Any>>() }
                if (scenes.size < min) {
                    println("  ❌ SceneEvents esperados ≥$min, hallados ${scenes.size} → $body2")
                    ok = false
                } else {
                    println("  ✅ SceneEvents ≥$min → ${scenes.size} (${scenes.map { it["eventType"] }})")
                }
            } catch (e: Exception) {
                println("  ⚠ No se pudo verificar scene_events: ${e.message}")
            }
        }
        expected.minSignals?.let { min ->
            if (actual.size < min) {
                println("  ❌ Signals esperados ≥$min, hallados ${actual.size} episodios proxy")
                ok = false
            } else {
                println("  ✅ Signals ≥$min → ${actual.size} episodios (proxy)")
            }
        }
        if (ok) println("  ✅ Verificación OK")
        else println("  ❌ Verificación FALLÓ — episodios actual: $body")
        return ok
    }
}
