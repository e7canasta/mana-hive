package com.manahive.contracts.dag

import com.manahive.contracts.engine.Subscription
import com.manahive.kernel.DagId

/**
 * Port for loading Scene DAG from different sources.
 *
 * The Scene DAG is a SHARED graph — all engines hydrate from it.
 * Two implementations are provided:
 * - TomlSceneDagSource: Load from TOML files on disk (LOCAL mode)
 * - HubSceneDagSource: Load from Hub API + subscribe to changes (HUB mode)
 *
 * Fowler: "Interface Segregation" — clients depend only on methods they use.
 * Vernon: Port (Hexagonal Architecture) — interface defines the contract.
 */
public interface SceneDagSource {
    /**
     * Load the Scene DAG.
     *
     * @return The Scene DAG
     * @throws com.manahive.contracts.engine.ConfigNotFoundException if DAG cannot be found
     */
    public fun load(): SceneDag

    /**
     * Subscribe to DAG changes.
     *
     * @param onChange The callback to invoke when DAG changes
     * @return A subscription that can be cancelled
     */
    public fun subscribe(onChange: (SceneDag) -> Unit): Subscription

    /**
     * Unsubscribe from DAG changes.
     */
    public fun unsubscribe()
}
