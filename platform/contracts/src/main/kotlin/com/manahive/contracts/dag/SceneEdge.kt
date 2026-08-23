package com.manahive.contracts.dag

import com.manahive.kernel.NodeId

/**
 * An edge in the Scene DAG.
 *
 * Represents a valid transition between two physical positions.
 *
 * Evans: "Association" — navigable relationship between value objects.
 */
public data class SceneEdge(
    public val from: NodeId,
    public val to: NodeId,
) {
    init {
        require(from != to) { "Self-loop edge from $from is not allowed" }
    }
}
