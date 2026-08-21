package com.manahive.sentinel

import com.manahive.contracts.scene.StateKind
import com.manahive.kernel.BedId
import com.manahive.kernel.EpisodeId
import com.manahive.kernel.RuleId
import java.time.Instant

/**
 * Episodes are NOT an aggregate: they are decision state of the evaluator,
 * foldable from scene facts, passed in as a parameter and returned as the
 * next value. Making them an aggregate would duplicate truth already present
 * in the scene stream.
 */
public data class EpisodeLedger(
    public val open: Map<BedId, Episode>,
    public val fatigue: FatigueBudget,
) {
    public companion object {
        public fun empty(budget: FatigueBudget): EpisodeLedger = EpisodeLedger(emptyMap(), budget)
    }
}

/** The arc between leaving a safe state and returning to it stably. */
public data class Episode(
    public val id: EpisodeId,
    public val bed: BedId,
    public val openedAt: Instant,
    public val origin: StateKind,
    public val alertedRules: Set<RuleId>,
)

/** Alarm fatigue as a design budget, not a staff complaint. */
public data class FatigueBudget(
    public val interruptionsThisShift: Int,
    public val maxPerShift: Int,
) {
    public val exceeded: Boolean get() = interruptionsThisShift >= maxPerShift
}
