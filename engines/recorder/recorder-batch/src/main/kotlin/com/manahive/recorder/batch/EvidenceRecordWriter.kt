package com.manahive.recorder.batch

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.manahive.recorder.EvidenceClipCreated
import com.manahive.recorder.EvidenceRecord
import com.manahive.recorder.EvidenceRecordingStarted
import com.manahive.recorder.EvidenceRecordingStopped
import java.io.File

private val mapper = jacksonObjectMapper()

/**
 * Write evidence records to a JSONL file.
 *
 * Same pattern as RecordingCommandWriter — extension function on List.
 */
public fun List<EvidenceRecord>.writeTo(file: File) {
    file.parentFile?.mkdirs()
    file.bufferedWriter().use { writer ->
        for (record in this) {
            val json = record.toJson()
            writer.write(mapper.writeValueAsString(json))
            writer.newLine()
        }
    }
}

/**
 * Convert an EvidenceRecord to a JSON-serializable map.
 */
public fun EvidenceRecord.toJson(): Map<String, Any?> = when (this) {
    is EvidenceRecordingStarted -> mapOf(
        "type" to "EvidenceRecordingStarted",
        "bed" to bed.value,
        "episode" to episode?.value,
        "monitors" to monitors.map { it.value },
        "start" to start.toString(),
        "trigger" to trigger,
        "at" to at.toString(),
    )
    is EvidenceRecordingStopped -> mapOf(
        "type" to "EvidenceRecordingStopped",
        "bed" to bed.value,
        "episode" to episode?.value,
        "monitors" to monitors.map { it.value },
        "end" to end.toString(),
        "at" to at.toString(),
    )
    is EvidenceClipCreated -> mapOf(
        "type" to "EvidenceClipCreated",
        "bed" to bed.value,
        "episode" to episode.value,
        "monitors" to monitors.map { it.value },
        "start" to start.toString(),
        "end" to end.toString(),
        "path" to path?.value,
        "size" to size.bytes,
        "at" to at.toString(),
    )
}
