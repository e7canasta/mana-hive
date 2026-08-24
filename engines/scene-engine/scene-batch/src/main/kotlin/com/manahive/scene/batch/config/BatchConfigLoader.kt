package com.manahive.scene.batch.config

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.manahive.contracts.policy.DwellThreshold
import com.manahive.contracts.scene.StateKind
import com.manahive.scene.core.TransitionTable
import com.manahive.scene.batch.BatchError
import java.io.File
import java.time.Duration
import java.time.Instant

/**
 * Loads [BatchConfig] from YAML files.
 *
 * Pattern: Adapter (Vernon) — bridges YAML format to domain model.
 * The Jackson annotations live here, not in the domain model.
 */
object BatchConfigLoader {

    fun load(file: File): BatchConfig {
        if (!file.exists()) throw BatchError.ConfigNotFound(file.absolutePath)

        val mapper = ObjectMapper(YAMLFactory())
        val raw = mapper.readValue(file, RawBatchConfig::class.java)

        return raw.toDomain()
    }

    // ── Raw YAML models (framework-coupled) ────────────────────────────────

    private data class RawBatchConfig @JsonCreator constructor(
        @JsonProperty("scene") val scene: RawSceneConfig,
        @JsonProperty("calibration") val calibration: RawCalibrationConfig,
        @JsonProperty("events") val events: RawEventsConfig,
    )

    private data class RawSceneConfig @JsonCreator constructor(
        @JsonProperty("bed") val bed: String,
        @JsonProperty("night") val night: String,
        @JsonProperty("resident") val resident: String,
        @JsonProperty("monitor") val monitor: String,
    )

    private data class RawCalibrationConfig @JsonCreator constructor(
        @JsonProperty("transitions") val transitions: String = "RELEASE_2",
        @JsonProperty("confidence") val confidence: Map<String, Double> = emptyMap(),
        @JsonProperty("dwell") val dwell: Map<String, RawDwellConfig> = emptyMap(),
        @JsonProperty("comeBack") val comeBack: Map<String, RawDwellConfig>? = null,
        @JsonProperty("heartbeat") val heartbeat: RawHeartbeatConfig = RawHeartbeatConfig(),
    )

    private data class RawDwellConfig @JsonCreator constructor(
        @JsonProperty("warning") val warning: String? = null,
        @JsonProperty("exceeded") val exceeded: String? = null,
    )

    private data class RawHeartbeatConfig @JsonCreator constructor(
        @JsonProperty("timeout") val timeout: String = "90s",
    )

    private data class RawEventsConfig @JsonCreator constructor(
        @JsonProperty("source") val source: String,
        @JsonProperty("output") val output: String = "output",
        @JsonProperty("start") val start: String? = null,
    )

    // ── Conversion to domain ───────────────────────────────────────────────

    private fun RawBatchConfig.toDomain(): BatchConfig = BatchConfig(
        scene = scene.toDomain(),
        calibration = calibration.toDomain(),
        events = events.toDomain(),
    )

    private fun RawSceneConfig.toDomain(): SceneConfig = SceneConfig(
        bed = bed,
        night = night,
        resident = resident,
        monitor = monitor,
    )

    private fun RawCalibrationConfig.toDomain(): CalibrationConfig = CalibrationConfig(
        transitionTable = when (transitions) {
            "RELEASE_1" -> TransitionTable.RELEASE_1
            "RELEASE_2" -> TransitionTable.RELEASE_2
            else -> throw BatchError.InvalidTransitionTable(transitions)
        },
        confidence = confidence.mapKeys { StateKind.valueOf(it.key) },
        dwellThresholds = dwell.map { (key, raw) ->
            StateKind.valueOf(key) to DwellThreshold(
                warning = parseDuration(raw.warning ?: "0s"),
                exceeded = parseDuration(raw.exceeded ?: "0s"),
            )
        }.toMap(),
        comeBackThresholds = (comeBack ?: emptyMap()).map { (key, raw) ->
            StateKind.valueOf(key) to DwellThreshold(
                warning = parseDuration(raw.warning ?: "0s"),
                exceeded = parseDuration(raw.exceeded ?: "0s"),
            )
        }.toMap(),
        heartbeatTimeout = parseDuration(heartbeat.timeout),
    )

    private fun RawEventsConfig.toDomain(): EventsConfig = EventsConfig(
        source = source,
        output = output,
        start = start?.let { Instant.parse(it) },
    )

    private fun parseDuration(text: String): Duration {
        val regex = Regex("""(\d+)([smh])""")
        var duration = Duration.ZERO
        for (match in regex.findAll(text)) {
            val value = match.groupValues[1].toLong()
            val unit = match.groupValues[2]
            duration = when (unit) {
                "s" -> duration.plusSeconds(value)
                "m" -> duration.plusMinutes(value)
                "h" -> duration.plusHours(value)
                else -> throw BatchError.InvalidDuration(text)
            }
        }
        return duration
    }
}
