package com.manahive.contracts.census

import com.manahive.kernel.BedId
import com.manahive.kernel.ResidentId
import java.time.Instant

/**
 * Who occupies which bed — the 1:1 invariant lives INSIDE the hub's housing
 * context; engines consume this as a read-only photo, conformist style.
 * Distributed on `hub.census.snapshot.v1`.
 */
public data class CensusSnapshot(
    public val at: Instant,
    public val assignments: List<Assignment>,
)

public data class Assignment(
    public val bed: BedId,
    public val resident: ResidentId,
    public val since: Instant,
)
