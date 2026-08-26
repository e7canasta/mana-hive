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

    override fun encode(obj: SentinelSignal): String {
        val map = buildMap<String, Any> {
            put("type", obj::class.simpleName ?: "Unknown")
            put("at", obj.at.toString())
            put("bed", obj.bed.value)
            put("rulesFingerprint", obj.rulesFingerprint)

            when (obj) {
                is SentinelSignal.EpisodeOpened -> {
                    put("resident", obj.resident?.value ?: "unknown")
                    put("episode", obj.episode.value)
                    put("rule", obj.rule.value)
                    put("trigger", obj.trigger.name)
                    put("severity", obj.severity.name)
                    put("reversible", obj.reversible)
                    put("requiresNvr", obj.requiresNvr)
                    obj.confirmationWindow?.let { put("confirmationWindow", it.toString()) }
                }
                is SentinelSignal.EpisodeClosed -> {
                    put("resident", obj.resident?.value ?: "unknown")
                    put("episode", obj.episode.value)
                    put("cause", obj.cause.name)
                    obj.gapDuration?.let { put("gapDuration", it.toString()) }
                }
                is SentinelSignal.AutoRecovery -> {
                    put("resident", obj.resident?.value ?: "unknown")
                    put("episode", obj.episode.value)
                    put("reversible", obj.reversible)
                    put("requiresConfirmation", obj.requiresConfirmation)
                }
                is SentinelSignal.UmbrellaEvent -> {
                    put("resident", obj.resident?.value ?: "unknown")
                    put("episode", obj.episode.value)
                    put("state", obj.state.name)
                    put("originalSeverity", obj.originalSeverity.name)
                }
                is SentinelSignal.SuppressedWithRecord -> {
                    put("resident", obj.resident?.value ?: "unknown")
                    put("rule", obj.rule.value)
                    put("cause", obj.cause.name)
                    put("evidenceStream", obj.evidence.stream)
                    put("evidenceSeq", obj.evidence.seq)
                }
            }
        }

        return com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
            .writerWithDefaultPrettyPrinter()
            .writeValueAsString(map)
    }

    override fun decode(text: String): SerializationResult<SentinelSignal> = serialization {
        val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
        val node = mapper.readTree(text)
        val type = node.get("type")?.asText()
            ?: throw SerializationException(SerializationError.MissingField("type", "SentinelSignal"))
        val at = Instant.parse(node.get("at").asText())
        val bed = BedId(node.get("bed").asText())
        val rulesFingerprint = node.get("rulesFingerprint").asText()
        val resident = node.get("resident")?.asText()?.takeIf { it != "unknown" }
            ?.let { ResidentId(it) }

        when (type) {
            "EpisodeOpened" -> {
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
            "EpisodeClosed" -> {
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
            "SuppressedWithRecord" -> {
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
            else -> throw SerializationException(SerializationError.InvalidState(type, setOf(
                "EpisodeOpened", "EpisodeClosed", "AutoRecovery",
                "UmbrellaEvent", "SuppressedWithRecord"
            )))
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
    }

    private fun parseSeverity(name: String): SerializationResult<Severity> {
        return try {
            SerializationResult.Success(Severity.valueOf(name))
        } catch (_: IllegalArgumentException) {
            SerializationResult.Failure(
                SerializationError.InvalidState(name, Severity.values().map { it.name }.toSet())
            )
        }
    }

    private fun parseClosureCause(name: String): SerializationResult<ClosureCause> {
        return try {
            SerializationResult.Success(ClosureCause.valueOf(name))
        } catch (_: IllegalArgumentException) {
            SerializationResult.Failure(
                SerializationError.InvalidState(name, ClosureCause.values().map { it.name }.toSet())
            )
        }
    }

    private fun parseSuppressionCause(name: String): SerializationResult<SuppressionCause> {
        return try {
            SerializationResult.Success(SuppressionCause.valueOf(name))
        } catch (_: IllegalArgumentException) {
            SerializationResult.Failure(
                SerializationError.InvalidState(name, SuppressionCause.values().map { it.name }.toSet())
            )
        }
    }
}
