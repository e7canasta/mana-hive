package com.manahive.messaging

import io.nats.client.Connection
import io.nats.client.Nats
import io.nats.client.Options
import java.time.Duration

/**
 * NATS connection factory and configuration.
 * Provides a shared Connection bean for all services.
 *
 * Vernon: "Infrastructure layer" — handles NATS connectivity.
 */
public object NatsConfig {
    /**
     * Create a NATS connection from the configured URL.
     * Falls back to default localhost:4222 if not specified.
     */
    public fun createConnection(url: String = "nats://localhost:4222"): Connection {
        val options = Options.Builder()
            .server(url)
            .reconnectWait(Duration.ofSeconds(1))
            .maxReconnects(-1) // Infinite reconnects
            .connectionName("mana-hive")
            .build()
        return Nats.connect(options)
    }
}
