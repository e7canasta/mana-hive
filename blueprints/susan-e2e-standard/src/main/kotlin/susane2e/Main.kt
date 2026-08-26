package susane2e

import com.manahive.contracts.perception.ObservationKind.*
import com.manahive.contracts.policy.*
import com.manahive.contracts.scene.SceneEvent
import com.manahive.contracts.scene.StateKind
import com.manahive.kernel.*
import com.manahive.pipeline.bdd.PipelineContext
import com.manahive.pipeline.bdd.pipeline
import com.manahive.politica.PolicyResolver
import com.manahive.politica.adapters.toHarborCalibration
import com.manahive.politica.adapters.toRecordingCalibration
import com.manahive.politica.adapters.toSceneCalibration
import com.manahive.politica.adapters.toSentinelCalibration
import java.time.Duration
import java.time.Instant

val BED = BedId("bed-5")
val SUSAN = ResidentId("susan")
val NIGHT = NightId("night-susan-401")
val CAM = MonitorId("CAMERA_MAIN")
val START = Instant.parse("2024-01-15T22:00:00Z")

// ── Susan's Profile — Libre, sin alertas ────────────────────────────────────

val susanProfile = buildResidentProfile("susan") {
    risk(RiskLevel.LOW)
    mobility(MobilityAid.NONE)
    template("standard")
}

// ── Susan's Dwell Profile — Medicación cambiada ────────────────────────────

val susanDwellProfile = buildResidentProfile("susan-dwell") {
    risk(RiskLevel.LOW)
    mobility(MobilityAid.NONE)
    template("standard")
    resident {
        bathroom {
            warningAfter(Duration.ofMinutes(5))    // aviso a los 5 min
            alertAfter(Duration.ofMinutes(15))     // episodio a los 15 min
        }
    }
}

// ── Politica Engine resolves ────────────────────────────────────────────────

val policyCalibration = PolicyResolver.resolve(STANDARD_CATALOG, susanProfile.profile)
val policyCalibrationDwell = PolicyResolver.resolve(STANDARD_CATALOG, susanDwellProfile.profile)

// ── Derived Calibrations (from policy result) ───────────────────────────────

val sceneCal = policyCalibration.toSceneCalibration()
val sentinelCal = policyCalibration.toSentinelCalibration()
val harborCal = policyCalibration.toHarborCalibration()
val recorderCal = policyCalibration.toRecordingCalibration(BED, CAM)

val sceneCalDwell = policyCalibrationDwell.toSceneCalibration()
val sentinelCalDwell = policyCalibrationDwell.toSentinelCalibration()
val harborCalDwell = policyCalibrationDwell.toHarborCalibration()
val recorderCalDwell = policyCalibrationDwell.toRecordingCalibration(BED, CAM)

// ── Pipeline Contexts ───────────────────────────────────────────────────────

val ctx = PipelineContext(
    bed = BED,
    resident = SUSAN,
    night = NIGHT,
    monitor = CAM,
    sceneCalibration = sceneCal,
    sentinelCalibration = sentinelCal,
    harborCalibration = harborCal,
    recorderCalibration = recorderCal,
    start = START,
)

val ctxDwell = PipelineContext(
    bed = BED,
    resident = SUSAN,
    night = NIGHT,
    monitor = CAM,
    sceneCalibration = sceneCalDwell,
    sentinelCalibration = sentinelCalDwell,
    harborCalibration = harborCalDwell,
    recorderCalibration = recorderCalDwell,
    start = START,
)

// ── Scenarios ───────────────────────────────────────────────────────────────

fun main() {
    println("═══════════════════════════════════════════════════════════════")
    println("  Susan 401 — E2E Pipeline (STANDARD + Dwell Profile)")
    println("═══════════════════════════════════════════════════════════════")
    println()
    println("  Perfil STANDARD: ${susanProfile.profile.riskLevel} / ${susanProfile.profile.mode}")
    println("  Template: ${susanProfile.profile.templateId?.value ?: "none"}")
    println()
    println("  Politica resuelta (STANDARD):")
    println("    Scene:     ${policyCalibration.scene.dwellThresholds.size} dwell, ${policyCalibration.scene.hysteresis.size} hysteresis")
    println("    Sentinel:  ${policyCalibration.sentinel.alertRules.size} alert rules")
    println("    Recorder:  ${policyCalibration.recorder.transitionWindows.size} transition windows")
    println()

    val bathroomDwell = policyCalibrationDwell.scene.dwellThresholds[StateKind.IN_BATHROOM]
    println("  Perfil DWELL: ${susanDwellProfile.profile.riskLevel} / ${susanDwellProfile.profile.mode}")
    println("  Dwell IN_BATHROOM: warning=${bathroomDwell?.warning}, exceeded=${bathroomDwell?.exceeded}")
    println()

    // ── STANDARD scenarios (sin alertas) ──────────────────────────────────

    ctx.pipeline("Susan se sienta y se acuesta — sin alertas") {
        obs(IN_BED, "0s")
        obs(SITTING_IN_BED, "1h15m")
        obs(IN_BED, "1h32m")

        thenSceneEventPresent(SceneEvent.TransitionDetected::class)
        thenEpisodeOpenCount(0)
        thenHarborCommandCount(0)
        thenEvidenceCount(0)
    }.report()

    ctx.pipeline("Susan va al baño — sin alertas") {
        obs(IN_BED, "0s")
        obs(SITTING_IN_BED, "2h47m")
        obs(STANDING, "2h48m")
        obs(IN_BATHROOM, "2h50m")
        obs(IN_ROOM, "3h00m")
        obs(IN_BED, "3h02m")

        thenSceneEventPresent(SceneEvent.TransitionDetected::class)
        thenEpisodeOpenCount(0)
        thenHarborCommandCount(0)
    }.report()

    ctx.pipeline("Susan se sienta 3 veces — sin budget") {
        obs(IN_BED, "0s")
        obs(SITTING_IN_BED, "1h15m")
        obs(IN_BED, "1h32m")
        obs(SITTING_IN_BED, "3h00m")
        obs(IN_BED, "3h15m")
        obs(SITTING_IN_BED, "5h00m")
        obs(IN_BED, "5h10m")

        thenEpisodeOpenCount(0)
        thenHarborCommandCount(0)
    }.report()

    ctx.pipeline("Susan camina al baño — sin alertas") {
        obs(IN_BED, "0s")
        obs(STANDING, "1h00m")
        obs(IN_BATHROOM, "1h05m")
        obs(IN_ROOM, "1h30m")
        obs(IN_BED, "1h35m")

        thenSentinelSignalCount(0)
        thenHarborCommandCount(0)
    }.report()

    ctx.pipeline("Susan LYING→STANDING — sin grabación") {
        obs(IN_BED, "0s")
        obs(STANDING, "180s")
        obs(IN_BED, "600s")

        thenSentinelSignalCount(0)
    }.report()

    // ── DWELL scenarios (con alertas por dwell) ───────────────────────────

    println()
    println("───────────────────────────────────────────────────────────────")
    println("  DWELL PROFILE: Susan con medicación cambiada")
    println("───────────────────────────────────────────────────────────────")
    println()

    ctxDwell.pipeline("Susan en baño 17 min — warning + episodio") {
        obs(IN_BED, "0s")
        obs(STANDING, "4m")
        obs(IN_BATHROOM, "6m")
        obs(IN_BATHROOM, "22m")
        obs(STANDING, "23m")
        obs(IN_BED, "25m")

        thenSceneEventPresent(SceneEvent.DwellWarning::class)
        thenSceneEventPresent(SceneEvent.DwellExceeded::class)
        thenEpisodeOpenCount(1)
    }.report()

    ctxDwell.pipeline("Susan en baño 4 min — sin warning, sin episodio") {
        obs(IN_BED, "0s")
        obs(STANDING, "4m")
        obs(IN_BATHROOM, "6m")
        obs(IN_ROOM, "10m")
        obs(IN_BED, "11m")

        thenSceneEventNotPresent(SceneEvent.DwellExceeded::class)
        thenEpisodeOpenCount(0)
    }.report()

    println("═══════════════════════════════════════════════════════════════")
    println("  ✅ PIPELINE E2E COMPLETA — STANDARD + DWELL")
    println("═══════════════════════════════════════════════════════════════")
}
