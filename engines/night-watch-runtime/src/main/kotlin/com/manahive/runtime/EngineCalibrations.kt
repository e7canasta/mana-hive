package com.manahive.runtime

import com.manahive.contracts.policy.PolicyCalibration
import com.manahive.harbor.HarborCalibration
import com.manahive.kernel.BedId
import com.manahive.kernel.MonitorId
import com.manahive.politica.adapters.toHarborCalibration
import com.manahive.politica.adapters.toRecordingCalibration
import com.manahive.politica.adapters.toSceneCalibration
import com.manahive.politica.adapters.toSentinelCalibration
import com.manahive.recorder.RecordingCalibration
import com.manahive.scene.calibration.SceneCalibration
import com.manahive.sentinel.SentinelCalibration

/**
 * All four engine calibrations derived from a single [PolicyCalibration].
 *
 * Vernon: "Aggregate" — the calibrations travel together because they
 * share the same origin and the same lifecycle (recalibrate together).
 */
data class EngineCalibrations(
    val scene: SceneCalibration,
    val sentinel: SentinelCalibration,
    val harbor: HarborCalibration,
    val recorder: RecordingCalibration,
) {
    companion object {
        fun from(
            policy: PolicyCalibration,
            bedId: BedId = BedId("unknown"),
            monitorId: MonitorId = MonitorId("unknown"),
        ): EngineCalibrations = EngineCalibrations(
            scene = policy.toSceneCalibration(),
            sentinel = policy.toSentinelCalibration(),
            harbor = policy.toHarborCalibration(),
            recorder = policy.toRecordingCalibration(bedId, monitorId),
        )
    }
}
