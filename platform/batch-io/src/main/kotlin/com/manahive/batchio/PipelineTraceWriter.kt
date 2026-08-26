package com.manahive.batchio

import com.manahive.contracts.scene.SceneEvent
import com.manahive.contracts.sentinel.SentinelSignal
import com.manahive.harbor.NoticeCommand
import java.io.File
import java.time.Duration
import java.time.Instant

/**
 * Writes pipeline trace to .out files.
 *
 * Format: `t=<offset>  <ENGINE>     <EVENT>                 <details>`
 *
 * Shows chronological flow across all engines.
 */
object PipelineTraceWriter {

    data class TraceEntry(
        val offset: Duration,
        val engine: String,
        val event: String,
        val details: String = "",
    )

    fun write(
        file: File,
        startTime: Instant,
        inputEvents: List<EventParser.Event>,
        sceneEvents: List<SceneEvent>,
        sentinelSignals: List<SentinelSignal>,
        harborCommands: List<NoticeCommand>,
    ) {
        val trace = buildTrace(startTime, inputEvents, sceneEvents, sentinelSignals, harborCommands)

        file.parentFile?.mkdirs()
        file.bufferedWriter().use { writer ->
            writer.write("# Pipeline trace: ${inputEvents.size} observations → ${sceneEvents.size} scene → ${sentinelSignals.size} signals → ${harborCommands.size} commands")
            writer.newLine()
            writer.newLine()

            trace.forEach { entry ->
                val t = "t=${DurationFormat.format(entry.offset)}"
                val engine = entry.engine.padEnd(10)
                val event = entry.event.padEnd(25)
                val details = if (entry.details.isNotEmpty()) " ${entry.details}" else ""
                writer.write("$t  $engine $event$details")
                writer.newLine()
            }
        }
    }

    private fun buildTrace(
        startTime: Instant,
        inputEvents: List<EventParser.Event>,
        sceneEvents: List<SceneEvent>,
        sentinelSignals: List<SentinelSignal>,
        harborCommands: List<NoticeCommand>,
    ): List<TraceEntry> {
        val trace = mutableListOf<TraceEntry>()

        // Input observations
        inputEvents.forEach { event ->
            trace.add(TraceEntry(event.offset, "INPUT", event.kind.name, "confidence=${event.confidence}"))
        }

        // Scene events
        sceneEvents.forEach { event ->
            val offset = Duration.between(startTime, event.at)
            val details = SceneEventWriter.formatEvent(event)
            trace.add(TraceEntry(offset, "SCENE", event::class.simpleName!!, details))
        }

        // Sentinel signals
        sentinelSignals.forEach { signal ->
            val offset = Duration.between(startTime, signal.at)
            val details = SentinelSignalWriter.formatSignal(signal)
            trace.add(TraceEntry(offset, "SENTINEL", signal::class.simpleName!!, details))
        }

        // Harbor commands
        var harborIdx = 0
        harborCommands.forEach { command ->
            val offset = if (harborIdx < sentinelSignals.size) {
                Duration.between(startTime, sentinelSignals[harborIdx].at)
            } else {
                Duration.ZERO
            }
            trace.add(TraceEntry(offset, "HARBOR", command::class.simpleName!!, ""))
            harborIdx++
        }

        // Sort by offset
        return trace.sortedBy { it.offset }
    }
}
