package com.manahive.contracts.policy

/**
 * Port for querying the policy catalog.
 *
 * The catalog contains metadata about events and dimensions.
 * Engines use this to understand what events are available and how to configure them.
 *
 * Fowler: "Dependency Inversion" — domain depends on abstraction.
 * Vernon: "Port" — interface owned by the domain.
 */
public interface PolicyCatalog {
    /**
     * Get all events in the catalog.
     */
    public fun getAllEvents(): List<EventDescriptor>

    /**
     * Get an event by ID, or null if not found.
     */
    public fun getEvent(id: String): EventDescriptor?

    /**
     * Get all events for a category.
     */
    public fun getEventsByCategory(category: PolicyCategory): List<EventDescriptor>

    /**
     * Get all events for a group.
     */
    public fun getEventsByGroup(group: String): List<EventDescriptor>

    /**
     * Get all dimensions in the catalog.
     */
    public fun getAllDimensions(): List<DimensionDescriptor>

    /**
     * Get a dimension by ID, or null if not found.
     */
    public fun getDimension(id: String): DimensionDescriptor?

    /**
     * Get all dimensions for a category.
     */
    public fun getDimensionsByCategory(category: PolicyCategory): List<DimensionDescriptor>
}
