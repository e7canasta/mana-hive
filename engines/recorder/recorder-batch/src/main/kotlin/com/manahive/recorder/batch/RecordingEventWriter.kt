package com.manahive.recorder.batch

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.manahive.recorder.ClipCreated
import com.manahive.recorder.RecordingCommand
import com.manahive.recorder.RecordingStarted
import com.manahive.recorder.RecordingStopped
import java.io.File

private val mapper = jacksonObjectMapper()

/**
 * Write recording commands to a JSONL file.
 *
 * Same pattern as HarborOutWriter — extension function on List.
 */
public fun List<RecordingCommand>.writeTo(file: File) {
    file.parentFile?.mkdirs()
    file.bufferedWriter().use { writer ->
        for (command in this) {
            val json = command.toJson()
            writer.write(mapper.writeValueAsString(json))
            writer.newLine()
        }
    }
}

/**
 * Convert a RecordingCommand to a JSON-serializable map.
 */
public fun RecordingCommand.toJson(): Map<String, Any?> = when (this) {
    is RecordingStarted -> mapOf(
        "type" to "RecordingStarted",
        "bed" to target.bed.value,
        "monitor" to target.monitor.value,
        "start" to config.start.toString(),
        "quality" to config.quality.name,
        "context" to when (context) {
            is com.manahive.recorder.RecordingContext.Standalone -> "standalone"
            is com.manahive.recorder.RecordingContext.TiedToEpisode -> "tied-to-episode"
        },
        "episode" to context.episode?.value,
        "at" to at.toString(),
    )
    is RecordingStopped -> mapOf(
        "type" to "RecordingStopped",
        "bed" to target.bed.value,
        "monitor" to target.monitor.value,
        "end" to end.toString(),
        "context" to when (context) {
            is com.manahive.recorder.RecordingContext.Standalone -> "standalone"
            is com.manahive.recorder.RecordingContext.TiedToEpisode -> "tied-to-episode"
        },
        "episode" to context.episode?.value,
        "at" to at.toString(),
    )
    is ClipCreated -> mapOf(
        "type" to "ClipCreated",
        "bed" to target.bed.value,
        "monitor" to target.monitor.value,
        "episode" to episode.value,
        "start" to start.toString(),
        "end" to end.toString(),
        "path" to path?.value,
        "size" to size.bytes,
        "at" to at.toString(),
    )
}
