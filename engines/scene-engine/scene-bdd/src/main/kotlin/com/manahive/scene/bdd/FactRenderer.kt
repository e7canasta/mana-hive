package com.manahive.scene.bdd

import com.manahive.contracts.scene.PersonState
import com.manahive.contracts.scene.SceneEvent
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class TimelineBuilder {
    private var facts: List<SceneEvent> = emptyList()
    private var start: Instant? = null
    private var showSignalLost: Boolean = true
    private var showTimeOffsets: Boolean = true

    fun facts(value: List<SceneEvent>) { facts = value }
    fun start(value: Instant) { start = value }
    fun showSignalLost(value: Boolean) { showSignalLost = value }
    fun showTimeOffsets(value: Boolean) { showTimeOffsets = value }

    fun render(): String {
        val baseTime = start ?: facts.firstOrNull()?.let { extractTime(it) } ?: return ""
        val filtered = if (showSignalLost) facts
            else facts.filter { it !is SceneEvent.SignalLost && it !is SceneEvent.SignalRecovered }

        val sb = StringBuilder()
        for (fact in filtered) {
            val time = extractTime(fact)
            val offset = Duration.between(baseTime, time)
            val timeStr = formatTime(time)
            val offsetStr = if (showTimeOffsets && offset.seconds > 0) "  ${formatOffset(offset)}" else ""

            sb.appendLine("$timeStr${offsetStr}  ${renderFact(fact)}")
        }
        return sb.toString().trimEnd()
    }

    fun renderSummary(): String {
        val transitions = facts.filterIsInstance<SceneEvent.TransitionDetected>()
        val comeBacks = facts.filterIsInstance<SceneEvent.ComeBackExceeded>()
        val comeBackWarnings = facts.filterIsInstance<SceneEvent.ComeBackWarning>()
        val dwellExceeded = facts.filterIsInstance<SceneEvent.DwellExceeded>()
        val dwellWarnings = facts.filterIsInstance<SceneEvent.DwellWarning>()
        val signalLost = facts.filterIsInstance<SceneEvent.SignalLost>()

        return buildString {
            appendLine("── Summary ──")
            appendLine("  Facts: ${facts.size}")
            appendLine("  Transitions: ${transitions.size}")
            if (dwellExceeded.isNotEmpty()) appendLine("  DwellExceeded: ${dwellExceeded.size}")
            if (dwellWarnings.isNotEmpty()) appendLine("  DwellWarning: ${dwellWarnings.size}")
            if (comeBacks.isNotEmpty()) appendLine("  ComeBackExceeded: ${comeBacks.size}")
            if (comeBackWarnings.isNotEmpty()) appendLine("  ComeBackWarning: ${comeBackWarnings.size}")
            if (signalLost.isNotEmpty()) appendLine("  SignalLost: ${signalLost.size}")
        }.trimEnd()
    }

    private fun renderFact(fact: SceneEvent): String = when (fact) {
        is SceneEvent.TransitionDetected -> "${renderState(fact.from)} → ${renderState(fact.to)}"
        is SceneEvent.ComeBackWarning -> "⚠️  ComeBackWarning(${renderState(fact.baseline)})   ${formatDuration(fact.threshold)} fuera"
        is SceneEvent.ComeBackExceeded -> "💥 ComeBackExceeded(${renderState(fact.baseline)})   ${formatDuration(fact.threshold)} fuera"
        is SceneEvent.DwellWarning -> "⚠️  DwellWarning(${renderState(fact.state)})   ${formatDuration(fact.threshold)}"
        is SceneEvent.DwellExceeded -> "💥 DwellExceeded(${renderState(fact.state)})   ${formatDuration(fact.threshold)}"
        is SceneEvent.SignalLost -> "⚡ SignalLost"
        is SceneEvent.SignalRecovered -> "✅ SignalRecovered"
        is SceneEvent.SceneStateChanged -> "🔄 ${fact.field}: ${fact.from} → ${fact.to}"
        is SceneEvent.NightOpened -> "🌙 NightOpened"
        is SceneEvent.NightClosed -> "🌅 NightClosed"
        else -> fact::class.simpleName ?: "Unknown"
    }

    private fun renderState(state: PersonState): String = when (state) {
        is PersonState.Unknown -> "Unknown"
        is PersonState.Lying -> "Lying"
        is PersonState.SittingInBed -> "SittingInBed"
        is PersonState.Standing -> "Standing"
        is PersonState.OnFloor -> "OnFloor"
        is PersonState.InBathroom -> "InBathroom"
        is PersonState.InRoom -> "InRoom"
        is PersonState.AttemptingExit -> "AttemptingExit"
        is PersonState.BedEdge -> "BedEdge"
        is PersonState.InHallway -> "InHallway"
        is PersonState.Outdoor -> "Outdoor"
        is PersonState.Absent -> "Absent"
        is PersonState.InChair -> "InChair"
        is PersonState.InWheelchair -> "InWheelchair"
    }

    private fun extractTime(fact: SceneEvent): Instant = when (fact) {
        is SceneEvent.TransitionDetected -> fact.at
        is SceneEvent.ComeBackWarning -> fact.at
        is SceneEvent.ComeBackExceeded -> fact.at
        is SceneEvent.DwellWarning -> fact.at
        is SceneEvent.DwellExceeded -> fact.at
        is SceneEvent.SignalLost -> fact.at
        is SceneEvent.SignalRecovered -> fact.at
        is SceneEvent.SceneStateChanged -> fact.at
        is SceneEvent.NightOpened -> fact.at
        is SceneEvent.NightClosed -> fact.at
        else -> Instant.EPOCH
    }

    private fun formatTime(instant: Instant): String {
        val ldt = instant.atZone(ZoneOffset.UTC).toLocalDateTime()
        return ldt.format(DateTimeFormatter.ofPattern("HH:mm"))
    }

    private fun formatOffset(offset: Duration): String {
        val h = offset.toHours()
        val m = offset.toMinutesPart()
        val s = offset.toSecondsPart()
        return when {
            h > 0 -> "(+${h}h${m}m)"
            m > 0 -> "(+${m}m${s}s)"
            else -> "(+${s}s)"
        }
    }

    private fun formatDuration(d: Duration): String = "${d.toMinutes()}m"
}

fun timeline(init: TimelineBuilder.() -> Unit): String {
    return TimelineBuilder().apply(init).render()
}
