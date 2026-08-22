package com.manahive.contracts.scene

/**
 * The person FSM: thirteen states matching the clinical catalog.
 *
 * "Absence of information is not information": Unknown carries its cause,
 * because a resident lost by the scene and a dead sensor are OPPOSITE risks.
 *
 * IN BED (in_bed):
 * - Lying:         fully reclined
 * - SittingInBed:  propped up or sitting in bed (incorporado)
 * - AttemptingExit: trying to exit — arms/face at the edge, "gusanito" movement
 * - BedEdge:       seated on the edge of the bed
 *
 * OUT OF BED (out_of_bed):
 * - Standing:    upright, location unspecified
 * - InBathroom:  in the bathroom
 * - InRoom:      in the room but not in bed/bathroom
 * - InHallway:   in the corridor
 * - Outdoor:     outside the building
 * - Absent:      left the room, location undetermined
 *
 * FURNITURE:
 * - InChair:     seated in a regular chair
 * - InWheelchair: seated in a wheelchair
 *
 * UNKNOWN:
 * - Unknown:     cannot determine (carries cause)
 */
public sealed interface PersonState {
    // ── In bed ────────────────────────────────────────────────────────
    public data object Lying : PersonState
    public data object SittingInBed : PersonState
    public data object AttemptingExit : PersonState
    public data object BedEdge : PersonState

    // ── Out of bed ────────────────────────────────────────────────────
    public data object Standing : PersonState
    public data object InBathroom : PersonState
    public data object InRoom : PersonState
    public data object InHallway : PersonState
    public data object Outdoor : PersonState
    public data object Absent : PersonState

    // ── Furniture ─────────────────────────────────────────────────────
    public data object InChair : PersonState
    public data object InWheelchair : PersonState

    // ── Unknown ───────────────────────────────────────────────────────
    public data class Unknown(public val cause: UnknownCause) : PersonState
}

public enum class UnknownCause { SIGNAL_LOST, SCENE }

/**
 * Stable kind for tables, maps and wire format (sealed classes make poor map keys).
 *
 * The 13 states mirror [PersonState] exactly.
 */
public enum class StateKind {
    LYING, SITTING_IN_BED, ATTEMPTING_EXIT, BED_EDGE,
    STANDING, IN_BATHROOM, IN_ROOM, IN_HALLWAY, OUTDOOR, ABSENT,
    IN_CHAIR, IN_WHEELCHAIR,
    UNKNOWN,
}

public val PersonState.kind: StateKind
    get() = when (this) {
        PersonState.Lying          -> StateKind.LYING
        PersonState.SittingInBed   -> StateKind.SITTING_IN_BED
        PersonState.AttemptingExit -> StateKind.ATTEMPTING_EXIT
        PersonState.BedEdge        -> StateKind.BED_EDGE
        PersonState.Standing       -> StateKind.STANDING
        PersonState.InBathroom     -> StateKind.IN_BATHROOM
        PersonState.InRoom         -> StateKind.IN_ROOM
        PersonState.InHallway      -> StateKind.IN_HALLWAY
        PersonState.Outdoor        -> StateKind.OUTDOOR
        PersonState.Absent         -> StateKind.ABSENT
        PersonState.InChair        -> StateKind.IN_CHAIR
        PersonState.InWheelchair   -> StateKind.IN_WHEELCHAIR
        is PersonState.Unknown     -> StateKind.UNKNOWN
    }

public enum class RiskGroup { SAFE, AT_RISK, UNKNOWN }

public val PersonState.riskGroup: RiskGroup
    get() = when (this) {
        PersonState.Lying -> RiskGroup.SAFE
        is PersonState.Unknown -> RiskGroup.UNKNOWN
        else -> RiskGroup.AT_RISK
    }
