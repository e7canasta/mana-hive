package com.manahive.contracts.dag

import com.manahive.kernel.NodeId

/**
 * A node in the Scene DAG.
 *
 * Represents a physical position/state of a person in a room.
 *
 * Evans: "Value Object" — immutable, equality by value.
 */
public data class SceneNode(
    public val id: NodeId,
    public val state: SceneState,
)

/**
 * Physical states of a person in a room.
 *
 * These are the ONLY states the Scene DAG models.
 * No engine events, no alerts, no notifications — just physical positions.
 *
 * Vernon: "Type-safe enum" — exhaustive when() expressions.
 */
public enum class SceneState {
    /** Lying in bed. */
    LYING,
    /** In bed, not lying (sitting up, etc). */
    IN_BED,
    /** Sitting on the edge of the bed. */
    SITTING_IN_BED,
    /** Standing next to the bed. */
    STANDING,
    /** Walking/moving. */
    WALKING,
    /** In the bathroom. */
    IN_BATHROOM,
    /** In the hallway. */
    IN_HALLWAY,
    /** On the floor (always critical). */
    ON_FLOOR,
}
