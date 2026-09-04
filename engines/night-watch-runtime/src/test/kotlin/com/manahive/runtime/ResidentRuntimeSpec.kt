package com.manahive.runtime

import com.manahive.contracts.perception.Observation
import com.manahive.contracts.perception.ObservationKind
import com.manahive.contracts.policy.FALL_RISK_CATALOG
import com.manahive.contracts.policy.STANDARD_CATALOG
import com.manahive.contracts.policy.WatchLevel
import com.manahive.contracts.scene.SceneEvent
import com.manahive.contracts.sentinel.SentinelSignal
import com.manahive.contracts.policy.LevelTemplate
import com.manahive.contracts.policy.PolicyLayers
import com.manahive.contracts.policy.toAlarmProfile
import com.manahive.kernel.BedId
import com.manahive.kernel.MonitorId
import com.manahive.kernel.NightId
import com.manahive.kernel.ResidentId
import com.manahive.politica.PolicyResolver
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class ResidentRuntimeSpec {

    private val jose = ResidentId("jose")
    private val elena = ResidentId("elena")
    private val bed4 = BedId("bed-4")
    private val bed5 = BedId("bed-5")
    private val night = NightId("night-jose-301")
    private val cam = MonitorId("CAMERA_MAIN")
    private val t0 = Instant.parse("2024-01-15T22:00:00Z")

    private fun obs(kind: ObservationKind, bed: BedId, at: Instant): Observation = Observation(
        monitor = cam,
        bed = bed,
        kind = kind,
        confidence = 0.95,
        observedAt = at,
    )

    private fun fallRiskCalibrations(): EngineCalibrations {
        val layers = PolicyLayers(
            level = WatchLevel.FALL_RISK,
            template = LevelTemplate(id = "fall-risk", level = WatchLevel.FALL_RISK),
            adjustments = emptyList(),
            windows = emptyList(),
        )
        val profile = layers.toAlarmProfile(jose, t0)
        val calibration = PolicyResolver.resolve(FALL_RISK_CATALOG, profile.value).value
        return EngineCalibrations.from(calibration)
    }

    private fun standardCalibrations(): EngineCalibrations {
        val layers = PolicyLayers(
            level = WatchLevel.STANDARD,
            template = LevelTemplate(id = "standard", level = WatchLevel.STANDARD),
            adjustments = emptyList(),
            windows = emptyList(),
        )
        val profile = layers.toAlarmProfile(elena, t0)
        val calibration = PolicyResolver.resolve(STANDARD_CATALOG, profile.value).value
        return EngineCalibrations.from(calibration)
    }

    @Test
    fun `FALL_RISK runtime detects dwell exceeded after 20 minutes`() {
        val rt = ResidentRuntime(jose, bed4, night, cam, fallRiskCalibrations())

        rt.onObservation(obs(ObservationKind.IN_BED, bed4, t0))
        rt.onObservation(obs(ObservationKind.SITTING_IN_BED, bed4, t0 + Duration.ofMinutes(1)))

        // Sweep at 21 minutes — FALL_RISK threshold is 20min for SITTING_IN_BED
        val sweepOut = rt.onTick(t0 + Duration.ofMinutes(21))

        val dwellExceeded = sweepOut.sceneFacts.filterIsInstance<SceneEvent.DwellExceeded>()
        assertEquals(1, dwellExceeded.size, "Expected one DwellExceeded fact from sweep")

        val opened = sweepOut.signals.filterIsInstance<SentinelSignal.EpisodeOpened>()
        assertEquals(1, opened.size, "Expected one EpisodeOpened signal")
        assertTrue(sweepOut.recorderCommands.isNotEmpty(), "Sweep-generated episode must reach Recorder")
    }

    @Test
    fun `STANDARD runtime does not trigger on 10 minute sitting`() {
        val rt = ResidentRuntime(elena, bed5, NightId("night-elena-401"), MonitorId("CAM2"), standardCalibrations())

        rt.onObservation(obs(ObservationKind.IN_BED, bed5, t0))
        rt.onObservation(obs(ObservationKind.SITTING_IN_BED, bed5, t0 + Duration.ofMinutes(1)))

        // Sweep at 11 minutes — STANDARD threshold is longer than 11min
        val sweepOut = rt.onTick(t0 + Duration.ofMinutes(11))

        val dwellExceeded = sweepOut.sceneFacts.filterIsInstance<SceneEvent.DwellExceeded>()
        assertTrue(dwellExceeded.isEmpty(), "STANDARD should not trigger on 11min sweep")

        assertTrue(sweepOut.signals.isEmpty(), "No signals expected for STANDARD")
    }

    @Test
    fun `two runtimes with different calibrations produce different results`() {
        val joseRt = ResidentRuntime(jose, bed4, night, cam, fallRiskCalibrations())
        val elenaRt = ResidentRuntime(elena, bed5, NightId("night-elena-401"), MonitorId("CAM2"), standardCalibrations())

        // Both sit for 1 minute, then sweep at 21 minutes
        joseRt.onObservation(obs(ObservationKind.IN_BED, bed4, t0))
        joseRt.onObservation(obs(ObservationKind.SITTING_IN_BED, bed4, t0 + Duration.ofMinutes(1)))
        val joseOut = joseRt.onTick(t0 + Duration.ofMinutes(21))

        elenaRt.onObservation(obs(ObservationKind.IN_BED, bed5, t0))
        elenaRt.onObservation(obs(ObservationKind.SITTING_IN_BED, bed5, t0 + Duration.ofMinutes(1)))
        val elenaOut = elenaRt.onTick(t0 + Duration.ofMinutes(21))

        // José FALL_RISK: dwell exceeded at 20min, sweep at 21min → triggered
        // Elena STANDARD: dwell threshold is longer → not triggered
        val joseDwell = joseOut.sceneFacts.filterIsInstance<SceneEvent.DwellExceeded>()
        val elenaDwell = elenaOut.sceneFacts.filterIsInstance<SceneEvent.DwellExceeded>()
        assertEquals(1, joseDwell.size, "José FALL_RISK should get DwellExceeded at 21min")
        assertEquals(0, elenaDwell.size, "Elena STANDARD should NOT get DwellExceeded at 21min")
    }

    @Test
    fun `recalibrate changes calibration`() {
        val rt = ResidentRuntime(jose, bed4, night, cam, fallRiskCalibrations())
        val originalCal = rt.calibrations

        val stdCal = standardCalibrations()
        rt.recalibrate(stdCal)

        assertNotEquals(originalCal, rt.calibrations)
        assertEquals(stdCal, rt.calibrations)
    }

    @Test
    fun `census maps beds to residents`() {
        val census = Census()
        census.register(bed4, jose, night, cam)
        census.register(bed5, elena, NightId("night-elena-401"), MonitorId("CAM2"))

        assertEquals(2, census.size)
        assertEquals(jose, census.lookup(bed4)?.resident)
        assertEquals(elena, census.lookup(bed5)?.resident)
        assertNull(census.lookup(BedId("bed-99")))
    }

    @Test
    fun `NightWatchRuntime registry`() {
        val runtime = NightWatchRuntime()
        runtime.register(jose, bed4, night, cam, fallRiskCalibrations())
        runtime.register(elena, bed5, NightId("night-elena-401"), MonitorId("CAM2"), standardCalibrations())

        assertEquals(2, runtime.size)
        assertNotNull(runtime.get(jose))
        assertNotNull(runtime.get(elena))
        assertNull(runtime.get(ResidentId("unknown")))
    }
}
