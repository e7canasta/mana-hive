package com.manahive.contracts.policy

/**
 * Metadata about a policy event in the catalog.
 *
 * Each event represents a detectable occurrence in the system.
 * Events are classified by their behavior (transition vs dwell).
 *
 * Fowler: "Replace Primitive with Object" — event metadata is more than a string.
 * Vernon: "Value Object" — no identity, compared by value.
 *
 * @property id Unique identifier (e.g., "fall", "bathroom_dwell")
 * @property group Logical grouping (e.g., "fall_prevention", "location")
 * @property description Human-readable description
 * @property eventClass Type of event: TRANSITION or DWELL
 * @property category Which semantic category this event belongs to
 */
public data class EventDescriptor(
    val id: String,
    val group: String,
    val description: String,
    val eventClass: EventClass,
    val category: PolicyCategory,
) {
    init {
        require(id.isNotBlank()) { "event id must not be blank" }
        require(group.isNotBlank()) { "event group must not be blank" }
        require(description.isNotBlank()) { "event description must not be blank" }
    }
}

/**
 * Classification of event behavior.
 *
 * TRANSITION: Occurs when entering a state (e.g., bathroom_visit)
 * DWELL: Occurs when remaining in a state (e.g., bathroom_dwell)
 */
public enum class EventClass {
    TRANSITION,
    DWELL,
}
