package com.manahive.sentinel.batch.output

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.manahive.contracts.sentinel.SentinelSignal
import com.manahive.contracts.sentinel.toMap
import java.io.File

/**
 * Writes SentinelSignal instances to a JSONL file.
 *
 * Each line is a self-contained JSON object:
 * ```json
 * {"type":"EpisodeOpened","bed":"301","resident":"maria","episode":"301-xxx","rule":"r-fall","trigger":"BED_EDGE","severity":"CRITICAL"}
 * ```
 */
class SignalJsonlWriter(private val outputFile: File) {

    private val mapper: ObjectMapper = ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT)

    init {
        outputFile.parentFile?.mkdirs()
    }

    fun write(signal: SentinelSignal, eventLine: Int? = null) {
        val map = signal.toMap().toMutableMap()
        if (eventLine != null) map["event"] = eventLine
        outputFile.appendText(mapper.writeValueAsString(map) + "\n")
    }
}
