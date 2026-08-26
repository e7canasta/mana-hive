package levelfr

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

val BED = BedId("bed-fr-1")
val RESIDENT = ResidentId("pedro")
val NIGHT = NightId("night-pedro-103")
val CAM = MonitorId("CAMERA_MAIN")
val START = Instant.parse("2024-01-15T22:00:00Z")

// ── Pedro's Profile — FALL_RISK ───────────────────────────────────────────

val pedroProfile = buildResidentProfile("pedro") {
    risk(RiskLevel.HIGH)
    mobility(MobilityAid.WALKER)
    level(WatchLevel.FALL_RISK)
}

// ── Resolve ───────────────────────────────────────────────────────────────

val policyCalibration = PolicyResolver.resolve(FALL_RISK_CATALOG, pedroProfile.profile).value

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
    println("  NIVEL 2: FALL-RISK — Pedro 103")
    println("═══════════════════════════════════════════════════════════════")
    println()
    println("  Perfil: ${pedroProfile.profile.riskLevel} / ${pedroProfile.profile.mode}")
    println("  Nivel: ${pedroProfile.watchLevel}")
    println()
    println("  Politica resuelta:")
    println("    Scene:     ${policyCalibration.scene.dwellThresholds.size} dwell, ${policyCalibration.scene.hysteresis.size} hysteresis")
    println("    Sentinel:  ${policyCalibration.sentinel.alertRules.size} alert rules")
    println("    Recorder:  ${policyCalibration.recorder.transitionWindows.size} transition windows")
    println()

    // FR: BED_EDGE → warning@1min, exceeded@2min
    ctx.pipeline("Pedro en borde de cama 90 s — episodio a los 2 min") {
        obs(IN_BED, "0s")
        obs(SITTING_IN_BED, "30m")
        obs(BED_EDGE, "31m")
        obs(BED_EDGE, "33m")
        obs(IN_BED, "34m")

        thenSceneEventPresent(SceneEvent.DwellExceeded::class)
        thenEpisodeOpenCount(1)
    }.report()

    // FR: BED_EDGE — vuelve antes del exceeded
    ctx.pipeline("Pedro en borde de cama 45 s — sin episodio") {
        obs(IN_BED, "0s")
        obs(SITTING_IN_BED, "30m")
        obs(BED_EDGE, "31m")
        obs(IN_BED, "31m45s")

        thenSceneEventNotPresent(SceneEvent.DwellExceeded::class)
        thenEpisodeOpenCount(0)
    }.report()

    // FR: STANDING → warning@2min, exceeded@3min
    ctx.pipeline("Pedro parado 4 min — episodio") {
        obs(IN_BED, "0s")
        obs(STANDING, "1h00m")
        obs(STANDING, "1h04m")
        obs(IN_BED, "1h06m")

        thenSceneEventPresent(SceneEvent.DwellExceeded::class)
        thenEpisodeOpenCount(1)
    }.report()

    // FR: IN_BATHROOM → warning@10min, exceeded@15min
    ctx.pipeline("Pedro en baño 17 min — episodio") {
        obs(IN_BED, "0s")
        obs(STANDING, "4m")
        obs(IN_BATHROOM, "6m")
        obs(IN_BATHROOM, "23m")
        obs(IN_BED, "25m")

        thenSceneEventPresent(SceneEvent.DwellExceeded::class)
        thenEpisodeOpenCount(1)
    }.report()

    println("═══════════════════════════════════════════════════════════════")
    println("  ✅ NIVEL 2: FALL-RISK — COMPLETO")
    println("═══════════════════════════════════════════════════════════════")
}
