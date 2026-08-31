package com.manahive.runtime.control

import java.time.Instant

/**
 * Control event published after hive control action.
 * Subject: `hive.control.v1`
 *
 * Fowler: Domain Event — something happened, not a command.
 */
data class HiveControlEvent(
    val type: String, // "HiveReloaded" | "HiveReset" | "HiveResetFull"
    val residentId: String,
    val bedId: String,
    val at: Instant,
    val oldVersion: Int?,
    val newVersion: Int?,
    val fingerprint: String?,
    val twinState: String?,
    val message: String? = null,
)
