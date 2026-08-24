package com.manahive.sentinel.batch.events

import com.manahive.contracts.scene.StateKind
import com.manahive.sentinel.batch.SentinelBatchError
import com.manahive.sentinel.batch.parseDuration
import java.io.File

/**
 * Parses the events.dat file for sentinel-batch.
 *
 * Format per line:
 * ```
 * t=<offset> <FACT_TYPE> <details>
 * ```
 *
 * Fact types:
 * - `TRANSITION from <FROM> to <TO>`
 * - `STAFF_PRESENT staff <ID>`
 * - `DWELL_EXCEEDED state <STATE> threshold <DURATION>`
 * - `DWELL_WARNING state <STATE> threshold <DURATION>`
 * - `SIGNAL_LOST monitor <ID> [lastHeartbeat <ISO>]`
 * - `SIGNAL_RECOVERED monitor <ID>`
 *
 * Lines starting with `#` are comments. Blank lines are ignored.
 */
object SceneEventEventParser {

    private val OFFSET_PATTERN = Regex("""t=(\d+)([smh])?(\d+)?([smh])?""")

    fun parse(file: File): List<SceneEventEvent> {
        if (!file.exists()) throw SentinelBatchError.EventsNotFound(file.absolutePath)

        return file.readLines()
            .mapIndexed { index, line -> parseLine(line, index + 1) }
            .filterNotNull()
    }

    private fun parseLine(line: String, lineNumber: Int): SceneEventEvent? {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return null

        val offset = parseOffset(trimmed)
            ?: throw SentinelBatchError.ParseError(lineNumber, "invalid offset format")

        val typeName = parseTypeName(trimmed)
            ?: throw SentinelBatchError.ParseError(lineNumber, "unknown fact type")

        return when (typeName) {
            "TRANSITION" -> parseTransition(trimmed, offset, lineNumber)
            "STAFF_PRESENT" -> parseStaffPresent(trimmed, offset, lineNumber)
            "DWELL_EXCEEDED" -> parseDwellExceeded(trimmed, offset, lineNumber)
            "DWELL_WARNING" -> parseDwellWarning(trimmed, offset, lineNumber)
            "SIGNAL_LOST" -> parseSignalLost(trimmed, offset, lineNumber)
            "SIGNAL_RECOVERED" -> parseSignalRecovered(trimmed, offset, lineNumber)
            else -> null
        }
    }

    private fun parseTransition(text: String, offset: EventOffset, lineNumber: Int): SceneEventEvent.Transition {
        val fromMatch = Regex("""from\s+(\w+)""").find(text)
            ?: throw SentinelBatchError.ParseError(lineNumber, "TRANSITION missing 'from'")
        val toMatch = Regex("""to\s+(\w+)""").find(text)
            ?: throw SentinelBatchError.ParseError(lineNumber, "TRANSITION missing 'to'")

        val from = parseStateKind(fromMatch.groupValues[1])
            ?: throw SentinelBatchError.ParseError(lineNumber, "unknown state: ${fromMatch.groupValues[1]}")
        val to = parseStateKind(toMatch.groupValues[1])
            ?: throw SentinelBatchError.ParseError(lineNumber, "unknown state: ${toMatch.groupValues[1]}")

        return SceneEventEvent.Transition(offset, lineNumber, from, to)
    }

    private fun parseStaffPresent(text: String, offset: EventOffset, lineNumber: Int): SceneEventEvent.StaffPresent {
        val staffMatch = Regex("""staff\s+(\S+)""").find(text)
        return SceneEventEvent.StaffPresent(offset, lineNumber, staffMatch?.groupValues?.get(1))
    }

    private fun parseDwellExceeded(text: String, offset: EventOffset, lineNumber: Int): SceneEventEvent.DwellExceeded {
        val (state, threshold) = parseDwellFields(text, lineNumber)
        return SceneEventEvent.DwellExceeded(offset, lineNumber, state, threshold)
    }

    private fun parseDwellWarning(text: String, offset: EventOffset, lineNumber: Int): SceneEventEvent.DwellWarning {
        val (state, threshold) = parseDwellFields(text, lineNumber)
        return SceneEventEvent.DwellWarning(offset, lineNumber, state, threshold)
    }

    private fun parseDwellFields(text: String, lineNumber: Int): Pair<StateKind, java.time.Duration> {
        val stateMatch = Regex("""state\s+(\w+)""").find(text)
            ?: throw SentinelBatchError.ParseError(lineNumber, "DWELL missing 'state'")
        val thresholdMatch = Regex("""threshold\s+(\S+)""").find(text)
            ?: throw SentinelBatchError.ParseError(lineNumber, "DWELL missing 'threshold'")

        val state = parseStateKind(stateMatch.groupValues[1])
            ?: throw SentinelBatchError.ParseError(lineNumber, "unknown state: ${stateMatch.groupValues[1]}")

        val threshold = try {
            parseDuration(thresholdMatch.groupValues[1])
        } catch (e: Exception) {
            throw SentinelBatchError.ParseError(lineNumber, "invalid duration: ${thresholdMatch.groupValues[1]}")
        }

        return state to threshold
    }

    private fun parseSignalLost(text: String, offset: EventOffset, lineNumber: Int): SceneEventEvent.SignalLost {
        val monitorMatch = Regex("""monitor\s+(\S+)""").find(text)
            ?: throw SentinelBatchError.ParseError(lineNumber, "SIGNAL_LOST missing 'monitor'")
        val heartbeatMatch = Regex("""lastHeartbeat\s+(\S+)""").find(text)

        return SceneEventEvent.SignalLost(offset, lineNumber, monitorMatch.groupValues[1], heartbeatMatch?.groupValues?.get(1))
    }

    private fun parseSignalRecovered(text: String, offset: EventOffset, lineNumber: Int): SceneEventEvent.SignalRecovered {
        val monitorMatch = Regex("""monitor\s+(\S+)""").find(text)
            ?: throw SentinelBatchError.ParseError(lineNumber, "SIGNAL_RECOVERED missing 'monitor'")

        return SceneEventEvent.SignalRecovered(offset, lineNumber, monitorMatch.groupValues[1])
    }

    private fun parseTypeName(text: String): String? {
        val tokens = text.split(Regex("""\s+"""))
        // Skip the offset prefix (e.g., "t=0s")
        val startIdx = if (tokens.firstOrNull()?.startsWith("t=") == true) 1 else 0
        return tokens.getOrNull(startIdx)
    }

    private fun parseOffset(text: String): EventOffset? {
        val match = OFFSET_PATTERN.find(text) ?: return null
        var seconds = 0L
        var minutes = 0L
        var hours = 0L

        val n1 = match.groupValues[1].toLongOrNull() ?: return null
        val u1 = match.groupValues[2].ifEmpty { "s" }
        when (u1) {
            "s" -> seconds += n1
            "m" -> minutes += n1
            "h" -> hours += n1
        }

        val n2 = match.groupValues[3].toLongOrNull()
        val u2 = match.groupValues[4]
        if (n2 != null && u2.isNotEmpty()) {
            when (u2) {
                "s" -> seconds += n2
                "m" -> minutes += n2
                "h" -> hours += n2
            }
        }

        val duration = java.time.Duration.ofHours(hours)
            .plusMinutes(minutes)
            .plusSeconds(seconds)

        return EventOffset(duration)
    }

    private fun parseStateKind(name: String): StateKind? = try {
        StateKind.valueOf(name)
    } catch (_: IllegalArgumentException) {
        null
    }
}
