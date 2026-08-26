package com.manahive.politica.adapters

import com.manahive.contracts.policy.PolicyCalibration
import com.manahive.contracts.policy.Severity
import com.manahive.contracts.policy.TransitionWindow
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
import com.manahive.scene.calibration.SceneCalibration
import com.manahive.scene.calibration.sceneCalibration
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
 */
public fun PolicyCalibration.toSceneCalibration(): SceneCalibration = sceneCalibration {
    val scenePolicy = this@toSceneCalibration.scene

    heartbeatTimeout = scenePolicy.confidence.heartbeatTimeout

    dwell {
        // Sin `when`: traducir un mapa enumerando estados a mano hace que olvidar
        // uno pierda el umbral en silencio. Es lo que pasaba con ABSENT.
        scenePolicy.dwellThresholds.forEach { (kind, threshold) ->
            state(kind) warning threshold.warning exceeded threshold.exceeded
        }
    }

    confidence {
        scenePolicy.confidence.minConfidence.forEach { (state, minConf) ->
            state min minConf
        }
    }
}

// ── Sentinel Adapter ────────────────────────────────────────────────────────

/**
 * Convert PolicyCalibration.sentinel → SentinelCalibration.
 */
public fun PolicyCalibration.toSentinelCalibration(): SentinelCalibration = sentinelCalibration {
    resident(this@toSentinelCalibration.residentId.value)

    this@toSentinelCalibration.sentinel.alertRules.values.forEach { rule ->
        rule(rule.id.value) {
            trigger = rule.trigger
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

    this@toRecordingCalibration.sentinel.alertRules.values.forEach { rule ->
        rule("rec-${rule.id.value}") {
            trigger { episodeOpened(rule.severity) }
            recordingWindow {
                before = Duration.ofSeconds(30)
                after = Duration.ofMinutes(2)
            }
            quality = if (rule.severity == Severity.CRITICAL) Quality.FULL else Quality.HD
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
 * Convert StateKind to PersonState for recorder trigger matching.
 */
private fun StateKind.toPersonState(): PersonState = when (this) {
    StateKind.LYING -> PersonState.Lying
    StateKind.SITTING_IN_BED -> PersonState.SittingInBed
    StateKind.BED_EDGE -> PersonState.BedEdge
    StateKind.STANDING -> PersonState.Standing
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
