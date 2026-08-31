package simulator.report

import java.time.Instant

data class ReportDataset(
    val meta: Meta,
    val steps: List<Map<String, Any>>,
    val episodes: List<Map<String, Any>>,
    val sceneEvents: List<Map<String, Any>>,
    val signals: List<Map<String, Any>>,
    val expect: Map<String, Any>?,
    val verified: Boolean
) {
    data class Meta(
        val scenario: String,
        val profile: String?,
        val hubUrl: String,
        val natsUrl: String,
        val residentId: String,
        val bed: String,
        val from: Instant,
        val to: Instant,
        val generatedAt: Instant = Instant.now()
    )
}
