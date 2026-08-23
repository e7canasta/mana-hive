package com.manahive.infrastructure.config

import com.manahive.contracts.engine.CallbackSubscription
import com.manahive.contracts.engine.ConfigNotFoundException
import com.manahive.contracts.engine.ResidentConfigSource
import com.manahive.contracts.engine.Subscription
import com.manahive.kernel.ResidentId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Abstract base for adapter config sources that convert LocalConfig to a domain type.
 *
 * Eliminates code duplication across Scene, Sentinel, Harbor, and Recorder adapters.
 *
 * Fowler: "Template Method" — subclasses only implement the conversion function.
 *
 * @param T The domain configuration type
 * @property tomlSource The underlying TOML config source
 * @property converter Function to convert LocalConfig to T
 */
public abstract class BaseTomlConfigSource<T>(
    protected val tomlSource: TomlConfigSource,
    private val converter: (LocalConfig, String) -> T,
) : ResidentConfigSource<T> {
    private val cache = ConcurrentHashMap<String, T>()
    private val watchers = ConcurrentHashMap<String, CopyOnWriteArrayList<(T) -> Unit>>()

    /**
     * Load configuration for a resident.
     *
     * Thread-safe: uses computeIfAbsent to prevent duplicate conversion.
     */
    override fun load(residentId: ResidentId): T {
        val id = residentId.value
        require(id.isNotBlank()) { "Resident ID must not be blank" }

        return cache.computeIfAbsent(id) { _ ->
            val localConfig = tomlSource.load(residentId)
            converter(localConfig, id)
        }
    }

    /**
     * Load configuration for all residents.
     */
    override fun loadAll(): Map<ResidentId, T> {
        return tomlSource.loadAll().mapKeys { it.key }
            .mapValues { (id, localConfig) ->
                cache.computeIfAbsent(id.value) { _ ->
                    converter(localConfig, id.value)
                }
            }
    }

    /**
     * Subscribe to configuration changes. Returns a cancellable Subscription.
     */
    override fun subscribe(residentId: ResidentId, onChange: (T) -> Unit): Subscription {
        val id = residentId.value
        require(id.isNotBlank()) { "Resident ID must not be blank" }

        val list = watchers.computeIfAbsent(id) { CopyOnWriteArrayList() }
        list.add(onChange)

        return CallbackSubscription {
            list.remove(onChange)
        }
    }

    /**
     * Unsubscribe all callbacks for a resident.
     */
    override fun unsubscribe(residentId: ResidentId) {
        watchers.remove(residentId.value)
    }

    /**
     * Reload configuration for a resident.
     */
    public fun reload(residentId: String): T {
        require(residentId.isNotBlank()) { "Resident ID must not be blank" }

        val id = ResidentId(residentId)
        val localConfig = tomlSource.reload(id)
        val config = converter(localConfig, residentId)
        cache[residentId] = config

        watchers[residentId]?.forEach { callback ->
            callback(config)
        }

        return config
    }

    /**
     * Check if configuration exists for a resident.
     */
    public fun exists(residentId: String): Boolean {
        return tomlSource.exists(ResidentId(residentId))
    }

    /**
     * Shutdown the underlying TOML source.
     */
    public fun shutdown() {
        tomlSource.close()
    }
}
