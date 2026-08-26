package com.manahive.batchio

import com.manahive.harbor.NoticeCommand
import java.io.File

/**
 * Writes NoticeCommands to .out files.
 *
 * Format: `<TYPE> <details>`
 */
object HarborCommandWriter {

    fun write(file: File, commands: List<NoticeCommand>) {
        file.parentFile?.mkdirs()
        file.bufferedWriter().use { writer ->
            commands.forEach { command ->
                val body = formatCommand(command)
                writer.write(body)
                writer.newLine()
            }
        }
    }

    fun formatCommand(command: NoticeCommand): String = when (command) {
        is NoticeCommand.Create ->
            "CREATE episode=${command.signal.episode.value}"
        is NoticeCommand.Dispatch ->
            "DISPATCH channels=${command.channels}"
        is NoticeCommand.MarkSeen ->
            "MARK_SEEN by=${command.by.value}"
        is NoticeCommand.Acknowledge ->
            "ACKNOWLEDGE by=${command.by.value}"
        is NoticeCommand.Escalate ->
            "ESCALATE"
        is NoticeCommand.Cancel ->
            "CANCEL reason=${command.reason}"
        is NoticeCommand.Resolve ->
            "RESOLVE resolution=${command.resolution}"
    }
}
