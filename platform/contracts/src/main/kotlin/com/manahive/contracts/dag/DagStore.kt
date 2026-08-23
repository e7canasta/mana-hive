package com.manahive.contracts.dag

import com.manahive.contracts.engine.Subscription
import com.manahive.kernel.DagId

/**
 * Port for Scene DAG storage.
 *
 * The Scene DAG is a SHARED graph — all engines hydrate from it.
 * This interface provides storage and subscription for DAG changes.
 *
 * Fowler: "Repository" — abstracts persistence.
 */
public interface DagStore {
    /** Store a DAG. */
    public fun store(dag: SceneDag)

    /** Optimistic concurrent store. Returns true if stored, false if version mismatch. */
    public fun storeIfVersion(dag: SceneDag, expectedVersion: DagVersion): Boolean

    /** Load a DAG by ID. */
    public fun load(dagId: DagId): SceneDag?

    /** Check if a DAG exists. */
    public fun exists(dagId: DagId): Boolean

    /** Delete a DAG. */
    public fun delete(dagId: DagId)

    /** Subscribe to DAG changes. */
    public fun subscribe(dagId: DagId, onChange: (DagChange) -> Unit): Subscription

    /** Unsubscribe all callbacks for a DAG. */
    public fun unsubscribe(dagId: DagId)
}
