package com.manahive.sentinel

import com.manahive.contracts.policy.AlertRule
import com.manahive.contracts.policy.ClosureCondition
import com.manahive.contracts.policy.EffectiveRules
import com.manahive.contracts.policy.Severity
import com.manahive.contracts.scene.StateKind
import com.manahive.kernel.ResidentId
import com.manahive.kernel.RuleId
import java.time.Duration

/**
 * Compiled business rules for one resident's sentinel evaluator.
 * Analogous to [com.manahive.scene.calibration.SceneCalibration] for SceneInterpreter.
 *
 * Created from EffectiveRules (Politica Engine output).
 * Injected at construction time — immutable for the evaluator's lifetime.
 *
 * If rules change, create a new evaluator with a new calibration.
 *
 * NOTE: Fatigue is NOT a concern of Sentinel (clinical judgment).
 * Fatigue is a delivery concern handled by Harbor (the watchdog).
 * Sentinel ALWAYS opens episodes when a rule matches — facts are facts.
 */
public data class SentinelCalibration(
    public val residentId: ResidentId,
    /** The alert rules, keyed by trigger state for fast lookup. */
    public val rulesByTrigger: Map<StateKind, AlertRule>,
    /** Rules for transition events (keyed by target state). */
    public val transitionRules: Map<StateKind, AlertRule>,
    /** Rules for dwell events (keyed by state). */
    public val dwellRules: Map<StateKind, AlertRule>,
    /** Rules for scene state events (keyed by field). */
    public val sceneStateRules: Map<String, AlertRule>,
    /** All rule IDs for this resident. */
    public val ruleIds: Set<RuleId>,
    /** Rules fingerprint for reproducibility. */
    public val fingerprint: String,
) {
    public companion object {
        /**
         * Build a [SentinelCalibration] from effective rules.
         */
        public fun from(rules: EffectiveRules): SentinelCalibration {
            val byTrigger = rules.rules.associateBy { it.trigger }
            return SentinelCalibration(
                residentId = rules.residentId,
                rulesByTrigger = byTrigger,
                transitionRules = byTrigger,
                dwellRules = byTrigger,
                sceneStateRules = emptyMap(),
                ruleIds = rules.rules.map { it.id }.toSet(),
                fingerprint = rules.fingerprint,
            )
        }
    }

    /** Find the rule that matches a trigger state (legacy method). */
    public fun ruleFor(trigger: StateKind): AlertRule? = rulesByTrigger[trigger]

    /** Find the rule for a transition event. */
    public fun transitionRuleFor(targetState: StateKind): AlertRule? = transitionRules[targetState]

    /** Find the rule for a dwell event. */
    public fun dwellRuleFor(state: StateKind): AlertRule? = dwellRules[state]

    /** Find the rule for a scene state event. */
    public fun sceneStateRuleFor(field: String): AlertRule? = sceneStateRules[field]

    /** Find the notifiable states for a given trigger (umbrella events). */
    public fun notifiableStatesFor(trigger: StateKind): Set<StateKind> {
        val rule = ruleFor(trigger)
        return rule?.umbrellaEvents ?: emptySet()
    }
}

// ── DSL ──────────────────────────────────────────────────────────────────────

/**
 * Type-safe DSL for building [SentinelCalibration] instances.
 *
 * Example:
 * ```kotlin
 * val calibration = sentinelCalibration {
 *     resident("maria")
 *
 *     rule("r-fall") {
 *         trigger = StateKind.BED_EDGE
 *         severity = Severity.CRITICAL
 *         closureCondition = ClosureCondition.STAFF_AND_SAFE
 *         reversible = false
 *         requiresNvr = true
 *         requiresConfirmation = true
 *         confirmationWindow = Duration.ofSeconds(30)
 *         umbrellaEvents(StateKind.STANDING, StateKind.ATTEMPTING_EXIT)
 *     }
 *
 *     rule("r-sit") {
 *         trigger = StateKind.SITTING_IN_BED
 *         severity = Severity.WARNING
 *         closureCondition = ClosureCondition.SAFE_ONLY
 *         reversible = true
 *     }
 * }
 * ```
 */
public fun sentinelCalibration(init: SentinelCalibrationBuilder.() -> Unit): SentinelCalibration =
    SentinelCalibrationBuilder().apply(init).build()

@SentinelDsl
public class SentinelCalibrationBuilder {
    private var residentId: ResidentId? = null
    private val rules = mutableMapOf<RuleId, AlertRuleBuilder>()

    public fun resident(id: String) {
        residentId = ResidentId(id)
    }

    public fun resident(id: ResidentId) {
        residentId = id
    }

    public fun rule(id: String, init: AlertRuleBuilder.() -> Unit) {
        AlertRuleBuilder(RuleId(id)).apply(init).also { rules[RuleId(id)] = it }
    }

    internal fun build(): SentinelCalibration {
        val id = requireNotNull(residentId) { "resident() must be called" }
        val builtRules = rules.values.map { it.build() }
        val byTrigger = builtRules.associateBy { it.trigger }
        return SentinelCalibration(
            residentId = id,
            rulesByTrigger = byTrigger,
            transitionRules = byTrigger,
            dwellRules = byTrigger,
            sceneStateRules = emptyMap(),
            ruleIds = builtRules.map { it.id }.toSet(),
            fingerprint = builtRules.joinToString(",") { it.id.value },
        )
    }
}

@SentinelDsl
public class AlertRuleBuilder(private val ruleId: RuleId) {
    public var trigger: StateKind = StateKind.LYING
    public var severity: Severity = Severity.WARNING
    public var closureCondition: ClosureCondition = ClosureCondition.SAFE_ONLY
    public var reversible: Boolean = true
    public var requiresConfirmation: Boolean = false
    public var requiresNvr: Boolean = false
    public var confirmationWindow: Duration? = null
    private val umbrellaEvents = mutableSetOf<StateKind>()

    public fun umbrellaEvents(vararg states: StateKind) {
        umbrellaEvents.addAll(states)
    }

    internal fun build(): AlertRule = AlertRule(
        id = ruleId,
        trigger = trigger,
        severity = severity,
        closureCondition = closureCondition,
        reversible = reversible,
        requiresConfirmation = requiresConfirmation,
        requiresNvr = requiresNvr,
        confirmationWindow = confirmationWindow,
        umbrellaEvents = umbrellaEvents.toSet(),
    )
}

@DslMarker
public annotation class SentinelDsl
