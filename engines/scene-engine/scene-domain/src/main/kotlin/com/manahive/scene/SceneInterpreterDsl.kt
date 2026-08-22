package com.manahive.scene

import com.manahive.contracts.scene.StateKind
import com.manahive.contracts.scene.UnknownCause
import com.manahive.kernel.BedId
import com.manahive.kernel.MonitorId
import com.manahive.kernel.NightId
import com.manahive.kernel.ResidentId
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
        state = when (state) {
            StateKind.LYING          -> com.manahive.contracts.scene.PersonState.Lying
            StateKind.SITTING_IN_BED -> com.manahive.contracts.scene.PersonState.SittingInBed
            StateKind.ATTEMPTING_EXIT -> com.manahive.contracts.scene.PersonState.AttemptingExit
            StateKind.BED_EDGE       -> com.manahive.contracts.scene.PersonState.BedEdge
            StateKind.STANDING       -> com.manahive.contracts.scene.PersonState.Standing
            StateKind.IN_BATHROOM    -> com.manahive.contracts.scene.PersonState.InBathroom
            StateKind.IN_ROOM        -> com.manahive.contracts.scene.PersonState.InRoom
            StateKind.IN_HALLWAY     -> com.manahive.contracts.scene.PersonState.InHallway
            StateKind.OUTDOOR        -> com.manahive.contracts.scene.PersonState.Outdoor
            StateKind.ABSENT         -> com.manahive.contracts.scene.PersonState.Absent
            StateKind.IN_CHAIR       -> com.manahive.contracts.scene.PersonState.InChair
            StateKind.IN_WHEELCHAIR  -> com.manahive.contracts.scene.PersonState.InWheelchair
            StateKind.UNKNOWN        -> com.manahive.contracts.scene.PersonState.Unknown(UnknownCause.SCENE)
        },
        stateSince = since,
        signal = SignalHealth(
            monitor = monitor,
            lastHeartbeat = lastHeartbeat ?: since.minusSeconds(60),
            lost = signalLost,
        ),
    )
}
