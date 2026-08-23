package com.manahive.hub.api

import com.manahive.contracts.policy.PolicyCatalog
import com.manahive.contracts.policy.PolicyCategory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * REST controller for policy catalog queries.
 *
 * Provides endpoints for engines to query event and dimension metadata.
 * This is the HTTP adapter for the PolicyCatalog port.
 *
 * Fowler: "Adapter" — translates HTTP requests to port method calls.
 * Vernon: "Driving Adapter" — the primary adapter for the use case.
 */
@RestController
@RequestMapping("/api/catalog")
public class PolicyCatalogController(
    private val policyCatalog: PolicyCatalog,
) {

    /**
     * Get catalog summary with counts per category.
     *
     * @return Catalog summary
     */
    @GetMapping
    public fun getSummary(): ResponseEntity<CatalogSummaryResponse> {
        val events = policyCatalog.getAllEvents()
        val dimensions = policyCatalog.getAllDimensions()

        val categories = PolicyCategory.entries.map { category ->
            CategorySummary(
                category = category.name.lowercase(),
                eventCount = events.count { it.category == category },
                dimensionCount = dimensions.count { it.category == category },
            )
        }

        return ResponseEntity.ok(
            CatalogSummaryResponse(
                eventCount = events.size,
                dimensionCount = dimensions.size,
                categories = categories,
            )
        )
    }

    /**
     * Get all events.
     *
     * @return List of event descriptors
     */
    @GetMapping("/events")
    public fun getAllEvents(): ResponseEntity<List<EventDescriptorResponse>> {
        val events = policyCatalog.getAllEvents()
        return ResponseEntity.ok(events.map { it.toResponse() })
    }

    /**
     * Get event by ID.
     *
     * @param eventId The event ID
     * @return The event descriptor, or 404 if not found
     */
    @GetMapping("/events/{eventId}")
    public fun getEvent(
        @PathVariable eventId: String,
    ): ResponseEntity<EventDescriptorResponse> {
        val event = policyCatalog.getEvent(eventId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(event.toResponse())
    }

    /**
     * Get events by category.
     *
     * @param category The policy category
     * @return List of event descriptors
     */
    @GetMapping("/events/category/{category}")
    public fun getEventsByCategory(
        @PathVariable category: String,
    ): ResponseEntity<List<EventDescriptorResponse>> {
        val parsedCategory = CategoryParser.parse(category)
            ?: return ResponseEntity.badRequest().build()

        val events = policyCatalog.getEventsByCategory(parsedCategory)
        return ResponseEntity.ok(events.map { it.toResponse() })
    }

    /**
     * Get events by group.
     *
     * @param group The event group
     * @return List of event descriptors
     */
    @GetMapping("/events/group/{group}")
    public fun getEventsByGroup(
        @PathVariable group: String,
    ): ResponseEntity<List<EventDescriptorResponse>> {
        val events = policyCatalog.getEventsByGroup(group)
        return ResponseEntity.ok(events.map { it.toResponse() })
    }

    /**
     * Get all dimensions.
     *
     * @return List of dimension descriptors
     */
    @GetMapping("/dimensions")
    public fun getAllDimensions(): ResponseEntity<List<DimensionDescriptorResponse>> {
        val dimensions = policyCatalog.getAllDimensions()
        return ResponseEntity.ok(dimensions.map { it.toResponse() })
    }

    /**
     * Get dimension by ID.
     *
     * @param dimensionId The dimension ID
     * @return The dimension descriptor, or 404 if not found
     */
    @GetMapping("/dimensions/{dimensionId}")
    public fun getDimension(
        @PathVariable dimensionId: String,
    ): ResponseEntity<DimensionDescriptorResponse> {
        val dimension = policyCatalog.getDimension(dimensionId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(dimension.toResponse())
    }

    /**
     * Get dimensions by category.
     *
     * @param category The policy category
     * @return List of dimension descriptors
     */
    @GetMapping("/dimensions/category/{category}")
    public fun getDimensionsByCategory(
        @PathVariable category: String,
    ): ResponseEntity<List<DimensionDescriptorResponse>> {
        val parsedCategory = CategoryParser.parse(category)
            ?: return ResponseEntity.badRequest().build()

        val dimensions = policyCatalog.getDimensionsByCategory(parsedCategory)
        return ResponseEntity.ok(dimensions.map { it.toResponse() })
    }
}

/**
 * Extension function to convert EventDescriptor to response DTO.
 */
private fun com.manahive.contracts.policy.EventDescriptor.toResponse(): EventDescriptorResponse =
    EventDescriptorResponse(
        id = id,
        group = group,
        description = description,
        eventClass = eventClass.name.lowercase(),
        category = category.name.lowercase(),
    )

/**
 * Extension function to convert DimensionDescriptor to response DTO.
 */
private fun com.manahive.contracts.policy.DimensionDescriptor.toResponse(): DimensionDescriptorResponse =
    DimensionDescriptorResponse(
        id = id,
        description = description,
        dimensionType = dimensionType.name.lowercase(),
        category = category.name.lowercase(),
        allowedValues = allowedValues,
        min = min,
        max = max,
        unit = unit,
        defaultValue = defaultValue,
        required = required,
    )
