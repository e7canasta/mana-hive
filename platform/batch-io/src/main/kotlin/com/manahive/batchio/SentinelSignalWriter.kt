package com.manahive.batchio

import com.manahive.contracts.sentinel.SentinelSignal
import com.manahive.contracts.sentinel.stateLabel
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
        is SentinelSignal.EpisodeComplicated ->
            "EPISODE_COMPLICATED rule=${signal.rule.value} severity=${signal.severity} previousSeverity=${signal.previousSeverity}"
        is SentinelSignal.EpisodeClosed ->
            "EPISODE_CLOSED cause=${signal.cause}"
        is SentinelSignal.AutoRecovery ->
            "AUTO_RECOVERY reversible=${signal.reversible}"
        is SentinelSignal.UmbrellaEvent ->
            "UMBRELLA_EVENT ${signal.stateLabel()} severity=${signal.originalSeverity}"
        is SentinelSignal.SuppressedWithRecord ->
            "SUPPRESSED cause=${signal.cause}"
        is SentinelSignal.DwellPreWarning ->
            "DWELL_PRE_WARNING state=${signal.state} elapsed=${signal.elapsed} threshold=${signal.threshold}"
        is SentinelSignal.ComeBackPreWarning ->
            "COME_BACK_PRE_WARNING awayFrom=${signal.baseline} elapsed=${signal.elapsed} threshold=${signal.threshold}"
    }
}
