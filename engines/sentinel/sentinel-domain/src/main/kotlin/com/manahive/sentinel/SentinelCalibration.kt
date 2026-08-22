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
 * Created from EffectiveRules (Politica Engine output) + fatigue budget config.
 * Injected at construction time — immutable for the evaluator's lifetime.
 *
 * If rules change, create a new evaluator with a new calibration.
 */
public data class SentinelCalibration(
    public val residentId: ResidentId,
    /** The alert rules, keyed by trigger state for fast lookup. */
    public val rulesByTrigger: Map<StateKind, AlertRule>,
    /** All rule IDs for this resident. */
    public val ruleIds: Set<RuleId>,
    /** Fatigue budget configuration. */
    public val fatigue: FatigueBudget,
    /** Rules fingerprint for reproducibility. */
    public val fingerprint: String,
) {
    public companion object {
        /**
         * Build a [SentinelCalibration] from effective rules and fatigue config.
         */
        public fun from(
            rules: EffectiveRules,
            fatigue: FatigueBudget,
        ): SentinelCalibration {
            val byTrigger = rules.rules.associateBy { it.trigger }
            return SentinelCalibration(
                residentId = rules.residentId,
                rulesByTrigger = byTrigger,
                ruleIds = rules.rules.map { it.id }.toSet(),
                fatigue = fatigue,
                fingerprint = rules.fingerprint,
            )
        }
    }

    /** Find the rule that matches a trigger state. */
    public fun ruleFor(trigger: StateKind): AlertRule? = rulesByTrigger[trigger]

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
 *     fatigue {
 *         maxPerShift = 5
 *     }
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
    private var fatigue = FatigueBudget(interruptionsThisShift = 0, maxPerShift = 5)
    private val rules = mutableMapOf<RuleId, AlertRuleBuilder>()

    public fun resident(id: String) {
        residentId = ResidentId(id)
    }

    public fun resident(id: ResidentId) {
        residentId = id
    }

    public fun fatigue(init: FatigueBudgetBuilder.() -> Unit) {
        FatigueBudgetBuilder().apply(init).also { fatigue = it.build() }
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
            ruleIds = builtRules.map { it.id }.toSet(),
            fatigue = fatigue,
            fingerprint = builtRules.joinToString(",") { it.id.value },
        )
    }
}

@SentinelDsl
public class FatigueBudgetBuilder {
    public var maxPerShift: Int = 5

    internal fun build(): FatigueBudget = FatigueBudget(
        interruptionsThisShift = 0,
        maxPerShift = maxPerShift,
    )
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
