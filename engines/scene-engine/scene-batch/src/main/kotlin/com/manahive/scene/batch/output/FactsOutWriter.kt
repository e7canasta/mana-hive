package com.manahive.scene.batch.output

import com.manahive.contracts.scene.SceneEvent
import com.manahive.contracts.scene.kind
import com.manahive.scene.batch.config.formatOffset
import com.manahive.scene.batch.events.EventOffset
import java.io.File

/**
 * Writes facts in the same format as events.dat for easy diff.
 *
 * Format: `t=<offset>  <TYPE> <details>          # ← evento <n>`
 *
 * Example:
 * ```
 * t=2s      TRANSITION LYING → BED_EDGE           # ← evento 6
 * t=4m      DWELL_WARNING STANDING PT4M           # ← evento 8
 * t=10m     SCENE_DWELL_EXCEEDED staff PT10M      # ← evento 12
 * ```
 */
class FactsOutWriter(private val outputFile: File) {

    init {
        outputFile.parentFile?.mkdirs()
    }

    fun write(offset: EventOffset, fact: SceneEvent, eventLine: Int) {
        outputFile.appendText(formatFact(offset, fact, eventLine) + "\n")
    }

    private fun formatFact(offset: EventOffset, fact: SceneEvent, eventLine: Int): String {
        val t = "t=${formatOffset(offset)}"
        val padding = " ".repeat(maxOf(1, 10 - t.length))
        val body = formatBody(fact)
        return "$t${padding}$body".padEnd(60) + "# ← evento $eventLine"
    }

    /**
     * Formats a SceneEvent as a human-readable string.
     *
     * The `when` is exhaustive over all SceneEvent subtypes — adding a new
     * subtype to SceneEvent will cause a compile error here (Fowler: "Fix Switch Statements").
     */
    private fun formatBody(fact: SceneEvent): String = when (fact) {
        // ── Person State Facts ─────────────────────────────
        is SceneEvent.TransitionDetected ->
            "TRANSITION ${fact.from.kind.name} → ${fact.to.kind.name}"
        is SceneEvent.DwellWarning ->
            "DWELL_WARNING ${fact.state.kind.name} ${fact.threshold}"
        is SceneEvent.DwellExceeded ->
            "DWELL_EXCEEDED ${fact.state.kind.name} ${fact.threshold}"

        // ── ComeBack Facts (Inverse Dwell) ─────────────
        is SceneEvent.ComeBackWarning ->
            "COMEBACK_WARNING ${fact.baseline.kind.name} ${fact.threshold}"
        is SceneEvent.ComeBackExceeded ->
            "COMEBACK_EXCEEDED ${fact.baseline.kind.name} ${fact.threshold}"

        // ── Scene State Facts ──────────────────────────────
        is SceneEvent.SceneStateChanged ->
            "SCENE_CHANGED ${fact.field} ${fact.from} → ${fact.to}"
        is SceneEvent.SceneDwellWarning ->
            "SCENE_DWELL_WARNING ${fact.field} ${fact.threshold}"
        is SceneEvent.SceneDwellExceeded ->
            "SCENE_DWELL_EXCEEDED ${fact.field} ${fact.threshold}"

        // ── Signal Facts ───────────────────────────────────
        is SceneEvent.SignalRecovered ->
            "SIGNAL_RECOVERED monitor=${fact.monitor.value}"
        is SceneEvent.SignalLost ->
            "SIGNAL_LOST monitor=${fact.monitor.value}"
        is SceneEvent.StaffPresenceDetected ->
            "STAFF_PRESENCE staff=${fact.staff?.value}"
        is SceneEvent.StaffLeftDetected ->
            "STAFF_LEFT"

        // ── Lifecycle Facts ────────────────────────────────
        is SceneEvent.NightOpened ->
            "NIGHT_OPENED occupant=${fact.occupant?.value}"
        is SceneEvent.NightClosed ->
            "NIGHT_CLOSED"
    }
}
