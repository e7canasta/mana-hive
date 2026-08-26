package com.manahive.sentinel

import com.manahive.contracts.policy.AlertRule
import com.manahive.contracts.policy.ClosureCondition
import com.manahive.contracts.policy.EffectiveRules
import com.manahive.contracts.policy.Severity
import com.manahive.contracts.policy.TriggerOn
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
    /**
     * Every rule watching a state, grouped by that state — the union of
     * [transitionRules] and [dwellRules].
     *
     * IMPORTANT: ComeBack rules are EXCLUDED. ComeBack watches the ABSENCE
     * of a state (time since leaving), not the state itself. Including LYING
     * in rulesByState would make isWatched(LYING) return true, causing
     * DwellExceeded(LYING) to be notifiable under an umbrella — a bug.
     *
     * Use it for "is this state watched at all" — umbrella membership and
     * reporting. To decide whether an episode OPENS or ESCALATES, use
     * [transitionRuleFor], [dwellRuleFor], or [comeBackRuleFor].
     */
    public val rulesByState: Map<StateKind, List<AlertRule>>,
    /** Rules for transition events (keyed by target state). ENTRY rules only. */
    public val transitionRules: Map<StateKind, AlertRule>,
    /** Rules for dwell events (keyed by state). DWELL rules only. */
    public val dwellRules: Map<StateKind, AlertRule>,
    /** Rules for come-back events (keyed by baseline state). COME_BACK rules only. */
    public val comeBackRules: Map<StateKind, AlertRule>,
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
         *
         * ComeBack rules are separated into [comeBackRules] and excluded from
         * [rulesByState] — they watch absence, not the state itself.
         */
        public fun from(rules: EffectiveRules): SentinelCalibration {
            val nonComeBack = rules.rules.filter { it.triggerOn != TriggerOn.COME_BACK }
            val transition = nonComeBack.filter { it.triggerOn == TriggerOn.ENTRY }.associateBy { it.trigger }
            val dwell = nonComeBack.filter { it.triggerOn == TriggerOn.DWELL }.associateBy { it.trigger }
            val comeBack = rules.rules.filter { it.triggerOn == TriggerOn.COME_BACK }.associateBy { it.trigger }
            return SentinelCalibration(
                residentId = rules.residentId,
                rulesByState = nonComeBack.groupBy { it.trigger },
                transitionRules = transition,
                dwellRules = dwell,
                comeBackRules = comeBack,
                sceneStateRules = emptyMap(),
                ruleIds = rules.rules.map { it.id }.toSet(),
                fingerprint = rules.fingerprint,
            )
        }

    }

    /**
     * Every rule watching a state, whatever fact triggers each one.
     *
     * Answers "is this state watched at all" — for umbrella membership and for
     * reporting. It must NOT be used to decide whether to open or escalate an
     * episode: that depends on which fact arrived. Use [transitionRuleFor] or
     * [dwellRuleFor] there.
     */
    public fun rulesForState(state: StateKind): List<AlertRule> = rulesByState[state].orEmpty()

    /** Is this state watched at all, by either family? */
    public fun isWatched(state: StateKind): Boolean = rulesByState.containsKey(state)

    /** Find the rule for a transition event. */
    public fun transitionRuleFor(targetState: StateKind): AlertRule? = transitionRules[targetState]

    /** Find the rule for a dwell event. */
    public fun dwellRuleFor(state: StateKind): AlertRule? = dwellRules[state]

    /** Find the rule for a come-back event (keyed by baseline state). */
    public fun comeBackRuleFor(baseline: StateKind): AlertRule? = comeBackRules[baseline]

    /** Find the rule for a scene state event. */
    public fun sceneStateRuleFor(field: String): AlertRule? = sceneStateRules[field]

    /**
     * Find the notifiable states for a given trigger (umbrella events).
     *
     * Unions the umbrella sets of every rule watching the state: if either the
     * entry rule or the dwell rule says a state is notifiable under this episode,
     * it is. Umbrella membership is a property of the state, independent of which
     * fact opened the episode.
     */
    public fun notifiableStatesFor(trigger: StateKind): Set<StateKind> =
        rulesForState(trigger).flatMapTo(mutableSetOf()) { it.umbrellaEvents }
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
 *     rule("r-fall", StateKind.BED_EDGE, TriggerOn.ENTRY) {
 *         severity = Severity.CRITICAL
 *         closureCondition = ClosureCondition.STAFF_AND_SAFE
 *         reversible = false
 *         requiresNvr = true
 *         requiresConfirmation = true
 *         confirmationWindow = Duration.ofSeconds(30)
 *         umbrellaEvents(StateKind.STANDING, StateKind.ATTEMPTING_EXIT)
 *     }
 *
 *     rule("r-sit", StateKind.SITTING_IN_BED, TriggerOn.DWELL) {
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

    /**
     * [triggerOn] is a parameter, not a settable field with a default.
     *
     * A rule is a (state, trigger family) pair, and the family decides whether
     * the episode opens the moment he sits down or only after he has been
     * sitting a while. That is a clinical decision, so it cannot be something a
     * call site forgets: it used to default to DWELL, and the sentinel-alerts
     * blueprint quietly took that default for rules whose own scenario names
     * say "sentarse ABRE episode" — every one of its episodes stopped opening
     * and nothing failed, because blueprints were not run by `check`.
     *
     * PolicyResolver.buildAlertRule already refuses to default this. The DSL
     * now refuses too.
     *
     * [trigger] is a parameter for the same reason, and because a rule IS a
     * (state, family) pair — that is exactly why the two families needed
     * different RuleIds. Its old default was StateKind.LYING: a rule that
     * forgot to name its state silently watched the bed.
     */
    public fun rule(
        id: String,
        trigger: StateKind,
        triggerOn: TriggerOn,
        init: AlertRuleBuilder.() -> Unit,
    ) {
        AlertRuleBuilder(RuleId(id), trigger, triggerOn).apply(init).also { rules[RuleId(id)] = it }
    }

    internal fun build(): SentinelCalibration {
        val id = requireNotNull(residentId) { "resident() must be called" }
        val builtRules = rules.values.map { it.build() }
        val nonComeBack = builtRules.filter { it.triggerOn != TriggerOn.COME_BACK }
        val transition = nonComeBack.filter { it.triggerOn == TriggerOn.ENTRY }.associateBy { it.trigger }
        val dwell = nonComeBack.filter { it.triggerOn == TriggerOn.DWELL }.associateBy { it.trigger }
        val comeBack = builtRules.filter { it.triggerOn == TriggerOn.COME_BACK }.associateBy { it.trigger }
        return SentinelCalibration(
            residentId = id,
            rulesByState = nonComeBack.groupBy { it.trigger },
            transitionRules = transition,
            dwellRules = dwell,
            comeBackRules = comeBack,
            sceneStateRules = emptyMap(),
            ruleIds = builtRules.map { it.id }.toSet(),
            fingerprint = builtRules.joinToString(",") { it.id.value },
        )
    }
}

@SentinelDsl
public class AlertRuleBuilder(
    private val ruleId: RuleId,
    private val trigger: StateKind,
    private val triggerOn: TriggerOn,
) {
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
        triggerOn = triggerOn,
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
