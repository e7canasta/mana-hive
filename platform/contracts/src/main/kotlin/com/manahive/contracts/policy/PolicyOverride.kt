package com.manahive.contracts.policy

import com.manahive.contracts.scene.StateKind
import com.manahive.kernel.RuleId
import java.time.Duration

/**
 * A manual override to a catalog rule. Different override types have
 * different value types — this sealed class enforces type safety.
 *
 * Fowler's "Replace Primitive with Value Object": instead of
 * `value: String`, we use typed subclasses.
 *
 * Fowler's "Primitive Obsession" on param: instead of `param: String`,
 * we carry the typed key directly (TransitionKey for hysteresis,
 * StateKind for dwell). The param string is gone.
 */
public sealed interface PolicyOverride {
    public val ruleId: RuleId

    /** Override a hysteresis duration for a specific transition. */
    public data class HysteresisOverride(
        override val ruleId: RuleId,
        public val key: TransitionKey,
        public val value: Duration,
    ) : PolicyOverride

    /** Override a dwell threshold (warning or exceeded) for a specific state. */
    public data class DwellOverride(
        override val ruleId: RuleId,
        public val state: StateKind,
        public val value: DwellThreshold,
    ) : PolicyOverride

    /**
     * Override a come-back threshold for a specific baseline state.
     *
     * [severity] and [closureCondition] are nullable on purpose: null means the
     * director did not speak about them, and the catalog's own values stand.
     * Collapsing null into a default here would silently downgrade a CRITICAL
     * catalog rule to WARNING the moment someone retimed it.
     */
    public data class ComeBackOverride(
        override val ruleId: RuleId,
        public val baseline: StateKind,
        public val value: DwellThreshold,
        public val severity: Severity? = null,
        public val closureCondition: ClosureCondition? = null,
    ) : PolicyOverride
}
