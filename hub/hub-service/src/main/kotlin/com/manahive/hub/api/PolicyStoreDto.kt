package com.manahive.hub.api

import com.manahive.contracts.policy.PolicyPayload
import com.fasterxml.jackson.annotation.JsonInclude
import java.time.Instant

/**
 * Response DTO for semantic bucket.
 *
 * @property residentId The resident this bucket applies to
 * @property category The semantic category
 * @property version Monotonically increasing version number
 * @property payload Typed payload for this category
 * @property fingerprint Fingerprint of the bucket content
 * @property storedAt When the bucket was stored
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public data class SemanticBucketResponse(
    val residentId: String,
    val category: String,
    val version: Int,
    val payload: PolicyPayload,
    val fingerprint: String,
    val storedAt: Instant,
)

/**
 * Response DTO for all buckets of a resident.
 *
 * @property residentId The resident
 * @property buckets All semantic buckets for this resident
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public data class ResidentBucketsResponse(
    val residentId: String,
    val buckets: List<SemanticBucketResponse>,
)

/**
 * Response DTO for raw policy.
 *
 * @property residentId The resident this policy applies to
 * @property version Version number
 * @property payload The raw policy payload
 * @property receivedAt When the policy was received
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public data class RawPolicyResponse(
    val residentId: String,
    val version: Int,
    val payload: Map<String, Any>,
    val receivedAt: Instant,
)

/**
 * Response DTO for listing residents.
 *
 * @property residents List of resident IDs
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public data class ResidentsListResponse(
    val residents: List<String>,
)
