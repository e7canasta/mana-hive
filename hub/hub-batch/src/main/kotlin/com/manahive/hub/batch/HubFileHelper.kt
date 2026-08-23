package com.manahive.hub.batch

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.manahive.contracts.EventEnvelope
import com.manahive.contracts.ledger.StoredEvent
import java.io.File

/**
 * Helper for reading/writing Hub JSONL files.
 *
 * Fowler: "Extract Class" — file I/O logic separated from command logic.
 * Eliminates Data Clumps (repeated file reading pattern).
 */
internal object HubFileHelper {

    private val mapper: ObjectMapper = jacksonObjectMapper()
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    fun readEvents(file: File): List<EventEnvelope> {
        if (!file.exists()) return emptyList()
        return file.readLines()
            .filter { it.isNotBlank() }
            .map { mapper.readValue<EventEnvelope>(it) }
    }

    fun readStoredEvents(file: File): List<StoredEvent> {
        if (!file.exists()) return emptyList()
        return file.readLines()
            .filter { it.isNotBlank() }
            .map { mapper.readValue<StoredEvent>(it) }
    }

    fun writeStoredEvents(file: File, events: List<StoredEvent>) {
        file.parentFile?.mkdirs()
        file.bufferedWriter().use { writer ->
            events.forEach { stored ->
                writer.write(mapper.writeValueAsString(stored))
                writer.newLine()
            }
        }
    }

    fun toJson(event: Any): String {
        return mapper.writeValueAsString(event)
    }
}
