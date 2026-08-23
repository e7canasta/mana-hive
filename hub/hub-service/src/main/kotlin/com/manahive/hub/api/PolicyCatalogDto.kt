package com.manahive.hub.api

import com.fasterxml.jackson.annotation.JsonInclude

/**
 * Response DTO for event descriptor.
 *
 * @property id Unique identifier
 * @property group Logical grouping
 * @property description Human-readable description
 * @property eventClass Type of event (transition, dwell)
 * @property category Semantic category
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public data class EventDescriptorResponse(
    val id: String,
    val group: String,
    val description: String,
    val eventClass: String,
    val category: String,
)

/**
 * Response DTO for dimension descriptor.
 *
 * @property id Unique identifier
 * @property description Human-readable description
 * @property dimensionType Type of dimension
 * @property category Semantic category
 * @property allowedValues For ENUM types
 * @property min For RANGE types
 * @property max For RANGE types
 * @property unit Unit of measurement
 * @property defaultValue Default value
 * @property required Whether required
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public data class DimensionDescriptorResponse(
    val id: String,
    val description: String,
    val dimensionType: String,
    val category: String,
    val allowedValues: List<String>,
    val min: Double?,
    val max: Double?,
    val unit: String?,
    val defaultValue: String?,
    val required: Boolean,
)

/**
 * Response DTO for catalog summary.
 *
 * @property eventCount Number of events
 * @property dimensionCount Number of dimensions
 * @property categories List of categories with events
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public data class CatalogSummaryResponse(
    val eventCount: Int,
    val dimensionCount: Int,
    val categories: List<CategorySummary>,
)

/**
 * Summary of a category.
 *
 * @property category Category name
 * @property eventCount Number of events in this category
 * @property dimensionCount Number of dimensions in this category
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public data class CategorySummary(
    val category: String,
    val eventCount: Int,
    val dimensionCount: Int,
)
