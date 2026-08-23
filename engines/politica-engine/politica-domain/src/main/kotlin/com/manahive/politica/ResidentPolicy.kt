package com.manahive.politica

import com.manahive.contracts.common.Fingerprint
import com.manahive.contracts.common.buildFingerprint
import com.manahive.contracts.policy.PolicyCategory
import com.manahive.contracts.policy.PolicyEvent
import com.manahive.kernel.ResidentId
import java.time.Instant

/**
 * Aggregate root grouping all policy buckets for one resident.
 *
 * Vernon: "Aggregate Root" — the only entry point for modifications.
 * All buckets must belong to the same resident (invariant protection).
 *
 * Usage:
 * ```kotlin
 * val policy = ResidentPolicy(ResidentId("maria"))
 * policy.apply(calibrationBucket)
 * policy.apply(responseBucket)
 * policy.apply(escalationBucket)
 * policy.apply(recordingBucket)
 *
 * val events = policy.toEvents(Instant.now())
 * ```
 *
 * @property residentId The resident this aggregate belongs to
 */
public class ResidentPolicy(
    public val residentId: ResidentId,
) {
    private val buckets = mutableMapOf<PolicyCategory, SemanticBucket>()

    /**
     * Apply a bucket to this aggregate.
     * Fails if the bucket's residentId doesn't match.
     */
    public fun apply(bucket: SemanticBucket) {
        require(bucket.residentId == residentId) {
            "Bucket residentId ${bucket.residentId} does not match aggregate $residentId"
        }
        buckets[bucket.category] = bucket
    }

    /** Get a bucket by category, or null if not set. */
    public fun get(category: PolicyCategory): SemanticBucket? =
        buckets[category]

    /** Get all buckets. */
    public fun all(): List<SemanticBucket> =
        buckets.values.toList()

    /** Get all categories that have been set. */
    public fun categories(): Set<PolicyCategory> =
        buckets.keys.toSet()

    /** Check if all 4 categories are set. */
    public fun isComplete(): Boolean =
        buckets.size == PolicyCategory.entries.size

    /** Convert all buckets to events using the mapper. */
    public fun toEvents(at: Instant): List<PolicyEvent> =
        PolicyBucketMapper.toEvents(buckets.values.toList(), at)

    /** Combined fingerprint from all buckets. */
    public fun fingerprint(): Fingerprint {
        val parts = buckets.values
            .sortedBy { it.category.name }
            .map { it.category.name to it.payload.fingerprint }
            .toTypedArray()
        return buildFingerprint(*parts)
    }

    /** Number of buckets in this aggregate. */
    public fun size(): Int = buckets.size

    override fun toString(): String =
        "ResidentPolicy($residentId, ${buckets.size} buckets: ${buckets.keys})"

    public companion object {
        /**
         * Create a ResidentPolicy from a list of buckets.
         * All buckets must have the same residentId.
         */
        public fun from(buckets: List<SemanticBucket>): ResidentPolicy {
            require(buckets.isNotEmpty()) { "Cannot create ResidentPolicy from empty buckets" }
            val residentId = buckets.first().residentId
            val policy = ResidentPolicy(residentId)
            buckets.forEach { policy.apply(it) }
            return policy
        }
    }
}
