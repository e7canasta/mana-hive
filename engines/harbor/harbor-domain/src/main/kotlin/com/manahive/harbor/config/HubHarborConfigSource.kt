package com.manahive.harbor.config

import com.manahive.contracts.engine.CallbackSubscription
import com.manahive.contracts.engine.Subscription
import com.manahive.contracts.policy.HubConfigSource
import com.manahive.kernel.ResidentId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Hub-based configuration source for Harbor Engine.
 *
 * Note: Conversion from StoredSemanticBucket is not yet implemented.
 * This adapter is a placeholder for future Hub integration.
 *
 * @property hubConfigSource The underlying Hub config source
 */
public class HubHarborConfigSource(
    private val hubConfigSource: HubConfigSource,
) : HarborConfigSource {
    private val cache = ConcurrentHashMap<String, HarborConfig>()
    private val watchers = ConcurrentHashMap<String, CopyOnWriteArrayList<(HarborConfig) -> Unit>>()

    override fun load(residentId: ResidentId): HarborConfig {
        throw UnsupportedOperationException(
            "Hub mode for Harbor Engine not yet implemented. " +
                "StoredSemanticBucket to HarborConfig conversion requires payload parsing."
        )
    }

    override fun loadAll(): Map<ResidentId, HarborConfig> {
        throw UnsupportedOperationException("Hub mode for Harbor Engine not yet implemented.")
    }

    override fun subscribe(residentId: ResidentId, onChange: (HarborConfig) -> Unit): Subscription {
        throw UnsupportedOperationException("Hub mode for Harbor Engine not yet implemented.")
    }

    override fun subscribeEscalation(residentId: String, onChange: (HarborConfig) -> Unit): Subscription {
        throw UnsupportedOperationException("Hub mode for Harbor Engine not yet implemented.")
    }

    override fun unsubscribe(residentId: ResidentId) {
        // No-op until Hub mode is implemented
    }
}
