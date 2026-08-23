package com.manahive.contracts.policy

import com.manahive.contracts.common.Fingerprint
import com.manahive.kernel.ResidentId
import java.time.Instant

/**
 * Event emitted when calibration changes for a resident.
 * Scene Engine subscribes to this to regenerate its interpreter.
 *
 * Lives in contracts because it crosses the Politica→Scene boundary.
 *
 * Extends PolicyEvent for sealed hierarchy + pattern matching.
 * The category (CALIBRATION) is implied by the type.
 *
 * Fowler: "Data belongs where it's used." Scene Engine uses
 * hysteresis/dwell/confidence. No redundant fields.
 *
 * Kotlin: no redundant `residentId` — it's already in `calibration.residentId`.
 * DRY: single source of truth.
 */
public data class CalibrationChanged(
    override val residentId: ResidentId,
    override val at: Instant,
    override val version: Version,
    override val fingerprint: Fingerprint,
    val calibration: PolicyCalibration,
) : PolicyEvent
