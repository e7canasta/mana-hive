package com.manahive.infrastructure.config

import com.manahive.contracts.dag.SceneDag
import com.manahive.contracts.dag.SceneDagSource
import com.manahive.contracts.engine.CallbackSubscription
import com.manahive.contracts.engine.Subscription
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Hub source for the shared Scene DAG.
 *
 * Placeholder implementation — will be implemented when Hub integration is built.
 * Loads the Scene DAG from Hub API + subscribes to changes.
 *
 * Fowler: "Anti-Corruption Layer" — isolates from Hub API.
 * Vernon: "Infrastructure Adapter" — implements SceneDagSource for Hub.
 */
public class HubSceneDagSource : SceneDagSource {
    private val watchers = CopyOnWriteArrayList<(SceneDag) -> Unit>()

    /**
     * Load the Scene DAG from Hub.
     *
     * @throws UnsupportedOperationException until Hub integration is built
     */
    override fun load(): SceneDag {
        throw UnsupportedOperationException(
            "HubSceneDagSource.load() not yet implemented — Hub integration pending"
        )
    }

    /**
     * Subscribe to DAG changes from Hub.
     *
     * @throws UnsupportedOperationException until Hub integration is built
     */
    override fun subscribe(onChange: (SceneDag) -> Unit): Subscription {
        throw UnsupportedOperationException(
            "HubSceneDagSource.subscribe() not yet implemented — Hub integration pending"
        )
    }

    /**
     * Unsubscribe from DAG changes.
     *
     * @throws UnsupportedOperationException until Hub integration is built
     */
    override fun unsubscribe() {
        throw UnsupportedOperationException(
            "HubSceneDagSource.unsubscribe() not yet implemented — Hub integration pending"
        )
    }
}
