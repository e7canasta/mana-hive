package com.manahive.serialization

import com.manahive.contracts.policy.*
import com.manahive.contracts.scene.StateKind
import com.manahive.kernel.ResidentId
import java.time.Duration

/**
 * Policy serializer: ResidentProfileConfig ↔ TOML.
 *
 * Usage:
 * ```kotlin
 * // Serialize to TOML
 * val toml = PolicySerializer.toToml(profileConfig)
 *
 * // Deserialize from TOML
 * val profileConfig = PolicySerializer.fromToml(toml)
 *
 * // Save to file
 * PolicySerializer.saveToFile(profileConfig, File("jose-policy.toml"))
 *
 * // Load from file
 * val profileConfig = PolicySerializer.loadFromFile(File("jose-policy.toml"))
 * ```
 */
object PolicySerializer {

    fun toToml(config: ResidentProfileConfig): String {
        val toml = PolicyToml(
            name = config.profile.residentId.value,
            resident = config.profile.residentId.value,
            risk = config.profile.riskLevel.name,
            mobility = config.profile.mobilityAid.name,
            template = config.profile.templateId?.value ?: "standard",
            rules = config.stateOverrides.map { (state, override) ->
                state.name.lowercase() to StateRuleToml(
                    alertAfter = override.alertAfter?.toString(),
                    severity = override.severity?.name,
                )
            }.toMap(),
            transitions = config.transitionOverrides.map { (key, override) ->
                "${key.from.name.lowercase()}To${key.to.name.lowercase()}" to TransitionRuleToml(
                    hysteresis = override.hysteresis.toString(),
                )
            }.toMap(),
        )

        return com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
            .writerWithDefaultPrettyPrinter()
            .writeValueAsString(toml)
    }

    fun fromToml(toml: String): ResidentProfileConfig {
        val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
        val config = mapper.readValue(toml, PolicyToml::class.java)

        return ResidentProfileConfig(
                profile = AlarmProfile(
                    residentId = ResidentId(config.resident),
                    riskLevel = RiskLevel.valueOf(config.risk),
                    mobilityAid = MobilityAid.valueOf(config.mobility),
                    autopilot = false,
                    mode = PolicyMode.PRESET,
                    templateId = TemplateId(config.template),
                    overrides = emptyMap(),
                    catalogVersion = CatalogVersion("2.1.0"),
                    validFrom = java.time.Instant.now(),
                ),
            stateOverrides = config.rules.map { (stateName, rule) ->
                StateKind.valueOf(stateName.uppercase()) to ProfileStateOverride(
                    alertAfter = rule.alertAfter?.let { Duration.parse(it) },
                    severity = rule.severity?.let { Severity.valueOf(it) },
                )
            }.toMap(),
            transitionOverrides = config.transitions.map { (transitionName, rule) ->
                val parts = transitionName.split("To")
                val from = StateKind.valueOf(parts[0].uppercase())
                val to = StateKind.valueOf(parts[1].uppercase())
                TransitionKey(from, to) to ProfileTransitionOverride(
                    hysteresis = Duration.parse(rule.hysteresis),
                )
            }.toMap(),
        )
    }

    fun saveToFile(config: ResidentProfileConfig, file: java.io.File) {
        file.parentFile?.mkdirs()
        file.writeText(toToml(config))
    }

    fun loadFromFile(file: java.io.File): ResidentProfileConfig {
        if (!file.exists()) throw IllegalArgumentException("File not found: ${file.absolutePath}")
        return fromToml(file.readText())
    }
}
