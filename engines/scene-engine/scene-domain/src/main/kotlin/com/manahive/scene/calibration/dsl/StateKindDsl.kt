package com.manahive.scene.calibration.dsl

import com.manahive.contracts.scene.StateKind

/**
 * Shared DSL interface for StateKind properties.
 *
 * Eliminates the duplicated properties across [TransitionTableBuilder],
 * [FromBuilder], and [DwellThresholdsBuilder].
 *
 * Fowler: "Extract Superclass" — shared properties in a single interface.
 */
@SceneDsl
public interface StateKindDsl {
    public val LYING: StateKind get() = StateKind.LYING
    public val SITTING_IN_BED: StateKind get() = StateKind.SITTING_IN_BED
    public val ATTEMPTING_EXIT: StateKind get() = StateKind.ATTEMPTING_EXIT
    public val BED_EDGE: StateKind get() = StateKind.BED_EDGE
    public val STANDING: StateKind get() = StateKind.STANDING
    public val IN_BATHROOM: StateKind get() = StateKind.IN_BATHROOM
    public val IN_ROOM: StateKind get() = StateKind.IN_ROOM
    public val IN_HALLWAY: StateKind get() = StateKind.IN_HALLWAY
    public val OUTDOOR: StateKind get() = StateKind.OUTDOOR
    public val ABSENT: StateKind get() = StateKind.ABSENT
    public val IN_CHAIR: StateKind get() = StateKind.IN_CHAIR
    public val IN_WHEELCHAIR: StateKind get() = StateKind.IN_WHEELCHAIR
    public val UNKNOWN: StateKind get() = StateKind.UNKNOWN
}
