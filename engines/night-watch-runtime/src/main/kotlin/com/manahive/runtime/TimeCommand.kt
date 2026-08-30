package com.manahive.runtime

import java.time.Instant

/**
 * Time control command — received via NATS to manipulate the clock.
 *
 * Subject: `test.time.v1`
 *
 * ```json
 * { "action": "advance",     "duration": "PT12M" }
 * { "action": "setTo",       "instant": "2024-01-15T23:00:00Z" }
 * { "action": "useManual",   "startAt": "2024-01-15T22:00:00Z" }
 * { "action": "useSystem" }
 * ```
 */
data class TimeCommand(
    val action: String,
    val duration: String? = null,
    val instant: String? = null,
    val startAt: String? = null,
) {
    companion object {
        const val SWEEP = "sweep"
    }
}
