package com.manahive.sentinel.config

import com.manahive.contracts.policy.AlertRule
import com.manahive.contracts.policy.ClosureCondition
import com.manahive.contracts.policy.Severity
import com.manahive.contracts.scene.StateKind
import com.manahive.kernel.RuleId
import java.time.Duration

/**
 * Sentinel Engine configuration for a resident.
 *
 * This is the domain-specific configuration that Sentinel Engine uses.
 * It's derived from LocalConfig (TOML) or StoredSemanticBucket (Hub).
 *
 * Fowler: "Domain Model" — captures business rules for sentinel evaluation.
 *
 * @property residentId Resident identifier
 * @property rules Alert rules for this resident
 * @property maxAlertsPerShift Maximum alerts per shift (fatigue budget)
 * @property fingerprint Rules fingerprint for change detection
 */
public data class SentinelConfig(
    val residentId: String,
    val rules: List<AlertRule> = emptyList(),
    val maxAlertsPerShift: Int = 5,
    val fingerprint: String = "",
) {
    init {
        require(residentId.isNotBlank()) { "Resident ID must not be blank" }
        require(maxAlertsPerShift > 0) { "Max alerts per shift must be positive" }
    }

    /**
     * Get rules indexed by trigger state for fast lookup.
     */
    public val rulesByTrigger: Map<StateKind, AlertRule> by lazy {
        rules.associateBy { it.trigger }
    }

    /**
     * Get all rule IDs.
     */
    public val ruleIds: Set<RuleId> by lazy {
        rules.map { it.id }.toSet()
    }

    /**
     * Find the rule that matches a trigger state.
     *
     * @param trigger The trigger state
     * @return The matching rule, or null if no match
     */
    public fun ruleFor(trigger: StateKind): AlertRule? = rulesByTrigger[trigger]

    /**
     * Get the notifiable states for a given trigger (umbrella events).
     *
     * @param trigger The trigger state
     * @return Set of notifiable states
     */
    public fun notifiableStatesFor(trigger: StateKind): Set<StateKind> {
        return ruleFor(trigger)?.umbrellaEvents ?: emptySet()
    }
}
