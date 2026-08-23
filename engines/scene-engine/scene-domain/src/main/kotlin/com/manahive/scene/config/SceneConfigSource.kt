package com.manahive.scene.config

import com.manahive.contracts.engine.ResidentConfigSource
import com.manahive.contracts.engine.Subscription

/**
 * Configuration source for Scene Engine.
 *
 * Extends ResidentConfigSource with Scene-specific functionality.
 *
 * Fowler: "Port" (Hexagonal Architecture) — defines the contract
 * for configuration loading adapters.
 */
public interface SceneConfigSource : ResidentConfigSource<SceneConfig> {
    /**
     * Subscribe to calibration changes for a resident.
     *
     * @param residentId The resident to subscribe to (without prefix)
     * @param onChange The callback to invoke when calibration changes
     * @return A subscription that can be cancelled
     */
    public fun subscribeCalibration(residentId: String, onChange: (SceneConfig) -> Unit): Subscription
}
