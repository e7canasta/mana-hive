package com.manahive.contracts.policy

import com.manahive.kernel.ResidentId
import java.time.Instant

/**
 * Raw master policy from external systems.
 *
 * This is the unprocessed policy as received from the outside.
 * The Politica Engine will distill this into semantic buckets.
 *
 * @property residentId The resident this policy applies to
 * @property version Version number for optimistic concurrency
 * @property payload The raw policy payload (JSON-like structure)
 * @property receivedAt When the policy was received
 */
public data class RawPolicy(
    val residentId: ResidentId,
    val version: Version,
    val payload: Map<String, Any>,
    val receivedAt: Instant,
) {
    init {
        require(payload.isNotEmpty()) { "payload must not be empty" }
    }
}
