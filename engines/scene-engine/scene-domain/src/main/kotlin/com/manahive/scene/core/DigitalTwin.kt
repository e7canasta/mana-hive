package com.manahive.scene.core

import com.manahive.contracts.scene.PersonState
import com.manahive.contracts.scene.SceneFact
import com.manahive.contracts.scene.SceneFact.DwellExceeded
import com.manahive.contracts.scene.SceneFact.DwellWarning
import com.manahive.contracts.scene.SceneFact.NightClosed
import com.manahive.contracts.scene.SceneFact.NightOpened
import com.manahive.contracts.scene.SceneFact.SceneDwellExceeded
import com.manahive.contracts.scene.SceneFact.SceneDwellWarning
import com.manahive.contracts.scene.SceneFact.SceneStateChanged
import com.manahive.contracts.scene.SceneFact.SignalLost
import com.manahive.contracts.scene.SceneFact.SignalRecovered
import com.manahive.contracts.scene.SceneFact.StaffPresenceDetected
import com.manahive.contracts.scene.SceneFact.TransitionDetected
import com.manahive.contracts.scene.SceneState
import com.manahive.contracts.scene.kind
import com.manahive.kernel.BedId
import com.manahive.kernel.MonitorId
import com.manahive.kernel.NightId
import com.manahive.kernel.ResidentId
import com.manahive.scene.calibration.SceneCalibration
import com.manahive.scene.sweeper.DwellMarkKey
import java.time.Duration
import java.time.Instant

/**
 * The living model of one bed: who occupies it, in what state, since when,
 * and whether its own eye is alive. Immutable — evolving it is a fold over
 * scene facts; hydration reads the open night only (closing-the-books).
 *
 * No setters, no ORM identity: the twin is a value.
 *
 * Vernon: "In event-sourced aggregates, the entity folds its own events."
 * Evans: "The central aggregate guards its own invariants."
 */
public data class DigitalTwin(
    public val bed: BedId,
    public val night: NightId,
    public val occupant: ResidentId?,
    public val state: PersonState,
    public val stateSince: Instant,
    public val scene: SceneState = SceneState(),
    public val sceneSince: Instant = stateSince,
    public val signal: SignalHealth,
    public val calibration: SceneCalibration? = null,
) {
    /**
     * Folds a SceneFact into the twin, producing an updated twin.
     *
     * Pure function: no side effects, no mutation. The original twin
     * is unchanged; a new twin is returned.
     *
     * Event Sourcing: the twin is the projection of all facts seen so far.
     */
    public fun evolve(fact: SceneFact): DigitalTwin = when (fact) {
        is NightOpened -> copy(
            occupant = fact.occupant,
            state = fact.initialState,
            stateSince = fact.stateSince,
        )
        is TransitionDetected -> copy(
            state = fact.to,
            stateSince = fact.at,
        )
        is SceneStateChanged -> this  // Scene state is updated via evolveScene()
        is SignalLost -> copy(signal = signal.copy(lost = true))
        is SignalRecovered -> copy(signal = signal.copy(lost = false))
        is DwellWarning -> this  // Does not change state, only reports
        is DwellExceeded -> this
        is SceneDwellWarning -> this  // Does not change state, only reports
        is SceneDwellExceeded -> this
        is StaffPresenceDetected -> this
        is NightClosed -> this  // Closes the night
    }

    /**
     * Folds a scene state change into the twin.
     *
     * This is separate from evolve() because scene state changes
     * don't produce SceneFacts directly — they're derived.
     */
    public fun evolveScene(change: (SceneState) -> SceneState, at: Instant): DigitalTwin {
        val newScene = change(scene)
        return if (newScene != scene) {
            copy(scene = newScene, sceneSince = at)
        } else {
            this
        }
    }

    /** Duration in current person state. */
    public fun durationInState(now: Instant): Duration = Duration.between(stateSince, now)

    /** Duration in current scene state. */
    public fun durationInSceneState(now: Instant): Duration = Duration.between(sceneSince, now)

    /** Creates a DwellMarkKey from this twin. */
    public fun toDwellMarkKey(warning: Boolean = false): DwellMarkKey = DwellMarkKey(
        bed = bed,
        state = state.kind,
        since = stateSince,
        warning = warning,
    )

    /** Emits a TransitionDetected fact from this twin. */
    public fun emitTransition(to: PersonState, at: Instant): TransitionDetected = TransitionDetected(
        bed = bed,
        night = night,
        at = at,
        from = state,
        to = to,
    )

    /** Emits a SceneStateChanged fact from this twin. */
    public fun emitSceneStateChanged(field: String, from: String, to: String, at: Instant): SceneStateChanged = SceneStateChanged(
        bed = bed,
        night = night,
        at = at,
        field = field,
        from = from,
        to = to,
    )

    /** Emits a SignalRecovered fact from this twin. */
    public fun emitSignalRecovered(at: Instant): SignalRecovered = SignalRecovered(
        bed = bed,
        night = night,
        at = at,
        monitor = signal.monitor,
    )

    /** Emits a DwellExceeded fact from this twin. */
    public fun emitDwellExceeded(threshold: Duration, at: Instant): DwellExceeded = DwellExceeded(
        bed = bed,
        night = night,
        at = at,
        state = state,
        threshold = threshold,
        since = stateSince,
    )

    /** Emits a DwellWarning fact from this twin. */
    public fun emitDwellWarning(threshold: Duration, at: Instant): DwellWarning = DwellWarning(
        bed = bed,
        night = night,
        at = at,
        state = state,
        threshold = threshold,
        since = stateSince,
    )

    /** Emits a SignalLost fact from this twin. */
    public fun emitSignalLost(at: Instant): SignalLost = SignalLost(
        bed = bed,
        night = night,
        at = at,
        monitor = signal.monitor,
        lastHeartbeat = signal.lastHeartbeat,
    )
}

public data class SignalHealth(
    public val monitor: MonitorId,
    public val lastHeartbeat: Instant,
    public val lost: Boolean,
)
