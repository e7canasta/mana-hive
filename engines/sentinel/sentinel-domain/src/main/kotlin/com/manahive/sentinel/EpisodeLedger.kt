package com.manahive.sentinel

import com.manahive.contracts.policy.AlertRule
import com.manahive.contracts.policy.SceneFieldRule
import com.manahive.contracts.policy.Severity
import com.manahive.contracts.policy.ClosureCondition
import com.manahive.contracts.scene.StateKind
import com.manahive.contracts.sentinel.ClosureCause
import com.manahive.kernel.BedId
import com.manahive.kernel.EpisodeId
import com.manahive.kernel.ResidentId
import com.manahive.kernel.RuleId
import com.manahive.kernel.StaffId
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
 *
 * NOTE: Fatigue is NOT tracked here. Fatigue is a delivery concern (Harbor),
 * not a clinical judgment concern (Sentinel). Sentinel ALWAYS opens episodes.
 */
public data class EpisodeLedger(
    public val residentId: ResidentId,
    /** One open episode per bed at most. Keyed by bed because a resident
     *  could theoretically have events on multiple beds in the same night. */
    public val open: Map<BedId, Episode>,
    /** Closed episodes for audit trail. */
    public val closed: List<Episode> = emptyList(),
) {
    public companion object {
        public fun empty(residentId: ResidentId): EpisodeLedger =
            EpisodeLedger(residentId, open = emptyMap(), closed = emptyList())
    }

    /** Find the open episode for a specific bed. */
    public fun openForBed(bed: BedId): Episode? = open[bed]

    /** Register a new open episode. */
    public fun open(episode: Episode): EpisodeLedger =
        copy(open = open + (episode.bed to episode))

    /** Close an episode for a bed. */
    public fun close(bed: BedId): EpisodeLedger {
        val episode = open[bed] ?: return this
        return copy(
            open = open - bed,
            closed = closed + episode,
        )
    }
}

/**
 * The arc between leaving a safe state and returning to it stably.
 * One episode per bed, tracking the full lifecycle.
 *
 * Vernon's Aggregate Root: the episode guards its own invariants.
 * Factory method enforces creation rules; business methods enforce state transitions.
 *
 * Event Sourced: each state change is recorded as an immutable EpisodeEvent.
 */
public data class Episode(
    val id: EpisodeId,
    val bed: BedId,
    val residentId: ResidentId,
    val openedAt: Instant,
    /**
     * El estado de la persona que abrio este episodio.
     *
     * Null cuando lo abrio un campo de escena: la baranda baja no es una postura
     * y no hay [StateKind] que la nombre. Ver [triggerField].
     */
    val trigger: StateKind?,
    /** El campo `sujeto.aspecto` que lo abrio, si no fue una postura. */
    val triggerField: String? = null,
    /** Severity from the rule that opened this episode. */
    val severity: Severity,
    /** How this episode closes. */
    val closureCondition: ClosureCondition,
    /** Whether the resident can self-close (reversible). */
    val reversible: Boolean,
    /** Event log: immutable record of all state changes. */
    val eventLog: List<EpisodeEvent>,
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
         * Enforces invariant: episode ID is generated, event log starts with Opened.
         */
        public fun open(
            bed: BedId,
            residentId: ResidentId,
            at: Instant,
            rule: AlertRule,
        ): Episode {
            val episodeId = EpisodeId("${bed.value}-${UUID.randomUUID()}")
            return Episode(
                id = episodeId,
                bed = bed,
                residentId = residentId,
                openedAt = at,
                trigger = rule.trigger,
                severity = rule.severity,
                closureCondition = rule.closureCondition,
                reversible = rule.reversible,
                eventLog = listOf(
                    EpisodeEvent.Opened(
                        episodeId = episodeId,
                        trigger = rule.trigger,
                        severity = rule.severity,
                        at = at,
                    ),
                ),
                staffPresent = false,
                lastSafeState = null,
                alertedRules = setOf(rule.id),
            )
        }

        /**
         * Abrir un episodio a partir de una regla de campo de escena.
         *
         * Es una fabrica aparte y no un parametro opcional de [open] porque no
         * hay ningun estado de persona que poner: el sujeto del episodio es la
         * baranda, no la postura. Poner `UNKNOWN` para reusar la otra hubiera
         * sido afirmar algo falso sobre el residente.
         */
        public fun openForField(
            bed: BedId,
            residentId: ResidentId,
            at: Instant,
            rule: SceneFieldRule,
        ): Episode {
            val episodeId = EpisodeId("${bed.value}-${UUID.randomUUID()}")
            return Episode(
                id = episodeId,
                bed = bed,
                residentId = residentId,
                openedAt = at,
                trigger = null,
                triggerField = rule.field,
                severity = rule.severity,
                closureCondition = rule.closureCondition,
                // Un campo de escena vuelve solo: la baranda se sube. Es
                // reversible en el mismo sentido que una postura segura.
                reversible = true,
                eventLog = listOf(
                    EpisodeEvent.Opened(
                        episodeId = episodeId,
                        trigger = null,
                        triggerField = rule.field,
                        severity = rule.severity,
                        at = at,
                    ),
                ),
                staffPresent = false,
                lastSafeState = null,
                alertedRules = setOf(rule.id),
            )
        }
    }

    /** Can this episode close given current state? */
    public fun canClose(): Boolean = when (closureCondition) {
        ClosureCondition.SAFE_ONLY -> lastSafeState != null
        ClosureCondition.STAFF_AND_SAFE -> staffPresent && lastSafeState != null
        ClosureCondition.STAFF_OR_SAFE -> staffPresent || lastSafeState != null
    }

    /** Duration from episode open to now (or close). */
    public fun duration(now: Instant): Duration = Duration.between(openedAt, now)

    /** Gap duration: time without staff presence. */
    public fun gapDuration(now: Instant): Duration =
        if (staffPresent) Duration.ZERO else Duration.between(openedAt, now)

    /** Mark staff as present. Records event in log. */
    public fun withStaffPresent(at: Instant): Episode = copy(
        staffPresent = true,
        eventLog = eventLog + EpisodeEvent.StaffArrived(episodeId = id, at = at),
    )

    /** Mark staff as absent (staff left the room). Records event in log. */
    public fun withStaffAbsent(at: Instant): Episode = copy(
        staffPresent = false,
        eventLog = eventLog + EpisodeEvent.StaffLeft(episodeId = id, at = at),
    )

    /** Mark safe state reached. Records event in log. */
    public fun withSafeState(at: Instant): Episode = copy(
        lastSafeState = at,
        eventLog = eventLog + EpisodeEvent.SafeStateReached(episodeId = id, at = at),
    )

    /** Add an event under the umbrella. */
    public fun withEvent(event: EpisodeEvent): Episode = copy(eventLog = eventLog + event)

    /** Escalate severity (only if new severity is higher). Records event in log. */
    public fun escalate(rule: AlertRule, at: Instant): Episode = copy(
        severity = rule.severity,
        closureCondition = rule.closureCondition,
        alertedRules = alertedRules + rule.id,
        eventLog = eventLog + EpisodeEvent.Escalated(
            episodeId = id,
            from = severity,
            to = rule.severity,
            ruleId = rule.id,
            at = at,
        ),
    )

    /**
     * Elevar por una regla de campo de escena.
     *
     * La baranda baja puede elevar un episodio que abrio una postura, y al reves:
     * la severidad es el mecanismo de composicion y no distingue quien fue el
     * sujeto. Lo unico que no viaja es el `trigger`, que sigue siendo el de quien
     * abrio el episodio — elevarlo no lo reescribe.
     */
    public fun escalate(rule: SceneFieldRule, at: Instant): Episode = copy(
        severity = rule.severity,
        closureCondition = rule.closureCondition,
        alertedRules = alertedRules + rule.id,
        eventLog = eventLog + EpisodeEvent.Escalated(
            episodeId = id,
            from = severity,
            to = rule.severity,
            ruleId = rule.id,
            at = at,
        ),
    )

    /** Close the episode. Records event in log. */
    public fun close(cause: ClosureCause, at: Instant): Episode = copy(
        eventLog = eventLog + EpisodeEvent.Closed(
            episodeId = id,
            cause = cause,
            at = at,
        ),
    )

    /** Reconstruct state from event log (Event Sourcing). */
    public fun reconstruct(): Episode = eventLog.fold(this) { ep, event ->
        when (event) {
            is EpisodeEvent.Opened -> ep
            is EpisodeEvent.StaffArrived -> ep.copy(staffPresent = true)
            is EpisodeEvent.StaffLeft -> ep.copy(staffPresent = false)
            is EpisodeEvent.SafeStateReached -> ep.copy(lastSafeState = event.at)
            is EpisodeEvent.UmbrellaEvent -> ep
            is EpisodeEvent.Escalated -> ep.copy(
                severity = event.to,
                alertedRules = ep.alertedRules + event.ruleId,
            )
            is EpisodeEvent.Closed -> ep
        }
    }
}

/**
 * Event Sourcing: immutable record of all state changes in an episode.
 * Each event is a fact about what happened, not a command.
 *
 * Fowler: "Domain Event — a record of something that happened."
 * Vernon: "Event Sourcing — capture all changes as a sequence of events."
 */
public sealed interface EpisodeEvent {
    public val episodeId: EpisodeId
    public val at: Instant

    /** Episode opened. */
    public data class Opened(
        override val episodeId: EpisodeId,
        val trigger: StateKind?,
        val severity: Severity,
        override val at: Instant,
        /** El campo `sujeto.aspecto` que lo abrio, si no fue una postura. */
        val triggerField: String? = null,
    ) : EpisodeEvent

    /** Staff arrived in the room. */
    public data class StaffArrived(
        override val episodeId: EpisodeId,
        override val at: Instant,
    ) : EpisodeEvent

    /** Staff left the room. */
    public data class StaffLeft(
        override val episodeId: EpisodeId,
        override val at: Instant,
    ) : EpisodeEvent

    /** Resident returned to a safe state. */
    public data class SafeStateReached(
        override val episodeId: EpisodeId,
        override val at: Instant,
    ) : EpisodeEvent

    /** Event under the episode's umbrella. */
    public data class UmbrellaEvent(
        override val episodeId: EpisodeId,
        val state: StateKind,
        val matchedRule: RuleId?,
        val originalSeverity: Severity,
        override val at: Instant,
    ) : EpisodeEvent

    /** Episode escalated to higher severity. */
    public data class Escalated(
        override val episodeId: EpisodeId,
        val from: Severity,
        val to: Severity,
        val ruleId: RuleId,
        override val at: Instant,
    ) : EpisodeEvent

    /** Episode closed. */
    public data class Closed(
        override val episodeId: EpisodeId,
        val cause: ClosureCause,
        override val at: Instant,
    ) : EpisodeEvent
}
