package com.manahive.contracts.scene

import java.time.Instant

/**
 * Scene State — orthogonal dimension to PersonState.
 *
 * While PersonState is a FSM (mutually exclusive states),
 * SceneState is a collection of independent flags that describe
 * the environment around the resident.
 *
 * Each object type has its own hierarchy:
 * - PresenceState: NotPresent → Present → InReach (InReach ⊂ Present)
 * - RailState: Down → Up → Cover (Cover ⊂ Up)
 *
 * Bitmask representation (10 bits):
 * [0-1]   staff       (00=NotPresent, 01=Present, 10=InReach)
 * [2-3]   wheelchair
 * [4-5]   walker
 * [6-7]   bed.left    (00=Down, 01=Up, 10=Cover)
 * [8-9]   bed.right   (00=Down, 01=Up, 10=Cover)
 */
public data class SceneState(
    val staff: PresenceState = PresenceState.NotPresent,
    val staffSince: Instant? = null,
    val wheelchair: PresenceState = PresenceState.NotPresent,
    val walker: PresenceState = PresenceState.NotPresent,
    val bed: BedState = BedState(),
) {
    /**
     * Convert to bitmask for serialization/comparison.
     * 10 bits → UShort.
     */
    public fun toBitmask(): UShort {
        var bits = 0
        bits = bits or (staff.level shl 0)
        bits = bits or (wheelchair.level shl 2)
        bits = bits or (walker.level shl 4)
        bits = bits or (bed.left.level shl 6)
        bits = bits or (bed.right.level shl 8)
        return bits.toUShort()
    }

    /**
     * Detect what changed between this and another SceneState.
     * Returns a list of field changes (Fowler: "Feature Envy" → Move Method).
     */
    public fun diff(other: SceneState): List<SceneFieldChange> = buildList {
        if (staff != other.staff) add(SceneFieldChange("staff", staff, other.staff))
        if (wheelchair != other.wheelchair) add(SceneFieldChange("wheelchair", wheelchair, other.wheelchair))
        if (walker != other.walker) add(SceneFieldChange("walker", walker, other.walker))
        if (bed.left != other.bed.left) add(SceneFieldChange("bed.left", bed.left, other.bed.left))
        if (bed.right != other.bed.right) add(SceneFieldChange("bed.right", bed.right, other.bed.right))
    }

    public companion object {
        /**
         * Convert from bitmask.
         */
        public fun fromBitmask(bits: UShort): SceneState {
            val staff = PresenceState.fromLevel((bits.toInt() shr 0) and 0b11)
            val wheelchair = PresenceState.fromLevel((bits.toInt() shr 2) and 0b11)
            val walker = PresenceState.fromLevel((bits.toInt() shr 4) and 0b11)
            val left = RailState.fromLevel((bits.toInt() shr 6) and 0b11)
            val right = RailState.fromLevel((bits.toInt() shr 8) and 0b11)

            return SceneState(
                staff = staff,
                wheelchair = wheelchair,
                walker = walker,
                bed = BedState(left = left, right = right),
            )
        }
    }
}

/**
 * Represents a change in a single SceneState field.
 * Used by [SceneState.diff] to describe what changed.
 */
public data class SceneFieldChange(
    val field: String,
    val from: Any,
    val to: Any,
)

/**
 * Base interface for all scene objects.
 */
public sealed interface SceneObjectState

/**
 * A state with a numeric level for bitmask serialization.
 * Shared by PresenceState and RailState (Fowler: Extract Superclass).
 */
public sealed interface LeveledState {
    public val level: Int
}

/**
 * Presence state for personnel and mobility aids.
 *
 * Hierarchy: NotPresent → Present → InReach
 * (InReach ⊂ Present: InReach implies Present, but Present ≠ InReach)
 */
public sealed interface PresenceState : SceneObjectState, LeveledState {
    public val isPresent: Boolean get() = this !is NotPresent

    public data object NotPresent : PresenceState { override val level: Int = 0b00 }
    public data object Present : PresenceState { override val level: Int = 0b01 }
    public data object InReach : PresenceState { override val level: Int = 0b10 }

    public companion object {
        public fun fromLevel(level: Int): PresenceState = when (level) {
            0b00 -> NotPresent
            0b01 -> Present
            0b10 -> InReach
            else -> NotPresent
        }
    }
}

/**
 * Rail state for bed rails.
 *
 * Hierarchy: Down → Up → Cover
 * (Cover ⊂ Up: Cover implies Up, but Up ≠ Cover)
 */
public sealed interface RailState : SceneObjectState, LeveledState {
    public data object Down : RailState { override val level: Int = 0b00 }
    public data object Up : RailState { override val level: Int = 0b01 }
    public data object Cover : RailState { override val level: Int = 0b10 }

    public companion object {
        public fun fromLevel(level: Int): RailState = when (level) {
            0b00 -> Down
            0b01 -> Up
            0b10 -> Cover
            else -> Down
        }
    }
}

/**
 * Bed state — composition of left and right rail states.
 *
 * Cover requires both rails Up (enforced by semantics, not by type).
 * The `hasCover` property checks this invariant.
 */
public data class BedState(
    val left: RailState = RailState.Down,
    val right: RailState = RailState.Down,
) {
    /** Both rails up (with or without cover). */
    public val isRailsUp: Boolean
        get() = left != RailState.Down && right != RailState.Down

    /** Both rails up with cover. */
    public val hasCover: Boolean
        get() = left == RailState.Cover && right == RailState.Cover

    /** At least one rail down. */
    public val hasDownRail: Boolean
        get() = left == RailState.Down || right == RailState.Down
}

// ── DSL ─────────────────────────────────────────────────────────────────────

/**
 * Type-safe DSL for building [SceneState] instances.
 *
 * Example:
 * ```kotlin
 * val scene = sceneState {
 *     staff = PresenceState.InReach
 *     staffSince = Instant.now()
 *     wheelchair = PresenceState.Present
 *     bed {
 *         left = RailState.Up
 *         right = RailState.Up
 *     }
 * }
 * ```
 */
public fun sceneState(init: SceneStateBuilder.() -> Unit): SceneState = SceneStateBuilder().apply(init).build()

@SceneStateDsl
public class SceneStateBuilder {
    public var staff: PresenceState = PresenceState.NotPresent
    public var staffSince: Instant? = null
    public var wheelchair: PresenceState = PresenceState.NotPresent
    public var walker: PresenceState = PresenceState.NotPresent
    private var bedBuilder = BedStateBuilder()

    public fun bed(init: BedStateBuilder.() -> Unit) {
        bedBuilder.apply(init)
    }

    public fun build(): SceneState = SceneState(
        staff = staff,
        staffSince = staffSince,
        wheelchair = wheelchair,
        walker = walker,
        bed = bedBuilder.build(),
    )
}

@SceneStateDsl
public class BedStateBuilder {
    public var left: RailState = RailState.Down
    public var right: RailState = RailState.Down

    public fun build(): BedState = BedState(left = left, right = right)
}

@DslMarker
public annotation class SceneStateDsl
