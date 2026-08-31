package com.manahive.scene.core

import com.manahive.contracts.scene.PersonState
import com.manahive.contracts.scene.SceneEvent
import com.manahive.contracts.scene.SceneEvent.DwellExceeded
import com.manahive.contracts.scene.SceneEvent.DwellWarning
import com.manahive.contracts.scene.SceneEvent.NightClosed
import com.manahive.contracts.scene.SceneEvent.NightOpened
import com.manahive.contracts.scene.SceneEvent.ComeBackExceeded
import com.manahive.contracts.scene.SceneEvent.ComeBackWarning
import com.manahive.contracts.scene.SceneEvent.SceneDwellExceeded
import com.manahive.contracts.scene.SceneEvent.SceneDwellWarning
import com.manahive.contracts.scene.SceneEvent.SceneStateChanged
import com.manahive.contracts.scene.SceneEvent.SignalLost
import com.manahive.contracts.scene.SceneEvent.SignalRecovered
import com.manahive.contracts.scene.SceneEvent.StaffLeftDetected
import com.manahive.contracts.scene.SceneEvent.StaffPresenceDetected
import com.manahive.contracts.scene.SceneEvent.TransitionDetected
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
    /**
     * When the person left the tracked state (e.g., LYING).
     * Null when the person IS in the tracked state.
     *
     * The "mine" is planted on departure. It explodes if the person
     * doesn't return within the return dwell threshold.
     *
     * Set once on transition away from baseline, cleared on return.
     *
     * Fowler: "Derived Value" — computed from state transitions,
     * never set directly by callers.
     */
    public val leftStateAt: Instant? = null,
    /**
     * The baseline state for return dwell calculations.
     * Default: LYING (most residents start and end in bed).
     */
    public val baselineState: PersonState = PersonState.Lying,
) {
    /**
     * Folds a SceneEvent into the twin, producing an updated twin.
     *
     * Pure function: no side effects, no mutation. The original twin
     * is unchanged; a new twin is returned.
     *
     * Event Sourcing: the twin is the projection of all facts seen so far.
     */
    public fun evolve(fact: SceneEvent): DigitalTwin = when (fact) {
        is NightOpened -> copy(
            occupant = fact.occupant,
            state = fact.initialState,
            stateSince = fact.stateSince,
            leftStateAt = if (fact.initialState == baselineState) null else fact.stateSince,
        )
        is TransitionDetected -> {
            val isReturningToBaseline = fact.to == baselineState
            copy(
                state = fact.to,
                stateSince = fact.at,
                // Mine planted on departure, disarmed on return
                leftStateAt = if (isReturningToBaseline) null else leftStateAt ?: fact.at,
            )
        }
        is SceneStateChanged -> this  // Scene state is updated via evolveScene()
        is SignalLost -> copy(signal = signal.copy(lost = true))
        is SignalRecovered -> copy(signal = signal.copy(lost = false))
        is DwellWarning -> this  // Does not change state, only reports
        is DwellExceeded -> this
        is ComeBackWarning -> this  // Does not change state, only reports
        is ComeBackExceeded -> this
        is SceneDwellWarning -> this  // Does not change state, only reports
        is SceneDwellExceeded -> this
        is StaffPresenceDetected -> this
        is StaffLeftDetected -> this
        is NightClosed -> this  // Closes the night
    }

    /**
     * Folds a scene state change into the twin.
     *
     * This is separate from evolve() because scene state changes
     * don't produce SceneEvents directly — they're derived.
     */
    public fun evolveScene(change: (SceneState) -> SceneState, at: Instant): DigitalTwin {
        val newScene = change(scene).stamped(previous = scene, at = at)
        return if (newScene != scene) {
            copy(scene = newScene, sceneSince = at)
        } else {
            this
        }
    }

    /**
     * Cuanto lleva un campo de escena en su estado actual.
     *
     * Null si ese campo nunca fue observado. No es cero: un campo que nadie miro
     * no lleva cero tiempo en un estado, no tiene estado — y una regla de
     * permanencia sobre el no se puede evaluar todavia.
     */
    public fun durationInSceneField(field: String, now: Instant): Duration? =
        scene.durationIn(field, now)

    /** Duration in current person state. */
    public fun durationInState(now: Instant): Duration = Duration.between(stateSince, now)

    /**
     * Cuanto hace que cambio *algo* de la escena.
     *
     * No sirve para permanencia por campo — para eso esta [durationInSceneField].
     * Este reloj se resetea cuando cambia cualquier campo, asi que usarlo para
     * medir cuanto lleva la baranda abajo da la respuesta equivocada apenas se
     * mueva la silla.
     */
    public fun durationInSceneState(now: Instant): Duration = Duration.between(sceneSince, now)

    /**
     * Duration since leaving the baseline state.
     * Returns null if the person IS in the baseline state (mine not planted).
     *
     * Used by ClockSweeper for return dwell (inverse dwell).
     */
    public fun durationSinceLeftBaseline(now: Instant): Duration? {
        if (state == baselineState) return null  // Mine not planted
        val since = leftStateAt ?: return null
        return Duration.between(since, now)
    }

    /** Creates a DwellMarkKey from this twin. */
    public fun toDwellMarkKey(warning: Boolean = false): DwellMarkKey = DwellMarkKey(
        bed = bed,
        state = state.kind,
        since = stateSince,
        warning = warning,
    )

    public fun toSnapshot(): com.manahive.contracts.scene.TwinSnapshot = com.manahive.contracts.scene.TwinSnapshot(
        state = state,
        stateSince = stateSince,
        scene = scene,
        sceneSince = sceneSince,
        signalLost = signal.lost,
        signalLastHeartbeat = signal.lastHeartbeat,
        monitor = signal.monitor,
    )

    /** Emits a TransitionDetected fact from this twin. */
    public fun emitTransition(to: PersonState, at: Instant): TransitionDetected {
        val snapshot = copy(state = to, stateSince = at).toSnapshot()
        return TransitionDetected(
            bed = bed,
            night = night,
            at = at,
            from = state,
            to = to,
            twinSnapshot = snapshot,
        )
    }

    /** Emits a SceneStateChanged fact from this twin. */
    public fun emitSceneStateChanged(field: String, from: String, to: String, at: Instant): SceneStateChanged = SceneStateChanged(
        bed = bed,
        night = night,
        at = at,
        field = field,
        from = from,
        to = to,
        twinSnapshot = toSnapshot(),
    )

    /** Emits a SignalRecovered fact from this twin. */
    public fun emitSignalRecovered(at: Instant): SignalRecovered = SignalRecovered(
        bed = bed,
        night = night,
        at = at,
        monitor = signal.monitor,
        twinSnapshot = toSnapshot(),
    )

    /** Emits a DwellExceeded fact from this twin. */
    public fun emitDwellExceeded(threshold: Duration, at: Instant): DwellExceeded = DwellExceeded(
        bed = bed,
        night = night,
        at = at,
        state = state,
        threshold = threshold,
        since = stateSince,
        twinSnapshot = toSnapshot(),
    )

    /** Emits a DwellWarning fact from this twin. */
    public fun emitDwellWarning(threshold: Duration, at: Instant): DwellWarning = DwellWarning(
        bed = bed,
        night = night,
        at = at,
        state = state,
        threshold = threshold,
        since = stateSince,
        twinSnapshot = toSnapshot(),
    )

    /** Emits a SignalLost fact from this twin. */
    public fun emitSignalLost(at: Instant): SignalLost = SignalLost(
        bed = bed,
        night = night,
        at = at,
        monitor = signal.monitor,
        lastHeartbeat = signal.lastHeartbeat,
        twinSnapshot = toSnapshot(),
    )

    /** Emits a ComeBackWarning fact from this twin. */
    public fun emitComeBackWarning(threshold: Duration, at: Instant): ComeBackWarning = ComeBackWarning(
        bed = bed,
        night = night,
        at = at,
        baseline = baselineState,
        threshold = threshold,
        since = leftStateAt ?: stateSince,
        twinSnapshot = toSnapshot(),
    )

    /** Emits a ComeBackExceeded fact from this twin. */
    public fun emitComeBackExceeded(threshold: Duration, at: Instant): ComeBackExceeded = ComeBackExceeded(
        bed = bed,
        night = night,
        at = at,
        baseline = baselineState,
        threshold = threshold,
        since = leftStateAt ?: stateSince,
        twinSnapshot = toSnapshot(),
    )
}

public data class SignalHealth(
    public val monitor: MonitorId,
    public val lastHeartbeat: Instant,
    public val lost: Boolean,
)
