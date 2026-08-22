package com.manahive.scene.batch.events

import com.manahive.contracts.perception.ObservationKind

/**
 * A parsed event from the events.dat file.
 *
 * Format: `t=<offset> OBS <kind> confidence=<value>`
 *
 * Examples:
 * - `t=0s    OBS IN_BED confidence=0.95`
 * - `t=2s    OBS BED_EDGE confidence=0.92`
 * - `t=4m30s OBS STANDING confidence=0.90`
 */
data class Event(
    val offset: EventOffset,
    val kind: ObservationKind,
    val confidence: Double,
    val lineNumber: Int,
)
