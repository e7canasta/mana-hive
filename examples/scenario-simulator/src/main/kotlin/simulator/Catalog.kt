package simulator

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

data class Catalog(
    val profile: String? = null,
    val clean: Boolean = false,
    val autoDate: Boolean = false,
    val start: String? = null,
    val residentId: String = "jose",
    val bed: String = "bed-4",
    val monitor: String = "m1",
    val hubUrl: String = "http://localhost:8080",
    val natsUrl: String = "nats://localhost:4222",
    val expect: Expect? = null,
    val steps: List<Step> = emptyList()
) {
    fun startInstant(): Instant = start?.let { Instant.parse(it) } ?: Instant.parse("2024-01-15T22:00:00Z")
}

data class Expect(
    val episodes: List<ExpectedEpisode> = emptyList(),
    val minSceneEvents: Int? = null,
    val minSignals: Int? = null
)

sealed class Step {
    data class UseManual(val at: String) : Step()
    data class UseSystem(val dummy: String = "true") : Step()
    data class Advance(val duration: String) : Step()
    data class Obs(val kind: String, val conf: Double = 0.92) : Step()
}

// Jackson deserializer for polymorphic Step from yaml map like {useManual: "..."} or {obs: {kind: ...}}
