package com.manahive.politica

import com.manahive.contracts.policy.CalibrationChanged
import com.manahive.contracts.policy.PolicyCalibration
import com.manahive.contracts.policy.PolicyChangeDetected
import com.manahive.kernel.ResidentId
import java.time.Instant

/**
 * Integration Pattern (Hohpe & Woolf) for processing policy changes.
 *
 * Follows:
 * - SRP (Martin): only processes policy changes, nothing else
 * - Event Driven (Hohpe): reacts to events, emits events
 */
public interface PolicyChangeProcessor {
    public fun process(event: PolicyChangeDetected, now: Instant): PolicyChangeResult
}

/**
 * The result of processing a policy change.
 * Contains the resolved calibration and any events to emit.
 *
 * Kotlin: no redundant `residentId` — it's already in `calibration.residentId`.
 * DRY: single source of truth.
 */
public data class PolicyChangeResult(
    val calibration: PolicyCalibration,
    val emittedEvents: List<CalibrationChanged>,
) {
    val residentId: ResidentId get() = calibration.residentId
}
