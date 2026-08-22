package com.manahive.scene.batch.config

import com.manahive.contracts.scene.PersonState
import com.manahive.contracts.scene.StateKind
import com.manahive.kernel.BedId
import com.manahive.kernel.MonitorId
import com.manahive.kernel.NightId
import com.manahive.kernel.ResidentId
import com.manahive.scene.batch.BatchError
import com.manahive.scene.calibration.Confidence
import com.manahive.scene.calibration.ConfidenceThresholds
import com.manahive.scene.calibration.SceneCalibration
import com.manahive.scene.core.DigitalTwin
import com.manahive.scene.core.SignalHealth
import com.manahive.scene.core.TransitionTable
import java.time.Duration
import java.time.Instant

/**
 * Root configuration for a scene-batch run.
 *
 * Rich Domain Model (Evans): the config knows how to create its own
 * domain objects — SceneCalibration, DigitalTwin — without leaking
 * framework details.
 *
 * Self-Validating Entity (Vernon): validates invariants on creation.
 *
 * ```kotlin
 * val config = sceneConfig {
 *     scene {
 *         bed = "bed-1"
 *         night = "night-1"
 *         resident = "maria"
 *         monitor = "m1"
 *     }
 *     calibration {
 *         transitions = TransitionTable.RELEASE_2
 *         confidence(StateKind.BED_EDGE) min 0.8
 *         dwell {
 *             STANDING warning Duration.ofMinutes(4) exceeded Duration.ofMinutes(5)
 *         }
 *     }
 *     events {
 *         source = "events.dat"
 *         output = "output"
 *     }
 * }
 * ```
 */
data class BatchConfig(
    val scene: SceneConfig,
    val calibration: CalibrationConfig,
    val events: EventsConfig,
) {
    init {
        require(scene.bed.isNotBlank()) { "scene.bed must not be blank" }
        require(scene.night.isNotBlank()) { "scene.night must not be blank" }
        require(scene.resident.isNotBlank()) { "scene.resident must not be blank" }
        require(scene.monitor.isNotBlank()) { "scene.monitor must not be blank" }
        require(events.source.isNotBlank()) { "events.source must not be blank" }
    }

    // ── Convenience Accessors (Fowler: Move Field) ────────────────────────

    val bedId: BedId get() = BedId(scene.bed)
    val nightId: NightId get() = NightId(scene.night)
    val monitorId: MonitorId get() = MonitorId(scene.monitor)
    val residentId: ResidentId get() = ResidentId(scene.resident)
    val startTime: Instant get() = events.start ?: Instant.now()

    // ── Domain Object Creation ────────────────────────────────────────────

    /** Creates a [SceneCalibration] from this config. */
    fun toSceneCalibration(): SceneCalibration = SceneCalibration(
        table = calibration.transitionTable,
        confidence = ConfidenceThresholds(
            calibration.confidence.mapKeys { it.key }
                .mapValues { Confidence(it.value) }
        ),
        heartbeatTimeout = calibration.heartbeatTimeout,
        dwellThresholds = calibration.dwellThresholds,
    )

    /** Creates a [DigitalTwin] from this config at the given start time. */
    fun toDigitalTwin(startTime: Instant = this.startTime): DigitalTwin = DigitalTwin(
        bed = bedId,
        night = nightId,
        occupant = residentId,
        state = PersonState.Lying,
        stateSince = startTime,
        signal = SignalHealth(monitorId, startTime.minusSeconds(60), false),
    )
}

data class SceneConfig(
    val bed: String,
    val night: String,
    val resident: String,
    val monitor: String,
)

data class CalibrationConfig(
    val transitionTable: TransitionTable = TransitionTable.RELEASE_2,
    val confidence: Map<StateKind, Double> = emptyMap(),
    val dwellThresholds: Map<StateKind, com.manahive.contracts.policy.DwellThreshold> = emptyMap(),
    val heartbeatTimeout: Duration = Duration.ofSeconds(90),
)

data class EventsConfig(
    val source: String,
    val output: String = "output",
    val start: Instant? = null,
)
