package com.manahive.messaging

import io.nats.client.Connection
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * La conexión al bus y la declaración de sus streams, para cualquier servicio.
 *
 * No se auto-descubre: vive en `com.manahive.messaging`, fuera del paquete que
 * escanea cada aplicación, así que cada servicio la trae con un `@Import`
 * explícito. Es a propósito — estar en el bus es una decisión del servicio.
 *
 * Existe porque no existía: cuatro de los cinco motores declaraban adaptadores
 * NATS que pedían un [Connection] **que ningún módulo proveía**, así que no
 * arrancaban. El wiring de JetStream compilaba y no había corrido nunca.
 */
@Configuration
@ConditionalOnProperty(name = ["nats.enabled"], havingValue = "true", matchIfMissing = true)
public open class NatsClientConfiguration {

    @Bean
    public open fun natsConnection(
        @Value("\${nats.url:nats://localhost:4222}") url: String,
    ): Connection = NatsConfig.createConnection(url)

    /**
     * Declara los streams al arrancar. [NatsTopology] dice que «every service
     * calls `ensure(...)` on startup» y no la llamaba nadie: sin stream que
     * cubra el subject, publicar devuelve *503 No Responders* y el mensaje se
     * pierde con un log de error. Es idempotente: el primero crea, el resto
     * verifica.
     */
    @Bean
    public open fun natsTopology(connection: Connection): NatsTopology =
        NatsTopology(connection.jetStreamManagement()).also { it.ensureAll() }
}
