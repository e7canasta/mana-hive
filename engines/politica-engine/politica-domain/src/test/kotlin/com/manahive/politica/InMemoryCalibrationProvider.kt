package com.manahive.politica

import com.manahive.contracts.policy.PolicyCalibration
import com.manahive.kernel.Explained
import com.manahive.kernel.ExplanationStep
import com.manahive.kernel.ResidentId

/**
 * In-memory implementation of CalibrationProvider for testing.
 *
 * Pattern: Adapter (Vernon) — implements the port interface.
 */
public class InMemoryCalibrationProvider : CalibrationProvider {

    private val calibrations = mutableMapOf<ResidentId, PolicyCalibration>()

    public fun register(residentId: ResidentId, calibration: PolicyCalibration) {
        calibrations[residentId] = calibration
    }

    override fun getCalibration(residentId: ResidentId): Explained<PolicyCalibration?> {
        val calibration = calibrations[residentId]
        return Explained(calibration, emptyList<ExplanationStep>())
    }
}
