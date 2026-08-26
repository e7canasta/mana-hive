package com.manahive.runtime

import com.manahive.messaging.NatsConfig
import io.nats.client.ConnectionListener
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory

/**
 * Mantiene viva la conexión al bus, reintentando para siempre.
 *
 * No bloquea el arranque: el servicio levanta con o sin NATS y queda en
 * `WAITING_FOR_BUS` hasta que el bus aparece. Es lo que hace que una caída del
 * bus sea una degradación y no una caída del sistema — que es lo que 24/7 exige.
 */
class BusConnector(
    private val url: String,
    private val events: BusEvents,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostConstruct
    fun connect() {
        log.info("Conectando al bus en {} (reintenta indefinidamente)", url)
        NatsConfig.connectAsync(url) { conn, type ->
            events.connection = conn
            when (type) {
                ConnectionListener.Events.CONNECTED,
                ConnectionListener.Events.RECONNECTED,
                -> {
                    log.info("Bus disponible ({})", type)
                    events.onConnected?.invoke()
                }

                ConnectionListener.Events.DISCONNECTED,
                ConnectionListener.Events.CLOSED,
                -> events.onLost?.invoke(type.name)

                else -> Unit
            }
        }
    }
}
