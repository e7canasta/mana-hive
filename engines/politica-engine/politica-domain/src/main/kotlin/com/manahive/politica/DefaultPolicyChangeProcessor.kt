package com.manahive.politica

import com.manahive.contracts.policy.AlarmCatalog
import com.manahive.contracts.policy.CalibrationChanged
import com.manahive.contracts.policy.CatalogVersion
import com.manahive.contracts.policy.DwellThreshold
import com.manahive.contracts.policy.PolicyCalibration
import com.manahive.contracts.policy.PolicyChangeDetected
import com.manahive.contracts.policy.PolicySource
import com.manahive.contracts.scene.StateKind
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
 */
public class DefaultPolicyChangeProcessor(
    private val catalog: AlarmCatalog = defaultCatalog(),
) : PolicyChangeProcessor {

    override fun process(event: PolicyChangeDetected, now: Instant): PolicyChangeResult {
        val profile = event.snapshot

        val calibration = PolicyResolver.resolve(catalog, profile)
        val source = PolicyResolver.resolveSource(profile)

        val changeEvent = CalibrationChanged(
            at = now,
            calibration = calibration,
            source = source,
        )

        return PolicyChangeResult(
            calibration = calibration,
            emittedEvents = listOf(changeEvent),
        )
    }

    private companion object {
        fun defaultCatalog(): AlarmCatalog = AlarmCatalog(
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
