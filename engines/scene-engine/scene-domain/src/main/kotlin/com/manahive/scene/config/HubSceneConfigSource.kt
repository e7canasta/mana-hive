package com.manahive.scene.config

import com.manahive.contracts.engine.CallbackSubscription
import com.manahive.contracts.engine.ConfigNotFoundException
import com.manahive.contracts.engine.Subscription
import com.manahive.contracts.policy.HubConfigSource
import com.manahive.contracts.policy.StoredSemanticBucket
import com.manahive.kernel.ResidentId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Hub-based configuration source for Scene Engine.
 *
 * Loads configuration from Hub API and converts to SceneConfig.
 *
 * Note: Conversion from StoredSemanticBucket is not yet implemented.
 * This adapter is a placeholder for future Hub integration.
 *
 * Fowler: "Adapter" — adapts HubConfigSource to SceneConfigSource.
 *
 * @property hubConfigSource The underlying Hub config source
 */
public class HubSceneConfigSource(
    private val hubConfigSource: HubConfigSource,
) : SceneConfigSource {
    private val cache = ConcurrentHashMap<String, SceneConfig>()
    private val watchers = ConcurrentHashMap<String, CopyOnWriteArrayList<(SceneConfig) -> Unit>>()

    override fun load(residentId: ResidentId): SceneConfig {
        val id = residentId.value
        require(id.isNotBlank()) { "Resident ID must not be blank" }

        cache[id]?.let { return it }

        // TODO: Implement actual conversion from StoredSemanticBucket to SceneConfig
        // For now, throw to indicate unimplemented functionality
        throw UnsupportedOperationException(
            "Hub mode for Scene Engine not yet implemented. " +
                "StoredSemanticBucket to SceneConfig conversion requires payload parsing."
        )
    }

    override fun loadAll(): Map<ResidentId, SceneConfig> {
        throw UnsupportedOperationException("Hub mode for Scene Engine not yet implemented.")
    }

    override fun subscribe(residentId: ResidentId, onChange: (SceneConfig) -> Unit): Subscription {
        throw UnsupportedOperationException("Hub mode for Scene Engine not yet implemented.")
    }

    override fun subscribeCalibration(residentId: String, onChange: (SceneConfig) -> Unit): Subscription {
        throw UnsupportedOperationException("Hub mode for Scene Engine not yet implemented.")
    }

    override fun unsubscribe(residentId: ResidentId) {
        // No-op until Hub mode is implemented
    }
}
