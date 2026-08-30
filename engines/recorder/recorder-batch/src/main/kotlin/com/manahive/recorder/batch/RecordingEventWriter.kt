package com.manahive.recorder.batch

import com.manahive.recorder.ClipCreated
import com.manahive.recorder.RecordingCommand
import com.manahive.recorder.RecordingStarted
import com.manahive.recorder.RecordingStopped
import java.io.File
import java.time.Duration
import java.time.Instant

/**
 * Writes RecordingCommands to .out files.
 *
 * Format: `t=<offset>  <TYPE> <details>`
 */
object RecordingEventWriter {

    fun write(file: File, commands: List<RecordingCommand>, startTime: Instant) {
        file.parentFile?.mkdirs()
        file.bufferedWriter().use { writer ->
            commands.forEach { command ->
                val offset = formatOffset(Duration.between(startTime, command.at))
                val body = formatCommand(command)
                writer.write("t=$offset  $body")
                writer.newLine()
            }
        }
    }

    private fun formatCommand(command: RecordingCommand): String = when (command) {
        is RecordingStarted ->
            "RECORDING_STARTED monitor=${command.target.monitor.value} quality=${command.config.quality.name}"
        is RecordingStopped ->
            "RECORDING_STOPPED monitor=${command.target.monitor.value}"
        is ClipCreated ->
            "CLIP_CREATED episode=${command.episode.value} monitor=${command.target.monitor.value}"
    }

    private fun formatOffset(d: Duration): String {
        val h = d.toHours()
        val m = d.toMinutesPart()
        val s = d.toSecondsPart()
        return buildString {
            if (h > 0) append("${h}h")
            if (m > 0) append("${m}m")
            if (s > 0 || isEmpty()) append("${s}s")
        }
    }
}

/**
 * Extension function for backward compatibility with RecorderBatchApp.
 */
public fun List<RecordingCommand>.writeTo(file: File) {
    RecordingEventWriter.write(file, this, Instant.EPOCH)
}
