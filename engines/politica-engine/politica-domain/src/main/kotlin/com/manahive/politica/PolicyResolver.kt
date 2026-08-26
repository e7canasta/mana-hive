package com.manahive.politica

import com.manahive.contracts.common.Channel
import com.manahive.contracts.policy.AlertRule
import com.manahive.contracts.policy.AlarmCatalog
import com.manahive.contracts.policy.AlarmProfile
import com.manahive.contracts.policy.ClosureCondition
import com.manahive.contracts.policy.ConfidenceConfig
import com.manahive.contracts.policy.DagCatalog
import com.manahive.contracts.policy.DwellThreshold
import com.manahive.contracts.policy.HarborPolicy
import com.manahive.contracts.policy.PolicyCalibration
import com.manahive.contracts.policy.PolicyDefaults
import com.manahive.contracts.policy.PolicyOverride
import com.manahive.contracts.policy.PolicySource
import com.manahive.contracts.policy.RecorderPolicy
import com.manahive.contracts.policy.ScenePolicy
import com.manahive.contracts.policy.SentinelPolicy
import com.manahive.contracts.policy.Severity
import com.manahive.contracts.policy.Template
import com.manahive.contracts.policy.TemplateId
import com.manahive.contracts.policy.TransitionKey
import com.manahive.contracts.policy.TransitionWindow
import com.manahive.contracts.policy.TriggerOn
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
public object PolicyResolver {

    /**
     * Resolve using DAG-centric catalog (new API).
     * Converts DagCatalog to PolicyCalibration for all downstream engines.
     */
    public fun resolve(catalog: DagCatalog, profile: AlarmProfile): PolicyCalibration {
        val hysteresis = resolveHysteresisFromDag(catalog, profile)
        val dwellThresholds = resolveDwellThresholdsFromDag(catalog, profile)
        val alertRules = resolveAlertRulesFromDag(catalog, profile)
        val transitionWindows = resolveTransitionWindowsFromDag(catalog)

        return PolicyCalibration(
            residentId = profile.residentId,
            scene = ScenePolicy(
                hysteresis = hysteresis,
                dwellThresholds = dwellThresholds,
                confidence = ConfidenceConfig(
                    minConfidence = PolicyDefaults.minConfidence,
                    heartbeatTimeout = PolicyDefaults.heartbeatTimeout,
                ),
            ),
            sentinel = SentinelPolicy(alertRules = alertRules),
            harbor = HarborPolicy(
                defaultChannels = emptyMap(),
                escalationTimeouts = emptyMap(),
            ),
            recorder = RecorderPolicy(transitionWindows = transitionWindows),
        )
    }

    /**
     * Resolve using legacy AlarmCatalog (backward compatible).
     */
    public fun resolve(catalog: AlarmCatalog, profile: AlarmProfile): PolicyCalibration {
        val hysteresis = resolveHysteresis(catalog, profile)
        val dwellThresholds = resolveDwellThresholds(catalog, profile)

        return PolicyCalibration(
            residentId = profile.residentId,
            scene = ScenePolicy(
                hysteresis = hysteresis,
                dwellThresholds = dwellThresholds,
                confidence = ConfidenceConfig(
                    minConfidence = PolicyDefaults.minConfidence,
                    heartbeatTimeout = PolicyDefaults.heartbeatTimeout,
                ),
            ),
            sentinel = SentinelPolicy(alertRules = emptyMap()),
            harbor = HarborPolicy(defaultChannels = emptyMap(), escalationTimeouts = emptyMap()),
            recorder = RecorderPolicy(transitionWindows = emptyMap()),
        )
    }

    public fun resolveSource(profile: AlarmProfile): PolicySource = when {
        profile.overrides.isNotEmpty() -> PolicySource.OVERRIDE
        profile.templateId != null -> PolicySource.TEMPLATE
        else -> PolicySource.CATALOG
    }

    // ── DAG-centric resolution ──────────────────────────────────────────

    private fun resolveHysteresisFromDag(
        catalog: DagCatalog,
        profile: AlarmProfile,
    ): Map<TransitionKey, Duration> {
        val base = catalog.transitions.mapValues { it.value.hysteresis }
        return applyOverrides<PolicyOverride.HysteresisOverride, TransitionKey, Duration>(base, profile.overrides) { it.key to it.value }
    }

    private fun resolveDwellThresholdsFromDag(
        catalog: DagCatalog,
        profile: AlarmProfile,
    ): Map<StateKind, DwellThreshold> {
        val base = catalog.residentStates.mapNotNull { (state, rule) ->
            rule.alertAfter?.let { exceeded ->
                // When the director gives only a deadline ("avísenme a los 15 minutos"),
                // the silent pre-warning lands at half of it — the same rule the override
                // path applies. Defaulting warning to `exceeded` instead would violate
                // DwellThreshold's warning < exceeded invariant and crash the resolver.
                val warning = rule.warningAfter ?: exceeded.dividedBy(2)
                state to DwellThreshold(warning = warning, exceeded = exceeded)
            }
        }.toMap()
        return applyOverrides<PolicyOverride.DwellOverride, StateKind, DwellThreshold>(base, profile.overrides) { it.state to it.value }
    }

    /**
     * Derive AlertRules from ResidentStateRule in the catalog + profile overrides.
     * States with alertAfter in catalog OR profile overrides produce an AlertRule.
     *
     * Fowler: "Extract Method" — shared builder eliminates duplication between
     * catalog rules and override rules.
     */
    private fun resolveAlertRulesFromDag(
        catalog: DagCatalog,
        profile: AlarmProfile,
    ): Map<StateKind, AlertRule> {
        val dwellOverrides = profile.overrides.values.filterIsInstance<PolicyOverride.DwellOverride>()

        val catalogRules = catalog.residentStates.mapNotNull { (state, rule) ->
            if (!rule.alerts) return@mapNotNull null
            buildAlertRule(
                state = state,
                severity = rule.severity,
                closureCondition = rule.closureCondition,
                // The catalog decides how the rule fires: alertOnEntry() → ENTRY,
                // alertAfter() → DWELL. Never inferred here.
                triggerOn = rule.triggerOn,
            )
        }

        val overrideRules = dwellOverrides.mapNotNull { override ->
            if (catalogRules.any { it.trigger == override.state }) return@mapNotNull null
            buildAlertRule(
                state = override.state,
                severity = Severity.WARNING,
                closureCondition = ClosureCondition.STAFF_OR_SAFE,
                // A DwellOverride is, by its own name, a time threshold.
                triggerOn = TriggerOn.DWELL,
            )
        }

        return (catalogRules + overrideRules).associateBy { it.trigger }
    }

    /**
     * Build an AlertRule with sensible defaults for configurable fields.
     *
     * [triggerOn] has NO default on purpose: every call site must state how the
     * rule fires. A silent default is exactly how the "episode opens on entry"
     * defect got in (see docs/roadmap/SPEC-01).
     *
     * Fowler: "Extract Method" — single point of truth for AlertRule construction.
     */
    private fun buildAlertRule(
        state: StateKind,
        severity: Severity,
        closureCondition: ClosureCondition,
        triggerOn: TriggerOn,
    ): AlertRule = AlertRule(
        id = RuleId("alert-${state.name.lowercase()}"),
        trigger = state,
        triggerOn = triggerOn,
        severity = severity,
        closureCondition = closureCondition,
        reversible = true,
        requiresConfirmation = severity == Severity.WARNING,
        requiresNvr = severity == Severity.CRITICAL,
        confirmationWindow = null,
        umbrellaEvents = emptySet(),
    )

    /**
     * Derive TransitionWindows from DagTransitionRule.recordBefore/recordAfter.
     * Only transitions with at least one recording window defined produce a TransitionWindow.
     */
    private fun resolveTransitionWindowsFromDag(
        catalog: DagCatalog,
    ): Map<TransitionKey, TransitionWindow> {
        return catalog.transitions.mapNotNull { (key, rule) ->
            val before = rule.recordBefore ?: return@mapNotNull null
            val after = rule.recordAfter ?: Duration.ZERO
            key to TransitionWindow(before = before, after = after)
        }.toMap()
    }

    // ── Legacy AlarmCatalog resolution ──────────────────────────────────

    private fun resolveHysteresis(
        catalog: AlarmCatalog,
        profile: AlarmProfile,
    ): Map<TransitionKey, Duration> {
        val base = resolveBase(profile.templateId, catalog)
            ?.hysteresis
            ?.takeIf { it.isNotEmpty() }
            ?: catalog.transitions
        return applyOverrides<PolicyOverride.HysteresisOverride, TransitionKey, Duration>(base, profile.overrides) { it.key to it.value }
    }

    private fun resolveDwellThresholds(
        catalog: AlarmCatalog,
        profile: AlarmProfile,
    ): Map<StateKind, DwellThreshold> {
        val base = resolveBase(profile.templateId, catalog)
            ?.dwellThresholds
            ?.takeIf { it.isNotEmpty() }
            ?: catalog.dwellThresholds
        return applyOverrides<PolicyOverride.DwellOverride, StateKind, DwellThreshold>(base, profile.overrides) { it.state to it.value }
    }

    private fun resolveBase(templateId: TemplateId?, catalog: AlarmCatalog): Template? =
        templateId?.let { id ->
            catalog.templates[id]
                ?: throw IllegalArgumentException(
                    "Template '$id' not found in catalog version ${catalog.version}"
                )
        }

    private inline fun <reified O : PolicyOverride, K, V> applyOverrides(
        base: Map<K, V>,
        overrides: Map<RuleId, PolicyOverride>,
        extract: (O) -> Pair<K, V>,
    ): Map<K, V> {
        if (overrides.isEmpty()) return base
        val result = base.toMutableMap()
        overrides.values.filterIsInstance<O>().forEach { override ->
            val (key, value) = extract(override)
            result[key] = value
        }
        return result
    }
}
