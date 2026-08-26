package levelnw

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
import java.time.Instant

val BED = BedId("bed-nw-1")
val RESIDENT = ResidentId("elena")
val NIGHT = NightId("night-elena-202")
val CAM = MonitorId("CAMERA_MAIN")
val START = Instant.parse("2024-01-15T22:00:00Z")

// ── Elena's Profile — NIGHT_WANDERING ─────────────────────────────────────

val elenaProfile = buildResidentProfile("elena") {
    risk(RiskLevel.MEDIUM)
    mobility(MobilityAid.NONE)
    level(WatchLevel.NIGHT_WANDERING)
}

// ── Resolve ───────────────────────────────────────────────────────────────

val policyCalibration = PolicyResolver.resolve(NIGHT_WANDERING_CATALOG, elenaProfile.profile).value

val sceneCal = policyCalibration.toSceneCalibration()
val sentinelCal = policyCalibration.toSentinelCalibration()
val harborCal = policyCalibration.toHarborCalibration()
val recorderCal = policyCalibration.toRecordingCalibration(BED, CAM)

val ctx = PipelineContext(
    bed = BED,
    resident = RESIDENT,
    night = NIGHT,
    monitor = CAM,
    sceneCalibration = sceneCal,
    sentinelCalibration = sentinelCal,
    harborCalibration = harborCal,
    recorderCalibration = recorderCal,
    start = START,
)

// ── Scenarios ─────────────────────────────────────────────────────────────

fun main() {
    println("═══════════════════════════════════════════════════════════════")
    println("  NIVEL 1: NIGHT-WANDERING — Elena 202")
    println("═══════════════════════════════════════════════════════════════")
    println()
    println("  Perfil: ${elenaProfile.profile.riskLevel} / ${elenaProfile.profile.mode}")
    println("  Nivel: ${elenaProfile.watchLevel}")
    println()
    println("  Politica resuelta:")
    println("    Scene:     ${policyCalibration.scene.dwellThresholds.size} dwell, ${policyCalibration.scene.hysteresis.size} hysteresis")
    println("    Sentinel:  ${policyCalibration.sentinel.alertRules.size} alert rules")
    println("    Recorder:  ${policyCalibration.recorder.transitionWindows.size} transition windows")
    println()

    // NW: SITTING_IN_BED → warning@20min, exceeded@30min
    ctx.pipeline("Elena se sienta 25 min — warning a los 20, episodio a los 30") {
        obs(IN_BED, "0s")
        obs(SITTING_IN_BED, "1h00m")
        obs(SITTING_IN_BED, "1h25m")
        obs(IN_BED, "1h35m")

        thenSceneEventPresent(SceneEvent.DwellWarning::class)
        thenSceneEventPresent(SceneEvent.DwellExceeded::class)
        thenEpisodeOpenCount(1)
    }.report()

    // NW: SITTING_IN_BED — vuelve antes del warning → sin episodio
    ctx.pipeline("Elena se sienta 10 min — sin warning, sin episodio") {
        obs(IN_BED, "0s")
        obs(SITTING_IN_BED, "1h00m")
        obs(IN_BED, "1h10m")

        thenSceneEventNotPresent(SceneEvent.DwellExceeded::class)
        thenEpisodeOpenCount(0)
    }.report()

    // NW: STANDING — warning@10min, exceeded@15min
    ctx.pipeline("Elena camina 8 min — sin warning, sin episodio") {
        obs(IN_BED, "0s")
        obs(STANDING, "1h00m")
        obs(IN_BED, "1h08m")

        thenSceneEventNotPresent(SceneEvent.DwellExceeded::class)
        thenEpisodeOpenCount(0)
    }.report()

    // NW: IN_BATHROOM — warning@15min, exceeded@25min
    ctx.pipeline("Elena en baño 27 min — episodio") {
        obs(IN_BED, "0s")
        obs(STANDING, "4m")
        obs(IN_BATHROOM, "6m")
        obs(IN_BATHROOM, "33m")
        obs(IN_BED, "35m")

        thenSceneEventPresent(SceneEvent.DwellExceeded::class)
        thenEpisodeOpenCount(1)
    }.report()

    // NW: LYING → STANDING graba
    ctx.pipeline("Elena LYING→STANDING — grabación") {
        obs(IN_BED, "0s")
        obs(STANDING, "180s")
        obs(IN_BED, "600s")

        thenRecorderCommandPresent(com.manahive.recorder.RecordingStarted::class)
    }.report()

    println("═══════════════════════════════════════════════════════════════")
    println("  ✅ NIVEL 1: NIGHT-WANDERING — COMPLETO")
    println("═══════════════════════════════════════════════════════════════")
}
