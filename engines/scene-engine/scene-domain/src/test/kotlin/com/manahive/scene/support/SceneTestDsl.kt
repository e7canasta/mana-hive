package com.manahive.scene.support

import com.manahive.contracts.perception.Observation
import com.manahive.contracts.perception.ObservationKind
import com.manahive.contracts.scene.PersonState
import com.manahive.contracts.scene.StateKind
import com.manahive.contracts.scene.UnknownCause
import com.manahive.kernel.BedId
import com.manahive.kernel.MonitorId
import com.manahive.kernel.NightId
import com.manahive.kernel.ResidentId
import com.manahive.scene.calibration.SceneCalibration
import com.manahive.scene.calibration.dsl.SceneDsl
import com.manahive.scene.core.DigitalTwin
import com.manahive.scene.core.SignalHealth
import java.time.Instant

/**
 * Test DSL for building scene engine objects.
 *
 * ```kotlin
 * val twin = bed(3) occupiedBy maria at LYING since time
 * val obs = obs(BED_EDGE, 0.9) at time
 * ```
 */
@SceneDsl
object SceneTestDsl {

    // --- Constants ---

    val monitor1: MonitorId = MonitorId("m1")
    val night1: NightId = NightId("night-1")
    val bed3: BedId = BedId("bed-3")
    val maria: ResidentId = ResidentId("maria")
    val jose: ResidentId = ResidentId("jose")

    // --- Time constants ---

    val time00_00_00: Instant = Instant.parse("2024-01-01T00:00:00Z")
    val time02_58_00: Instant = Instant.parse("2024-01-01T02:58:00Z")
    val time02_59_30: Instant = Instant.parse("2024-01-01T02:59:30Z")
    val time02_59_58: Instant = Instant.parse("2024-01-01T02:59:58Z")
    val time03_00_00: Instant = Instant.parse("2024-01-01T03:00:00Z")
    val time03_00_01: Instant = Instant.parse("2024-01-01T03:00:01Z")
    val time03_00_02: Instant = Instant.parse("2024-01-01T03:00:02Z")
    val time03_00_05: Instant = Instant.parse("2024-01-01T03:00:05Z")
    val time03_02_30: Instant = Instant.parse("2024-01-01T03:02:30Z")
    val time03_03_00: Instant = Instant.parse("2024-01-01T03:03:00Z")
    val time03_04_00: Instant = Instant.parse("2024-01-01T03:04:00Z")
    val time03_05_00: Instant = Instant.parse("2024-01-01T03:05:00Z")

    // --- StateKind -> PersonState mapping ---

    fun StateKind.toPersonState(): PersonState = when (this) {
        StateKind.LYING          -> PersonState.Lying
        StateKind.SITTING_IN_BED -> PersonState.SittingInBed
        StateKind.ATTEMPTING_EXIT -> PersonState.AttemptingExit
        StateKind.BED_EDGE       -> PersonState.BedEdge
        StateKind.STANDING       -> PersonState.Standing
        StateKind.IN_BATHROOM    -> PersonState.InBathroom
        StateKind.IN_ROOM        -> PersonState.InRoom
        StateKind.IN_HALLWAY     -> PersonState.InHallway
        StateKind.OUTDOOR        -> PersonState.Outdoor
        StateKind.ABSENT         -> PersonState.Absent
        StateKind.IN_CHAIR       -> PersonState.InChair
        StateKind.IN_WHEELCHAIR  -> PersonState.InWheelchair
        StateKind.UNKNOWN        -> PersonState.Unknown(UnknownCause.SCENE)
    }

    // --- DigitalTwin builder ---

    fun bed(number: Int): BedBuilder = BedBuilder(BedId("bed-$number"))

    class BedBuilder(private val bed: BedId) {
        infix fun occupiedBy(resident: ResidentId): TwinBuilder = TwinBuilder(bed, resident)
    }

    class TwinBuilder(private val bed: BedId, private val resident: ResidentId) {
        infix fun at(state: StateKind): TwinStateBuilder = TwinStateBuilder(bed, resident, state)
    }

    class TwinStateBuilder(
        private val bed: BedId,
        private val resident: ResidentId,
        private val state: StateKind,
    ) {
        private var calibration: SceneCalibration? = null

        infix fun withCalibration(cal: SceneCalibration): TwinStateBuilder {
            calibration = cal
            return this
        }

        infix fun since(time: Instant): DigitalTwin = DigitalTwin(
            bed = bed,
            night = night1,
            occupant = resident,
            state = state.toPersonState(),
            stateSince = time,
            signal = SignalHealth(monitor1, time.minusSeconds(60), false),
            calibration = calibration,
        )
    }

    // --- Observation builder ---

    fun obs(kind: ObservationKind, confidence: Double): ObsBuilder = ObsBuilder(kind, confidence)

    class ObsBuilder(private val kind: ObservationKind, private val confidence: Double) {
        infix fun at(time: Instant): Observation = Observation(
            sourceEventId = "test-${System.nanoTime()}",
            monitor = monitor1,
            bed = bed3,
            kind = kind,
            confidence = confidence,
            observedAt = time,
        )
    }
}

// --- Extension functions for DigitalTwin ---

fun DigitalTwin.withSignalLost(): DigitalTwin = copy(signal = signal.copy(lost = true))

fun DigitalTwin.withSignal(
    lastHeartbeat: Instant = signal.lastHeartbeat,
    lost: Boolean = signal.lost,
): DigitalTwin = copy(signal = signal.copy(lastHeartbeat = lastHeartbeat, lost = lost))

fun DigitalTwin.withState(
    state: PersonState,
    stateSince: Instant = this.stateSince,
): DigitalTwin = copy(state = state, stateSince = stateSince)
