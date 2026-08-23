package com.manahive.hub.ledger

/**
 * Catalog of known consumers in the system.
 *
 * Vernon: "Bounded Context" — the Hub should not hardcode consumer names
 * belonging to other engines. This catalog is the single source of truth
 * for consumer identity.
 */
public data class WatermarkCatalog(
    public val consumers: List<ConsumerDefinition>,
) {
    public companion object {
        public val DEFAULT: WatermarkCatalog = WatermarkCatalog(
            consumers = listOf(
                ConsumerDefinition("scene-engine", "Scene Engine"),
                ConsumerDefinition("sentinel-engine", "Sentinel Engine"),
                ConsumerDefinition("harbor-engine", "Harbor Engine"),
                ConsumerDefinition("recorder-engine", "Recorder Engine"),
            ),
        )
    }

    public fun names(): List<String> = consumers.map { it.name }

    public fun findByName(name: String): ConsumerDefinition? =
        consumers.find { it.name == name }
}

public data class ConsumerDefinition(
    public val name: String,
    public val description: String,
)
