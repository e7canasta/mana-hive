package com.manahive.scene

import com.manahive.contracts.scene.SceneFact
import com.manahive.contracts.scene.SceneFact.NightClosed
import com.manahive.contracts.scene.SceneFact.NightOpened
import com.manahive.contracts.scene.SceneFact.SignalLost
import com.manahive.contracts.scene.SceneFact.SignalRecovered
import com.manahive.contracts.scene.SceneFact.StaffPresenceDetected
import com.manahive.contracts.scene.SceneFact.TransitionDetected
import com.manahive.contracts.scene.SceneFact.DwellExceeded
import com.manahive.contracts.scene.SceneFact.DwellWarning

/**
 * Folds a SceneFact into the DigitalTwin, producing an updated twin.
 *
 * This is a pure function: no side effects, no mutation. The original twin
 * is unchanged; a new twin is returned.
 *
 * Event Sourcing: the twin is the projection of all facts seen so far.
 * Hydration reads the open night's facts and folds them into the initial twin.
 *
 * Example:
 * ```kotlin
 * val twin = bed(3) occupiedBy maria at LYING since time
 * val updated = twin.evolve(TransitionDetected(bed3, night1, time2, LYING, BED_EDGE))
 * // twin.state == LYING (unchanged)
 * // updated.state == BedEdge (new)
 * ```
 */
public fun DigitalTwin.evolve(fact: SceneFact): DigitalTwin = when (fact) {
    is NightOpened -> copy(
        occupant = fact.occupant,
        state = fact.initialState,
        stateSince = fact.stateSince,
    )
    is TransitionDetected -> copy(
        state = fact.to,
        stateSince = fact.at,
    )
    is SignalLost -> copy(signal = signal.copy(lost = true))
    is SignalRecovered -> copy(signal = signal.copy(lost = false))
    is DwellWarning -> this  // Does not change state, only reports
    is DwellExceeded -> this
    is StaffPresenceDetected -> this
    is NightClosed -> this  // Closes the night
}
