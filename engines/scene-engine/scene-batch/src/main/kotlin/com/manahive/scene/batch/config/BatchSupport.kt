package com.manahive.scene.batch.config

import com.manahive.scene.batch.events.EventOffset
import java.io.File
import java.time.Duration

// ── Duration Formatting ─────────────────────────────────────────────────────

/**
 * Formats a Duration as a human-readable string.
 *
 * Fowler: "Introduce Centralized Conversion" — single source of truth.
 */
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

// ── File Resolution ─────────────────────────────────────────────────────────

fun resolveFile(base: File, path: String): File {
    val file = File(path)
    return if (file.isAbsolute) file else File(base.parentFile, path)
}
