package levelcr

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

val BED = BedId("bed-cr-1")
val RESIDENT = ResidentId("rosa")
val NIGHT = NightId("night-rosa-501")
val CAM = MonitorId("CAMERA_MAIN")
val START = Instant.parse("2024-01-15T22:00:00Z")

val rosaProfile = buildResidentProfile("rosa") {
    risk(RiskLevel.HIGH)
    mobility(MobilityAid.WHEELCHAIR)
    level(WatchLevel.CRITICAL)
}

val policyCalibration = PolicyResolver.resolve(CRITICAL_CATALOG, rosaProfile.profile).value

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

fun main() {
    println("NIVEL 3: CRITICAL - Rosa 501")
    println()
    println("  Politica resuelta:")
    println("    Scene:     ${policyCalibration.scene.dwellThresholds.size} dwell, ${policyCalibration.scene.hysteresis.size} hysteresis")
    println("    Sentinel:  ${policyCalibration.sentinel.alertRules.size} alert rules")
    println("    Recorder:  ${policyCalibration.recorder.transitionWindows.size} transition windows")
    println()

    ctx.pipeline("Rosa en bano 12 min - episodio CRITICAL") {
        obs(IN_BED, "0s")
        obs(STANDING, "2m")
        obs(IN_BATHROOM, "3m")
        obs(IN_BATHROOM, "15m")
        obs(IN_BED, "17m")

        thenSceneEventPresent(SceneEvent.DwellExceeded::class)
        thenEpisodeOpenCount(1)
        thenSentinelAbrioEpisodio()
    }.report()

    ctx.pipeline("Rosa en bano 8 min - sin episodio") {
        obs(IN_BED, "0s")
        obs(STANDING, "2m")
        obs(IN_BATHROOM, "3m")
        obs(IN_BED, "11m")

        thenSceneEventNotPresent(SceneEvent.DwellExceeded::class)
        thenEpisodeOpenCount(0)
    }.report()

    ctx.pipeline("Rosa parada 4 min - episodio CRITICAL") {
        obs(IN_BED, "0s")
        obs(STANDING, "1h00m")
        obs(STANDING, "1h04m")
        obs(IN_BED, "1h06m")

        thenSceneEventPresent(SceneEvent.DwellExceeded::class)
        thenEpisodeOpenCount(1)
        thenSentinelAbrioEpisodio()
    }.report()

    // "Se fue de la habitacion y no volvio." En CRITICAL el catalogo avisa a los
    // 2 minutos. El recorrido natural es habitacion -> pasillo -> fuera, no un
    // salto desde STANDING: por eso hizo falta abrir esas aristas en el grafo.
    ctx.pipeline("Rosa sale de la habitacion 6 min - episodio CRITICAL") {
        obs(IN_BED, "0s")
        obs(STANDING, "1m")
        obs(IN_ROOM, "2m")
        obs(OUT_OF_ROOM, "3m")
        obs(IN_BED, "12m")

        thenSceneEventPresent(SceneEvent.DwellExceeded::class)
        thenEpisodeOpenCount(1)
        thenSentinelAbrioEpisodio()
    }.report()

    ctx.pipeline("Rosa LYING->STANDING - grabacion extendida") {
        obs(IN_BED, "0s")
        obs(STANDING, "180s")
        obs(IN_BED, "600s")

        thenRecorderCommandPresent(com.manahive.recorder.RecordingStarted::class)
    }.report()

    println("NIVEL 3: CRITICAL - COMPLETO")
}
