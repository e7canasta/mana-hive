package jose301e2e

import com.manahive.contracts.perception.ObservationKind.*
import com.manahive.contracts.policy.*
import com.manahive.contracts.scene.PersonState
import com.manahive.contracts.scene.SceneEvent
import com.manahive.contracts.scene.StateKind
import com.manahive.contracts.sentinel.SentinelSignal
import com.manahive.harbor.*
import com.manahive.kernel.*
import com.manahive.pipeline.bdd.PipelineContext
import com.manahive.pipeline.bdd.pipeline
import com.manahive.politica.PolicyResolver
import com.manahive.politica.adapters.toHarborCalibration
import com.manahive.politica.adapters.toSceneCalibration
import com.manahive.politica.adapters.toSentinelCalibration
import com.manahive.recorder.*
import java.time.Duration
import java.time.Instant

val BED = BedId("bed-4")
val JOSE = ResidentId("jose")
val NIGHT = NightId("night-jose-301")
val CAM = MonitorId("CAMERA_MAIN")
val START = Instant.parse("2024-01-15T22:00:00Z")

// ── José's Profile — Director's Language ─────────────────────────────────────

val joseProfile = buildResidentProfile("jose") {
    risk(RiskLevel.HIGH)
    mobility(MobilityAid.NONE)
    template("standard")

    resident {
        sitting { alertAfter(Duration.ofMinutes(15)) }
        bathroom { alertAfter(Duration.ofMinutes(10)) }
    }

    transitions {
        lyingToStanding { hysteresis(Duration.ofMillis(1000)) }
    }
}

// ── Politica Engine resolves ────────────────────────────────────────────────

val policyCalibration = PolicyResolver.resolve(STANDARD_CATALOG, joseProfile.profile)

// ── Derived Calibrations ────────────────────────────────────────────────────

val sceneCal = policyCalibration.toSceneCalibration()
val sentinelCal = policyCalibration.toSentinelCalibration()
val harborCal = policyCalibration.toHarborCalibration()

// ── Recorder: custom rules from blueprint ───────────────────────────────────

val recorderCal = recordingCalibration {
    resident("jose")
    rule("r-fall-recording") {
        trigger { transition(from = PersonState.Lying, to = PersonState.Standing) }
        recordingWindow { before = Duration.ofMinutes(2); after = Duration.ofMinutes(5) }
        quality = Quality.HD
        monitors = listOf(CAM)
    }
    rule("r-dwell-recording") {
        trigger { dwellExceeded(state = PersonState.InBathroom) }
        recordingWindow { before = Duration.ofMinutes(3); after = Duration.ofMinutes(10) }
        quality = Quality.HD
        monitors = listOf(CAM)
    }
    rule("r-incident-recording") {
        trigger { episodeOpened(severity = Severity.CRITICAL) }
        recordingWindow { before = Duration.ofMinutes(10); after = Duration.ofMinutes(15) }
        quality = Quality.FULL
        monitors = listOf(CAM, MonitorId("CAMERA_CORRIDOR"))
    }
    evidenceRule("e-warning-evidence") {
        trigger { episodeOpened(severity = Severity.WARNING) }
        evidenceType = EvidenceType.INCIDENT
    }
}

// ── Pipeline Context ────────────────────────────────────────────────────────

val ctx = PipelineContext(
    bed = BED,
    resident = JOSE,
    night = NIGHT,
    monitor = CAM,
    sceneCalibration = sceneCal,
    sentinelCalibration = sentinelCal,
    harborCalibration = harborCal,
    recorderCalibration = recorderCal,
    start = START,
)

// ── Scenarios ───────────────────────────────────────────────────────────────

fun main() {
    println("═══════════════════════════════════════════════════════════════")
    println("  José 301 — E2E Pipeline (Scene → Sentinel → Harbor → Recorder)")
    println("═══════════════════════════════════════════════════════════════")
    println()
    println("  Perfil: ${joseProfile.profile.riskLevel} / ${joseProfile.profile.mode}")
    println("  Template: ${joseProfile.profile.templateId?.value ?: "none"}")
    println()
    println("  Politica resuelta:")
    println("    Scene:     ${policyCalibration.scene.dwellThresholds.size} dwell, ${policyCalibration.scene.hysteresis.size} hysteresis")
    println("    Sentinel:  ${policyCalibration.sentinel.alertRules.size} alert rules")
    println("    Recorder:  ${policyCalibration.recorder.transitionWindows.size} transition windows")
    println()
    println("  Reglas de residente:")
    joseProfile.stateOverrides.forEach { (state, override) ->
        println("    ${state.name}: alertAfter=${override.alertAfter}")
    }
    println()

    ctx.pipeline("José se sienta en la cama — pipeline completa") {
        obs(IN_BED, "0s")
        obs(SITTING_IN_BED, "1h15m")
        obs(IN_BED, "1h32m")

        thenSceneEventPresent(SceneEvent.TransitionDetected::class)
        thenSignalPresent(SentinelSignal.EpisodeOpened::class)
        thenEpisodeOpenCount(1)
        thenHarborCommandPresent(NoticeCommand.Dispatch::class)
        thenEvidenceCount(1)
    }.report()

    ctx.pipeline("José va al baño y tarda — dwell exceeded") {
        obs(IN_BED, "0s")
        obs(SITTING_IN_BED, "2h47m")
        obs(STANDING, "2h48m")
        obs(IN_BATHROOM, "2h50m")
        obs(IN_ROOM, "3h00m")
        obs(IN_BED, "3h02m")

        thenSceneEventPresent(SceneEvent.TransitionDetected::class)
        thenSignalPresent(SentinelSignal.EpisodeOpened::class)
        thenHarborCommandPresent(NoticeCommand.Dispatch::class)
    }.report()

    ctx.pipeline("José se sienta 3 veces — budget agota") {
        obs(IN_BED, "0s")
        obs(SITTING_IN_BED, "1h15m")
        obs(IN_BED, "1h32m")
        obs(SITTING_IN_BED, "3h00m")
        obs(IN_BED, "3h15m")
        obs(SITTING_IN_BED, "5h00m")
        obs(IN_BED, "5h10m")

        thenSceneEventCount(7)
        thenEpisodeOpenCount(3)
        thenHarborCommandCount(6)
    }.report()

    ctx.pipeline("José camina al baño sin sitting — dwell alerta bathroom") {
        obs(IN_BED, "0s")
        obs(STANDING, "1h00m")
        obs(IN_BATHROOM, "1h05m")
        obs(IN_ROOM, "1h30m")
        obs(IN_BED, "1h35m")

        thenSceneEventCount(5)
        thenSentinelSignalCount(2)
        thenHarborCommandCount(2)
        thenRecorderCommandPresent(RecordingStarted::class)
    }.report()

    ctx.pipeline("LYING→STANDING activa grabación") {
        obs(IN_BED, "0s")
        obs(STANDING, "180s")
        obs(IN_BED, "600s")

        thenSceneEventCount(3)
        thenRecorderCommandPresent(RecordingStarted::class)
    }.report()

    ctx.pipeline("Staff asiste a José durante incidente") {
        obs(IN_BED, "0s")
        obs(SITTING_IN_BED, "30m")
        obs(STAFF_ENTERED, "35m")

        thenSceneEventCount(3)
        thenSentinelSignalCount(2)
        thenHarborCommandCount(2)
    }.report()

    ctx.pipeline("20:00 — Staff asiste y deja residente solo") {
        obs(IN_BED, "0s")
        obs(SITTING_IN_BED, "30m")
        obs(STAFF_ENTERED, "35m")
        obs(STAFF_LEFT, "45m")
        obs(IN_BED, "60m")

        thenSceneEventCount(5)
        thenSentinelSignalCount(2)
        thenHarborCommandCount(2)
    }.report()

    ctx.pipeline("08:00 — Staff se lleva al residente") {
        obs(IN_BED, "0s")
        obs(SITTING_IN_BED, "30m")
        obs(STAFF_ENTERED, "35m")
        obs(STANDING, "45m")
        obs(STAFF_LEFT, "50m")

        thenSceneEventCount(5)
        thenSentinelSignalCount(2)
        thenHarborCommandCount(2)
    }.report()

    println("═══════════════════════════════════════════════════════════════")
    println("  ✅ PIPELINE E2E COMPLETA")
    println("═══════════════════════════════════════════════════════════════")
}
