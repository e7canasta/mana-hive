package com.manahive.sentinel.batch

import com.manahive.sentinel.batch.events.EventOffset
import com.manahive.sentinel.batch.SentinelBatchError
import java.io.File
import java.time.Duration

// ── Duration Formatting ─────────────────────────────────────────────────────

fun formatDuration(d: Duration): String {
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

fun formatOffset(offset: EventOffset): String = formatDuration(offset.duration)

// ── Duration Parsing ────────────────────────────────────────────────────────

private val DURATION_PATTERN = Regex("""(\d+)([smh])""")

fun parseDuration(raw: String): Duration {
    var duration = Duration.ZERO
    for (match in DURATION_PATTERN.findAll(raw)) {
        val value = match.groupValues[1].toLong()
        val unit = match.groupValues[2]
        duration = when (unit) {
            "s" -> duration.plusSeconds(value)
            "m" -> duration.plusMinutes(value)
            "h" -> duration.plusHours(value)
            else -> throw SentinelBatchError.InvalidDuration(raw)
        }
    }
    return duration
}

// ── File Resolution ─────────────────────────────────────────────────────────

fun resolveFile(base: File, path: String): File {
    val file = File(path)
    return if (file.isAbsolute) file else File(base.parentFile, path)
}
