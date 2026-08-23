package com.manahive.hub.policy

import com.manahive.contracts.policy.RawPolicy
import com.manahive.contracts.policy.RawPolicyStore
import com.manahive.kernel.ResidentId
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory implementation of RawPolicyStore.
 *
 * Stores raw master policies in a thread-safe map.
 * Suitable for development, testing, and single-instance deployments.
 *
 * For production, replace with a database-backed implementation.
 *
 * Fowler: "Simple" — start with the simplest thing that works.
 * Vernon: "Infrastructure" — implementation detail behind the port.
 */
public class InMemoryRawPolicyStore : RawPolicyStore {

    private val store = ConcurrentHashMap<ResidentId, RawPolicy>()

    override fun store(residentId: ResidentId, raw: RawPolicy) {
        store[residentId] = raw
    }

    override fun get(residentId: ResidentId): RawPolicy? =
        store[residentId]

    override fun listAll(): List<ResidentId> =
        store.keys.toList()

    /**
     * Number of raw policies stored.
     * Internal: test helper only.
     */
    internal fun size(): Int = store.size

    /**
     * Clear all stored raw policies.
     * Internal: test helper only.
     */
    internal fun clear() {
        store.clear()
    }
}
