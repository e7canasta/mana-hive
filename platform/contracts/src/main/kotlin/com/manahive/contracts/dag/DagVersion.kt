package com.manahive.contracts.dag

/**
 * Version of a DAG, monotonically increasing.
 *
 * Used for optimistic concurrency control and cache invalidation.
 *
 * Vernon: "Value Object" — immutable, equality by value.
 */
@JvmInline
public value class DagVersion(public val value: Int) {
    init {
        require(value > 0) { "DagVersion must be positive, got $value" }
    }

    /** Increment version. */
    public fun next(): DagVersion {
        require(value < Int.MAX_VALUE) { "DagVersion overflow: cannot increment $value" }
        return DagVersion(value + 1)
    }

    public override fun toString(): String = "DagVersion($value)"
}
