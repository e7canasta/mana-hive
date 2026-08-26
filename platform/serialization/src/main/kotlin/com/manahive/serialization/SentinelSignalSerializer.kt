package com.manahive.serialization

import com.manahive.contracts.policy.Severity
import com.manahive.contracts.scene.StateKind
import com.manahive.contracts.sentinel.ClosureCause
import com.manahive.contracts.sentinel.SentinelSignal
import com.manahive.contracts.sentinel.SignalType
import com.manahive.contracts.sentinel.SuppressionCause
import com.manahive.contracts.sentinel.toMap
import com.manahive.kernel.*
import java.time.Duration
import java.time.Instant

/**
 * SentinelSignal serializer.
 *
 * Bidirectional: SentinelSignal ↔ String (any format).
 */
object SentinelSignalSerializer {

    private val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()

    fun toJson(signal: SentinelSignal): String =
        mapper.writerWithDefaultPrettyPrinter().writeValueAsString(signal.toMap())

    fun fromJson(json: String): SentinelSignal {
        val node = mapper.readTree(json)
        val type = SignalType.valueOf(node.get("type").asText())
        val at = Instant.parse(node.get("at").asText())
        val bed = BedId(node.get("bed").asText())
        val rulesFingerprint = node.get("rulesFingerprint").asText()
        val resident = node.get("resident")?.asText()?.takeIf { it != "unknown" }?.let { ResidentId(it) }

        return when (type) {
            SignalType.EPISODE_OPENED -> {
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
            SignalType.EPISODE_CLOSED -> {
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
            SignalType.AUTO_RECOVERY -> {
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
            SignalType.UMBRELLA_EVENT -> {
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
            SignalType.SUPPRESSED_WITH_RECORD -> {
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
            SignalType.DWELL_PRE_WARNING -> {
                val state = StateKind.valueOf(node.get("state").asText())
                val elapsed = Duration.parse(node.get("elapsed").asText())
                val threshold = Duration.parse(node.get("threshold").asText())

                SentinelSignal.DwellPreWarning(
                    bed = bed,
                    resident = resident,
                    at = at,
                    rulesFingerprint = rulesFingerprint,
                    state = state,
                    elapsed = elapsed,
                    threshold = threshold,
                )
            }
        }
    }

    fun toText(signal: SentinelSignal, startTime: Instant): String {
        val offset = java.time.Duration.between(startTime, signal.at)
        val details = formatDetails(signal)
        return "t=${formatDuration(offset)}  ${signal.type.name} $details"
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
        is SentinelSignal.DwellPreWarning ->
            "state=${signal.state} elapsed=${signal.elapsed} threshold=${signal.threshold}"
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
