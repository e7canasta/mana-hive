package com.manahive.messaging

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

/**
 * Shared ObjectMapper for NATS message serialization/deserialization.
 * All services should use this instead of creating their own instances.
 *
 * [JavaTimeModule] **sí** va registrado acá. Antes no, y el comentario decía
 * que cada servicio armara el suyo si lo necesitaba — pero todo hecho que
 * viaja por este bus lleva un `Instant`: `EventEnvelope.occurredAt`, el `at`
 * de cada evento de dominio. Esa política garantizaba el fallo, y fallaba:
 * publicar un `PolicyChangeDetected` tiraba «Java 8 date/time type not
 * supported», el egress lo atrapaba en su `catch`, y el cambio de política
 * del director no llegaba nunca a los motores. La escritura devolvía 200.
 *
 * Las fechas van como ISO-8601 y no como número: es lo que ya hacen
 * `SentinelSignal.toMap()` y los serializadores de `platform/serialization`,
 * y es lo que hace legible un mensaje del bus cuando hay que auditarlo.
 *
 * Fowler: "Reuse through composition" — single source of truth for serialization config.
 */
public object NatsObjectMapper {
    public val mapper: ObjectMapper = jacksonObjectMapper()
        .registerKotlinModule()
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
}
