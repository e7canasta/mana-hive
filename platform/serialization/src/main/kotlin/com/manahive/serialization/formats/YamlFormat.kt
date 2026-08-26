package com.manahive.serialization.formats

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.manahive.serialization.Codec
import com.manahive.serialization.SerializationResult

/**
 * YAML format codec.
 *
 * Good for: configuration files, human-readable configs.
 * Example:
 * ```yaml
 * name: jose-301
 * resident:
 *   risk: HIGH
 *   mobility: NONE
 * rules:
 *   - trigger: SITTING_IN_BED
 *     alertAfter: PT15M
 * ```
 */
class YamlCodec<T>(private val clazz: Class<T>) : Codec<T> {

    private val mapper = ObjectMapper(YAMLFactory()).apply {
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
        inline fun <reified T> create(): YamlCodec<T> {
            return YamlCodec(T::class.java)
        }
    }
}
