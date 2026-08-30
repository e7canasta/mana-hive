package levelthresholds

import com.manahive.contracts.perception.ObservationKind.*
import com.manahive.contracts.policy.*
import com.manahive.contracts.scene.SceneEvent
import com.manahive.kernel.*
import com.manahive.pipeline.bdd.PipelineContext
import com.manahive.pipeline.bdd.pipeline
import com.manahive.politica.PolicyResolver
import com.manahive.politica.adapters.toHarborCalibration
import com.manahive.politica.adapters.toRecordingCalibration
import com.manahive.politica.adapters.toSceneCalibration
import com.manahive.politica.adapters.toSentinelCalibration
import java.time.Instant

/**
 * Parameterized level thresholds test.
 *
 * Tests FALL_RISK, CRITICAL, and NIGHT_WANDERING with their respective
 * dwell thresholds, states, and scenarios.
 */
fun main() {
    println("═══════════════════════════════════════════════════════════════")
    println("  Level Thresholds — Parameterized Test")
    println("═══════════════════════════════════════════════════════════════")
    println()

    testFallRisk()
    testCritical()
    testNightWandering()

    println("═══════════════════════════════════════════════════════════════")
    println("  ✅ ALL LEVELS COMPLETED")
    println("═══════════════════════════════════════════════════════════════")
}

fun testFallRisk() {
    println("── NIVEL 2: FALL-RISK — Pedro 103 ──")

    val profile = buildResidentProfile("pedro") {
        risk(RiskLevel.HIGH)
        mobility(MobilityAid.WALKER)
        level(WatchLevel.FALL_RISK)
    }

    val cal = PolicyResolver.resolve(FALL_RISK_CATALOG, profile.profile).value
    val ctx = PipelineContext(
        bed = BedId("bed-fr-1"),
        resident = ResidentId("pedro"),
        night = NightId("night-pedro-103"),
        monitor = MonitorId("CAMERA_MAIN"),
        sceneCalibration = cal.toSceneCalibration(),
        sentinelCalibration = cal.toSentinelCalibration(),
        harborCalibration = cal.toHarborCalibration(),
        recorderCalibration = cal.toRecordingCalibration(BedId("bed-fr-1"), MonitorId("CAMERA_MAIN")),
        start = Instant.parse("2024-01-15T22:00:00Z"),
    )

    // BED_EDGE: warning@1min, exceeded@2min
    ctx.pipeline("Pedro en borde de cama 90s — episodio") {
        obs(IN_BED, "0s"); obs(SITTING_IN_BED, "30m"); obs(BED_EDGE, "31m"); obs(BED_EDGE, "33m"); obs(IN_BED, "34m")
        thenSceneEventPresent(SceneEvent.DwellExceeded::class)
        thenEpisodeOpenCount(1)
    }.report()

    // BED_EDGE: vuelve antes del exceeded
    ctx.pipeline("Pedro en borde de cama 45s — sin episodio") {
        obs(IN_BED, "0s"); obs(SITTING_IN_BED, "30m"); obs(BED_EDGE, "31m"); obs(IN_BED, "31m45s")
        thenSceneEventNotPresent(SceneEvent.DwellExceeded::class)
        thenEpisodeOpenCount(0)
    }.report()

    // STANDING: warning@2min, exceeded@3min
    ctx.pipeline("Pedro parado 4 min — episodio") {
        obs(IN_BED, "0s"); obs(STANDING, "1h00m"); obs(STANDING, "1h04m"); obs(IN_BED, "1h06m")
        thenSceneEventPresent(SceneEvent.DwellExceeded::class)
        thenEpisodeOpenCount(1)
    }.report()

    // IN_BATHROOM: warning@10min, exceeded@15min
    ctx.pipeline("Pedro en baño 17 min — episodio") {
        obs(IN_BED, "0s"); obs(STANDING, "4m"); obs(IN_BATHROOM, "6m"); obs(IN_BATHROOM, "23m"); obs(IN_BED, "25m")
        thenSceneEventPresent(SceneEvent.DwellExceeded::class)
        thenEpisodeOpenCount(1)
    }.report()

    println("  ✅ FALL-RISK COMPLETO")
    println()
}

fun testCritical() {
    println("── NIVEL 3: CRITICAL — Rosa 501 ──")

    val profile = buildResidentProfile("rosa") {
        risk(RiskLevel.HIGH)
        mobility(MobilityAid.WHEELCHAIR)
        level(WatchLevel.CRITICAL)
    }

    val cal = PolicyResolver.resolve(CRITICAL_CATALOG, profile.profile).value
    val ctx = PipelineContext(
        bed = BedId("bed-cr-1"),
        resident = ResidentId("rosa"),
        night = NightId("night-rosa-501"),
        monitor = MonitorId("CAMERA_MAIN"),
        sceneCalibration = cal.toSceneCalibration(),
        sentinelCalibration = cal.toSentinelCalibration(),
        harborCalibration = cal.toHarborCalibration(),
        recorderCalibration = cal.toRecordingCalibration(BedId("bed-cr-1"), MonitorId("CAMERA_MAIN")),
        start = Instant.parse("2024-01-15T22:00:00Z"),
    )

    // IN_BATHROOM: exceeded@15min
    ctx.pipeline("Rosa en baño 12 min — episodio CRITICAL") {
        obs(IN_BED, "0s"); obs(STANDING, "2m"); obs(IN_BATHROOM, "3m"); obs(IN_BATHROOM, "15m"); obs(IN_BED, "17m")
        thenSceneEventPresent(SceneEvent.DwellExceeded::class)
        thenEpisodeOpenCount(1)
        thenSentinelAbrioEpisodio()
    }.report()

    ctx.pipeline("Rosa en baño 8 min — sin episodio") {
        obs(IN_BED, "0s"); obs(STANDING, "2m"); obs(IN_BATHROOM, "3m"); obs(IN_BED, "11m")
        thenSceneEventNotPresent(SceneEvent.DwellExceeded::class)
        thenEpisodeOpenCount(0)
    }.report()

    // STANDING: exceeded@3min
    ctx.pipeline("Rosa parada 4 min — episodio CRITICAL") {
        obs(IN_BED, "0s"); obs(STANDING, "1h00m"); obs(STANDING, "1h04m"); obs(IN_BED, "1h06m")
        thenSceneEventPresent(SceneEvent.DwellExceeded::class)
        thenEpisodeOpenCount(1)
        thenSentinelAbrioEpisodio()
    }.report()

    // OUT_OF_ROOM: unique to CRITICAL
    ctx.pipeline("Rosa sale de la habitacion 6 min — episodio CRITICAL") {
        obs(IN_BED, "0s"); obs(STANDING, "1m"); obs(IN_ROOM, "2m"); obs(OUT_OF_ROOM, "3m"); obs(IN_BED, "12m")
        thenSceneEventPresent(SceneEvent.DwellExceeded::class)
        thenEpisodeOpenCount(1)
        thenSentinelAbrioEpisodio()
    }.report()

    // Recording
    ctx.pipeline("Rosa LYING→STANDING — grabacion extendida") {
        obs(IN_BED, "0s"); obs(STANDING, "180s"); obs(IN_BED, "600s")
        thenRecorderCommandPresent(com.manahive.recorder.RecordingStarted::class)
    }.report()

    println("  ✅ CRITICAL COMPLETO")
    println()
}

fun testNightWandering() {
    println("── NIVEL 1: NIGHT-WANDERING — Elena 202 ──")

    val profile = buildResidentProfile("elena") {
        risk(RiskLevel.MEDIUM)
        mobility(MobilityAid.NONE)
        level(WatchLevel.NIGHT_WANDERING)
    }

    val cal = PolicyResolver.resolve(NIGHT_WANDERING_CATALOG, profile.profile).value
    val ctx = PipelineContext(
        bed = BedId("bed-nw-1"),
        resident = ResidentId("elena"),
        night = NightId("night-elena-202"),
        monitor = MonitorId("CAMERA_MAIN"),
        sceneCalibration = cal.toSceneCalibration(),
        sentinelCalibration = cal.toSentinelCalibration(),
        harborCalibration = cal.toHarborCalibration(),
        recorderCalibration = cal.toRecordingCalibration(BedId("bed-nw-1"), MonitorId("CAMERA_MAIN")),
        start = Instant.parse("2024-01-15T22:00:00Z"),
    )

    // SITTING_IN_BED: warning@20min, exceeded@30min
    ctx.pipeline("Elena se sienta 25 min — warning + episodio") {
        obs(IN_BED, "0s"); obs(SITTING_IN_BED, "1h00m"); obs(SITTING_IN_BED, "1h25m"); obs(IN_BED, "1h35m")
        thenSceneEventPresent(SceneEvent.DwellWarning::class)
        thenSceneEventPresent(SceneEvent.DwellExceeded::class)
        thenEpisodeOpenCount(1)
    }.report()

    ctx.pipeline("Elena se sienta 10 min — sin warning, sin episodio") {
        obs(IN_BED, "0s"); obs(SITTING_IN_BED, "1h00m"); obs(IN_BED, "1h10m")
        thenSceneEventNotPresent(SceneEvent.DwellExceeded::class)
        thenEpisodeOpenCount(0)
    }.report()

    // STANDING: warning@10min, exceeded@15min
    ctx.pipeline("Elena camina 8 min — sin warning, sin episodio") {
        obs(IN_BED, "0s"); obs(STANDING, "1h00m"); obs(IN_BED, "1h08m")
        thenSceneEventNotPresent(SceneEvent.DwellExceeded::class)
        thenEpisodeOpenCount(0)
    }.report()

    // IN_BATHROOM: warning@15min, exceeded@25min
    ctx.pipeline("Elena en baño 27 min — episodio") {
        obs(IN_BED, "0s"); obs(STANDING, "4m"); obs(IN_BATHROOM, "6m"); obs(IN_BATHROOM, "33m"); obs(IN_BED, "35m")
        thenSceneEventPresent(SceneEvent.DwellExceeded::class)
        thenEpisodeOpenCount(1)
    }.report()

    // Recording
    ctx.pipeline("Elena LYING→STANDING — grabación") {
        obs(IN_BED, "0s"); obs(STANDING, "180s"); obs(IN_BED, "600s")
        thenRecorderCommandPresent(com.manahive.recorder.RecordingStarted::class)
    }.report()

    println("  ✅ NIGHT-WANDERING COMPLETO")
    println()
}
