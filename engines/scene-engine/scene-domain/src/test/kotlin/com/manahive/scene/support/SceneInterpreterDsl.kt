package com.manahive.scene.support

import com.manahive.contracts.scene.StateKind
import com.manahive.contracts.scene.UnknownCause
import com.manahive.kernel.BedId
import com.manahive.kernel.MonitorId
import com.manahive.kernel.NightId
import com.manahive.kernel.ResidentId
import com.manahive.scene.calibration.SceneCalibration
import com.manahive.scene.calibration.dsl.CalibrationBuilder
import com.manahive.scene.calibration.dsl.SceneDsl
import com.manahive.scene.calibration.dsl.calibration
import com.manahive.scene.core.DigitalTwin
import com.manahive.scene.core.SignalHealth
import com.manahive.scene.core.TransitionTable
import java.time.Instant

/**
 * DSL for building a [SceneCalibration] inline.
 *
 * ```kotlin
 * val cal = buildCalibration {
 *     table = TransitionTable.RELEASE_1
 *     confidence(BED_EDGE) min 0.8
 * }
 * ```
 */
public fun buildCalibration(block: CalibrationBuilder.() -> Unit): SceneCalibration =
    calibration(block)

/**
 * DSL for creating a default [DigitalTwin] for tests.
 *
 * ```kotlin
 * val twin = buildTwin {
 *     bed = 3
 *     occupant = maria
 *     state = LYING
 *     since = time
 * }
 * ```
 */
public fun buildTwin(block: TwinConfig.() -> Unit): DigitalTwin =
    TwinConfig().apply(block).build()

@SceneDsl
public class TwinConfig {
    public var bed: Int = 0
    public var occupant: ResidentId? = null
    public var state: StateKind = StateKind.LYING
    public var since: Instant = Instant.EPOCH
    public var monitor: MonitorId = MonitorId("m0")
    public var night: NightId = NightId("night-0")
    public var lastHeartbeat: Instant? = null
    public var signalLost: Boolean = false

    internal fun build(): DigitalTwin = DigitalTwin(
        bed = BedId("bed-$bed"),
        night = night,
        occupant = occupant,
        // Delegado en la funcion canonica de contracts: habia cinco copias de
        // este mapeo en el repo y cada estado nuevo obligaba a tocarlas todas.
        state = com.manahive.contracts.scene.personStateFromKind(state),
        stateSince = since,
        signal = SignalHealth(
            monitor = monitor,
            lastHeartbeat = lastHeartbeat ?: since.minusSeconds(60),
            lost = signalLost,
        ),
    )
}
