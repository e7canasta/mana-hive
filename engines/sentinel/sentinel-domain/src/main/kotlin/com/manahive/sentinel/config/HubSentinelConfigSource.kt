package com.manahive.sentinel.config

import com.manahive.contracts.engine.CallbackSubscription
import com.manahive.contracts.engine.Subscription
import com.manahive.contracts.policy.HubConfigSource
import com.manahive.kernel.ResidentId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Hub-based configuration source for Sentinel Engine.
 *
 * Note: Conversion from StoredSemanticBucket is not yet implemented.
 * This adapter is a placeholder for future Hub integration.
 *
 * @property hubConfigSource The underlying Hub config source
 */
public class HubSentinelConfigSource(
    private val hubConfigSource: HubConfigSource,
) : SentinelConfigSource {
    private val cache = ConcurrentHashMap<String, SentinelConfig>()
    private val watchers = ConcurrentHashMap<String, CopyOnWriteArrayList<(SentinelConfig) -> Unit>>()

    override fun load(residentId: ResidentId): SentinelConfig {
        throw UnsupportedOperationException(
            "Hub mode for Sentinel Engine not yet implemented. " +
                "StoredSemanticBucket to SentinelConfig conversion requires payload parsing."
        )
    }

    override fun loadAll(): Map<ResidentId, SentinelConfig> {
        throw UnsupportedOperationException("Hub mode for Sentinel Engine not yet implemented.")
    }

    override fun subscribe(residentId: ResidentId, onChange: (SentinelConfig) -> Unit): Subscription {
        throw UnsupportedOperationException("Hub mode for Sentinel Engine not yet implemented.")
    }

    override fun subscribeResponseRules(residentId: String, onChange: (SentinelConfig) -> Unit): Subscription {
        throw UnsupportedOperationException("Hub mode for Sentinel Engine not yet implemented.")
    }

    override fun unsubscribe(residentId: ResidentId) {
        // No-op until Hub mode is implemented
    }
}
