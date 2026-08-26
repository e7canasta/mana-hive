package com.manahive.serialization

import com.manahive.contracts.scene.PersonState
import com.manahive.contracts.scene.StateKind

/**
 * Type-safe input for state kinds.
 *
 * Vernon: "Value Object" — encapsulates parsing logic.
 * Allows both enum values and string inputs for flexibility.
 */
sealed interface StateKindInput {
    data class Kind(val kind: StateKind) : StateKindInput
    data class Person(val person: PersonState) : StateKindInput
    data class Str(val name: String) : StateKindInput

    fun toStateKind(): SerializationResult<StateKind> = when (this) {
        is Kind -> SerializationResult.Success(kind)
        is Person -> SerializationResult.Success(person.toStateKind())
        is Str -> parseStateKind(name)
    }

    fun toPersonState(): SerializationResult<PersonState> = when (this) {
        is Kind -> SerializationResult.Success(kind.toPersonState())
        is Person -> SerializationResult.Success(person)
        is Str -> parsePersonState(name)
    }

    companion object {
        private val VALID_STATE_KINDS = StateKind.values().map { it.name }.toSet()
        private val VALID_PERSON_STATES = PersonState::class.sealedSubclasses
            .mapNotNull { it.simpleName }
            .toSet()

        fun parseStateKind(name: String): SerializationResult<StateKind> {
            return try {
                SerializationResult.Success(StateKind.valueOf(name.uppercase()))
            } catch (_: IllegalArgumentException) {
                SerializationResult.Failure(
                    SerializationError.InvalidState(name, VALID_STATE_KINDS)
                )
            }
        }

        fun parsePersonState(name: String): SerializationResult<PersonState> {
            return when (name) {
                "Lying" -> SerializationResult.Success(PersonState.Lying)
                "SittingInBed" -> SerializationResult.Success(PersonState.SittingInBed)
                "AttemptingExit" -> SerializationResult.Success(PersonState.AttemptingExit)
                "BedEdge" -> SerializationResult.Success(PersonState.BedEdge)
                "Standing" -> SerializationResult.Success(PersonState.Standing)
                "InBathroom" -> SerializationResult.Success(PersonState.InBathroom)
                "InRoom" -> SerializationResult.Success(PersonState.InRoom)
                "InHallway" -> SerializationResult.Success(PersonState.InHallway)
                "Outdoor" -> SerializationResult.Success(PersonState.Outdoor)
                "Absent" -> SerializationResult.Success(PersonState.Absent)
                "InChair" -> SerializationResult.Success(PersonState.InChair)
                "InWheelchair" -> SerializationResult.Success(PersonState.InWheelchair)
                else -> SerializationResult.Failure(
                    SerializationError.InvalidState(name, VALID_PERSON_STATES)
                )
            }
        }
    }
}

/**
 * Extension functions for StateKind.
 */
fun StateKind.toPersonState(): PersonState = when (this) {
    StateKind.LYING -> PersonState.Lying
    StateKind.SITTING_IN_BED -> PersonState.SittingInBed
    StateKind.ATTEMPTING_EXIT -> PersonState.AttemptingExit
    StateKind.BED_EDGE -> PersonState.BedEdge
    StateKind.STANDING -> PersonState.Standing
    StateKind.IN_BATHROOM -> PersonState.InBathroom
    StateKind.IN_ROOM -> PersonState.InRoom
    StateKind.IN_HALLWAY -> PersonState.InHallway
    StateKind.OUTDOOR -> PersonState.Outdoor
    StateKind.ABSENT -> PersonState.Absent
    StateKind.IN_CHAIR -> PersonState.InChair
    StateKind.IN_WHEELCHAIR -> PersonState.InWheelchair
    StateKind.UNKNOWN -> PersonState.Unknown(com.manahive.contracts.scene.UnknownCause.SIGNAL_LOST)
}

fun PersonState.toStateKind(): StateKind = when (this) {
    is PersonState.Lying -> StateKind.LYING
    is PersonState.SittingInBed -> StateKind.SITTING_IN_BED
    is PersonState.AttemptingExit -> StateKind.ATTEMPTING_EXIT
    is PersonState.BedEdge -> StateKind.BED_EDGE
    is PersonState.Standing -> StateKind.STANDING
    is PersonState.InBathroom -> StateKind.IN_BATHROOM
    is PersonState.InRoom -> StateKind.IN_ROOM
    is PersonState.InHallway -> StateKind.IN_HALLWAY
    is PersonState.Outdoor -> StateKind.OUTDOOR
    is PersonState.Absent -> StateKind.ABSENT
    is PersonState.InChair -> StateKind.IN_CHAIR
    is PersonState.InWheelchair -> StateKind.IN_WHEELCHAIR
    is PersonState.Unknown -> StateKind.UNKNOWN
}
