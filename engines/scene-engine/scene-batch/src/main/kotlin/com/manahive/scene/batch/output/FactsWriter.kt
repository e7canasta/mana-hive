package com.manahive.scene.batch.output

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.manahive.contracts.scene.SceneEvent
import com.manahive.contracts.scene.kind
import com.manahive.scene.batch.events.EventOffset
import com.manahive.scene.batch.config.formatOffset
import java.io.File

/**
 * Writes SceneEvent instances to a JSONL file.
 *
 * Each line is a self-contained JSON object:
 * ```json
 * {"t":"2s","event":2,"type":"TransitionDetected","from":"LYING","to":"BED_EDGE","bed":"bed-1"}
 * ```
 */
class FactsWriter(private val outputFile: File) {

    private val mapper: ObjectMapper = ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT)

    init {
        outputFile.parentFile?.mkdirs()
    }

    fun write(fact: SceneEvent, offset: EventOffset? = null, eventLine: Int? = null) {
        val node = factToJson(fact, offset, eventLine)
        outputFile.appendText(mapper.writeValueAsString(node) + "\n")
    }

    fun writeAll(facts: List<SceneEvent>, offset: EventOffset? = null, eventLine: Int? = null) {
        facts.forEach { write(it, offset, eventLine) }
    }

    private fun factToJson(fact: SceneEvent, offset: EventOffset?, eventLine: Int?): Map<String, Any?> {
        val base = linkedMapOf<String, Any?>()

        if (offset != null) base["t"] = formatOffset(offset)
        if (eventLine != null) base["event"] = eventLine

        when (fact) {
            // ── Person State Facts ─────────────────────────
            is SceneEvent.TransitionDetected -> {
                base["type"] = "TransitionDetected"
                base["bed"] = fact.bed.value
                base["night"] = fact.night.value
                base["from"] = fact.from.kind.name
                base["to"] = fact.to.kind.name
            }
            is SceneEvent.DwellWarning -> {
                base["type"] = "DwellWarning"
                base["bed"] = fact.bed.value
                base["night"] = fact.night.value
                base["state"] = fact.state.kind.name
                base["threshold"] = fact.threshold.toString()
                base["since"] = fact.since.toString()
            }
            is SceneEvent.DwellExceeded -> {
                base["type"] = "DwellExceeded"
                base["bed"] = fact.bed.value
                base["night"] = fact.night.value
                base["state"] = fact.state.kind.name
                base["threshold"] = fact.threshold.toString()
                base["since"] = fact.since.toString()
            }

            // ── ComeBack Facts (Inverse Dwell) ─────────
            is SceneEvent.ComeBackWarning -> {
                base["type"] = "ComeBackWarning"
                base["bed"] = fact.bed.value
                base["night"] = fact.night.value
                base["baseline"] = fact.baseline.kind.name
                base["threshold"] = fact.threshold.toString()
                base["since"] = fact.since.toString()
            }
            is SceneEvent.ComeBackExceeded -> {
                base["type"] = "ComeBackExceeded"
                base["bed"] = fact.bed.value
                base["night"] = fact.night.value
                base["baseline"] = fact.baseline.kind.name
                base["threshold"] = fact.threshold.toString()
                base["since"] = fact.since.toString()
            }

            // ── Scene State Facts ──────────────────────────
            is SceneEvent.SceneStateChanged -> {
                base["type"] = "SceneStateChanged"
                base["bed"] = fact.bed.value
                base["night"] = fact.night.value
                base["field"] = fact.field
                base["from"] = fact.from
                base["to"] = fact.to
            }
            is SceneEvent.SceneDwellWarning -> {
                base["type"] = "SceneDwellWarning"
                base["bed"] = fact.bed.value
                base["night"] = fact.night.value
                base["field"] = fact.field
                base["threshold"] = fact.threshold.toString()
                base["since"] = fact.since.toString()
            }
            is SceneEvent.SceneDwellExceeded -> {
                base["type"] = "SceneDwellExceeded"
                base["bed"] = fact.bed.value
                base["night"] = fact.night.value
                base["field"] = fact.field
                base["threshold"] = fact.threshold.toString()
                base["since"] = fact.since.toString()
            }

            // ── Signal Facts ───────────────────────────────
            is SceneEvent.SignalRecovered -> {
                base["type"] = "SignalRecovered"
                base["bed"] = fact.bed.value
                base["night"] = fact.night.value
                base["monitor"] = fact.monitor.value
            }
            is SceneEvent.SignalLost -> {
                base["type"] = "SignalLost"
                base["bed"] = fact.bed.value
                base["night"] = fact.night.value
                base["monitor"] = fact.monitor.value
                base["lastHeartbeat"] = fact.lastHeartbeat.toString()
            }
            is SceneEvent.StaffPresenceDetected -> {
                base["type"] = "StaffPresenceDetected"
                base["bed"] = fact.bed.value
                base["night"] = fact.night.value
                base["staff"] = fact.staff?.value
            }
            is SceneEvent.StaffLeftDetected -> {
                base["type"] = "StaffLeftDetected"
                base["bed"] = fact.bed.value
                base["night"] = fact.night.value
            }

            // ── Lifecycle Facts ────────────────────────────
            is SceneEvent.NightOpened -> {
                base["type"] = "NightOpened"
                base["bed"] = fact.bed.value
                base["night"] = fact.night.value
                base["occupant"] = fact.occupant?.value
            }
            is SceneEvent.NightClosed -> {
                base["type"] = "NightClosed"
                base["bed"] = fact.bed.value
                base["night"] = fact.night.value
            }
        }

        return base
    }
}
