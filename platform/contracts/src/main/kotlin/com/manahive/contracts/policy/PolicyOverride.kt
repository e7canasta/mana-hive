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
}
