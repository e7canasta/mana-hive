package com.manahive.scene.calibration.dsl

import com.manahive.contracts.policy.DwellThreshold
import com.manahive.contracts.scene.StateKind
import java.time.Duration

/**
 * Shared DSL for building dwell threshold maps.
 *
 * Used by both [dwellCatalog] and [calibration] builders.
 * Eliminates the parallel hierarchy smell (Fowler).
 *
 * ```kotlin
 * dwell {
 *     STANDING warning 4.minutes exceeded 5.minutes
 *     BED_EDGE warning 8.minutes exceeded 10.minutes
 * }
 * ```
 */
@SceneDsl
public class DwellThresholdsBuilder(
    private val thresholds: MutableMap<StateKind, DwellThreshold>,
) {
    public val LYING: DwellThresholdStateBuilder get() = DwellThresholdStateBuilder(StateKind.LYING, thresholds)
    public val SITTING_IN_BED: DwellThresholdStateBuilder get() = DwellThresholdStateBuilder(StateKind.SITTING_IN_BED, thresholds)
    public val ATTEMPTING_EXIT: DwellThresholdStateBuilder get() = DwellThresholdStateBuilder(StateKind.ATTEMPTING_EXIT, thresholds)
    public val BED_EDGE: DwellThresholdStateBuilder get() = DwellThresholdStateBuilder(StateKind.BED_EDGE, thresholds)
    public val STANDING: DwellThresholdStateBuilder get() = DwellThresholdStateBuilder(StateKind.STANDING, thresholds)
    public val IN_BATHROOM: DwellThresholdStateBuilder get() = DwellThresholdStateBuilder(StateKind.IN_BATHROOM, thresholds)
    public val IN_ROOM: DwellThresholdStateBuilder get() = DwellThresholdStateBuilder(StateKind.IN_ROOM, thresholds)
    public val IN_HALLWAY: DwellThresholdStateBuilder get() = DwellThresholdStateBuilder(StateKind.IN_HALLWAY, thresholds)
    public val OUTDOOR: DwellThresholdStateBuilder get() = DwellThresholdStateBuilder(StateKind.OUTDOOR, thresholds)
    public val ABSENT: DwellThresholdStateBuilder get() = DwellThresholdStateBuilder(StateKind.ABSENT, thresholds)
    public val IN_CHAIR: DwellThresholdStateBuilder get() = DwellThresholdStateBuilder(StateKind.IN_CHAIR, thresholds)
    public val IN_WHEELCHAIR: DwellThresholdStateBuilder get() = DwellThresholdStateBuilder(StateKind.IN_WHEELCHAIR, thresholds)
}

@SceneDsl
public class DwellThresholdStateBuilder(
    private val kind: StateKind,
    private val thresholds: MutableMap<StateKind, DwellThreshold>,
) {
    public infix fun warning(warning: Duration): DwellThresholdExceedsBuilder =
        DwellThresholdExceedsBuilder(kind, warning, thresholds)

    public infix fun exceeded(exceeds: Duration) {
        thresholds[kind] = DwellThreshold(Duration.ZERO, exceeds)
    }
}

@SceneDsl
public class DwellThresholdExceedsBuilder(
    private val kind: StateKind,
    private val warning: Duration,
    private val thresholds: MutableMap<StateKind, DwellThreshold>,
) {
    public infix fun exceeded(exceeds: Duration) {
        thresholds[kind] = DwellThreshold(warning, exceeds)
    }
}
