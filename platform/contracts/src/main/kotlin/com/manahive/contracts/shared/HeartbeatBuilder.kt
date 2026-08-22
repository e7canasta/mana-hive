package com.manahive.contracts.shared

import java.time.Duration

/**
 * Shared HeartbeatBuilder for all DSLs.
 * Fowler: "Duplicated Code" → Extract into shared module.
 *
 * Used by: PolicyCalibrationDsl, CalibrationDsl, DwellCatalogDsl
 */
public class HeartbeatBuilder {
    public var timeout: Duration = Duration.ofSeconds(90)
        set(value) {
            require(!value.isNegative) { "timeout must not be negative" }
            field = value
        }

    public infix fun to(value: Duration) {
        timeout = value
    }
}
