package com.manahive.scene

import com.manahive.contracts.scene.SceneFact
import com.manahive.contracts.scene.StateKind
import com.manahive.kernel.BedId
import com.manahive.kernel.Engine
import com.manahive.kernel.Explained
import java.time.Duration
import java.time.Instant

/**
 * The patrolman of silence: produces the facts only the passage of time
 * reveals. Dwells are DERIVED state (now - stateSince >= threshold), never
 * persisted timers — a process restart can neither shorten nor extend one.
 *
 * Responsible for: dwell warnings at `warningRatio` of the threshold;
 * post-transition grace; post-presence rearm (a staff visit resets the
 * situation); monitor heartbeat watch (input for SignalLost).
 *
 * Invariant: sweep idempotency — two consecutive ticks without state change
 * emit nothing new; one DwellExceeded per (bed, state, stateSince).
 *
 * The shell fires the tick; the sweeper never schedules itself. There is no
 * cron thinking here: one clock, one sweep, everything derived.
 */
public interface ClockSweeper : Engine {
    public fun sweep(
        twins: Collection<DigitalTwin>,
        now: Instant,
        thresholds: DwellCatalog,
        marks: DwellMarks,
    ): Explained<SweepResult>
}

public data class DwellCatalog(
    public val byState: Map<StateKind, Duration>,
    public val warningRatio: Double = 0.8,
    public val postTransitionGrace: Duration = Duration.ofSeconds(10),
)

/** Idempotency marks: what this sweep already emitted, keyed by dwell identity. */
public data class DwellMarks(public val emitted: Set<DwellMarkKey>) {
    public companion object { public val NONE: DwellMarks = DwellMarks(emptySet()) }
}

public data class DwellMarkKey(
    public val bed: BedId,
    public val state: StateKind,
    public val since: Instant,
    public val warning: Boolean,
)

public data class SweepResult(
    public val facts: List<SceneFact>,
    public val marks: DwellMarks,
)
