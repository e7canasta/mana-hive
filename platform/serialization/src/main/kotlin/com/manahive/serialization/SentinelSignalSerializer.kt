package com.manahive.serialization

import com.manahive.contracts.policy.Severity
import com.manahive.contracts.scene.StateKind
import com.manahive.contracts.sentinel.ClosureCause
import com.manahive.contracts.sentinel.SentinelSignal
import com.manahive.contracts.sentinel.SuppressionCause
import com.manahive.kernel.*
import java.time.Duration
import java.time.Instant

/**
 * SentinelSignal serializer.
 *
 * Bidirectional: SentinelSignal ↔ String (any format).
 */
object SentinelSignalSerializer {

    fun toJson(signal: SentinelSignal): String {
        val map = mutableMapOf<String, Any>(
            "type" to (signal::class.simpleName ?: "Unknown"),
            "at" to signal.at.toString(),
            "bed" to signal.bed.value,
            "rulesFingerprint" to signal.rulesFingerprint,
        )

        when (signal) {
            is SentinelSignal.EpisodeOpened -> {
                map["resident"] = signal.resident?.value ?: "unknown"
                map["episode"] = signal.episode.value
                map["rule"] = signal.rule.value
                map["trigger"] = signal.trigger.name
                map["severity"] = signal.severity.name
                map["reversible"] = signal.reversible
                map["requiresNvr"] = signal.requiresNvr
                signal.confirmationWindow?.let { map["confirmationWindow"] = it.toString() }
            }
            is SentinelSignal.EpisodeClosed -> {
                map["resident"] = signal.resident?.value ?: "unknown"
                map["episode"] = signal.episode.value
                map["cause"] = signal.cause.name
                signal.gapDuration?.let { map["gapDuration"] = it.toString() }
            }
            is SentinelSignal.AutoRecovery -> {
                map["resident"] = signal.resident?.value ?: "unknown"
                map["episode"] = signal.episode.value
                map["reversible"] = signal.reversible
                map["requiresConfirmation"] = signal.requiresConfirmation
            }
            is SentinelSignal.UmbrellaEvent -> {
                map["resident"] = signal.resident?.value ?: "unknown"
                map["episode"] = signal.episode.value
                map["state"] = signal.state.name
                map["originalSeverity"] = signal.originalSeverity.name
            }
            is SentinelSignal.SuppressedWithRecord -> {
                map["resident"] = signal.resident?.value ?: "unknown"
                map["rule"] = signal.rule.value
                map["cause"] = signal.cause.name
                map["evidenceStream"] = signal.evidence.stream
                map["evidenceSeq"] = signal.evidence.seq
            }
        }

        return com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
            .writerWithDefaultPrettyPrinter()
            .writeValueAsString(map)
    }

    fun fromJson(json: String): SentinelSignal {
        val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
        val node = mapper.readTree(json)
        val type = node.get("type").asText()
        val at = Instant.parse(node.get("at").asText())
        val bed = BedId(node.get("bed").asText())
        val rulesFingerprint = node.get("rulesFingerprint").asText()
        val resident = node.get("resident")?.asText()?.takeIf { it != "unknown" }?.let { ResidentId(it) }

        return when (type) {
            "EpisodeOpened" -> {
                val episode = EpisodeId(node.get("episode").asText())
                val rule = RuleId(node.get("rule").asText())
                val trigger = StateKind.valueOf(node.get("trigger").asText())
                val severity = Severity.valueOf(node.get("severity").asText())
                val reversible = node.get("reversible").asBoolean()
                val requiresNvr = node.get("requiresNvr").asBoolean()
                val confirmationWindow = node.get("confirmationWindow")?.asText()?.let { Duration.parse(it) }

                SentinelSignal.EpisodeOpened(
                    bed = bed,
                    resident = resident,
                    at = at,
                    rulesFingerprint = rulesFingerprint,
                    episode = episode,
                    rule = rule,
                    trigger = trigger,
                    severity = severity,
                    reversible = reversible,
                    requiresNvr = requiresNvr,
                    confirmationWindow = confirmationWindow,
                )
            }
            "EpisodeClosed" -> {
                val episode = EpisodeId(node.get("episode").asText())
                val cause = ClosureCause.valueOf(node.get("cause").asText())
                val gapDuration = node.get("gapDuration")?.asText()?.let { Duration.parse(it) }

                SentinelSignal.EpisodeClosed(
                    bed = bed,
                    resident = resident,
                    at = at,
                    rulesFingerprint = rulesFingerprint,
                    episode = episode,
                    cause = cause,
                    gapDuration = gapDuration,
                )
            }
            "AutoRecovery" -> {
                val episode = EpisodeId(node.get("episode").asText())
                val reversible = node.get("reversible").asBoolean()
                val requiresConfirmation = node.get("requiresConfirmation").asBoolean()

                SentinelSignal.AutoRecovery(
                    bed = bed,
                    resident = resident,
                    at = at,
                    rulesFingerprint = rulesFingerprint,
                    episode = episode,
                    reversible = reversible,
                    requiresConfirmation = requiresConfirmation,
                )
            }
            "UmbrellaEvent" -> {
                val episode = EpisodeId(node.get("episode").asText())
                val state = StateKind.valueOf(node.get("state").asText())
                val originalSeverity = Severity.valueOf(node.get("originalSeverity").asText())

                SentinelSignal.UmbrellaEvent(
                    bed = bed,
                    resident = resident,
                    at = at,
                    rulesFingerprint = rulesFingerprint,
                    episode = episode,
                    state = state,
                    originalSeverity = originalSeverity,
                )
            }
            "SuppressedWithRecord" -> {
                val rule = RuleId(node.get("rule").asText())
                val cause = SuppressionCause.valueOf(node.get("cause").asText())
                val evidence = EventRef(
                    stream = node.get("evidenceStream").asText(),
                    seq = node.get("evidenceSeq").asLong(),
                )

                SentinelSignal.SuppressedWithRecord(
                    bed = bed,
                    resident = resident,
                    at = at,
                    rulesFingerprint = rulesFingerprint,
                    rule = rule,
                    cause = cause,
                    evidence = evidence,
                )
            }
            else -> throw IllegalArgumentException("Unknown SentinelSignal type: $type")
        }
    }

    fun toText(signal: SentinelSignal, startTime: Instant): String {
        val offset = java.time.Duration.between(startTime, signal.at)
        val type = signal::class.simpleName ?: "Unknown"
        val details = formatDetails(signal)
        return "t=${formatDuration(offset)}  $type $details"
    }

    private fun formatDetails(signal: SentinelSignal): String = when (signal) {
        is SentinelSignal.EpisodeOpened ->
            "rule=${signal.rule.value} severity=${signal.severity} reversible=${signal.reversible}"
        is SentinelSignal.EpisodeClosed ->
            "cause=${signal.cause}"
        is SentinelSignal.AutoRecovery ->
            "reversible=${signal.reversible} requiresConfirmation=${signal.requiresConfirmation}"
        is SentinelSignal.UmbrellaEvent ->
            "state=${signal.state} severity=${signal.originalSeverity}"
        is SentinelSignal.SuppressedWithRecord ->
            "rule=${signal.rule.value} cause=${signal.cause}"
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
