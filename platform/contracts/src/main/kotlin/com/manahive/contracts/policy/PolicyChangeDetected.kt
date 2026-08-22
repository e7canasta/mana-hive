package com.manahive.contracts.policy

import com.manahive.kernel.ResidentId
import java.time.Instant

/**
 * Domain Event: a policy change has been detected with the full resident snapshot.
 * Emitted by the hub when an alarm profile is created, updated, or deleted.
 *
 * This is the enriched event — includes the full [AlarmProfile] snapshot.
 * Distinct from [PolicyChanged] which is the raw event without the snapshot.
 */
public data class PolicyChangeDetected(
    public val residentId: ResidentId,
    public val at: Instant,
    public val snapshot: AlarmProfile,
)
