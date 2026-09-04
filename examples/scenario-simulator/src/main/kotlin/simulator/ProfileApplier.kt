package simulator

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.File

object ProfileApplier {
    fun apply(hubUrl: String, profilePath: String) {
        val file = File(profilePath).let { if (it.exists()) it else File("/home/visiona/workspace/mana-hive/$profilePath") }.let { if (it.exists()) it else File("/home/visiona/workspace/mana-dist/$profilePath") }
        require(file.exists()) { "Perfil no encontrado: $profilePath" }
        val residentId = Regex("\"residentId\"\\s*:\\s*\"([^\"]+)\"").find(file.readText())?.groupValues?.get(1) ?: "jose"
        val (code, body) = Http.putJson("$hubUrl/api/profiles/$residentId", file.readText())
        println("  → PUT profile $profilePath → $code $body")
        require(code in 200..299) { "No se pudo aplicar el perfil $profilePath: HTTP $code $body" }
        Thread.sleep(3000)
    }

    fun applyMonitoringProfile(hubUrl: String, residentId: String, profile: Map<String, Any?>) {
        val body = jacksonObjectMapper().writeValueAsString(profile)
        val (code, response) = Http.patchJson(
            "$hubUrl/api/v1/alarm-presets/$residentId",
            body,
        )
        println("  → PATCH monitoring profile $residentId → $code $response")
        require(code in 200..299) { "No se pudo aplicar el perfil de monitoreo: HTTP $code $response" }
    }
}
