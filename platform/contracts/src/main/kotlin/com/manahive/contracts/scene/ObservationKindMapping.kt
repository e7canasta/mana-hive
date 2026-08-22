package com.manahive.contracts.scene

import com.manahive.contracts.perception.ObservationKind

/**
 * Maps an ObservationKind to the corresponding PersonState.
 *
 * This is an Anti-Corruption Layer between the perception bounded context
 * and the scene bounded context. The perception module doesn't know about
 * PersonState; this function translates between the two vocabularies.
 *
 * Example:
 * ```kotlin
 * ObservationKind.IN_BED.toPersonState() // PersonState.Lying
 * ObservationKind.STANDING.toPersonState() // PersonState.Standing
 * ```
 */
public fun ObservationKind.toPersonState(): PersonState = when (this) {
    // ── In bed ────────────────────────────────────────────────
    ObservationKind.IN_BED          -> PersonState.Lying
    ObservationKind.SITTING_IN_BED  -> PersonState.SittingInBed
    ObservationKind.ATTEMPTING_EXIT -> PersonState.AttemptingExit
    ObservationKind.BED_EDGE        -> PersonState.BedEdge

    // ── Out of bed ────────────────────────────────────────────
    ObservationKind.STANDING        -> PersonState.Standing
    ObservationKind.IN_BATHROOM     -> PersonState.InBathroom
    ObservationKind.IN_ROOM         -> PersonState.InRoom
    ObservationKind.IN_HALLWAY      -> PersonState.InHallway
    ObservationKind.OUTDOOR         -> PersonState.Outdoor

    // ── Furniture ─────────────────────────────────────────────
    ObservationKind.IN_CHAIR        -> PersonState.InChair
    ObservationKind.IN_WHEELCHAIR   -> PersonState.InWheelchair

    // ── Meta ──────────────────────────────────────────────────
    ObservationKind.OUT_OF_ROOM    -> PersonState.Absent        // Left room, location undetermined
    ObservationKind.HEARTBEAT      -> PersonState.Lying         // Heartbeat doesn't change state
    ObservationKind.STAFF_IN_ROOM  -> PersonState.Lying         // Staff doesn't affect person state
    ObservationKind.UNCLASSIFIED   -> PersonState.Unknown(UnknownCause.SCENE)
}
