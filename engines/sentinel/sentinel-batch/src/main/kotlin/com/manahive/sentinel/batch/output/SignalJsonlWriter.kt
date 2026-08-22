package com.manahive.sentinel.batch.output

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.manahive.contracts.sentinel.SentinelSignal
import java.io.File

/**
 * Writes SentinelSignal instances to a JSONL file.
 *
 * Each line is a self-contained JSON object:
 * ```json
 * {"type":"EpisodeOpened","bed":"301","resident":"maria","episode":"301-xxx","rule":"r-fall","trigger":"BED_EDGE","severity":"CRITICAL"}
 * ```
 */
class SignalJsonlWriter(private val outputFile: File) {

    private val mapper: ObjectMapper = ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT)

    init {
        outputFile.parentFile?.mkdirs()
    }

    fun write(signal: SentinelSignal, eventLine: Int? = null) {
        val node = signalToJson(signal, eventLine)
        outputFile.appendText(mapper.writeValueAsString(node) + "\n")
    }

    private fun signalToJson(signal: SentinelSignal, eventLine: Int?): Map<String, Any?> {
        val base = linkedMapOf<String, Any?>()

        if (eventLine != null) base["event"] = eventLine

        when (signal) {
            is SentinelSignal.EpisodeOpened -> {
                base["type"] = "EpisodeOpened"
                base["bed"] = signal.bed.value
                base["resident"] = signal.resident?.value
                base["episode"] = signal.episode.value
                base["rule"] = signal.rule.value
                base["trigger"] = signal.trigger.name
                base["severity"] = signal.severity.name
                base["reversible"] = signal.reversible
                base["nvr"] = signal.requiresNvr
                signal.confirmationWindow?.let { base["confirmationWindow"] = it.toString() }
            }
            is SentinelSignal.UmbrellaEvent -> {
                base["type"] = "UmbrellaEvent"
                base["bed"] = signal.bed.value
                base["resident"] = signal.resident?.value
                base["episode"] = signal.episode.value
                base["state"] = signal.state.name
                base["originalSeverity"] = signal.originalSeverity.name
            }
            is SentinelSignal.AutoRecovery -> {
                base["type"] = "AutoRecovery"
                base["bed"] = signal.bed.value
                base["resident"] = signal.resident?.value
                base["episode"] = signal.episode.value
                base["reversible"] = signal.reversible
                base["requiresConfirmation"] = signal.requiresConfirmation
            }
            is SentinelSignal.EpisodeClosed -> {
                base["type"] = "EpisodeClosed"
                base["bed"] = signal.bed.value
                base["resident"] = signal.resident?.value
                base["episode"] = signal.episode.value
                base["cause"] = signal.cause.name
                signal.gapDuration?.let { base["gapDuration"] = it.toString() }
            }
            is SentinelSignal.SuppressedWithRecord -> {
                base["type"] = "SuppressedWithRecord"
                base["bed"] = signal.bed.value
                base["resident"] = signal.resident?.value
                base["rule"] = signal.rule.value
                base["cause"] = signal.cause.name
            }
        }

        return base
    }
}
