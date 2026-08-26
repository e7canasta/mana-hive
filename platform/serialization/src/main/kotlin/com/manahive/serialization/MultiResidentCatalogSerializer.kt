package com.manahive.serialization

import com.manahive.contracts.policy.*
import com.manahive.contracts.scene.StateKind
import com.manahive.kernel.ResidentId
import java.time.Duration

/**
 * Multi-resident catalog serializer: Map<ResidentId, ResidentProfileConfig> ↔ TOML.
 *
 * Example TOML:
 * ```toml
 * version = "2.1.0"
 *
 * [residents.jose]
 * risk = "HIGH"
 * template = "standard"
 *
 * [residents.jose.rules.sitting]
 * alertAfter = "15m"
 *
 * [residents.maria]
 * risk = "LOW"
 * template = "night-wandering"
 *
 * [residents.maria.rules.sitting]
 * alertAfter = "20m"
 * ```
 */
object MultiResidentCatalogSerializer {

    fun toToml(profiles: Map<ResidentId, ResidentProfileConfig>): String {
        val catalog = MultiResidentCatalogToml(
            version = "2.1.0",
            residents = profiles.map { (id, config) ->
                id.value to ResidentConfigToml(
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
            }.toMap(),
        )

        return com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
            .writerWithDefaultPrettyPrinter()
            .writeValueAsString(catalog)
    }

    fun fromToml(toml: String): Map<ResidentId, ResidentProfileConfig> {
        val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
        val catalog = mapper.readValue(toml, MultiResidentCatalogToml::class.java)

        return catalog.residents.map { (residentId, config) ->
            ResidentId(residentId) to ResidentProfileConfig(
                profile = AlarmProfile(
                    residentId = ResidentId(residentId),
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
        }.toMap()
    }

    fun saveToFile(profiles: Map<ResidentId, ResidentProfileConfig>, file: java.io.File) {
        file.parentFile?.mkdirs()
        file.writeText(toToml(profiles))
    }

    fun loadFromFile(file: java.io.File): Map<ResidentId, ResidentProfileConfig> {
        if (!file.exists()) throw IllegalArgumentException("File not found: ${file.absolutePath}")
        return fromToml(file.readText())
    }
}
