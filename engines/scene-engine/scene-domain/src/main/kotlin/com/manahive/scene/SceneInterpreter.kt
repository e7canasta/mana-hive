package com.manahive.scene

import com.manahive.contracts.perception.Observation
import com.manahive.contracts.policy.DwellThreshold
import com.manahive.contracts.scene.SceneFact
import com.manahive.contracts.scene.StateKind
import com.manahive.kernel.Engine
import com.manahive.kernel.Explained
import java.time.Duration
import java.time.Instant

/**
 * The interpreter of the world: turns noisy observations into credible scene
 * facts over one bed's digital twin.
 *
 * Responsible for: transition legality (total table); hysteresis (an 800 ms
 * BED_EDGE is a shuffle, not an exit); per-state confidence thresholds
 * (entering costs more than staying); occupant binding from the census photo;
 * signal health (no heartbeat within threshold -> SignalLost and the twin
 * goes Unknown(SIGNAL_LOST), distinct from Unknown(SCENE)); staff presence as
 * a FACT (suppression belongs to the sentinel); and explained discards —
 * every rejected observation leaves its cause.
 *
 * NOT responsible for: deciding whether anything deserves an alarm (sentinel),
 * knowing clinical rules (hub policy), measuring dwells (ClockSweeper).
 *
 * Invariants: stateSince only advances when state changes, never backwards;
 * a duplicate observation (same sourceEventId) is an identical no-op.
 */
public interface SceneInterpreter : Engine {
    public fun interpret(
        twin: DigitalTwin,
        observation: Observation,
        now: Instant,
    ): Explained<SceneVerdict>
}

public data class SceneVerdict(
    public val twin: DigitalTwin,
    public val facts: List<SceneFact>,
)

public data class SceneCalibration(
    public val table: TransitionTable,
    public val minConfidence: Map<StateKind, Double>,
    public val heartbeatTimeout: Duration,
    public val dwellThresholds: Map<StateKind, DwellThreshold> = emptyMap(),
)
