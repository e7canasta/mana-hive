package com.manahive.serialization

import com.manahive.contracts.scene.SceneEvent
import com.manahive.contracts.scene.PersonState
import com.manahive.serialization.formats.TextEvent
import java.time.Duration
import java.time.Instant

/**
 * SceneEvent serializer.
 *
 * Bidirectional: SceneEvent ↔ String (any format).
 *
 * Usage:
 * ```kotlin
 * // Serialize to JSON
 * val json = SceneEventSerializer.toJson(event)
 *
 * // Serialize to text format
 * val text = SceneEventSerializer.toText(event, startTime)
 *
 * // Deserialize from JSON
 * val event = SceneEventSerializer.fromJson(json)
 *
 * // Deserialize from text
 * val event = SceneEventParser.fromText("t=1h15m  TRANSITION Lying → SittingInBed")
 * ```
 */
object SceneEventSerializer {

    fun toJson(event: SceneEvent): String {
        val map = mutableMapOf<String, Any>(
            "type" to (event::class.simpleName ?: "Unknown"),
            "at" to event.at.toString(),
            "bed" to event.bed.value,
            "night" to event.night.value,
        )

        when (event) {
            is SceneEvent.TransitionDetected -> {
                map["from"] = event.from::class.simpleName!!
                map["to"] = event.to::class.simpleName!!
            }
            is SceneEvent.DwellWarning -> {
                map["state"] = event.state::class.simpleName!!
                map["threshold"] = event.threshold.toString()
                map["since"] = event.since.toString()
            }
            is SceneEvent.DwellExceeded -> {
                map["state"] = event.state::class.simpleName!!
                map["threshold"] = event.threshold.toString()
                map["since"] = event.since.toString()
            }
            is SceneEvent.ComeBackWarning -> {
                map["baseline"] = event.baseline::class.simpleName!!
                map["threshold"] = event.threshold.toString()
                map["since"] = event.since.toString()
            }
            is SceneEvent.ComeBackExceeded -> {
                map["baseline"] = event.baseline::class.simpleName!!
                map["threshold"] = event.threshold.toString()
                map["since"] = event.since.toString()
            }
            is SceneEvent.SignalLost -> {
                map["monitor"] = event.monitor.value
                map["lastHeartbeat"] = event.lastHeartbeat.toString()
            }
            is SceneEvent.SignalRecovered -> {
                map["monitor"] = event.monitor.value
            }
            is SceneEvent.StaffPresenceDetected -> {
                map["staff"] = event.staff?.value ?: "unknown"
            }
            is SceneEvent.NightOpened -> {
                map["occupant"] = event.occupant?.value ?: "unknown"
                map["initialState"] = event.initialState::class.simpleName!!
                map["stateSince"] = event.stateSince.toString()
            }
            is SceneEvent.NightClosed -> {
                map["transitions"] = event.summary.transitions
                map["minutesUnknown"] = event.summary.minutesUnknown
                map["episodes"] = event.summary.episodes
            }
            is SceneEvent.StaffLeftDetected -> {
                // No additional fields
            }
            is SceneEvent.SceneStateChanged -> {
                map["field"] = event.field
                map["from"] = event.from
                map["to"] = event.to
            }
            is SceneEvent.SceneDwellWarning -> {
                map["field"] = event.field
                map["threshold"] = event.threshold.toString()
                map["since"] = event.since.toString()
            }
            is SceneEvent.SceneDwellExceeded -> {
                map["field"] = event.field
                map["threshold"] = event.threshold.toString()
                map["since"] = event.since.toString()
            }
        }

        return com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
            .writerWithDefaultPrettyPrinter()
            .writeValueAsString(map)
    }

    fun fromJson(json: String): SceneEvent {
        val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
        val node = mapper.readTree(json)
        val type = node.get("type").asText()
        val at = Instant.parse(node.get("at").asText())
        val bed = com.manahive.kernel.BedId(node.get("bed").asText())
        val night = com.manahive.kernel.NightId(node.get("night").asText())

        return when (type) {
            "TransitionDetected" -> {
                val from = parsePersonState(node.get("from").asText())
                val to = parsePersonState(node.get("to").asText())
                SceneEvent.TransitionDetected(bed, night, at, from, to)
            }
            "DwellWarning" -> {
                val state = parsePersonState(node.get("state").asText())
                val threshold = Duration.parse(node.get("threshold").asText())
                val since = Instant.parse(node.get("since").asText())
                SceneEvent.DwellWarning(bed, night, at, state, threshold, since)
            }
            "DwellExceeded" -> {
                val state = parsePersonState(node.get("state").asText())
                val threshold = Duration.parse(node.get("threshold").asText())
                val since = Instant.parse(node.get("since").asText())
                SceneEvent.DwellExceeded(bed, night, at, state, threshold, since)
            }
            "ComeBackWarning" -> {
                val baseline = parsePersonState(node.get("baseline").asText())
                val threshold = Duration.parse(node.get("threshold").asText())
                val since = Instant.parse(node.get("since").asText())
                SceneEvent.ComeBackWarning(bed, night, at, baseline, threshold, since)
            }
            "ComeBackExceeded" -> {
                val baseline = parsePersonState(node.get("baseline").asText())
                val threshold = Duration.parse(node.get("threshold").asText())
                val since = Instant.parse(node.get("since").asText())
                SceneEvent.ComeBackExceeded(bed, night, at, baseline, threshold, since)
            }
            "SignalLost" -> {
                val monitor = com.manahive.kernel.MonitorId(node.get("monitor").asText())
                val lastHeartbeat = Instant.parse(node.get("lastHeartbeat").asText())
                SceneEvent.SignalLost(bed, night, at, monitor, lastHeartbeat)
            }
            "SignalRecovered" -> {
                val monitor = com.manahive.kernel.MonitorId(node.get("monitor").asText())
                SceneEvent.SignalRecovered(bed, night, at, monitor)
            }
            "StaffPresenceDetected" -> {
                val staff = node.get("staff")?.asText()?.let { com.manahive.kernel.StaffId(it) }
                SceneEvent.StaffPresenceDetected(bed, night, at, staff)
            }
            "NightOpened" -> {
                val occupant = node.get("occupant")?.asText()?.takeIf { it != "unknown" }?.let { com.manahive.kernel.ResidentId(it) }
                val initialState = parsePersonState(node.get("initialState").asText())
                val stateSince = Instant.parse(node.get("stateSince").asText())
                SceneEvent.NightOpened(bed, night, at, occupant, initialState, stateSince)
            }
            "NightClosed" -> {
                val transitions = node.get("transitions").asInt()
                val minutesUnknown = node.get("minutesUnknown").asLong()
                val episodes = node.get("episodes").asInt()
                val summary = com.manahive.contracts.scene.NightSummary(transitions, minutesUnknown, episodes)
                SceneEvent.NightClosed(bed, night, at, summary)
            }
            "StaffLeftDetected" -> {
                SceneEvent.StaffLeftDetected(bed, night, at)
            }
            else -> throw IllegalArgumentException("Unknown SceneEvent type: $type")
        }
    }

    fun toText(event: SceneEvent, startTime: Instant): TextEvent {
        val offset = Duration.between(startTime, event.at)
        val type = event::class.simpleName ?: "Unknown"
        val details = formatDetails(event)
        return TextEvent(offset, type, details)
    }

    fun fromText(textEvent: TextEvent, startTime: Instant, bed: com.manahive.kernel.BedId, night: com.manahive.kernel.NightId): SceneEvent {
        val at = startTime.plus(textEvent.offset)
        val type = textEvent.type

        return when (type) {
            "TransitionDetected" -> {
                val parts = textEvent.details.split(" → ")
                val from = parsePersonState(parts[0].trim())
                val to = parsePersonState(parts[1].trim())
                SceneEvent.TransitionDetected(bed, night, at, from, to)
            }
            "DwellWarning" -> {
                val parts = textEvent.details.split(" warning=")
                val state = parsePersonState(parts[0].trim())
                val threshold = Duration.parse(parts[1].trim())
                val since = at.minus(threshold)
                SceneEvent.DwellWarning(bed, night, at, state, threshold, since)
            }
            "DwellExceeded" -> {
                val parts = textEvent.details.split(" exceeded=")
                val state = parsePersonState(parts[0].trim())
                val threshold = Duration.parse(parts[1].trim())
                val since = at.minus(threshold)
                SceneEvent.DwellExceeded(bed, night, at, state, threshold, since)
            }
            "SignalLost" -> {
                val monitor = com.manahive.kernel.MonitorId(textEvent.details.replace("monitor=", "").trim())
                val lastHeartbeat = at.minusSeconds(90) // Default to 90 seconds before
                SceneEvent.SignalLost(bed, night, at, monitor, lastHeartbeat)
            }
            "SignalRecovered" -> {
                val monitor = com.manahive.kernel.MonitorId(textEvent.details.replace("monitor=", "").trim())
                SceneEvent.SignalRecovered(bed, night, at, monitor)
            }
            "StaffPresenceDetected" -> {
                val staff = textEvent.details.replace("staff=", "").trim()
                    .takeIf { it != "unknown" }
                    ?.let { com.manahive.kernel.StaffId(it) }
                SceneEvent.StaffPresenceDetected(bed, night, at, staff)
            }
            "NightOpened" -> {
                val occupant = textEvent.details.replace("occupant=", "").trim()
                    .takeIf { it != "unknown" }
                    ?.let { com.manahive.kernel.ResidentId(it) }
                val initialState = PersonState.Lying // Default
                val stateSince = at
                SceneEvent.NightOpened(bed, night, at, occupant, initialState, stateSince)
            }
            "NightClosed" -> {
                val summary = com.manahive.contracts.scene.NightSummary(0, 0, 0)
                SceneEvent.NightClosed(bed, night, at, summary)
            }
            "StaffLeftDetected" -> {
                SceneEvent.StaffLeftDetected(bed, night, at)
            }
            else -> throw IllegalArgumentException("Unknown SceneEvent type: $type")
        }
    }

    private fun formatDetails(event: SceneEvent): String = when (event) {
        is SceneEvent.TransitionDetected -> "${event.from::class.simpleName} → ${event.to::class.simpleName}"
        is SceneEvent.DwellWarning -> "${event.state::class.simpleName} warning=${event.threshold}"
        is SceneEvent.DwellExceeded -> "${event.state::class.simpleName} exceeded=${event.threshold}"
        is SceneEvent.ComeBackWarning -> "warning=${event.threshold}"
        is SceneEvent.ComeBackExceeded -> "exceeded=${event.threshold}"
        is SceneEvent.SignalLost -> "monitor=${event.monitor.value}"
        is SceneEvent.SignalRecovered -> "monitor=${event.monitor.value}"
        is SceneEvent.StaffPresenceDetected -> "staff=${event.staff?.value ?: "unknown"}"
        is SceneEvent.NightOpened -> "occupant=${event.occupant?.value ?: "unknown"}"
        is SceneEvent.NightClosed -> ""
        is SceneEvent.StaffLeftDetected -> ""
        is SceneEvent.SceneStateChanged -> "${event.field} ${event.from} → ${event.to}"
        is SceneEvent.SceneDwellWarning -> "${event.field} warning=${event.threshold}"
        is SceneEvent.SceneDwellExceeded -> "${event.field} exceeded=${event.threshold}"
    }

    private fun parsePersonState(name: String): PersonState = when (name) {
        "Lying" -> PersonState.Lying
        "SittingInBed" -> PersonState.SittingInBed
        "AttemptingExit" -> PersonState.AttemptingExit
        "BedEdge" -> PersonState.BedEdge
        "Standing" -> PersonState.Standing
        "InBathroom" -> PersonState.InBathroom
        "InRoom" -> PersonState.InRoom
        "Absent" -> PersonState.Absent
        else -> throw IllegalArgumentException("Unknown PersonState: $name")
    }
}
