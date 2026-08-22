package com.manahive.scene.batch.events

import com.manahive.contracts.perception.ObservationKind
import com.manahive.scene.batch.BatchError
import java.io.File

/**
 * Parses the events.dat file.
 *
 * Format per line:
 * ```
 * t=<offset> OBS <kind> confidence=<value>
 * ```
 *
 * Offset format: `0s`, `2s`, `4m30s`, `1h5m`, etc.
 * Lines starting with `#` are comments. Blank lines are ignored.
 */
object EventParser {

    private val OFFSET_PATTERN = Regex("""t=(\d+)([smh])?(\d+)?([smh])?""")
    private val CONFIDENCE_PATTERN = Regex("""confidence=([\d.]+)""")
    private val KIND_PATTERN = Regex("""OBS\s+(\w+)""")

    fun parse(file: File): List<Event> {
        if (!file.exists()) throw BatchError.EventsNotFound(file.absolutePath)

        return file.readLines()
            .mapIndexed { index, line -> parseLine(line, index + 1) }
            .filterNotNull()
    }

    private fun parseLine(line: String, lineNumber: Int): Event? {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return null

        val offset = parseOffset(trimmed)
            ?: throw BatchError.ParseError(lineNumber, "invalid offset format")

        val kindStr = KIND_PATTERN.find(trimmed)?.groupValues?.get(1)
            ?: throw BatchError.ParseError(lineNumber, "missing OBS kind")

        val kind = parseKind(kindStr)
            ?: throw BatchError.ParseError(lineNumber, "unknown observation kind: $kindStr")

        val confidence = CONFIDENCE_PATTERN.find(trimmed)?.groupValues?.get(1)?.toDoubleOrNull()
            ?: throw BatchError.ParseError(lineNumber, "missing or invalid confidence")

        return Event(
            offset = offset,
            kind = kind,
            confidence = confidence,
            lineNumber = lineNumber,
        )
    }

    private fun parseOffset(text: String): EventOffset? {
        val match = OFFSET_PATTERN.find(text) ?: return null
        var seconds = 0L
        var minutes = 0L
        var hours = 0L

        // Parse first number + unit
        val n1 = match.groupValues[1].toLongOrNull() ?: return null
        val u1 = match.groupValues[2].ifEmpty { "s" }
        when (u1) {
            "s" -> seconds += n1
            "m" -> minutes += n1
            "h" -> hours += n1
        }

        // Parse second number + unit (if present)
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

    private fun parseKind(kind: String): ObservationKind? = try {
        ObservationKind.valueOf(kind)
    } catch (_: IllegalArgumentException) {
        null
    }
}
