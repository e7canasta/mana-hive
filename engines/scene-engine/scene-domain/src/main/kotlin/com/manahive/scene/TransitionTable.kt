package com.manahive.scene

import com.manahive.contracts.scene.StateKind
import java.time.Duration

/**
 * The person FSM as a TOTAL table: legality plus minimum hysteresis per
 * transition. Data, not a class hierarchy — clinicians read this table, the
 * simulator prints it, and an illegal transition is sensor noise to discard
 * with cause, never to apply.
 */
public data class TransitionTable(
    private val legal: Map<Pair<StateKind, StateKind>, Duration>,
) {
    public fun isLegal(from: StateKind, to: StateKind): Boolean = legal.containsKey(from to to)

    /** Minimum time the new observation must persist before the transition is believed. */
    public fun hysteresis(from: StateKind, to: StateKind): Duration =
        legal[from to to] ?: error("illegal transition $from -> $to has no hysteresis")

    public companion object {
        /** Release 1: the five-state table the 03:00 fall needs. */
        public val RELEASE_1: TransitionTable = TransitionTable(
            mapOf(
                (StateKind.LYING to StateKind.BED_EDGE) to Duration.ofMillis(1500),
                (StateKind.BED_EDGE to StateKind.LYING) to Duration.ofMillis(1000),
                (StateKind.BED_EDGE to StateKind.STANDING) to Duration.ofMillis(1200),
                (StateKind.STANDING to StateKind.BED_EDGE) to Duration.ofMillis(1200),
                (StateKind.STANDING to StateKind.ABSENT) to Duration.ofMillis(2000),
                (StateKind.ABSENT to StateKind.LYING) to Duration.ofMillis(2000),
                // any state may sink into UNKNOWN (signal) and recover; handled by kind
                (StateKind.UNKNOWN to StateKind.LYING) to Duration.ofMillis(2000),
            ),
        )
    }
}
