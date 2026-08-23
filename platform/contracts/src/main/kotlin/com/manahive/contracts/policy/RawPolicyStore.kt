package com.manahive.contracts.policy

import com.manahive.kernel.ResidentId

/**
 * Port for storing raw master policies.
 *
 * This is the entry point for raw policies coming from external systems.
 * The Hub stores them, and the Politica Engine reads them to distill into semantic buckets.
 *
 * Fowler: "Dependency Inversion" — domain depends on abstraction.
 * Vernon: "Port" — interface owned by the domain.
 */
public interface RawPolicyStore {
    /**
     * Store a raw policy for a resident.
     * Overwrites any existing raw policy for that resident.
     */
    public fun store(residentId: ResidentId, raw: RawPolicy)

    /**
     * Get the raw policy for a resident, or null if not found.
     */
    public fun get(residentId: ResidentId): RawPolicy?

    /**
     * List all residents with raw policies.
     */
    public fun listAll(): List<ResidentId>
}
