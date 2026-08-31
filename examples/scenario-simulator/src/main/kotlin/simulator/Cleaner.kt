package simulator

object Cleaner {
    fun clean(hubUrl: String = "http://localhost:8080", residentId: String = "jose", hiveUrl: String = "http://localhost:18081") {
        try {
            val (code, body) = Http.post("$hubUrl/api/v1/admin/clean?residentId=$residentId&cleanProfiles=true")
            println("  → POST /api/v1/admin/clean?residentId=$residentId&cleanProfiles=true → $code $body")
        } catch (e: Exception) {
            println("  ⚠ Clean hub falló: ${e.message}")
        }
        try {
            val (code, body) = Http.post("$hiveUrl/api/hive/reset?bedId=bed-4")
            println("  → POST /api/hive/reset?bedId=bed-4 → $code $body")
        } catch (e: Exception) {
            println("  ⚠ Reset hive falló (usando docker restart como fallback): ${e.message}")
        }
        Thread.sleep(300)
    }
}
