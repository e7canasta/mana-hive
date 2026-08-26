package com.manahive.serialization

import com.manahive.contracts.common.Channel
import com.manahive.contracts.policy.Severity
import com.manahive.contracts.scene.StateKind
import com.manahive.contracts.sentinel.SentinelSignal
import com.manahive.harbor.*
import com.manahive.kernel.*
import java.time.Instant

/**
 * NoticeCommand codec: NoticeCommand ↔ JSON/Text.
 *
 * Usage:
 * ```kotlin
 * val json = command.toJson()
 * val result = json.toNoticeCommand()
 * result.onSuccess { command -> ... }
 * ```
 */
object NoticeCommandCodec : Codec<NoticeCommand> {

    override fun encode(obj: NoticeCommand): String {
        val map = buildMap<String, Any> {
            put("type", obj::class.simpleName ?: "Unknown")

            when (obj) {
                is NoticeCommand.Create -> {
                    put("episode", obj.signal.episode.value)
                    put("rule", obj.signal.rule.value)
                    put("severity", obj.signal.severity.name)
                }
                is NoticeCommand.Dispatch -> {
                    put("id", obj.id.value)
                    put("channels", obj.channels.map { it.name })
                }
                is NoticeCommand.MarkSeen -> {
                    put("id", obj.id.value)
                    put("by", obj.by.value)
                    put("at", obj.at.toString())
                }
                is NoticeCommand.Acknowledge -> {
                    put("id", obj.id.value)
                    put("by", obj.by.value)
                    put("at", obj.at.toString())
                }
                is NoticeCommand.Escalate -> {
                    put("id", obj.id.value)
                    put("at", obj.at.toString())
                }
                is NoticeCommand.Cancel -> {
                    put("id", obj.id.value)
                    put("at", obj.at.toString())
                    put("reason", obj.reason)
                }
                is NoticeCommand.Resolve -> {
                    put("id", obj.id.value)
                    put("at", obj.at.toString())
                    put("resolution", obj.resolution.name)
                }
            }
        }

        return com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
            .writerWithDefaultPrettyPrinter()
            .writeValueAsString(map)
    }

    override fun decode(text: String): SerializationResult<NoticeCommand> = serialization {
        val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
        val node = mapper.readTree(text)
        val type = node.get("type")?.asText()
            ?: throw SerializationException(SerializationError.MissingField("type", "NoticeCommand"))

        when (type) {
            "Create" -> {
                val episode = EpisodeId(node.get("episode").asText())
                val rule = RuleId(node.get("rule").asText())
                val severityResult = parseSeverity(node.get("severity").asText())

                severityResult.map { severity ->
                    val signal = SentinelSignal.EpisodeOpened(
                        bed = BedId(""),
                        resident = null,
                        at = Instant.now(),
                        rulesFingerprint = "",
                        episode = episode,
                        rule = rule,
                        trigger = StateKind.SITTING_IN_BED,
                        severity = severity,
                        reversible = true,
                        requiresNvr = false,
                        confirmationWindow = null,
                    )
                    NoticeCommand.Create(signal)
                }.getOrThrow()
            }
            "Dispatch" -> {
                val id = NoticeId(node.get("id").asText())
                val channels = node.get("channels").map { Channel.valueOf(it.asText()) }.toSet()
                NoticeCommand.Dispatch(id, channels)
            }
            "MarkSeen" -> {
                val id = NoticeId(node.get("id").asText())
                val by = StaffId(node.get("by").asText())
                val at = Instant.parse(node.get("at").asText())
                NoticeCommand.MarkSeen(id, by, at)
            }
            "Acknowledge" -> {
                val id = NoticeId(node.get("id").asText())
                val by = StaffId(node.get("by").asText())
                val at = Instant.parse(node.get("at").asText())
                NoticeCommand.Acknowledge(id, by, at)
            }
            "Escalate" -> {
                val id = NoticeId(node.get("id").asText())
                val at = Instant.parse(node.get("at").asText())
                NoticeCommand.Escalate(id, at)
            }
            "Cancel" -> {
                val id = NoticeId(node.get("id").asText())
                val at = Instant.parse(node.get("at").asText())
                val reason = node.get("reason").asText()
                NoticeCommand.Cancel(id, at, reason)
            }
            "Resolve" -> {
                val id = NoticeId(node.get("id").asText())
                val at = Instant.parse(node.get("at").asText())
                val resolution = Resolution.valueOf(node.get("resolution").asText())
                NoticeCommand.Resolve(id, resolution, at)
            }
            else -> throw SerializationException(SerializationError.InvalidState(type, setOf(
                "Create", "Dispatch", "MarkSeen", "Acknowledge",
                "Escalate", "Cancel", "Resolve"
            )))
        }
    }

    fun formatCommand(command: NoticeCommand): String = when (command) {
        is NoticeCommand.Create -> "CREATE episode=${command.signal.episode.value}"
        is NoticeCommand.Dispatch -> "DISPATCH channels=${command.channels}"
        is NoticeCommand.MarkSeen -> "MARK_SEEN by=${command.by.value}"
        is NoticeCommand.Acknowledge -> "ACKNOWLEDGE by=${command.by.value}"
        is NoticeCommand.Escalate -> "ESCALATE"
        is NoticeCommand.Cancel -> "CANCEL reason=${command.reason}"
        is NoticeCommand.Resolve -> "RESOLVE resolution=${command.resolution}"
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
}
