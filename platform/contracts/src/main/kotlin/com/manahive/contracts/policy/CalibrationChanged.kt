package com.manahive.contracts.policy

import com.manahive.kernel.ResidentId
import java.time.Instant

/**
 * Event emitted when calibration changes for a resident.
 * Scene Engine subscribes to this to regenerate its interpreter.
 *
 * Lives in contracts because it crosses the Politica→Scene boundary.
 *
 * `source` lives here (the event), not in PolicyCalibration (the model).
 * Fowler: "Data belongs where it's used." Scene Engine uses
 * hysteresis/dwell/confidence. Source is audit metadata for the event.
 *
 * Kotlin: no redundant `residentId` — it's already in `calibration.residentId`.
 * DRY: single source of truth.
 */
public data class CalibrationChanged(
    public val at: Instant,
    public val calibration: PolicyCalibration,
    public val source: PolicySource,
) {
    public val residentId: ResidentId get() = calibration.residentId
}
