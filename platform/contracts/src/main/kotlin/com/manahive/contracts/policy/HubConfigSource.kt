package com.manahive.contracts.policy

import com.manahive.contracts.engine.CallbackSubscription
import com.manahive.contracts.engine.ConfigNotFoundException
import com.manahive.contracts.engine.ResidentConfigSource
import com.manahive.contracts.engine.Subscription
import com.manahive.kernel.ResidentId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Hub configuration source for engine configuration.
 *
 * Loads configuration from Hub API + subscribes to changes via JetStream.
 * This is the production mode for engines.
 *
 * Fowler: "Repository" — abstracts data access for Hub API.
 *
 * Vernon: "Infrastructure Adapter" — implements ResidentConfigSource for Hub API.
 *
 * @property hubBaseUrl Base URL for Hub API
 * @property httpClient HTTP client for API calls
 * @property topicPrefix Prefix for JetStream topics (default: "hub.policy")
 */
public class HubConfigSource(
    private val hubBaseUrl: String,
    private val httpClient: HubHttpClient,
    private val topicPrefix: String = "hub.policy",
) : ResidentConfigSource<StoredSemanticBucket> {
    private val cache = ConcurrentHashMap<ResidentId, StoredSemanticBucket>()
    private val watchers = ConcurrentHashMap<ResidentId, CopyOnWriteArrayList<(StoredSemanticBucket) -> Unit>>()

    /**
     * Load configuration for a resident from Hub API.
     *
     * Thread-safe: uses computeIfAbsent to prevent duplicate network calls.
     */
    override fun load(residentId: ResidentId): StoredSemanticBucket {
        require(residentId.value.isNotBlank()) { "Resident ID must not be blank" }

        return cache.computeIfAbsent(residentId) { id ->
            httpClient.getSemanticBucket(id)
        }
    }

    /**
     * Load configuration for all residents from Hub API.
     */
    override fun loadAll(): Map<ResidentId, StoredSemanticBucket> {
        return httpClient.getAllSemanticBuckets()
    }

    /**
     * Subscribe to configuration changes for a resident.
     *
     * Returns a Subscription that can cancel the individual callback
     * without affecting other subscribers.
     */
    override fun subscribe(residentId: ResidentId, onChange: (StoredSemanticBucket) -> Unit): Subscription {
        require(residentId.value.isNotBlank()) { "Resident ID must not be blank" }

        val list = watchers.computeIfAbsent(residentId) { CopyOnWriteArrayList() }
        list.add(onChange)

        // Subscribe to JetStream topic (idempotent)
        val topic = "$topicPrefix.calibration.v1.${residentId.value}"
        httpClient.subscribe(
            topic = topic,
            callback = { bucket ->
                cache[residentId] = bucket
                list.forEach { watcher ->
                    watcher(bucket)
                }
            }
        )

        return CallbackSubscription {
            list.remove(onChange)
            // If last subscriber removed, unsubscribe from topic
            if (list.isEmpty()) {
                watchers.remove(residentId)
                httpClient.unsubscribe(topic)
            }
        }
    }

    /**
     * Unsubscribe all callbacks for a resident and unsubscribe from topic.
     */
    override fun unsubscribe(residentId: ResidentId) {
        watchers.remove(residentId)
        val topic = "$topicPrefix.calibration.v1.${residentId.value}"
        httpClient.unsubscribe(topic)
    }
}

/**
 * HTTP client interface for Hub API.
 *
 * This is a port (Hexagonal Architecture) that defines the contract
 * for HTTP communication with Hub.
 */
public interface HubHttpClient {
    /**
     * Get semantic bucket for a resident.
     *
     * @param residentId The resident to get bucket for
     * @return The semantic bucket
     * @throws ConfigNotFoundException if resident not found
     */
    public fun getSemanticBucket(residentId: ResidentId): StoredSemanticBucket

    /**
     * Get semantic buckets for all residents.
     *
     * @return Map of residentId to semantic bucket
     */
    public fun getAllSemanticBuckets(): Map<ResidentId, StoredSemanticBucket>

    /**
     * Subscribe to JetStream topic.
     *
     * @param topic The topic to subscribe to
     * @param callback The callback to invoke when message received
     */
    public fun subscribe(topic: String, callback: (StoredSemanticBucket) -> Unit)

    /**
     * Unsubscribe from JetStream topic.
     *
     * @param topic The topic to unsubscribe from
     */
    public fun unsubscribe(topic: String)
}
