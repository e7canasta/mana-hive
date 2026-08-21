package com.manahive.hub.policy

import com.manahive.contracts.policy.EffectiveRules
import com.manahive.contracts.policy.EffectiveRule
import com.manahive.contracts.policy.WatchLevel
import com.manahive.kernel.Engine
import com.manahive.kernel.Explained
import com.manahive.kernel.ResidentId
import com.manahive.kernel.StaffId
import java.time.Instant
import java.time.LocalTime

/**
 * Resolves, for one resident and one instant, which rules govern — and can
 * say where each one came from. Layered resolution with TOTAL, deterministic
 * precedence: watch level -> level template -> manual adjustments -> time
 * windows. Tie-break rule: the most protective layer wins.
 *
 * The resident policy itself is event-sourced in the hub (every change of
 * clinical judgment about a person is clinical history, not an UPDATE).
 * This engine only folds the layers; humans change them.
 */
public interface PolicyResolver : Engine {
    public fun resolve(
        resident: ResidentId,
        at: Instant,
        layers: PolicyLayers,
    ): Explained<EffectiveRules>
}

public data class PolicyLayers(
    public val level: WatchLevel,
    public val template: LevelTemplate,
    public val adjustments: List<ManualAdjustment>,
    public val windows: List<TimeWindow>,
)

public data class LevelTemplate(
    public val id: String,
    public val level: WatchLevel,
    public val rules: List<EffectiveRule>,
)

public data class ManualAdjustment(
    public val id: String,
    public val rule: EffectiveRule,
    public val actor: StaffId,
    public val at: Instant,
)

/** e.g. the night window 22:00-07:00 tightening exit rules. */
public data class TimeWindow(
    public val id: String,
    public val from: LocalTime,
    public val to: LocalTime,
    public val rules: List<EffectiveRule>,
)
