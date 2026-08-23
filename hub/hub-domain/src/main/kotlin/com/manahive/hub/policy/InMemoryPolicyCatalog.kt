package com.manahive.hub.policy

import com.manahive.contracts.policy.DimensionDescriptor
import com.manahive.contracts.policy.EventDescriptor
import com.manahive.contracts.policy.PolicyCatalog
import com.manahive.contracts.policy.PolicyCategory
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory implementation of PolicyCatalog.
 *
 * Stores event and dimension descriptors in thread-safe maps.
 * Suitable for development, testing, and single-instance deployments.
 *
 * For production, replace with a database-backed or file-backed implementation.
 *
 * Fowler: "Simple" — start with the simplest thing that works.
 * Vernon: "Infrastructure" — implementation detail behind the port.
 */
public class InMemoryPolicyCatalog(
    events: List<EventDescriptor> = emptyList(),
    dimensions: List<DimensionDescriptor> = emptyList(),
) : PolicyCatalog {

    private val eventsById: ConcurrentHashMap<String, EventDescriptor> =
        ConcurrentHashMap(events.associateBy { it.id })
    private val dimensionsById: ConcurrentHashMap<String, DimensionDescriptor> =
        ConcurrentHashMap(dimensions.associateBy { it.id })

    override fun getAllEvents(): List<EventDescriptor> =
        eventsById.values.sortedBy { it.id }

    override fun getEvent(id: String): EventDescriptor? =
        eventsById[id]

    override fun getEventsByCategory(category: PolicyCategory): List<EventDescriptor> =
        eventsById.values.filter { it.category == category }.sortedBy { it.id }

    override fun getEventsByGroup(group: String): List<EventDescriptor> =
        eventsById.values.filter { it.group == group }.sortedBy { it.id }

    override fun getAllDimensions(): List<DimensionDescriptor> =
        dimensionsById.values.sortedBy { it.id }

    override fun getDimension(id: String): DimensionDescriptor? =
        dimensionsById[id]

    override fun getDimensionsByCategory(category: PolicyCategory): List<DimensionDescriptor> =
        dimensionsById.values.filter { it.category == category }.sortedBy { it.id }

    /**
     * Number of events in the catalog.
     * Internal: test helper only.
     */
    internal fun eventCount(): Int = eventsById.size

    /**
     * Number of dimensions in the catalog.
     * Internal: test helper only.
     */
    internal fun dimensionCount(): Int = dimensionsById.size

    /**
     * Clear all stored events and dimensions.
     * Internal: test helper only.
     */
    internal fun clear() {
        eventsById.clear()
        dimensionsById.clear()
    }
}
