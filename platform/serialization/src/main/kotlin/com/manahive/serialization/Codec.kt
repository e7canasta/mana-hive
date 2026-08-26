package com.manahive.serialization

import java.io.File

/**
 * Bidirectional codec: objects ↔ strings.
 *
 * Fowler: "Serializer" pattern — objects can be persisted and restored.
 * Vernon: "Serialization as a Bounded Context adapter."
 *
 * Renamed from Serializer to Codec for clarity and consistency.
 */
interface Codec<T> {
    fun encode(obj: T): String
    fun decode(text: String): SerializationResult<T>

    fun encodeOrNull(obj: T): String? = try {
        encode(obj)
    } catch (_: Exception) {
        null
    }

    fun decodeOrNull(text: String): T? = decode(text).getOrNull()
}

/**
 * File-based codec: objects ↔ files.
 */
interface FileCodec<T> : Codec<T> {
    fun save(obj: T, file: File) {
        file.parentFile?.mkdirs()
        file.writeText(encode(obj))
    }

    fun load(file: File): SerializationResult<T> {
        if (!file.exists()) {
            return SerializationResult.Failure(
                SerializationError.FileNotFound(file.absolutePath)
            )
        }
        return try {
            decode(file.readText())
        } catch (e: Exception) {
            SerializationResult.Failure(
                SerializationError.ParseError(0, e.message ?: "Unknown error", file.absolutePath)
            )
        }
    }

    fun loadOrNull(file: File): T? = load(file).getOrNull()
}

/**
 * Multi-format registry: pick the right codec for each format.
 */
object SerializationFormats {
    private val codecs = mutableMapOf<String, Codec<*>>()

    fun <T> register(name: String, codec: Codec<T>) {
        codecs[name] = codec
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> get(name: String): Codec<T> = codecs[name] as? Codec<T>
        ?: throw IllegalArgumentException("Unknown format: $name. Available: ${codecs.keys}")

    fun available(): Set<String> = codecs.keys.toSet()
}
