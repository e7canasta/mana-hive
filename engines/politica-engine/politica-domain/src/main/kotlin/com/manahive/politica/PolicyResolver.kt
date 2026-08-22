package com.manahive.politica

import com.manahive.contracts.policy.AlarmCatalog
import com.manahive.contracts.policy.AlarmProfile
import com.manahive.contracts.policy.ConfidenceConfig
import com.manahive.contracts.policy.DwellThreshold
import com.manahive.contracts.policy.PolicyCalibration
import com.manahive.contracts.policy.PolicyDefaults
import com.manahive.contracts.policy.PolicyOverride
import com.manahive.contracts.policy.PolicySource
import com.manahive.contracts.policy.Template
import com.manahive.contracts.policy.TemplateId
import com.manahive.contracts.policy.TransitionKey
import com.manahive.contracts.scene.StateKind
import com.manahive.kernel.RuleId
import java.time.Duration

/**
 * Resolves effective rules for a resident by combining:
 * catalog preset → resident template → manual override → effective rules.
 *
 * Pure function: catalog + profile → PolicyCalibration.
 * No side effects, no I/O.
 *
 * Fowler's "Remove Dispensable": this object directly returns PolicyCalibration
 * instead of an intermediate EffectivePolicy type.
 */
internal object PolicyResolver {

    fun resolve(catalog: AlarmCatalog, profile: AlarmProfile): PolicyCalibration {
        val hysteresis = resolveHysteresis(catalog, profile)
        val dwellThresholds = resolveDwellThresholds(catalog, profile)

        return PolicyCalibration(
            residentId = profile.residentId,
            hysteresis = hysteresis,
            dwellThresholds = dwellThresholds,
            confidence = ConfidenceConfig(
                minConfidence = PolicyDefaults.minConfidence,
                heartbeatTimeout = PolicyDefaults.heartbeatTimeout,
            ),
        )
    }

    fun resolveSource(profile: AlarmProfile): PolicySource = when {
        profile.overrides.isNotEmpty() -> PolicySource.OVERRIDE
        profile.templateId != null -> PolicySource.TEMPLATE
        else -> PolicySource.CATALOG
    }

    private fun resolveHysteresis(
        catalog: AlarmCatalog,
        profile: AlarmProfile,
    ): Map<TransitionKey, Duration> {
        val base = resolveBase(profile.templateId, catalog)
            ?.hysteresis
            ?: catalog.transitions
        return applyHysteresisOverrides(base, profile.overrides)
    }

    private fun resolveDwellThresholds(
        catalog: AlarmCatalog,
        profile: AlarmProfile,
    ): Map<StateKind, DwellThreshold> {
        val base = resolveBase(profile.templateId, catalog)
            ?.dwellThresholds
            ?: catalog.dwellThresholds
        return applyDwellOverrides(base, profile.overrides)
    }

    private fun resolveBase(templateId: TemplateId?, catalog: AlarmCatalog): Template? =
        templateId?.let { id ->
            catalog.templates[id]
                ?: throw IllegalArgumentException(
                    "Template '$id' not found in catalog version ${catalog.version}"
                )
        }

    private fun applyHysteresisOverrides(
        base: Map<TransitionKey, Duration>,
        overrides: Map<RuleId, PolicyOverride>,
    ): Map<TransitionKey, Duration> {
        if (overrides.isEmpty()) return base
        val result = base.toMutableMap()
        overrides.values.filterIsInstance<PolicyOverride.HysteresisOverride>().forEach { override ->
            result[override.key] = override.value
        }
        return result
    }

    private fun applyDwellOverrides(
        base: Map<StateKind, DwellThreshold>,
        overrides: Map<RuleId, PolicyOverride>,
    ): Map<StateKind, DwellThreshold> {
        if (overrides.isEmpty()) return base
        val result = base.toMutableMap()
        overrides.values.filterIsInstance<PolicyOverride.DwellOverride>().forEach { override ->
            result[override.state] = override.value
        }
        return result
    }
}
