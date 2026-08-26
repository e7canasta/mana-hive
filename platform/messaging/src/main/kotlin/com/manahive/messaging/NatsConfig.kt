package com.manahive.messaging

import io.nats.client.Connection
import io.nats.client.ConnectionListener
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
    /**
     * Conexión al bus que **nunca se rinde**.
     *
     * Esto es 24/7: el servicio tiene que poder arrancar antes que NATS, y
     * sobrevivir a que el bus se caiga y vuelva. [Nats.connectReconnectOnConnect]
     * es lo que hace verdadera la promesa de `maxReconnects(-1)`: sin él,
     * `Nats.connect` lanza si el bus todavía no está arriba y el servicio entero
     * no arranca, atando el orden de arranque de todo el sistema.
     *
     * Devuelve una conexión que puede estar todavía desconectada. Quien la use
     * debe suscribirse en el callback de conexión, no en el constructor — ver
     * `NightWatchService`.
     */
    public fun createConnection(
        url: String = "nats://localhost:4222",
        listener: ConnectionListener? = null,
    ): Connection {
        val options = Options.Builder()
            .server(url)
            .reconnectWait(Duration.ofSeconds(1))
            .maxReconnects(-1) // Infinite reconnects
            .connectionTimeout(Duration.ofSeconds(2))
            .connectionName("mana-hive")
            .apply { if (listener != null) connectionListener(listener) }
            .build()
        return Nats.connectReconnectOnConnect(options)
    }

    /**
     * Conecta **sin bloquear** y sigue reintentando para siempre.
     *
     * `Nats.connect` y `connectReconnectOnConnect` bloquean el hilo que llama
     * hasta lograr la primera conexión: usados al crear un bean, el servicio no
     * termina de arrancar mientras el bus no esté. En 24/7 eso ata el orden de
     * arranque de todo el sistema y convierte una caída del bus en una caída de
     * los motores.
     *
     * Acá la conexión llega por [listener] cuando existe. Quien la use debe
     * tolerar no tenerla todavía.
     */
    public fun connectAsync(
        url: String = "nats://localhost:4222",
        listener: ConnectionListener,
    ) {
        val options = Options.Builder()
            .server(url)
            .reconnectWait(Duration.ofSeconds(1))
            .maxReconnects(-1)
            .connectionTimeout(Duration.ofSeconds(2))
            .connectionName("mana-hive")
            .connectionListener(listener)
            .build()
        Nats.connectAsynchronously(options, true)
    }
}
