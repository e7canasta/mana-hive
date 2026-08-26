package com.manahive.politica

import com.manahive.contracts.common.Channel
import com.manahive.contracts.common.Fingerprint
import com.manahive.contracts.common.buildFingerprint
import com.manahive.contracts.policy.AlertRule
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
import com.manahive.contracts.policy.TransitionKey
import com.manahive.contracts.policy.TemplateId
import com.manahive.contracts.policy.TransitionWindow
import com.manahive.contracts.policy.TriggerOn
import com.manahive.contracts.scene.StateKind
import com.manahive.kernel.Explained
import com.manahive.kernel.ExplanationStep
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
     *
     * Returns [Explained] because the precedence it applies — catalog, then
     * template, then override — is exactly what the director is owed when he
     * asks "where did that ten minutes come from?". Producing the calibration
     * without saying which layer won answers half the question.
     */
    public fun resolve(catalog: DagCatalog, profile: AlarmProfile): Explained<PolicyCalibration> {
        val hysteresis = resolveHysteresisFromDag(catalog, profile)
        val dwellThresholds = resolveDwellThresholdsFromDag(catalog, profile)
        val alertRules = resolveAlertRulesFromDag(catalog, profile)
        val transitionWindows = resolveTransitionWindowsFromDag(catalog)

        val calibration = PolicyCalibration(
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
            fingerprint = fingerprintOf(catalog, profile),
        )

        return Explained(value = calibration, explanation = explain(catalog, profile))
    }

    /**
     * The fingerprint of the rules that produced a calibration.
     *
     * Includes the catalog version even though it does not change the resolved
     * values on its own: two catalogs of different versions must produce
     * different fingerprints, because the auditable question is "which rules
     * decided this", not "which rules look like these".
     */
    private fun fingerprintOf(catalog: DagCatalog, profile: AlarmProfile): Fingerprint =
        buildFingerprint(
            "catalog" to catalog.version.value,
            "resident" to profile.residentId.value,
            "template" to (profile.templateId?.value ?: "none"),
            "risk" to profile.riskLevel,
            "mobility" to profile.mobilityAid,
            "overrides" to profile.overrides.keys.map { it.value }.sorted().joinToString("+"),
        )

    /** One step per layer that actually contributed, in precedence order. */
    private fun explain(catalog: DagCatalog, profile: AlarmProfile): List<ExplanationStep> {
        val steps = mutableListOf(
            ExplanationStep(
                rule = "catalog",
                observed = "catalog ${catalog.version.value} with ${catalog.residentStates.size} watched states",
                conclusion = "base rules taken from the catalog",
            ),
        )
        profile.templateId?.let {
            steps += ExplanationStep(
                rule = "template",
                observed = "template ${it.value}",
                conclusion = "template applied over the catalog",
            )
        }
        if (profile.overrides.isNotEmpty()) {
            steps += ExplanationStep(
                rule = "override",
                observed = "overrides ${profile.overrides.keys.map { it.value }.sorted()}",
                conclusion = "per-resident overrides win over template and catalog",
            )
        }
        return steps
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
