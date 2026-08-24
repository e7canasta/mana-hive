package com.manahive.sentinel

import com.manahive.contracts.scene.SceneEvent
import com.manahive.contracts.sentinel.SentinelSignal
import com.manahive.kernel.Engine
import com.manahive.kernel.Explained
import java.time.Instant

/**
 * The clinical judge: one resident, one calibration.
 *
 * Evaluates a scene fact against the resident's compiled rules and current
 * episode state. Produces signals and the next episode state.
 *
 * Same philosophy as SceneInterpreter:
 * - Created with calibration (SentinelCalibration) — immutable for the evaluator's lifetime
 * - State flows through (EpisodeLedger in, SentinelVerdict.episodes out)
 * - Pure function: same input → same output
 * - Now is injected, never Instant.now()
 *
 * Responsible for:
 * - Episode lifecycle (open, track events, close)
 * - Severity assignment from rules
 * - Umbrella event classification (notifiable vs suppressed)
 * - Gap of assistance tracking
 * - Auto-recovery logic (reversible vs non-reversible)
 * - Fatigue budget enforcement
 *
 * NOT responsible for:
 * - Delivery (vigia/harbor)
 * - Confirmation tracking (vigia/harbor)
 * - Escalation (vigia/harbor)
 * - NVR recording (external)
 */
public interface SentinelEvaluator : Engine {
    /**
     * Evaluate one scene fact for this resident.
     *
     * @param fact     The scene fact to evaluate
     * @param episodes The resident's current episode state (folded from previous facts)
     * @param now      Current time (injected, never Instant.now())
     * @return         The verdict: signals to emit + next episode state
     */
    public fun evaluate(
        fact: SceneEvent,
        episodes: EpisodeLedger,
        now: Instant,
    ): Explained<SentinelVerdict>
}

/**
 * Factory function for creating [SentinelEvaluator] instances.
 *
 * Visible to tests and batch tools — consumers should depend on the interface.
 */
public fun createSentinelEvaluator(calibration: SentinelCalibration): SentinelEvaluator =
    SentinelEvaluatorImpl(calibration)

/**
 * The output of one evaluation: signals to emit and the next episode state.
 *
 * The shell persists [episodes]; the engine never touches storage.
 */
public data class SentinelVerdict(
    /** Signals to emit (incident, occurrence, suppression, or nothing). */
    public val signals: List<SentinelSignal>,
    /** Next episode state — immutable, the shell persists it. */
    public val episodes: EpisodeLedger,
)
