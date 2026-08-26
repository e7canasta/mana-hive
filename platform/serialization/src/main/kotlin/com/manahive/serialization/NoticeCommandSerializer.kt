package com.manahive.serialization

import com.manahive.contracts.common.Channel
import com.manahive.contracts.policy.Severity
import com.manahive.contracts.scene.StateKind
import com.manahive.contracts.sentinel.SentinelSignal
import com.manahive.harbor.*
import com.manahive.kernel.*
import java.time.Instant

/**
 * NoticeCommand serializer.
 *
 * Bidirectional: NoticeCommand ↔ String (any format).
 */
object NoticeCommandSerializer {

    fun toJson(command: NoticeCommand): String {
        val map = mutableMapOf<String, Any>(
            "type" to (command::class.simpleName ?: "Unknown"),
        )

        when (command) {
            is NoticeCommand.Create -> {
                map["episode"] = command.signal.episode.value
                map["rule"] = command.signal.rule.value
                map["severity"] = command.signal.severity.name
            }
            is NoticeCommand.Dispatch -> {
                map["id"] = command.id.value
                map["channels"] = command.channels.map { it.name }
            }
            is NoticeCommand.MarkSeen -> {
                map["id"] = command.id.value
                map["by"] = command.by.value
                map["at"] = command.at.toString()
            }
            is NoticeCommand.Acknowledge -> {
                map["id"] = command.id.value
                map["by"] = command.by.value
                map["at"] = command.at.toString()
            }
            is NoticeCommand.Escalate -> {
                map["id"] = command.id.value
                map["at"] = command.at.toString()
            }
            is NoticeCommand.Cancel -> {
                map["id"] = command.id.value
                map["at"] = command.at.toString()
                map["reason"] = command.reason
            }
            is NoticeCommand.Resolve -> {
                map["id"] = command.id.value
                map["at"] = command.at.toString()
                map["resolution"] = command.resolution.name
            }
        }

        return com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
            .writerWithDefaultPrettyPrinter()
            .writeValueAsString(map)
    }

    fun fromJson(json: String): NoticeCommand {
        val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
        val node = mapper.readTree(json)
        val type = node.get("type").asText()

        return when (type) {
            "Create" -> {
                val episode = EpisodeId(node.get("episode").asText())
                val rule = RuleId(node.get("rule").asText())
                val severity = Severity.valueOf(node.get("severity").asText())
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
            else -> throw IllegalArgumentException("Unknown NoticeCommand type: $type")
        }
    }

    fun toText(command: NoticeCommand): String {
        val type = command::class.simpleName ?: "Unknown"
        val details = formatDetails(command)
        return "$type $details"
    }

    private fun formatDetails(command: NoticeCommand): String = when (command) {
        is NoticeCommand.Create -> "episode=${command.signal.episode.value}"
        is NoticeCommand.Dispatch -> "channels=${command.channels}"
        is NoticeCommand.MarkSeen -> "by=${command.by.value}"
        is NoticeCommand.Acknowledge -> "by=${command.by.value}"
        is NoticeCommand.Escalate -> ""
        is NoticeCommand.Cancel -> "reason=${command.reason}"
        is NoticeCommand.Resolve -> "resolution=${command.resolution}"
    }
}
