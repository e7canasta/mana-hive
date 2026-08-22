package com.manahive.sentinel.batch.output

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.ObjectNode
import java.io.File
import java.time.Instant

/**
 * Writes engine logs to a JSONL file.
 *
 * Each line is a self-contained JSON object:
 * ```json
 * {"ts":"2024-01-01T03:00:02Z","level":"INFO","msg":"signal emitted","event":1,"type":"EpisodeOpened"}
 * ```
 */
class LogWriter(private val outputFile: File) {

    private val mapper: ObjectMapper = ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT)

    private val nodeFactory get() = mapper.nodeFactory

    init {
        outputFile.parentFile?.mkdirs()
    }

    fun info(msg: String, vararg pairs: Pair<String, Any?>) {
        write("INFO", msg, pairs.toMap())
    }

    fun warn(msg: String, vararg pairs: Pair<String, Any?>) {
        write("WARN", msg, pairs.toMap())
    }

    fun error(msg: String, vararg pairs: Pair<String, Any?>) {
        write("ERROR", msg, pairs.toMap())
    }

    fun debug(msg: String, vararg pairs: Pair<String, Any?>) {
        write("DEBUG", msg, pairs.toMap())
    }

    private fun write(level: String, msg: String, context: Map<String, Any?>) {
        val node: ObjectNode = nodeFactory.objectNode().apply {
            put("ts", Instant.now().toString())
            put("level", level)
            put("msg", msg)
            context.forEach { (k, v) -> putValue(this, k, v) }
        }

        outputFile.appendText(mapper.writeValueAsString(node) + "\n")
    }

    private fun putValue(node: ObjectNode, key: String, value: Any?) {
        when (value) {
            null -> node.putNull(key)
            is String -> node.put(key, value)
            is Int -> node.put(key, value)
            is Long -> node.put(key, value)
            is Double -> node.put(key, value)
            is Boolean -> node.put(key, value)
            else -> node.put(key, value.toString())
        }
    }
}
