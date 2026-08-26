package com.manahive.batchio

import com.manahive.contracts.scene.SceneEvent
import com.manahive.contracts.scene.PersonState
import java.io.File
import java.time.Instant

/**
 * Writes SceneEvents to .out files.
 *
 * Format: `<TYPE> <details>          # ← evento <n>`
 */
object SceneEventWriter {

    fun write(file: File, events: List<SceneEvent>, startTime: Instant) {
        file.parentFile?.mkdirs()
        file.bufferedWriter().use { writer ->
            events.forEach { event ->
                val offset = DurationFormat.format(java.time.Duration.between(startTime, event.at))
                val body = formatEvent(event)
                writer.write("t=$offset  $body")
                writer.newLine()
            }
        }
    }

    fun formatEvent(event: SceneEvent): String = when (event) {
        is SceneEvent.TransitionDetected ->
            "TRANSITION ${event.from.simpleName} → ${event.to.simpleName}"
        is SceneEvent.DwellWarning ->
            "DWELL_WARNING ${event.state.simpleName} ${event.threshold}"
        is SceneEvent.DwellExceeded ->
            "DWELL_EXCEEDED ${event.state.simpleName} ${event.threshold}"
        is SceneEvent.ComeBackWarning ->
            "COMEBACK_WARNING ${event.threshold}"
        is SceneEvent.ComeBackExceeded ->
            "COMEBACK_EXCEEDED ${event.threshold}"
        is SceneEvent.SceneStateChanged ->
            "SCENE_CHANGED ${event.field} ${event.from} → ${event.to}"
        is SceneEvent.SignalLost ->
            "SIGNAL_LOST monitor=${event.monitor.value}"
        is SceneEvent.SignalRecovered ->
            "SIGNAL_RECOVERED monitor=${event.monitor.value}"
        is SceneEvent.StaffPresenceDetected ->
            "STAFF_PRESENCE staff=${event.staff?.value}"
        is SceneEvent.NightOpened ->
            "NIGHT_OPENED occupant=${event.occupant?.value}"
        is SceneEvent.NightClosed ->
            "NIGHT_CLOSED"
        is SceneEvent.StaffLeftDetected ->
            "STAFF_LEFT"
        is SceneEvent.SceneDwellWarning ->
            "SCENE_DWELL_WARNING ${event.field} ${event.threshold}"
        is SceneEvent.SceneDwellExceeded ->
            "SCENE_DWELL_EXCEEDED ${event.field} ${event.threshold}"
    }

    private val PersonState.simpleName: String
        get() = this::class.simpleName ?: this.toString()
}
