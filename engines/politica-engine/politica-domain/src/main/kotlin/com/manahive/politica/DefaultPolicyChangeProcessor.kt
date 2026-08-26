package com.manahive.politica

import com.manahive.contracts.common.Fingerprint
import com.manahive.contracts.common.buildFingerprint
import com.manahive.contracts.policy.CalibrationChanged
import com.manahive.contracts.policy.DagCatalog
import com.manahive.contracts.policy.PolicyCalibration
import com.manahive.contracts.policy.PolicyChangeDetected
import com.manahive.contracts.policy.STANDARD_CATALOG
import com.manahive.contracts.policy.Version
import com.manahive.kernel.ResidentId
import java.time.Instant

/**
 * Default implementation of PolicyChangeProcessor.
 *
 * Flow:
 * 1. Receives PolicyChangeDetected from System of Record
 * 2. Resolves PolicyCalibration via PolicyResolver (DAG-centric)
 * 3. Emits CalibrationChanged event
 *
 * @property catalog The DAG catalog for resolution (defaults to STANDARD)
 * @property versionProvider Function to get next version for a resident
 */
public class DefaultPolicyChangeProcessor(
    private val catalog: DagCatalog = STANDARD_CATALOG,
    private val versionProvider: (ResidentId) -> Version = { Version(1) },
) : PolicyChangeProcessor {

    override fun process(event: PolicyChangeDetected, now: Instant): PolicyChangeResult {
        val profile = event.snapshot

        val result = PolicyResolver.resolve(catalog, profile)
        val calibration = result.value

        val version = versionProvider(profile.residentId)

        val changeEvent = CalibrationChanged(
            residentId = profile.residentId,
            at = now,
            version = version,
            fingerprint = calibration.fingerprint(),
            calibration = calibration,
        )

        return PolicyChangeResult(
            calibration = calibration,
            emittedEvents = listOf(changeEvent),
        )
    }
}

/** Generate a fingerprint from PolicyCalibration. */
private fun PolicyCalibration.fingerprint(): Fingerprint = buildFingerprint(
    "hysteresis" to scene.hysteresis,
    "dwell" to scene.dwellThresholds,
    "confidence" to scene.confidence,
)
