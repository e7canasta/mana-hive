package com.manahive.contracts.policy

/**
 * Port for accessing event and dimension descriptors.
 *
 * This interface is consumed by the hub and implemented by infrastructure.
 * Created to match the existing InMemoryPolicyCatalog implementation.
 * May be revisited in SPEC-02 when the canonical policy model is decided.
 */
public interface PolicyCatalog {
    public fun getAllEvents(): List<EventDescriptor>
    public fun getEvent(id: String): EventDescriptor?
    public fun getEventsByCategory(category: PolicyCategory): List<EventDescriptor>
    public fun getEventsByGroup(group: String): List<EventDescriptor>
    public fun getAllDimensions(): List<DimensionDescriptor>
    public fun getDimension(id: String): DimensionDescriptor?
    public fun getDimensionsByCategory(category: PolicyCategory): List<DimensionDescriptor>
}
