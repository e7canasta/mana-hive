package com.manahive.contracts.policy

import com.manahive.kernel.ResidentId

/**
 * Port for storing semantic buckets.
 *
 * The Hub stores semantic buckets after the Politica Engine distills raw policies.
 * Engines query this store to get their category-specific policies.
 *
 * Fowler: "Dependency Inversion" — domain depends on abstraction.
 * Vernon: "Port" — interface owned by the domain.
 */
public interface SemanticBucketStore {
    /**
     * Store a semantic bucket.
     * Overwrites any existing bucket for the same resident + category.
     */
    public fun store(bucket: StoredSemanticBucket)

    /**
     * Get a semantic bucket by resident and category, or null if not found.
     */
    public fun get(residentId: ResidentId, category: PolicyCategory): StoredSemanticBucket?

    /**
     * Get all semantic buckets for a resident.
     */
    public fun getAllByResident(residentId: ResidentId): List<StoredSemanticBucket>

    /**
     * Get all semantic buckets for a category across all residents.
     */
    public fun getAllByCategory(category: PolicyCategory): List<StoredSemanticBucket>

    /**
     * List all residents that have semantic buckets.
     */
    public fun listAllResidents(): List<ResidentId>
}
