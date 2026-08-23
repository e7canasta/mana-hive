package com.manahive.contracts.policy

/**
 * Metadata about a policy dimension in the catalog.
 *
 * Dimensions are configurable parameters for each event.
 * The medical director configures these per resident.
 *
 * Fowler: "Replace Primitive with Object" — dimension metadata is more than a string.
 * Vernon: "Value Object" — no identity, compared by value.
 *
 * @property id Unique identifier (e.g., "sensitivity", "hysteresis")
 * @property description Human-readable description
 * @property dimensionType Type of dimension (RANGE, ENUM, DURATION, BOOLEAN)
 * @property category Which semantic category this dimension belongs to
 * @property allowedValues For ENUM types: list of allowed values
 * @property min For RANGE types: minimum value
 * @property max For RANGE types: maximum value
 * @property unit For RANGE/DURATION types: unit of measurement
 * @property defaultValue Default value if not configured
 * @property required Whether this dimension must be configured
 */
public data class DimensionDescriptor(
    val id: String,
    val description: String,
    val dimensionType: DimensionType,
    val category: PolicyCategory,
    val allowedValues: List<String> = emptyList(),
    val min: Double? = null,
    val max: Double? = null,
    val unit: String? = null,
    val defaultValue: String? = null,
    val required: Boolean = false,
) {
    init {
        require(id.isNotBlank()) { "dimension id must not be blank" }
        require(description.isNotBlank()) { "dimension description must not be blank" }
        if (dimensionType == DimensionType.ENUM) {
            require(allowedValues.isNotEmpty()) { "ENUM dimension must have allowedValues" }
        }
        if (min != null && max != null) {
            require(min <= max) { "min ($min) must be <= max ($max)" }
        }
    }
}

/**
 * Type of dimension value.
 *
 * RANGE: Numeric value within a range (e.g., sensitivity: 0.0..1.0)
 * ENUM: One of a fixed set of values (e.g., criticity: notif|alert|incident)
 * DURATION: Time duration (e.g., hysteresis: -1m..+1m)
 * BOOLEAN: True/false value (e.g., record: yes|no)
 */
public enum class DimensionType {
    RANGE,
    ENUM,
    DURATION,
    BOOLEAN,
}
