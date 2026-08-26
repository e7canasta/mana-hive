package com.manahive.serialization

import com.manahive.contracts.scene.SceneEvent
import com.manahive.contracts.scene.PersonState
import com.manahive.contracts.scene.NightSummary
import com.manahive.kernel.*
import java.time.Duration
import java.time.Instant

/**
 * SceneEvent codec: SceneEvent ↔ JSON/Text.
 *
 * Usage:
 * ```kotlin
 * // Serialize to JSON
 * val json = SceneEventCodec.encode(event)
 *
 * // Deserialize from JSON
 * val result = SceneEventCodec.decode(json)
 * result.onSuccess { event -> ... }
 * result.onError { error -> ... }
 *
 * // Or use extension functions
 * val json = event.toJson()
 * val event = json.toSceneEvent()
 * ```
 */
object SceneEventCodec : Codec<SceneEvent> {

    override fun encode(obj: SceneEvent): String {
        val map = buildMap<String, Any> {
            put("type", obj::class.simpleName ?: "Unknown")
            put("at", obj.at.toString())
            put("bed", obj.bed.value)
            put("night", obj.night.value)

            when (obj) {
                is SceneEvent.TransitionDetected -> {
                    put("from", obj.from::class.simpleName ?: "Unknown")
                    put("to", obj.to::class.simpleName ?: "Unknown")
                }
                is SceneEvent.DwellWarning -> {
                    put("state", obj.state::class.simpleName ?: "Unknown")
                    put("threshold", obj.threshold.toString())
                    put("since", obj.since.toString())
                }
                is SceneEvent.DwellExceeded -> {
                    put("state", obj.state::class.simpleName ?: "Unknown")
                    put("threshold", obj.threshold.toString())
                    put("since", obj.since.toString())
                }
                is SceneEvent.ComeBackWarning -> {
                    put("baseline", obj.baseline::class.simpleName ?: "Unknown")
                    put("threshold", obj.threshold.toString())
                    put("since", obj.since.toString())
                }
                is SceneEvent.ComeBackExceeded -> {
                    put("baseline", obj.baseline::class.simpleName ?: "Unknown")
                    put("threshold", obj.threshold.toString())
                    put("since", obj.since.toString())
                }
                is SceneEvent.SignalLost -> {
                    put("monitor", obj.monitor.value)
                    put("lastHeartbeat", obj.lastHeartbeat.toString())
                }
                is SceneEvent.SignalRecovered -> {
                    put("monitor", obj.monitor.value)
                }
                is SceneEvent.StaffPresenceDetected -> {
                    put("staff", obj.staff?.value ?: "unknown")
                }
                is SceneEvent.NightOpened -> {
                    put("occupant", obj.occupant?.value ?: "unknown")
                    put("initialState", obj.initialState::class.simpleName ?: "Unknown")
                    put("stateSince", obj.stateSince.toString())
                }
                is SceneEvent.NightClosed -> {
                    put("transitions", obj.summary.transitions)
                    put("minutesUnknown", obj.summary.minutesUnknown)
                    put("episodes", obj.summary.episodes)
                }
                is SceneEvent.StaffLeftDetected -> {
                    // No additional fields
                }
                is SceneEvent.SceneStateChanged -> {
                    put("field", obj.field)
                    put("from", obj.from)
                    put("to", obj.to)
                }
                is SceneEvent.SceneDwellWarning -> {
                    put("field", obj.field)
                    put("threshold", obj.threshold.toString())
                    put("since", obj.since.toString())
                }
                is SceneEvent.SceneDwellExceeded -> {
                    put("field", obj.field)
                    put("threshold", obj.threshold.toString())
                    put("since", obj.since.toString())
                }
            }
        }

        return com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
            .writerWithDefaultPrettyPrinter()
            .writeValueAsString(map)
    }

    override fun decode(text: String): SerializationResult<SceneEvent> = serialization {
        val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
        val node = mapper.readTree(text)
        val type = node.get("type")?.asText()
            ?: throw SerializationException(SerializationError.MissingField("type", "SceneEvent"))
        val at = Instant.parse(node.get("at").asText())
        val bed = BedId(node.get("bed").asText())
        val night = NightId(node.get("night").asText())

        when (type) {
            "TransitionDetected" -> {
                val fromResult = StateKindInput.parsePersonState(node.get("from").asText())
                val toResult = StateKindInput.parsePersonState(node.get("to").asText())
                fromResult.flatMap { from ->
                    toResult.map { to ->
                        SceneEvent.TransitionDetected(bed, night, at, from, to)
                    }
                }.getOrThrow()
            }
            "DwellWarning" -> {
                val stateResult = StateKindInput.parsePersonState(node.get("state").asText())
                val threshold = Duration.parse(node.get("threshold").asText())
                val since = Instant.parse(node.get("since").asText())
                stateResult.map { state ->
                    SceneEvent.DwellWarning(bed, night, at, state, threshold, since)
                }.getOrThrow()
            }
            "DwellExceeded" -> {
                val stateResult = StateKindInput.parsePersonState(node.get("state").asText())
                val threshold = Duration.parse(node.get("threshold").asText())
                val since = Instant.parse(node.get("since").asText())
                stateResult.map { state ->
                    SceneEvent.DwellExceeded(bed, night, at, state, threshold, since)
                }.getOrThrow()
            }
            "ComeBackWarning" -> {
                val baselineResult = StateKindInput.parsePersonState(node.get("baseline").asText())
                val threshold = Duration.parse(node.get("threshold").asText())
                val since = Instant.parse(node.get("since").asText())
                baselineResult.map { baseline ->
                    SceneEvent.ComeBackWarning(bed, night, at, baseline, threshold, since)
                }.getOrThrow()
            }
            "ComeBackExceeded" -> {
                val baselineResult = StateKindInput.parsePersonState(node.get("baseline").asText())
                val threshold = Duration.parse(node.get("threshold").asText())
                val since = Instant.parse(node.get("since").asText())
                baselineResult.map { baseline ->
                    SceneEvent.ComeBackExceeded(bed, night, at, baseline, threshold, since)
                }.getOrThrow()
            }
            "SignalLost" -> {
                val monitor = MonitorId(node.get("monitor").asText())
                val lastHeartbeat = Instant.parse(node.get("lastHeartbeat").asText())
                SceneEvent.SignalLost(bed, night, at, monitor, lastHeartbeat)
            }
            "SignalRecovered" -> {
                val monitor = MonitorId(node.get("monitor").asText())
                SceneEvent.SignalRecovered(bed, night, at, monitor)
            }
            "StaffPresenceDetected" -> {
                val staff = node.get("staff")?.asText()?.takeIf { it != "unknown" }
                    ?.let { StaffId(it) }
                SceneEvent.StaffPresenceDetected(bed, night, at, staff)
            }
            "NightOpened" -> {
                val occupant = node.get("occupant")?.asText()?.takeIf { it != "unknown" }
                    ?.let { ResidentId(it) }
                val initialStateResult = StateKindInput.parsePersonState(node.get("initialState").asText())
                val stateSince = Instant.parse(node.get("stateSince").asText())
                initialStateResult.map { initialState ->
                    SceneEvent.NightOpened(bed, night, at, occupant, initialState, stateSince)
                }.getOrThrow()
            }
            "NightClosed" -> {
                val transitions = node.get("transitions").asInt()
                val minutesUnknown = node.get("minutesUnknown").asLong()
                val episodes = node.get("episodes").asInt()
                val summary = NightSummary(transitions, minutesUnknown, episodes)
                SceneEvent.NightClosed(bed, night, at, summary)
            }
            "StaffLeftDetected" -> {
                SceneEvent.StaffLeftDetected(bed, night, at)
            }
            else -> throw SerializationException(SerializationError.InvalidState(type, setOf(
                "TransitionDetected", "DwellWarning", "DwellExceeded",
                "ComeBackWarning", "ComeBackExceeded", "SignalLost", "SignalRecovered",
                "StaffPresenceDetected", "NightOpened", "NightClosed", "StaffLeftDetected"
            )))
        }
    }

    fun formatDetails(event: SceneEvent): String = when (event) {
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
}
