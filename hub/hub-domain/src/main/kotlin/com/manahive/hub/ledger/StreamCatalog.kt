package com.manahive.hub.ledger

/**
 * Catalog of known event streams in the system.
 *
 * Vernon: "Bounded Context" — the Hub should not hardcode stream names
 * belonging to other engines. This catalog is the single source of truth
 * for stream identity.
 */
public data class StreamCatalog(
    public val streams: List<StreamDefinition>,
) {
    public companion object {
        public val DEFAULT: StreamCatalog = StreamCatalog(
            streams = listOf(
                StreamDefinition("scene.fact.v1", "Scene facts"),
                StreamDefinition("sentinel.signal.v1", "Sentinel signals"),
                StreamDefinition("alarm.event.v1", "Alarm events"),
                StreamDefinition("evidence.recorded.v1", "Evidence records"),
                StreamDefinition("recording.command.v1", "Recording commands"),
            ),
        )
    }

    public fun names(): List<String> = streams.map { it.name }

    public fun findByName(name: String): StreamDefinition? =
        streams.find { it.name == name }
}

public data class StreamDefinition(
    public val name: String,
    public val description: String,
)
