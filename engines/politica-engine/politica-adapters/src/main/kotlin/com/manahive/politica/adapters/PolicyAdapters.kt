package com.manahive.politica.adapters

import com.manahive.contracts.policy.PolicyCalibration
import com.manahive.contracts.policy.RecordQuality
import com.manahive.contracts.policy.Severity
import com.manahive.contracts.policy.TransitionWindow
import com.manahive.contracts.policy.TriggerOn
import com.manahive.contracts.scene.PersonState
import com.manahive.contracts.scene.StateKind
import com.manahive.contracts.scene.UnknownCause
import com.manahive.contracts.common.Channel
import com.manahive.harbor.HarborCalibration
import com.manahive.harbor.harborCalibration
import com.manahive.kernel.BedId
import com.manahive.kernel.MonitorId
import com.manahive.recorder.Quality
import com.manahive.recorder.RecordingCalibration
import com.manahive.recorder.recordingCalibration
import com.manahive.scene.adapter.toSceneCalibration as toSceneCalibrationFromPolicy
import com.manahive.scene.calibration.SceneCalibration
import com.manahive.sentinel.SentinelCalibration
import com.manahive.sentinel.sentinelCalibration
import java.time.Duration

/**
 * Adapter functions that convert PolicyCalibration → engine-specific calibrations.
 *
 * Vernon's ACL: Politica Engine produces PolicyCalibration (public interface).
 * Each adapter extracts the relevant section and translates it into the
 * engine's own calibration format.
 *
 * Fowler: "Adapter" pattern — converts one interface to another.
 * The adapters are pure functions: same input → same output, no side effects.
 */

// ── Scene Adapter ───────────────────────────────────────────────────────────

/**
 * Convert PolicyCalibration.scene → SceneCalibration.
 *
 * Delegates to the adapter that lives next to [SceneCalibration] itself. This
 * used to be a second, independent translation of the same policy, and the two
 * had drifted: this one rebuilt the calibration through the DSL and never
 * applied `scene.hysteresis`, so every catalog that tuned a transition had that
 * tuning dropped on the way to the engine — silently, and only for the callers
 * who happened to import this one.
 */
public fun PolicyCalibration.toSceneCalibration(): SceneCalibration =
    toSceneCalibrationFromPolicy()

// ── Sentinel Adapter ────────────────────────────────────────────────────────

/**
 * Convert PolicyCalibration.sentinel → SentinelCalibration.
 */
public fun PolicyCalibration.toSentinelCalibration(): SentinelCalibration = sentinelCalibration {
    resident(this@toSentinelCalibration.residentId.value)

    this@toSentinelCalibration.sentinel.alertRules.values.forEach { rule ->
        rule(rule.id.value, rule.trigger, rule.triggerOn) {
            severity = rule.severity
            closureCondition = rule.closureCondition
            reversible = rule.reversible
            requiresConfirmation = rule.requiresConfirmation
            requiresNvr = rule.requiresNvr
            confirmationWindow = rule.confirmationWindow
            if (rule.umbrellaEvents.isNotEmpty()) {
                umbrellaEvents(*rule.umbrellaEvents.toTypedArray())
            }
        }
    }

    this@toSentinelCalibration.sentinel.comeBackRules.values.forEach { rule ->
        rule(rule.id.value, rule.trigger, TriggerOn.COME_BACK) {
            severity = rule.severity
            closureCondition = rule.closureCondition
            reversible = rule.reversible
            requiresConfirmation = rule.requiresConfirmation
            requiresNvr = rule.requiresNvr
            confirmationWindow = rule.confirmationWindow
        }
    }

    // Las reglas de campo de escena. El slot y su accessor existian desde hacia
    // rato, pero el adapter no las pasaba y las tres construcciones les daban
    // emptyMap(): era un stub, no una via funcionando.
    this@toSentinelCalibration.sentinel.sceneStateRules.values.forEach { sceneRule(it) }
    this@toSentinelCalibration.sentinel.closingStates.forEach { closingState(it) }
}

// ── Harbor Adapter ──────────────────────────────────────────────────────────

/**
 * Convert PolicyCalibration.harbor → HarborCalibration.
 *
 * If no channels configured, uses sensible defaults per severity.
 */
public fun PolicyCalibration.toHarborCalibration(): HarborCalibration = harborCalibration {
    resident(this@toHarborCalibration.residentId.value)

    val policy = this@toHarborCalibration.harbor

    notice { applySeverityConfig(policy, Severity.INFO, setOf(Channel.CONSOLE), Duration.ofMinutes(30)) }
    alert { applySeverityConfig(policy, Severity.WARNING, setOf(Channel.PUSH, Channel.TABLET), Duration.ofMinutes(5)) }
    call { applySeverityConfig(policy, Severity.HIGH, setOf(Channel.PUSH, Channel.TABLET), Duration.ofMinutes(2)) }
    incident {
        applySeverityConfig(
            policy,
            Severity.CRITICAL,
            setOf(Channel.PUSH, Channel.TABLET, Channel.WARD_BOARD, Channel.CONSOLE),
            Duration.ZERO,
        )
    }
}

/**
 * Apply channel and timeout configuration for a severity level.
 * Uses policy values if present, otherwise falls back to defaults.
 *
 * Fowler: "Extract Method" — eliminates duplication across notice/alert/incident blocks.
 */
private fun com.manahive.harbor.SeverityConfig.applySeverityConfig(
    policy: com.manahive.contracts.policy.HarborPolicy,
    severity: Severity,
    defaultChannels: Set<Channel>,
    defaultTimeout: Duration,
) {
    channels = policy.defaultChannels[severity] ?: defaultChannels
    escalationTimeout = policy.escalationTimeouts[severity] ?: defaultTimeout
}

// ── Recorder Adapter ────────────────────────────────────────────────────────

/**
 * Convert PolicyCalibration.recorder → RecordingCalibration.
 *
 * Generates:
 * - Episode-opened rules for each alert rule
 * - Transition-specific rules from transitionWindows
 */
public fun PolicyCalibration.toRecordingCalibration(
    bedId: BedId,
    monitorId: MonitorId,
): RecordingCalibration = recordingCalibration {
    resident(this@toRecordingCalibration.residentId.value)

    val ventanas = this@toRecordingCalibration.recorder.ruleWindows

    this@toRecordingCalibration.sentinel.alertRules.values.forEach { rule ->
        // La ventana la pide la regla. Cuando el perfil no dice nada, se cae a
        // la de siempre — pero mientras el perfil hablaba, el adapter no lo
        // escuchaba: grababa 30s/2m para todo y deducia la calidad de la
        // severidad, o sea que la politica de video no estaba en la politica.
        val pedida = ventanas[rule.id]
        rule("rec-${rule.id.value}") {
            trigger { episodeOpened(rule.severity) }
            recordingWindow {
                before = pedida?.before ?: Duration.ofSeconds(30)
                after = pedida?.after ?: Duration.ofMinutes(2)
            }
            quality = pedida?.quality?.toRecorderQuality()
                ?: if (rule.severity == Severity.CRITICAL) Quality.FULL else Quality.HD
            monitors = listOf(monitorId)
        }
    }

    // Las reglas de campo: la baranda, la silla. Solo producen orden de grabar
    // si el perfil la pidio — `requiresNvr` sin ventana era media orden.
    this@toRecordingCalibration.sentinel.sceneStateRules.values.forEach { rule ->
        val pedida = ventanas[rule.id] ?: return@forEach
        rule("rec-${rule.id.value}") {
            trigger { episodeOpened(rule.severity) }
            recordingWindow {
                before = pedida.before
                after = pedida.after
            }
            quality = pedida.quality.toRecorderQuality()
            monitors = listOf(monitorId)
        }
    }

    this@toRecordingCalibration.recorder.transitionWindows.forEach { (key, window) ->
        rule("rec-${key.from.name.lowercase()}-${key.to.name.lowercase()}") {
            trigger {
                transition(
                    from = key.from.toPersonState(),
                    to = key.to.toPersonState(),
                )
            }
            recordingWindow {
                before = window.before
                after = window.after
            }
            quality = Quality.HD
            monitors = listOf(monitorId)
        }
    }
}

// ── Internal helpers ────────────────────────────────────────────────────────

/**
 * La calidad que pidio el perfil, en el vocabulario del recorder.
 *
 * Son dos escalas distintas a proposito: el director dice "alta" y el recorder
 * sabe que eso son 1920x1080 a 30fps. Traducir aca es lo correcto; que el
 * director tuviera que elegir un bitrate, no.
 */
private fun RecordQuality.toRecorderQuality(): Quality = when (this) {
    RecordQuality.LOW -> Quality.SD
    RecordQuality.STANDARD -> Quality.HD
    RecordQuality.HIGH -> Quality.FULL
}

/**
 * Convert StateKind to PersonState for recorder trigger matching.
 */
private fun StateKind.toPersonState(): PersonState = when (this) {
    StateKind.LYING -> PersonState.Lying
    StateKind.SITTING_IN_BED -> PersonState.SittingInBed
    StateKind.BED_EDGE -> PersonState.BedEdge
    StateKind.STANDING -> PersonState.Standing
    StateKind.ON_FLOOR -> PersonState.OnFloor
    StateKind.IN_BATHROOM -> PersonState.InBathroom
    StateKind.IN_ROOM -> PersonState.InRoom
    StateKind.IN_HALLWAY -> PersonState.InHallway
    StateKind.ATTEMPTING_EXIT -> PersonState.AttemptingExit
    StateKind.ABSENT -> PersonState.Absent
    StateKind.IN_CHAIR -> PersonState.InChair
    StateKind.IN_WHEELCHAIR -> PersonState.InWheelchair
    StateKind.OUTDOOR -> PersonState.Outdoor
    StateKind.UNKNOWN -> PersonState.Unknown(UnknownCause.SCENE)
}
