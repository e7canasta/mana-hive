package com.manahive.politica

import com.manahive.contracts.policy.PolicyCalibration
import com.manahive.kernel.Explained
import com.manahive.kernel.ResidentId

/**
 * Port for Scene Engine to get calibration for a resident.
 *
 * Pattern: Port (Vernon) — Scene Engine depends on this abstraction,
 * not on Politica internals.
 *
 * Fowler's "YAGNI": only getCalibration() is needed now.
 * getAllCalibrations() removed — add it when needed.
 */
public interface CalibrationProvider {

    /**
     * Get the calibration for a resident.
     *
     * @param residentId the resident to get calibration for
     * @return the calibration, or null if not found
     */
    public fun getCalibration(residentId: ResidentId): Explained<PolicyCalibration?>
}
