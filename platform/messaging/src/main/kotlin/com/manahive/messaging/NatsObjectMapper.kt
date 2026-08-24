package com.manahive.messaging

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

/**
 * Shared ObjectMapper for NATS message serialization/deserialization.
 * All services should use this instead of creating their own instances.
 *
 * Note: JavaTimeModule is NOT registered here — services that need it
 * should create their own ObjectMapper with JavaTimeModule.
 * This mapper is for simple serialization/deserialization of domain types.
 *
 * Fowler: "Reuse through composition" — single source of truth for serialization config.
 */
public object NatsObjectMapper {
    public val mapper: ObjectMapper = jacksonObjectMapper()
        .registerKotlinModule()
}
