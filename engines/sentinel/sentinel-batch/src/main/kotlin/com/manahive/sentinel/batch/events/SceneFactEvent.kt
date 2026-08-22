package com.manahive.sentinel.batch.events

import com.manahive.contracts.scene.StateKind
import java.time.Duration

/**
 * A parsed scene fact event from the events.dat file.
 *
 * Fowler: "Replace Data Value Object with Object" — each event type
 * is a distinct subtype with only the fields it needs. No nulls,
 * no Data Clumps, exhaustive `when` without else.
 *
 * "Replace Primitive with Value Object" — [threshold] is [Duration],
 * not a raw string. Parsing happens at parse time, not at use time.
 *
 * Format: `t=<offset> <FACT_TYPE> <details>`
 *
 * Examples:
 * - `t=0s    TRANSITION from LYING to BED_EDGE`
 * - `t=10s   STAFF_PRESENT staff nurse-1`
 * - `t=40s   DWELL_EXCEEDED state STANDING threshold PT5M`
 */
sealed interface SceneFactEvent {
    val offset: EventOffset
    val lineNumber: Int
    val typeName: String

    data class Transition(
        override val offset: EventOffset,
        override val lineNumber: Int,
        val from: StateKind,
        val to: StateKind,
    ) : SceneFactEvent {
        override val typeName: String get() = "TRANSITION"
    }

    data class StaffPresent(
        override val offset: EventOffset,
        override val lineNumber: Int,
        val staff: String?,
    ) : SceneFactEvent {
        override val typeName: String get() = "STAFF_PRESENT"
    }

    data class DwellExceeded(
        override val offset: EventOffset,
        override val lineNumber: Int,
        val state: StateKind,
        val threshold: Duration,
    ) : SceneFactEvent {
        override val typeName: String get() = "DWELL_EXCEEDED"
    }

    data class DwellWarning(
        override val offset: EventOffset,
        override val lineNumber: Int,
        val state: StateKind,
        val threshold: Duration,
    ) : SceneFactEvent {
        override val typeName: String get() = "DWELL_WARNING"
    }

    data class SignalLost(
        override val offset: EventOffset,
        override val lineNumber: Int,
        val monitor: String,
        val lastHeartbeat: String?,
    ) : SceneFactEvent {
        override val typeName: String get() = "SIGNAL_LOST"
    }

    data class SignalRecovered(
        override val offset: EventOffset,
        override val lineNumber: Int,
        val monitor: String,
    ) : SceneFactEvent {
        override val typeName: String get() = "SIGNAL_RECOVERED"
    }
}
