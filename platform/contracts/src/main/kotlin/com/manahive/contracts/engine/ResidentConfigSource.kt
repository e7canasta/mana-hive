package com.manahive.contracts.engine

import com.manahive.kernel.ResidentId

/**
 * Port for loading engine configuration from different sources.
 *
 * Each engine implements this interface for its specific configuration type.
 * Two implementations are provided:
 * - TomlConfigSource: Load from TOML files on disk (LOCAL mode)
 * - HubConfigSource: Load from Hub API + subscribe to changes (HUB mode)
 *
 * Fowler: "Interface Segregation" — clients depend only on methods they use.
 *
 * Vernon: Port (Hexagonal Architecture) — interface defines the contract
 * for configuration loading adapters.
 *
 * @param T The configuration type (e.g., SceneCalibration, ResponseConfig)
 */
public interface ResidentConfigSource<T> {
    /**
     * Load configuration for a resident.
     *
     * @param residentId The resident to load configuration for
     * @return The configuration for the resident
     * @throws IllegalArgumentException if residentId is blank
     * @throws ConfigNotFoundException if configuration cannot be found
     */
    public fun load(residentId: ResidentId): T

    /**
     * Load configuration for all residents.
     *
     * @return Map of residentId to configuration
     */
    public fun loadAll(): Map<ResidentId, T>

    /**
     * Subscribe to configuration changes for a resident.
     *
     * @param residentId The resident to subscribe to
     * @param onChange The callback to invoke when configuration changes
     * @return A subscription that can be cancelled
     */
    public fun subscribe(residentId: ResidentId, onChange: (T) -> Unit): Subscription

    /**
     * Unsubscribe from configuration changes for a resident.
     *
     * @param residentId The resident to unsubscribe from
     */
    public fun unsubscribe(residentId: ResidentId)
}
