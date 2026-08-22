package com.manahive.scene.sweeper

import com.manahive.scene.calibration.DwellCatalog
import com.manahive.scene.core.DigitalTwin
import com.manahive.kernel.Engine
import com.manahive.kernel.Explained
import java.time.Instant

/**
 * The patrolman of silence: produces the facts only the passage of time
 * reveals. Dwells are DERIVED state (now - stateSince >= threshold), never
 * persisted timers — a process restart can neither shorten nor extend one.
 *
 * Responsible for: dwell warnings; post-transition grace;
 * post-presence rearm (a staff visit resets the situation);
 * monitor heartbeat watch (input for SignalLost).
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

/**
 * Factory function for creating [ClockSweeper] instances.
 */
public fun createSweeper(): ClockSweeper = ClockSweeperImpl()
