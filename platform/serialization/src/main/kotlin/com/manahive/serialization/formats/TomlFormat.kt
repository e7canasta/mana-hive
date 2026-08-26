package com.manahive.serialization.formats

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.toml.TomlFactory
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.manahive.serialization.Codec
import com.manahive.serialization.SerializationResult

/**
 * TOML format codec.
 *
 * Good for: configuration files, catalog definitions, resident policies.
 *
 * Example catalog.toml:
 * ```toml
 * [resident.sitting]
 * alertAfter = "15m"
 * severity = "WARNING"
 *
 * [resident.bathroom]
 * alertAfter = "10m"
 * severity = "WARNING"
 * ```
 *
 * Example policy.toml:
 * ```toml
 * name = "jose-301"
 * resident = "jose"
 * risk = "HIGH"
 * mobility = "NONE"
 * template = "standard"
 *
 * [rules.sitting]
 * alertAfter = "15m"
 *
 * [rules.bathroom]
 * alertAfter = "10m"
 * ```
 */
class TomlCodec<T>(private val clazz: Class<T>) : Codec<T> {

    private val mapper = ObjectMapper(TomlFactory()).apply {
        registerModule(kotlinModule())
    }

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
        inline fun <reified T> create(): TomlCodec<T> {
            return TomlCodec(T::class.java)
        }
    }
}
