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
 * SentinelSignal codec: SentinelSignal ↔ JSON/Text.
 *
 * Usage:
 * ```kotlin
 * val json = signal.toJson()
 * val result = json.toSentinelSignal()
 * result.onSuccess { signal -> ... }
 * ```
 */
object SentinelSignalCodec : Codec<SentinelSignal> {

    private val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()

    override fun encode(obj: SentinelSignal): String =
        mapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj.toMap())

    override fun decode(text: String): SerializationResult<SentinelSignal> = serialization {
        val node = mapper.readTree(text)
        val typeRaw = node.get("type")?.asText()
            ?: throw SerializationException(SerializationError.MissingField("type", "SentinelSignal"))
        val type = try {
            SignalType.valueOf(typeRaw)
        } catch (_: IllegalArgumentException) {
            throw SerializationException(SerializationError.InvalidState(typeRaw, SignalType.entries.map { it.name }.toSet()))
        }
        val at = Instant.parse(node.get("at").asText())
        val bed = BedId(node.get("bed").asText())
        val rulesFingerprint = node.get("rulesFingerprint").asText()
        val resident = node.get("resident")?.asText()?.takeIf { it != "unknown" }
            ?.let { ResidentId(it) }

        when (type) {
            SignalType.EPISODE_OPENED -> {
                val episode = EpisodeId(node.get("episode").asText())
                val rule = RuleId(node.get("rule").asText())
                val triggerResult = StateKindInput.parseStateKind(node.get("trigger").asText())
                val severityResult = parseSeverity(node.get("severity").asText())
                val reversible = node.get("reversible").asBoolean()
                val requiresNvr = node.get("requiresNvr").asBoolean()
                val confirmationWindow = node.get("confirmationWindow")?.asText()
                    ?.let { Duration.parse(it) }

                triggerResult.flatMap { trigger ->
                    severityResult.map { severity ->
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
                }.getOrThrow()
            }
            SignalType.EPISODE_CLOSED -> {
                val episode = EpisodeId(node.get("episode").asText())
                val causeResult = parseClosureCause(node.get("cause").asText())
                val gapDuration = node.get("gapDuration")?.asText()?.let { Duration.parse(it) }

                causeResult.map { cause ->
                    SentinelSignal.EpisodeClosed(
                        bed = bed,
                        resident = resident,
                        at = at,
                        rulesFingerprint = rulesFingerprint,
                        episode = episode,
                        cause = cause,
                        gapDuration = gapDuration,
                    )
                }.getOrThrow()
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
                val stateResult = StateKindInput.parseStateKind(node.get("state").asText())
                val severityResult = parseSeverity(node.get("originalSeverity").asText())

                stateResult.flatMap { state ->
                    severityResult.map { severity ->
                        SentinelSignal.UmbrellaEvent(
                            bed = bed,
                            resident = resident,
                            at = at,
                            rulesFingerprint = rulesFingerprint,
                            episode = episode,
                            state = state,
                            originalSeverity = severity,
                        )
                    }
                }.getOrThrow()
            }
            SignalType.SUPPRESSED_WITH_RECORD -> {
                val rule = RuleId(node.get("rule").asText())
                val causeResult = parseSuppressionCause(node.get("cause").asText())
                val evidence = EventRef(
                    stream = node.get("evidenceStream").asText(),
                    seq = node.get("evidenceSeq").asLong(),
                )

                causeResult.map { cause ->
                    SentinelSignal.SuppressedWithRecord(
                        bed = bed,
                        resident = resident,
                        at = at,
                        rulesFingerprint = rulesFingerprint,
                        rule = rule,
                        cause = cause,
                        evidence = evidence,
                    )
                }.getOrThrow()
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

    fun formatDetails(signal: SentinelSignal): String = when (signal) {
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

    private fun parseSeverity(name: String): SerializationResult<Severity> {
        return try {
            SerializationResult.Success(Severity.valueOf(name))
        } catch (_: IllegalArgumentException) {
            SerializationResult.Failure(
                SerializationError.InvalidState(name, Severity.entries.map { it.name }.toSet())
            )
        }
    }

    private fun parseClosureCause(name: String): SerializationResult<ClosureCause> {
        return try {
            SerializationResult.Success(ClosureCause.valueOf(name))
        } catch (_: IllegalArgumentException) {
            SerializationResult.Failure(
                SerializationError.InvalidState(name, ClosureCause.entries.map { it.name }.toSet())
            )
        }
    }

    private fun parseSuppressionCause(name: String): SerializationResult<SuppressionCause> {
        return try {
            SerializationResult.Success(SuppressionCause.valueOf(name))
        } catch (_: IllegalArgumentException) {
            SerializationResult.Failure(
                SerializationError.InvalidState(name, SuppressionCause.entries.map { it.name }.toSet())
            )
        }
    }
}
