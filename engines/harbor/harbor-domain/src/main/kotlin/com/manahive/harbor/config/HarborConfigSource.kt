package com.manahive.harbor.config

import com.manahive.contracts.engine.ResidentConfigSource
import com.manahive.contracts.engine.Subscription

/**
 * Configuration source for Harbor Engine.
 *
 * Extends ResidentConfigSource with Harbor-specific functionality.
 *
 * Fowler: "Port" (Hexagonal Architecture) — defines the contract
 * for configuration loading adapters.
 */
public interface HarborConfigSource : ResidentConfigSource<HarborConfig> {
    /**
     * Subscribe to escalation config changes for a resident.
     *
     * @param residentId The resident to subscribe to (without prefix)
     * @param onChange The callback to invoke when config changes
     * @return A subscription that can be cancelled
     */
    public fun subscribeEscalation(residentId: String, onChange: (HarborConfig) -> Unit): Subscription
}
