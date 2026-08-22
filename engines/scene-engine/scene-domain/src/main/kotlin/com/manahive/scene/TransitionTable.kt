package com.manahive.scene

import com.manahive.contracts.policy.TransitionKey
import com.manahive.contracts.scene.StateKind
import java.time.Duration

/**
 * The person FSM as a TOTAL table: legality plus minimum hysteresis per
 * transition. Data, not a class hierarchy — clinicians read this table, the
 * simulator prints it, and an illegal transition is sensor noise to discard
 * with cause, never to apply.
 *
 * Fowler: "Primitive Obsession" → Use TransitionKey instead of Pair.
 * Vernon: "Value Object" — stable hashCode, equals by value.
 */
public data class TransitionTable(
    private val legal: Map<TransitionKey, Duration>,
) {
    public fun isLegal(from: StateKind, to: StateKind): Boolean = legal.containsKey(TransitionKey(from, to))

    /** Minimum time the new observation must persist before the transition is believed. */
    public fun hysteresis(from: StateKind, to: StateKind): Duration =
        legal[TransitionKey(from, to)] ?: error("illegal transition $from -> $to has no hysteresis")

    public companion object {
        /** Release 1: the five-state table the 03:00 fall needs. */
        public val RELEASE_1: TransitionTable = TransitionTable(
            mapOf(
                TransitionKey(StateKind.LYING, StateKind.BED_EDGE) to Duration.ofMillis(1500),
                TransitionKey(StateKind.BED_EDGE, StateKind.LYING) to Duration.ofMillis(1000),
                TransitionKey(StateKind.BED_EDGE, StateKind.STANDING) to Duration.ofMillis(1200),
                TransitionKey(StateKind.STANDING, StateKind.BED_EDGE) to Duration.ofMillis(1200),
                TransitionKey(StateKind.STANDING, StateKind.ABSENT) to Duration.ofMillis(2000),
                TransitionKey(StateKind.ABSENT, StateKind.LYING) to Duration.ofMillis(2000),
                TransitionKey(StateKind.UNKNOWN, StateKind.LYING) to Duration.ofMillis(2000),
            ),
        )

        /**
         * Release 2: the thirteen-state table for the clinical catalog.
         *
         * IN BED: Lying, SittingInBed, AttemptingExit, BedEdge
         * OUT OF BED: Standing, InBathroom, InRoom, InHallway, Outdoor, Absent
         * FURNITURE: InChair, InWheelchair
         * UNKNOWN: Unknown
         *
         * Clinical rules (permissive — sensors report what they see):
         * - From bed states, can reach other bed states or Standing
         * - From Standing, can reach any out-of-bed or furniture state
         * - From any out-of-bed state, can reach Standing or other out-of-bed states
         * - Furniture states connect to Standing and other furniture/room states
         * - Unknown recovers to Lying or Standing
         * - Transitions that skip intermediate states are allowed (e.g., SittingInBed → Standing)
         */
        public val RELEASE_2: TransitionTable = TransitionTable(
            mapOf(
                // ── In bed transitions ──────────────────────────────────
                // Lying → bed states
                TransitionKey(StateKind.LYING, StateKind.SITTING_IN_BED) to Duration.ofMillis(1500),
                TransitionKey(StateKind.LYING, StateKind.ATTEMPTING_EXIT) to Duration.ofMillis(1500),
                TransitionKey(StateKind.LYING, StateKind.BED_EDGE) to Duration.ofMillis(1500),

                // SittingInBed → bed states or Standing
                TransitionKey(StateKind.SITTING_IN_BED, StateKind.LYING) to Duration.ofMillis(1000),
                TransitionKey(StateKind.SITTING_IN_BED, StateKind.ATTEMPTING_EXIT) to Duration.ofMillis(1200),
                TransitionKey(StateKind.SITTING_IN_BED, StateKind.BED_EDGE) to Duration.ofMillis(1200),
                TransitionKey(StateKind.SITTING_IN_BED, StateKind.STANDING) to Duration.ofMillis(1500),

                // AttemptingExit → bed states or Standing
                TransitionKey(StateKind.ATTEMPTING_EXIT, StateKind.LYING) to Duration.ofMillis(1000),
                TransitionKey(StateKind.ATTEMPTING_EXIT, StateKind.SITTING_IN_BED) to Duration.ofMillis(1200),
                TransitionKey(StateKind.ATTEMPTING_EXIT, StateKind.BED_EDGE) to Duration.ofMillis(1200),
                TransitionKey(StateKind.ATTEMPTING_EXIT, StateKind.STANDING) to Duration.ofMillis(1500),

                // BedEdge → bed states or Standing
                TransitionKey(StateKind.BED_EDGE, StateKind.LYING) to Duration.ofMillis(1000),
                TransitionKey(StateKind.BED_EDGE, StateKind.SITTING_IN_BED) to Duration.ofMillis(1000),
                TransitionKey(StateKind.BED_EDGE, StateKind.STANDING) to Duration.ofMillis(1200),

                // ── Out of bed transitions ──────────────────────────────
                // Standing → out-of-bed, furniture, or Absent
                TransitionKey(StateKind.STANDING, StateKind.BED_EDGE) to Duration.ofMillis(1200),
                TransitionKey(StateKind.STANDING, StateKind.IN_BATHROOM) to Duration.ofMillis(2000),
                TransitionKey(StateKind.STANDING, StateKind.IN_ROOM) to Duration.ofMillis(2000),
                TransitionKey(StateKind.STANDING, StateKind.IN_HALLWAY) to Duration.ofMillis(2000),
                TransitionKey(StateKind.STANDING, StateKind.OUTDOOR) to Duration.ofMillis(2000),
                TransitionKey(StateKind.STANDING, StateKind.ABSENT) to Duration.ofMillis(2000),
                TransitionKey(StateKind.STANDING, StateKind.IN_CHAIR) to Duration.ofMillis(1500),
                TransitionKey(StateKind.STANDING, StateKind.IN_WHEELCHAIR) to Duration.ofMillis(1500),

                // InBathroom → Standing or other out-of-bed
                TransitionKey(StateKind.IN_BATHROOM, StateKind.STANDING) to Duration.ofMillis(2000),
                TransitionKey(StateKind.IN_BATHROOM, StateKind.IN_ROOM) to Duration.ofMillis(2000),
                TransitionKey(StateKind.IN_BATHROOM, StateKind.IN_HALLWAY) to Duration.ofMillis(2000),
                TransitionKey(StateKind.IN_BATHROOM, StateKind.OUTDOOR) to Duration.ofMillis(2000),

                // InRoom → Standing, bathroom, hallway, outdoor, furniture
                TransitionKey(StateKind.IN_ROOM, StateKind.STANDING) to Duration.ofMillis(2000),
                TransitionKey(StateKind.IN_ROOM, StateKind.IN_BATHROOM) to Duration.ofMillis(2000),
                TransitionKey(StateKind.IN_ROOM, StateKind.IN_HALLWAY) to Duration.ofMillis(2000),
                TransitionKey(StateKind.IN_ROOM, StateKind.OUTDOOR) to Duration.ofMillis(2000),
                TransitionKey(StateKind.IN_ROOM, StateKind.IN_CHAIR) to Duration.ofMillis(1500),
                TransitionKey(StateKind.IN_ROOM, StateKind.IN_WHEELCHAIR) to Duration.ofMillis(1500),

                // InHallway → Standing, bathroom, room, outdoor
                TransitionKey(StateKind.IN_HALLWAY, StateKind.STANDING) to Duration.ofMillis(2000),
                TransitionKey(StateKind.IN_HALLWAY, StateKind.IN_BATHROOM) to Duration.ofMillis(2000),
                TransitionKey(StateKind.IN_HALLWAY, StateKind.IN_ROOM) to Duration.ofMillis(2000),
                TransitionKey(StateKind.IN_HALLWAY, StateKind.OUTDOOR) to Duration.ofMillis(2000),

                // Outdoor → Standing, room, hallway
                TransitionKey(StateKind.OUTDOOR, StateKind.STANDING) to Duration.ofMillis(2000),
                TransitionKey(StateKind.OUTDOOR, StateKind.IN_ROOM) to Duration.ofMillis(2000),
                TransitionKey(StateKind.OUTDOOR, StateKind.IN_HALLWAY) to Duration.ofMillis(2000),

                // Absent → Standing or other out-of-bed (returned from unknown location)
                TransitionKey(StateKind.ABSENT, StateKind.STANDING) to Duration.ofMillis(2000),
                TransitionKey(StateKind.ABSENT, StateKind.IN_BATHROOM) to Duration.ofMillis(2000),
                TransitionKey(StateKind.ABSENT, StateKind.IN_ROOM) to Duration.ofMillis(2000),
                TransitionKey(StateKind.ABSENT, StateKind.IN_HALLWAY) to Duration.ofMillis(2000),
                TransitionKey(StateKind.ABSENT, StateKind.OUTDOOR) to Duration.ofMillis(2000),

                // ── Furniture transitions ────────────────────────────────
                // InChair → Standing, wheelchair, room, hallway
                TransitionKey(StateKind.IN_CHAIR, StateKind.STANDING) to Duration.ofMillis(1500),
                TransitionKey(StateKind.IN_CHAIR, StateKind.IN_WHEELCHAIR) to Duration.ofMillis(2000),
                TransitionKey(StateKind.IN_CHAIR, StateKind.IN_ROOM) to Duration.ofMillis(2000),
                TransitionKey(StateKind.IN_CHAIR, StateKind.IN_HALLWAY) to Duration.ofMillis(2000),

                // InWheelchair → Standing, chair, room, hallway, outdoor
                TransitionKey(StateKind.IN_WHEELCHAIR, StateKind.STANDING) to Duration.ofMillis(1500),
                TransitionKey(StateKind.IN_WHEELCHAIR, StateKind.IN_CHAIR) to Duration.ofMillis(2000),
                TransitionKey(StateKind.IN_WHEELCHAIR, StateKind.IN_ROOM) to Duration.ofMillis(2000),
                TransitionKey(StateKind.IN_WHEELCHAIR, StateKind.IN_HALLWAY) to Duration.ofMillis(2000),
                TransitionKey(StateKind.IN_WHEELCHAIR, StateKind.OUTDOOR) to Duration.ofMillis(2000),

                // ── Unknown recovery ────────────────────────────────────
                TransitionKey(StateKind.UNKNOWN, StateKind.LYING) to Duration.ofMillis(2000),
                TransitionKey(StateKind.UNKNOWN, StateKind.STANDING) to Duration.ofMillis(2000),
            ),
        )

        /**
         * Creates a table from a base with overrides.
         * Vernon: "Don't hardcode domain data in the model."
         *
         * @param base the base table to start from
         * @param overrides transitions to override (e.g., from PolicyCalibration)
         */
        public fun from(
            base: TransitionTable = RELEASE_1,
            overrides: Map<TransitionKey, Duration> = emptyMap(),
        ): TransitionTable = TransitionTable(base.legal + overrides)
    }
}
