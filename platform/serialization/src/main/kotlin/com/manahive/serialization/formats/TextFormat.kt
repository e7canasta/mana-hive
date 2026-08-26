package com.manahive.serialization.formats

import com.manahive.serialization.Codec
import com.manahive.serialization.SerializationResult
import java.time.Duration
import java.time.Instant

/**
 * Text format codec for .dat/.out files.
 *
 * Human-readable, LLM-friendly format.
 * Good for: batch processing, debugging, documentation.
 *
 * Format: `t=<offset> <TYPE> <details>`
 */
class TextFormatCodec : Codec<List<TextEvent>> {

    override fun encode(obj: List<TextEvent>): String {
        return obj.joinToString("\n") { event ->
            val t = "t=${formatDuration(event.offset)}"
            val type = event.type.padEnd(15)
            val details = event.details
            "$t  $type $details"
        }
    }

    override fun decode(text: String): SerializationResult<List<TextEvent>> {
        return try {
            val events = text.lines()
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .map { line -> parseLine(line) }
            SerializationResult.Success(events)
        } catch (e: Exception) {
            SerializationResult.Failure(com.manahive.serialization.SerializationError.ParseError(0, e.message ?: "Unknown error"))
        }
    }

    private fun parseLine(line: String): TextEvent {
        val regex = Regex("""t=(\S+)\s+(\S+)\s+(.*)""")
        val match = regex.matchEntire(line.trim())
            ?: throw IllegalArgumentException("Invalid format: $line")

        val offset = parseDuration(match.groupValues[1])
        val type = match.groupValues[2]
        val details = match.groupValues[3].trim()

        return TextEvent(offset, type, details)
    }

    private fun parseDuration(text: String): Duration {
        val parts = Regex("""(\d+)([smh])""").findAll(text)
        var totalSeconds = 0L
        for (match in parts) {
            val value = match.groupValues[1].toLong()
            val unit = match.groupValues[2]
            when (unit) {
                "s" -> totalSeconds += value
                "m" -> totalSeconds += value * 60
                "h" -> totalSeconds += value * 3600
            }
        }
        return Duration.ofSeconds(totalSeconds)
    }

    private fun formatDuration(d: Duration): String {
        val hours = d.toHours()
        val minutes = d.toMinutesPart()
        val seconds = d.toSecondsPart()
        val parts = mutableListOf<String>()
        if (hours > 0) parts.add("${hours}h")
        if (minutes > 0) parts.add("${minutes}m")
        if (seconds > 0 || parts.isEmpty()) parts.add("${seconds}s")
        return parts.joinToString("")
    }
}

data class TextEvent(
    val offset: Duration,
    val type: String,
    val details: String,
)
