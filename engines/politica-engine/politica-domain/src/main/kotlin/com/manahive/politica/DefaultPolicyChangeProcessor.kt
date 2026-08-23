package com.manahive.politica

import com.manahive.contracts.common.Fingerprint
import com.manahive.contracts.common.buildFingerprint
import com.manahive.contracts.policy.AlarmCatalog
import com.manahive.contracts.policy.CalibrationChanged
import com.manahive.contracts.policy.CatalogVersion
import com.manahive.contracts.policy.DwellThreshold
import com.manahive.contracts.policy.PolicyCalibration
import com.manahive.contracts.policy.PolicyChangeDetected
import com.manahive.contracts.policy.Version
import com.manahive.contracts.scene.StateKind
import com.manahive.kernel.ResidentId
import java.time.Duration
import java.time.Instant

/**
 * Default implementation of PolicyChangeProcessor.
 *
 * Flow:
 * 1. Receives PolicyChangeDetected from System of Record
 * 2. Resolves PolicyCalibration directly via PolicyResolver
 * 3. Emits CalibrationChanged event
 *
 * Follows:
 * - SRP (Martin): only processes policy changes, nothing else
 * - Event Driven (Hohpe): reacts to events, emits events
 * - Remove Dispensable (Fowler): no intermediate EffectivePolicy type
 * - DI (Martin): catalog injected via constructor, not hardcoded
 *
 * @property catalog The alarm catalog for resolution
 * @property versionProvider Function to get next version for a resident
 */
public class DefaultPolicyChangeProcessor(
    private val catalog: AlarmCatalog = defaultCatalog(),
    private val versionProvider: (ResidentId) -> Version = { Version(1) },
) : PolicyChangeProcessor {

    override fun process(event: PolicyChangeDetected, now: Instant): PolicyChangeResult {
        val profile = event.snapshot

        val calibration = PolicyResolver.resolve(catalog, profile)

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

    private companion object {
        private fun defaultCatalog(): AlarmCatalog = AlarmCatalog(
            transitions = emptyMap(),
            dwellThresholds = mapOf(
                StateKind.STANDING to DwellThreshold(
                    warning = Duration.ofMinutes(4),
                    exceeded = Duration.ofMinutes(5),
                ),
            ),
            templates = emptyMap(),
            version = CatalogVersion("1.0.0"),
        )
    }
}

/** Generate a fingerprint from PolicyCalibration. */
private fun PolicyCalibration.fingerprint(): Fingerprint = buildFingerprint(
    "hysteresis" to hysteresis,
    "dwell" to dwellThresholds,
    "confidence" to confidence,
)
