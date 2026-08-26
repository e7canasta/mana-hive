package com.manahive.serialization.formats

import java.time.Duration

/**
 * Duration formatting utilities for .dat and .out files.
 *
 * Fowler: "Introduce Parameter Object" — shared formatting logic.
 */
object DurationFormat {

    fun format(d: Duration): String {
        val hours = d.toHours()
        val minutes = d.toMinutesPart()
        val seconds = d.toSecondsPart()
        val parts = mutableListOf<String>()
        if (hours > 0) parts.add("${hours}h")
        if (minutes > 0) parts.add("${minutes}m")
        if (seconds > 0 || parts.isEmpty()) parts.add("${seconds}s")
        return parts.joinToString("")
    }

    fun parse(text: String): Duration {
        val pattern = Regex("""(\d+)([smh])""")
        var totalSeconds = 0L
        pattern.findAll(text).forEach { match ->
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
}
