package simulator

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.time.Instant

object DateResolver {
    private val mapper = jacksonObjectMapper()

    fun resolveAutoDate(hubUrl: String, residentId: String): Instant {
        val body = try { Http.get("$hubUrl/api/v1/episodes?residentId=$residentId") } catch (e: Exception) { "[]" }
        return try {
            val arr = mapper.readValue<List<Map<String, Any>>>(body)
            val max = arr.mapNotNull { it["occurredAt"] as? String }.mapNotNull { runCatching { Instant.parse(it) }.getOrNull() }.maxOrNull()
            (max ?: Instant.parse("2024-01-15T22:00:00Z")).plusSeconds(24 * 3600)
        } catch (e: Exception) {
            Instant.parse("2024-01-15T22:00:00Z").plusSeconds(24 * 3600)
        }
    }
}
