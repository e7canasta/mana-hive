package com.manahive.recorder.testdata

import com.manahive.contracts.policy.Severity
import com.manahive.contracts.scene.PersonState
import com.manahive.contracts.scene.kind
import com.manahive.contracts.sentinel.SentinelSignal
import com.manahive.kernel.*
import com.manahive.recorder.*
import java.time.Duration
import java.time.Instant

// ── Calibration patterns ─────────────────────────────────────────────────────

/**
 * Minimal calibration: one rule, one monitor.
 */
fun testCalibration(
    residentId: String = "test",
    monitors: List<MonitorId> = listOf(MonitorId("CAMERA_MAIN")),
): RecordingCalibration = recordingCalibration {
    resident(residentId)
    rule("r-fall-recording") {
        trigger { transition(from = PersonState.Lying, to = PersonState.Standing) }
        recordingWindow { before = 2.minutes; after = 5.minutes }
        quality = Quality.HD
        this.monitors = monitors
    }
}

/**
 * Full calibration: multiple rules, evidence rules.
 */
fun fullCalibration(
    residentId: String = "test",
): RecordingCalibration = recordingCalibration {
    resident(residentId)

    rule("r-fall-recording") {
        trigger { transition(from = PersonState.Lying, to = PersonState.Standing) }
        recordingWindow { before = 2.minutes; after = 5.minutes }
        quality = Quality.HD
        monitors = listOf(MonitorId("CAMERA_MAIN"))
    }

    rule("r-dwell-recording") {
        trigger { dwellExceeded(state = PersonState.InBathroom) }
        recordingWindow { before = 3.minutes; after = 10.minutes }
        quality = Quality.HD
        monitors = listOf(MonitorId("CAMERA_MAIN"))
    }

    rule("r-incident-recording") {
        trigger { episodeOpened(severity = Severity.CRITICAL) }
        recordingWindow { before = 10.minutes; after = 15.minutes }
        quality = Quality.FULL
        monitors = listOf(MonitorId("CAMERA_MAIN"), MonitorId("CAMERA_CORRIDOR"))
    }

    evidenceRule("e-fall-evidence") {
        trigger { episodeOpened(severity = Severity.WARNING) }
        evidenceType = EvidenceType.INCIDENT
    }

    evidenceRule("e-incident-evidence") {
        trigger { episodeOpened(severity = Severity.CRITICAL) }
        evidenceType = EvidenceType.INCIDENT
    }
}

// ── Helper constructors ──────────────────────────────────────────────────────

fun sentinelEpisodeOpened(
    bed: BedId = BedId("test-bed"),
    resident: ResidentId = ResidentId("test"),
    at: Instant,
    episodeId: String,
    severity: Severity = Severity.WARNING,
    reversible: Boolean = true,
    requiresNvr: Boolean = false,
): SentinelSignal.EpisodeOpened = SentinelSignal.EpisodeOpened(
    bed = bed, resident = resident, at = at,
    rulesFingerprint = "test",
    episode = EpisodeId(episodeId),
    rule = RuleId("r-test"),
    trigger = PersonState.Lying.kind,
    severity = severity,
    reversible = reversible,
    requiresNvr = requiresNvr,
    confirmationWindow = null,
)
