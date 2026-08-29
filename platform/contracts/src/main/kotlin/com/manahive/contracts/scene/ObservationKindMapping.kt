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

    // ── Scene state events (don't change PersonState) ─────────
    ObservationKind.STAFF_ENTERED, ObservationKind.STAFF_LEFT, ObservationKind.STAFF_IN_REACH,
    ObservationKind.WHEELCHAIR_PRESENT, ObservationKind.WHEELCHAIR_ABSENT,
    ObservationKind.WALKER_PRESENT, ObservationKind.WALKER_ABSENT,
    ObservationKind.BED_RAILS_UP, ObservationKind.BED_RAILS_DOWN,
    ObservationKind.COVER_ON, ObservationKind.COVER_OFF,
    -> PersonState.Lying  // Scene events don't change person state
}

/**
 * Maps an ObservationKind to the corresponding SceneState change.
 *
 * Returns null if the observation doesn't affect scene state.
 * Returns a function that transforms the current SceneState.
 */
public fun ObservationKind.toSceneStateChange(): ((SceneState) -> SceneState)? = when (this) {
    // ── Staff ─────────────────────────────────────────────────
    ObservationKind.STAFF_ENTERED -> { state -> state.copy(staff = PresenceState.Present) }
    ObservationKind.STAFF_LEFT -> { state -> state.copy(staff = PresenceState.NotPresent) }
    ObservationKind.STAFF_IN_REACH -> { state -> state.copy(staff = PresenceState.InReach) }

    // ── Wheelchair ────────────────────────────────────────────
    ObservationKind.WHEELCHAIR_PRESENT -> { state -> state.copy(wheelchair = PresenceState.Present) }
    ObservationKind.WHEELCHAIR_ABSENT -> { state -> state.copy(wheelchair = PresenceState.NotPresent) }

    // ── Walker ────────────────────────────────────────────────
    ObservationKind.WALKER_PRESENT -> { state -> state.copy(walker = PresenceState.Present) }
    ObservationKind.WALKER_ABSENT -> { state -> state.copy(walker = PresenceState.NotPresent) }

    // ── Bed rails ─────────────────────────────────────────────
    ObservationKind.BED_RAILS_UP -> { state ->
        state.copy(bed = state.bed.copy(left = RailState.Up, right = RailState.Up))
    }
    ObservationKind.BED_RAILS_DOWN -> { state ->
        state.copy(bed = state.bed.copy(left = RailState.Down, right = RailState.Down))
    }

    // ── Cover ─────────────────────────────────────────────────
    ObservationKind.COVER_ON -> { state ->
        state.copy(bed = state.bed.copy(left = RailState.Cover, right = RailState.Cover))
    }
    ObservationKind.COVER_OFF -> { state ->
        state.copy(bed = state.bed.copy(left = RailState.Up, right = RailState.Up))
    }

    // ── Person state events (don't affect scene state) ────────
    ObservationKind.IN_BED, ObservationKind.SITTING_IN_BED, ObservationKind.ATTEMPTING_EXIT,
    ObservationKind.BED_EDGE, ObservationKind.STANDING, ObservationKind.IN_BATHROOM,
    ObservationKind.IN_ROOM, ObservationKind.IN_HALLWAY, ObservationKind.OUTDOOR,
    ObservationKind.IN_CHAIR, ObservationKind.IN_WHEELCHAIR, ObservationKind.OUT_OF_ROOM,
    ObservationKind.HEARTBEAT, ObservationKind.STAFF_IN_ROOM, ObservationKind.UNCLASSIFIED,
    -> null
}
