package com.manahive.contracts.dag

import com.manahive.kernel.DagId

/**
 * Sealed hierarchy for DAG change events.
 *
 * Used by DagStore to notify subscribers of changes.
 *
 * Vernon: "Domain Event" — something that happened in the domain.
 */
public sealed interface DagChange {
    /** The DAG that changed. */
    public val dagId: DagId

    /** A DAG was created or updated. */
    public data class Updated(val dag: SceneDag) : DagChange {
        override val dagId: DagId get() = dag.id
    }

    /** A DAG was deleted. */
    public data class Deleted(override val dagId: DagId) : DagChange
}
