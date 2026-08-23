package com.manahive.sentinel.config

import com.manahive.contracts.engine.ResidentConfigSource
import com.manahive.contracts.engine.Subscription

/**
 * Configuration source for Sentinel Engine.
 *
 * Extends ResidentConfigSource with Sentinel-specific functionality.
 *
 * Fowler: "Port" (Hexagonal Architecture) — defines the contract
 * for configuration loading adapters.
 */
public interface SentinelConfigSource : ResidentConfigSource<SentinelConfig> {
    /**
     * Subscribe to response rule changes for a resident.
     *
     * @param residentId The resident to subscribe to (without prefix)
     * @param onChange The callback to invoke when rules change
     * @return A subscription that can be cancelled
     */
    public fun subscribeResponseRules(residentId: String, onChange: (SentinelConfig) -> Unit): Subscription
}
