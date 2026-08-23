package com.manahive.contracts.policy

import com.manahive.contracts.common.Fingerprint
import com.manahive.kernel.ResidentId
import java.time.Instant

/**
 * Stored semantic bucket with metadata.
 *
 * This is the persisted version of a semantic bucket, with additional metadata
 * for tracking and auditing purposes.
 *
 * @property residentId The resident this bucket applies to
 * @property category The semantic category
 * @property version Monotonically increasing version number
 * @property payload Typed payload for this category
 * @property fingerprint Fingerprint of the bucket content
 * @property storedAt When the bucket was stored
 */
public data class StoredSemanticBucket(
    val residentId: ResidentId,
    val category: PolicyCategory,
    val version: Version,
    val payload: PolicyPayload,
    val fingerprint: Fingerprint,
    val storedAt: Instant,
)
