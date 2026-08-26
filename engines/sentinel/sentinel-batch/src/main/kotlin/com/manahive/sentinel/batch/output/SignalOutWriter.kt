package com.manahive.sentinel.batch.output

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.manahive.contracts.sentinel.SentinelSignal
import com.manahive.contracts.sentinel.stateLabel
import com.manahive.sentinel.batch.formatOffset
import com.manahive.sentinel.batch.events.EventOffset
import java.io.File

/**
 * Writes SentinelSignal instances to the .out file.
 *
 * Format: `t=<offset>  <SIGNAL_TYPE> <details>          # ← evento <n>`
 *
 * Example:
 * ```
 * t=0s      EPISODE_OPENED episode=301-xxx rule=r-fall trigger=BED_EDGE severity=CRITICAL
 * t=10s     STAFF_MARKED episode=301-xxx staff=nurse-1
 * t=20s     EPISODE_CLOSED episode=301-xxx cause=STAFF_AND_SAFE gap=10s
 * ```
 */
class SignalOutWriter(private val outputFile: File) {

    init {
        outputFile.parentFile?.mkdirs()
    }

    fun write(offset: EventOffset, signal: SentinelSignal, eventLine: Int) {
        outputFile.appendText(formatSignal(offset, signal, eventLine) + "\n")
    }

    private fun formatSignal(offset: EventOffset, signal: SentinelSignal, eventLine: Int): String {
        val t = "t=${formatOffset(offset)}"
        val padding = " ".repeat(maxOf(1, 10 - t.length))
        val body = formatBody(signal)
        return "$t${padding}$body".padEnd(60) + "# ← evento $eventLine"
    }

    /**
     * Formats a SentinelSignal as a human-readable string.
     */
    private fun formatBody(signal: SentinelSignal): String = when (signal) {
        is SentinelSignal.EpisodeOpened ->
            "EPISODE_OPENED episode=${signal.episode.value} rule=${signal.rule.value} " +
                "trigger=${signal.trigger} severity=${signal.severity} reversible=${signal.reversible} nvr=${signal.requiresNvr}"
        is SentinelSignal.UmbrellaEvent ->
            "UMBRELLA_EVENT episode=${signal.episode.value} ${signal.stateLabel()} severity=${signal.originalSeverity}"
        is SentinelSignal.AutoRecovery ->
            "AUTO_RECOVERY episode=${signal.episode.value} reversible=${signal.reversible} confirmation=${signal.requiresConfirmation}"
        is SentinelSignal.EpisodeClosed ->
            "EPISODE_CLOSED episode=${signal.episode.value} cause=${signal.cause}" +
                (signal.gapDuration?.let { " gap=$it" } ?: "")
        is SentinelSignal.SuppressedWithRecord ->
            "SUPPRESSED rule=${signal.rule.value} cause=${signal.cause}"
        is SentinelSignal.DwellPreWarning ->
            "DWELL_PRE_WARNING state=${signal.state} elapsed=${signal.elapsed} threshold=${signal.threshold}"
        is SentinelSignal.ComeBackPreWarning ->
            "COME_BACK_PRE_WARNING awayFrom=${signal.baseline} elapsed=${signal.elapsed} threshold=${signal.threshold}"
    }
}
