package com.manahive.hub.policy

import com.manahive.contracts.policy.PolicyCategory
import com.manahive.contracts.policy.SemanticBucketStore
import com.manahive.contracts.policy.StoredSemanticBucket
import com.manahive.kernel.ResidentId
import java.util.concurrent.ConcurrentHashMap

/**
 * Composite key for semantic buckets.
 *
 * Avoids fragile string interpolation and splitting.
 * Immutable, thread-safe, and properly implements equals/hashCode.
 *
 * Fowler: "Introduce Parameter Object" — groups related fields.
 */
private data class BucketKey(
    val residentId: ResidentId,
    val category: PolicyCategory,
)

/**
 * In-memory implementation of SemanticBucketStore.
 *
 * Stores semantic buckets in a thread-safe map, indexed by composite key.
 * Suitable for development, testing, and single-instance deployments.
 *
 * For production, replace with a database-backed implementation.
 *
 * Fowler: "Simple" — start with the simplest thing that works.
 * Vernon: "Infrastructure" — implementation detail behind the port.
 */
public class InMemorySemanticBucketStore : SemanticBucketStore {

    private val store = ConcurrentHashMap<BucketKey, StoredSemanticBucket>()

    override fun store(bucket: StoredSemanticBucket) {
        store[BucketKey(bucket.residentId, bucket.category)] = bucket
    }

    override fun get(residentId: ResidentId, category: PolicyCategory): StoredSemanticBucket? =
        store[BucketKey(residentId, category)]

    override fun getAllByResident(residentId: ResidentId): List<StoredSemanticBucket> =
        store.values
            .filter { it.residentId == residentId }
            .sortedBy { it.category.ordinal }

    override fun getAllByCategory(category: PolicyCategory): List<StoredSemanticBucket> =
        store.values
            .filter { it.category == category }
            .sortedBy { it.residentId.value }

    override fun listAllResidents(): List<ResidentId> =
        store.keys.map { it.residentId }.distinct()

    /**
     * Number of semantic buckets stored.
     * Internal: test helper only.
     */
    internal fun size(): Int = store.size

    /**
     * Clear all stored semantic buckets.
     * Internal: test helper only.
     */
    internal fun clear() {
        store.clear()
    }
}
