package com.manahive.recorder.config

import com.manahive.contracts.engine.ResidentConfigSource
import com.manahive.contracts.engine.Subscription

/**
 * Configuration source for Recorder Engine.
 *
 * Extends ResidentConfigSource with Recorder-specific functionality.
 *
 * Fowler: "Port" (Hexagonal Architecture) — defines the contract
 * for configuration loading adapters.
 */
public interface RecorderConfigSource : ResidentConfigSource<RecorderConfig> {
    /**
     * Subscribe to recording config changes for a resident.
     *
     * @param residentId The resident to subscribe to (without prefix)
     * @param onChange The callback to invoke when config changes
     * @return A subscription that can be cancelled
     */
    public fun subscribeRecording(residentId: String, onChange: (RecorderConfig) -> Unit): Subscription
}
