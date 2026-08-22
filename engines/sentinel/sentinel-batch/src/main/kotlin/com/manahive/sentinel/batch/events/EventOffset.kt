package com.manahive.sentinel.batch.events

import java.time.Duration

/**
 * Value Object: time offset from simulation start.
 *
 * Fowler: "Replace Primitive with Value Object" — type-safe,
 * self-documenting, prevents mixing with absolute timestamps.
 */
@JvmInline
value class EventOffset(val duration: Duration) : Comparable<EventOffset> {

    init {
        require(!duration.isNegative) { "EventOffset must not be negative: $duration" }
    }

    override fun compareTo(other: EventOffset): Int = duration.compareTo(other.duration)

    fun toSeconds(): Long = duration.toSeconds()

    override fun toString(): String = formatDuration(duration)

    companion object {
        val ZERO: EventOffset = EventOffset(Duration.ZERO)

        private val COMPONENT = Regex("""(\d+)([smh])""")

        fun of(duration: Duration): EventOffset = EventOffset(duration)

        fun parse(text: String): EventOffset {
            var duration = Duration.ZERO
            for (match in COMPONENT.findAll(text)) {
                val value = match.groupValues[1].toLong()
                val unit = match.groupValues[2]
                duration = when (unit) {
                    "s" -> duration.plusSeconds(value)
                    "m" -> duration.plusMinutes(value)
                    "h" -> duration.plusHours(value)
                    else -> throw IllegalArgumentException("Invalid time unit: $unit")
                }
            }
            return EventOffset(duration)
        }

        private fun formatDuration(d: Duration): String {
            val totalSeconds = d.toSeconds()
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return buildString {
                if (hours > 0) append("${hours}h")
                if (minutes > 0) append("${minutes}m")
                if (seconds > 0 || isEmpty()) append("${seconds}s")
            }
        }
    }
}
