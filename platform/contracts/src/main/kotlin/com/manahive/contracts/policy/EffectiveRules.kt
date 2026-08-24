package com.manahive.contracts.policy

import com.manahive.contracts.scene.StateKind
import com.manahive.kernel.ResidentId
import com.manahive.kernel.RuleId
import java.time.Duration

/**
 * The resolved rules for one resident: what happens when a scene fact arrives.
 *
 * Politica Engine produces this from catalog + profile + overrides.
 * Sentinel consults it to decide what action to take.
 *
 * Fowler's "Introduce Parameter Object": instead of passing catalog, profile,
 * and overrides separately, we pass one resolved object.
 * Vernon's "Published Interface": this is the ACL between Politica and Sentinel.
 */
public data class EffectiveRules(
    public val residentId: ResidentId,
    public val rules: List<AlertRule>,
    public val fingerprint: String,
)

/**
 * One alert rule: a trigger state, the severity it implies, and how the
 * episode closes. The director configures these per resident via the
 * "self-service menu" analogy.
 *
 * The rule is contextual: severity depends on the trigger, risk level,
 * time of day, and staff presence — all resolved by Politica Engine.
 */
public data class AlertRule(
    public val id: RuleId,
    public val trigger: StateKind,
    public val severity: Severity,
    public val closureCondition: ClosureCondition,
    public val reversible: Boolean,
    public val requiresConfirmation: Boolean,
    public val requiresNvr: Boolean,
    public val confirmationWindow: Duration?,
    /** Events that are notifiable under this episode's umbrella. */
    public val umbrellaEvents: Set<StateKind> = emptySet(),
)

/**
 * Severity of an alert. Maps to system behavior:
 * - INFO: log only, aggregated into round digest
 * - WARNING: notify staff, wait for confirmation
 * - CRITICAL: dispatch immediately, NVR recording
 */
public enum class Severity { INFO, WARNING, CRITICAL }

/**
 * How an episode closes. The director configures this per resident.
 *
 * SAFE_ONLY: closes when resident returns to safe state (alert)
 * STAFF_AND_SAFE: closes when staff assists AND resident is safe (incident)
 * STAFF_OR_SAFE: closes when staff assists OR resident is safe (flexible)
 */
public enum class ClosureCondition { SAFE_ONLY, STAFF_AND_SAFE, STAFF_OR_SAFE }
