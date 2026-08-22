package com.manahive.sentinel.batch.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.manahive.contracts.policy.ClosureCondition
import com.manahive.contracts.policy.Severity
import com.manahive.contracts.scene.StateKind
import com.manahive.sentinel.batch.SentinelBatchError
import com.manahive.sentinel.batch.parseDuration
import java.io.File
import java.time.Instant

/**
 * Loads BatchConfig from a YAML file.
 *
 * Uses Jackson for YAML deserialization, then maps to clean domain objects.
 * Fowler: "Introduce Explicit Conversion" — raw YAML → domain config.
 */
object BatchConfigLoader {

    private val mapper: ObjectMapper = ObjectMapper(YAMLFactory())
        .registerModule(KotlinModule.Builder().build())

    fun load(file: File): BatchConfig {
        if (!file.exists()) throw SentinelBatchError.ConfigNotFound(file.absolutePath)

        val raw = mapper.readValue<RawBatchConfig>(file)
        return raw.toDomain()
    }
}

// ── Raw YAML models (private — carry Jackson annotations) ────────────────

private data class RawBatchConfig(
    val resident: RawResidentConfig,
    val rules: List<RawRuleConfig>,
    val fatigue: RawFatigueConfig = RawFatigueConfig(),
    val events: RawEventsConfig,
) {
    fun toDomain(): BatchConfig = BatchConfig(
        resident = resident.toDomain(),
        rules = rules.map { it.toDomain() },
        fatigue = fatigue.toDomain(),
        events = events.toDomain(),
    )
}

private data class RawResidentConfig(
    val id: String,
    val bed: String,
    val night: String,
) {
    fun toDomain(): ResidentConfig = ResidentConfig(id, bed, night)
}

private data class RawRuleConfig(
    val id: String,
    val trigger: String,
    val severity: String = "WARNING",
    val closure: String = "SAFE_ONLY",
    val reversible: Boolean = true,
    val nvr: Boolean = false,
    val confirmation: Boolean = false,
    val confirmationWindow: String? = null,
    val umbrella: List<String> = emptyList(),
) {
    fun toDomain(): RuleConfig = RuleConfig(
        id = id,
        trigger = StateKind.valueOf(trigger),
        severity = Severity.valueOf(severity),
        closure = ClosureCondition.valueOf(closure),
        reversible = reversible,
        nvr = nvr,
        confirmation = confirmation,
        confirmationWindow = confirmationWindow?.let { parseDuration(it) },
        umbrella = umbrella.map { StateKind.valueOf(it) }.toSet(),
    )
}

private data class RawFatigueConfig(
    val maxPerShift: Int = 5,
) {
    fun toDomain(): FatigueConfig = FatigueConfig(maxPerShift)
}

private data class RawEventsConfig(
    val source: String,
    val output: String = "output",
    val start: String? = null,
) {
    fun toDomain(): EventsConfig = EventsConfig(
        source = source,
        output = output,
        start = start?.let { Instant.parse(it) },
    )
}
