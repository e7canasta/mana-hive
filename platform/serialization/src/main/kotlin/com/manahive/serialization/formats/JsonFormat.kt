package com.manahive.serialization.formats

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.databind.ObjectMapper
import com.manahive.serialization.Codec
import com.manahive.serialization.SerializationResult

/**
 * JSON format codec.
 *
 * Uses Jackson for serialization/deserialization.
 * Good for: Event Sourcing, APIs, inter-service communication.
 */
class JsonCodec<T>(private val clazz: Class<T>) : Codec<T> {

    private val mapper: ObjectMapper = jacksonObjectMapper()

    override fun encode(obj: T): String {
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj)
    }

    override fun decode(text: String): SerializationResult<T> {
        return try {
            SerializationResult.Success(mapper.readValue(text, clazz))
        } catch (e: Exception) {
            SerializationResult.Failure(com.manahive.serialization.SerializationError.ParseError(0, e.message ?: "Unknown error"))
        }
    }

    companion object {
        inline fun <reified T> create(): JsonCodec<T> {
            return JsonCodec(T::class.java)
        }
    }
}
