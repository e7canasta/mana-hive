package com.manahive.batchio

import com.manahive.contracts.sentinel.SentinelSignal
import java.io.File
import java.time.Instant

/**
 * Writes SentinelSignals to .out files.
 *
 * Format: `<TYPE> <details>          # ← signal <n>`
 */
object SentinelSignalWriter {

    fun write(file: File, signals: List<SentinelSignal>, startTime: Instant) {
        file.parentFile?.mkdirs()
        file.bufferedWriter().use { writer ->
            signals.forEach { signal ->
                val offset = DurationFormat.format(java.time.Duration.between(startTime, signal.at))
                val body = formatSignal(signal)
                writer.write("t=$offset  $body")
                writer.newLine()
            }
        }
    }

    fun formatSignal(signal: SentinelSignal): String = when (signal) {
        is SentinelSignal.EpisodeOpened ->
            "EPISODE_OPENED rule=${signal.rule.value} severity=${signal.severity}"
        is SentinelSignal.EpisodeClosed ->
            "EPISODE_CLOSED cause=${signal.cause}"
        is SentinelSignal.AutoRecovery ->
            "AUTO_RECOVERY reversible=${signal.reversible}"
        is SentinelSignal.UmbrellaEvent ->
            "UMBRELLA_EVENT state=${signal.state} severity=${signal.originalSeverity}"
        is SentinelSignal.SuppressedWithRecord ->
            "SUPPRESSED cause=${signal.cause}"
    }
}
