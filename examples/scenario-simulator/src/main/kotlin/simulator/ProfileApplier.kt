package simulator

import java.io.File

object ProfileApplier {
    fun apply(hubUrl: String, profilePath: String) {
        val file = File(profilePath).let { if (it.exists()) it else File("/home/visiona/workspace/mana-hive/$profilePath") }.let { if (it.exists()) it else File("/home/visiona/workspace/mana-dist/$profilePath") }
        require(file.exists()) { "Perfil no encontrado: $profilePath" }
        val residentId = Regex("\"residentId\"\\s*:\\s*\"([^\"]+)\"").find(file.readText())?.groupValues?.get(1) ?: "jose"
        val (code, body) = Http.putJson("$hubUrl/api/profiles/$residentId", file.readText())
        println("  → PUT profile $profilePath → $code $body")
        Thread.sleep(3000)
    }
}
