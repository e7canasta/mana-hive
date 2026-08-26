package com.manahive.serialization

import com.manahive.contracts.policy.*
import com.manahive.contracts.scene.StateKind
import com.manahive.kernel.ResidentId
import java.time.Duration
import java.io.File

/**
 * Policy codec: ResidentProfileConfig ↔ TOML.
 *
 * Usage:
 * ```kotlin
 * val toml = profile.toToml()
 * val result = toml.toProfile()
 * result.onSuccess { profile -> ... }
 * profile.saveTo(File("jose-policy.toml"))
 * ```
 */
object PolicyCodec : FileCodec<ResidentProfileConfig> {

    override fun encode(obj: ResidentProfileConfig): String {
        val toml = PolicyToml(
            name = obj.profile.residentId.value,
            resident = obj.profile.residentId.value,
            risk = obj.profile.riskLevel.name,
            mobility = obj.profile.mobilityAid.name,
            template = obj.profile.templateId?.value ?: "standard",
            rules = obj.stateOverrides.map { (state, override) ->
                state.name.lowercase() to StateRuleToml(
                    alertAfter = override.alertAfter?.toString(),
                    severity = override.severity?.name,
                )
            }.toMap(),
            transitions = obj.transitionOverrides.map { (key, override) ->
                "${key.from.name.lowercase()}To${key.to.name.lowercase()}" to TransitionRuleToml(
                    hysteresis = override.hysteresis.toString(),
                )
            }.toMap(),
        )

        return com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
            .writerWithDefaultPrettyPrinter()
            .writeValueAsString(toml)
    }

    override fun decode(text: String): SerializationResult<ResidentProfileConfig> = serialization {
        val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
        val config = mapper.readValue(text, PolicyToml::class.java)

        ResidentProfileConfig(
            profile = AlarmProfile(
                residentId = ResidentId(config.resident),
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
    }
}
