package com.manahive.politica

import com.manahive.contracts.policy.PolicyCategory
import com.manahive.contracts.policy.PolicyPayload
import com.manahive.contracts.policy.Version
import com.manahive.kernel.ResidentId

/**
 * A semantic bucket of policies for one resident and one category.
 *
 * This is the unit of storage and distribution in the policy system.
 * The Hub stores buckets, engines subscribe to their category.
 *
 * Fowler: "Introduce Parameter Object" — groups related fields.
 * Vernon: Value Object — no identity, compared by value.
 *
 * Pure data: no behavior, no side effects.
 * Event mapping is in PolicyBucketMapper (SRP).
 *
 * @property residentId The resident this bucket applies to
 * @property category The semantic category (CALIBRATION, RESPONSE, etc.)
 * @property version Monotonically increasing version number
 * @property payload Typed payload for this category
 */
public data class SemanticBucket(
    val residentId: ResidentId,
    val category: PolicyCategory,
    val version: Version,
    val payload: PolicyPayload,
)
