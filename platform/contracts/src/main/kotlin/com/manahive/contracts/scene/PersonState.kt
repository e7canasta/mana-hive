package com.manahive.contracts.scene

/**
 * The person FSM, release 1: five states. The legacy system's eleven states
 * enter when a story requires them — the catalog is a reference, not a debt.
 *
 * "Absence of information is not information": Unknown carries its cause,
 * because a resident lost by the scene and a dead sensor are OPPOSITE risks.
 */
public sealed interface PersonState {
    public data object Lying : PersonState
    public data object BedEdge : PersonState
    public data object Standing : PersonState
    public data object Absent : PersonState
    public data class Unknown(public val cause: UnknownCause) : PersonState
}

public enum class UnknownCause { SIGNAL_LOST, SCENE }

/** Stable kind for tables, maps and wire format (sealed classes make poor map keys). */
public enum class StateKind { LYING, BED_EDGE, STANDING, ABSENT, UNKNOWN }

public val PersonState.kind: StateKind
    get() = when (this) {
        PersonState.Lying -> StateKind.LYING
        PersonState.BedEdge -> StateKind.BED_EDGE
        PersonState.Standing -> StateKind.STANDING
        PersonState.Absent -> StateKind.ABSENT
        is PersonState.Unknown -> StateKind.UNKNOWN
    }

public enum class RiskGroup { SAFE, AT_RISK, UNKNOWN }

public val PersonState.riskGroup: RiskGroup
    get() = when (this) {
        PersonState.Lying -> RiskGroup.SAFE
        is PersonState.Unknown -> RiskGroup.UNKNOWN
        else -> RiskGroup.AT_RISK
    }
