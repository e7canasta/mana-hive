package com.manahive.sentinel

import com.manahive.contracts.policy.AlertRule
import com.manahive.contracts.policy.Severity
import com.manahive.contracts.policy.ClosureCondition
import com.manahive.contracts.scene.StateKind
import com.manahive.kernel.BedId
import com.manahive.kernel.EpisodeId
import com.manahive.kernel.ResidentId
import com.manahive.kernel.RuleId
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Decision state of the evaluator for ONE resident.
 * Foldable from scene facts, passed in as a parameter and returned as the
 * next value. Making them an aggregate would duplicate truth already present
 * in the scene stream.
 *
 * One ledger per resident. Episodes follow the resident across bed changes.
 */
public data class EpisodeLedger(
    public val residentId: ResidentId,
    /** One open episode per bed at most. Keyed by bed because a resident
     *  could theoretically have events on multiple beds in the same night. */
    public val open: Map<BedId, Episode>,
    public val fatigue: FatigueBudget,
) {
    public companion object {
        public fun empty(residentId: ResidentId, budget: FatigueBudget): EpisodeLedger =
            EpisodeLedger(residentId, emptyMap(), budget)
    }

    /** Find the open episode for a specific bed. */
    public fun openForBed(bed: BedId): Episode? = open[bed]

    /** Register a new open episode. */
    public fun open(episode: Episode): EpisodeLedger =
        copy(open = open + (episode.bed to episode))

    /** Close an episode for a bed. */
    public fun close(bed: BedId): EpisodeLedger =
        copy(open = open - bed)

    /** Increment fatigue counter (called when an episode opens). */
    public fun withFatigueIncrement(): EpisodeLedger =
        copy(fatigue = fatigue.copy(interruptionsThisShift = fatigue.interruptionsThisShift + 1))
}

/**
 * The arc between leaving a safe state and returning to it stably.
 * One episode per bed, tracking the full lifecycle.
 *
 * Vernon's Aggregate Root: the episode guards its own invariants.
 * Factory method enforces creation rules; business methods enforce state transitions.
 */
public data class Episode(
    val id: EpisodeId,
    val bed: BedId,
    val residentId: ResidentId,
    val openedAt: Instant,
    /** The trigger that opened this episode. */
    val trigger: StateKind,
    /** Severity from the rule that opened this episode. */
    val severity: Severity,
    /** How this episode closes. */
    val closureCondition: ClosureCondition,
    /** Whether the resident can self-close (reversible). */
    val reversible: Boolean,
    /** All events that occurred under this episode's umbrella. */
    val events: List<EpisodeEvent>,
    /** Whether staff has been present since the episode opened. */
    val staffPresent: Boolean,
    /** When the resident last returned to a safe state. null if still at risk. */
    val lastSafeState: Instant?,
    /** Rules that have already fired for this episode (prevents duplicate alerts). */
    val alertedRules: Set<RuleId>,
) {
    public companion object {
        /**
         * Factory: open a new episode from a rule and trigger.
         * Enforces invariant: episode ID is generated, events start empty.
         */
        public fun open(
            bed: BedId,
            residentId: ResidentId,
            at: Instant,
            rule: AlertRule,
        ): Episode = Episode(
            id = EpisodeId("${bed.value}-${UUID.randomUUID()}"),
            bed = bed,
            residentId = residentId,
            openedAt = at,
            trigger = rule.trigger,
            severity = rule.severity,
            closureCondition = rule.closureCondition,
            reversible = rule.reversible,
            events = emptyList(),
            staffPresent = false,
            lastSafeState = null,
            alertedRules = setOf(rule.id),
        )
    }

    /** Can this episode close given current state? */
    public fun canClose(): Boolean = when (closureCondition) {
        ClosureCondition.SAFE_ONLY -> lastSafeState != null
        ClosureCondition.STAFF_AND_SAFE -> staffPresent && lastSafeState != null
    }

    /** Duration from episode open to now (or close). */
    public fun duration(now: Instant): Duration = Duration.between(openedAt, now)

    /** Gap duration: time without staff presence. */
    public fun gapDuration(now: Instant): Duration =
        if (staffPresent) Duration.ZERO else Duration.between(openedAt, now)

    /** Mark staff as present. */
    public fun withStaffPresent(): Episode = copy(staffPresent = true)

    /** Mark safe state reached. */
    public fun withSafeState(at: Instant): Episode = copy(lastSafeState = at)

    /** Add an event under the umbrella. */
    public fun withEvent(event: EpisodeEvent): Episode = copy(events = events + event)

    /** Escalate severity (only if new severity is higher). */
    public fun escalate(rule: AlertRule): Episode = copy(
        severity = rule.severity,
        closureCondition = rule.closureCondition,
        alertedRules = alertedRules + rule.id,
    )
}

/**
 * A single event under an episode's umbrella.
 * Preserves the original fact's criticity even though the event is
 * reported as "under umbrella" (not a new episode).
 */
public data class EpisodeEvent(
    public val state: StateKind,
    public val at: Instant,
    /** The rule that would have triggered if no episode was open. */
    public val matchedRule: RuleId?,
    /** The severity that rule would have assigned. */
    public val originalSeverity: Severity,
)

/** Alarm fatigue as a design budget, not a staff complaint. */
public data class FatigueBudget(
    public val interruptionsThisShift: Int,
    public val maxPerShift: Int,
) {
    public val exceeded: Boolean get() = interruptionsThisShift >= maxPerShift
}
