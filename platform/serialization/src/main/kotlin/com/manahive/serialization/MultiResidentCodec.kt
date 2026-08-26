package com.manahive.serialization

import com.manahive.contracts.policy.*
import com.manahive.contracts.scene.StateKind
import com.manahive.kernel.ResidentId
import java.time.Duration
import java.io.File

/**
 * Multi-resident codec: Map<ResidentId, ResidentProfileConfig> ↔ TOML.
 *
 * Usage:
 * ```kotlin
 * val toml = residents.toToml()
 * val result = toml.toResidentCatalog()
 * result.onSuccess { residents -> ... }
 * residents.saveTo(File("residents.toml"))
 * ```
 */
object MultiResidentCodec : FileCodec<Map<ResidentId, ResidentProfileConfig>> {

    override fun encode(obj: Map<ResidentId, ResidentProfileConfig>): String {
        val catalog = MultiResidentCatalogToml(
            version = "2.1.0",
            residents = obj.map { (id, config) ->
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

    override fun decode(text: String): SerializationResult<Map<ResidentId, ResidentProfileConfig>> = serialization {
        val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
        val catalog = mapper.readValue(text, MultiResidentCatalogToml::class.java)

        catalog.residents.map { (residentId, config) ->
            ResidentId(residentId) to ResidentProfileConfig(
                profile = AlarmProfile(
                    residentId = ResidentId(residentId),
                    riskLevel = try { RiskLevel.valueOf(config.risk) } catch (_: Exception) { RiskLevel.MEDIUM },
                    mobilityAid = try { MobilityAid.valueOf(config.mobility) } catch (_: Exception) { MobilityAid.NONE },
                    autopilot = false,
                    mode = PolicyMode.PRESET,
                    templateId = TemplateId(config.template),
                    overrides = emptyMap(),
                    catalogVersion = CatalogVersion("2.1.0"),
                    validFrom = java.time.Instant.now(),
                ),
                stateOverrides = config.rules.map { (stateName, rule) ->
                    val stateResult = StateKindInput.parseStateKind(stateName)
                    stateResult.map { state ->
                        state to ProfileStateOverride(
                            warningAfter = rule.warningAfter?.let { Duration.parse(it) },
                            alertAfter = rule.alertAfter?.let { Duration.parse(it) },
                            severity = rule.severity?.let { try { Severity.valueOf(it) } catch (_: Exception) { null } },
                        )
                    }.getOrThrow()
                }.toMap(),
                transitionOverrides = config.transitions.map { (transitionName, rule) ->
                    val parts = transitionName.split("To")
                    if (parts.size != 2) {
                        throw SerializationException(SerializationError.InvalidTransition(parts.getOrElse(0) { "" }, parts.getOrElse(1) { "" }))
                    }
                    val fromResult = StateKindInput.parseStateKind(parts[0])
                    val toResult = StateKindInput.parseStateKind(parts[1])
                    fromResult.flatMap { from ->
                        toResult.map { to ->
                            TransitionKey(from, to) to ProfileTransitionOverride(
                                hysteresis = Duration.parse(rule.hysteresis),
                            )
                        }
                    }.getOrThrow()
                }.toMap(),
            )
        }.toMap()
    }
}
