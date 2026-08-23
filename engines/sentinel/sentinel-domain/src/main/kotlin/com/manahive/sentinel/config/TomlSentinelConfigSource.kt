package com.manahive.sentinel.config

import com.manahive.contracts.engine.CallbackSubscription
import com.manahive.contracts.engine.ConfigNotFoundException
import com.manahive.contracts.engine.ResidentConfigSource
import com.manahive.contracts.engine.Subscription
import com.manahive.infrastructure.config.LocalConfig
import com.manahive.infrastructure.config.TomlConfigSource
import com.manahive.kernel.ResidentId
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * TOML-based configuration source for Sentinel Engine.
 *
 * Loads configuration from TOML files and converts to SentinelConfig.
 *
 * Fowler: "Adapter" — adapts TomlConfigSource to SentinelConfigSource.
 *
 * @property basePath Base path for TOML files
 * @property cacheTtl Time-to-live for cached configurations
 */
public class TomlSentinelConfigSource(
    private val basePath: String = "/etc/mana-hive/residents",
    private val cacheTtl: Duration = Duration.ofMinutes(5),
) : SentinelConfigSource {
    private val tomlSource = TomlConfigSource(basePath, cacheTtl)
    private val cache = ConcurrentHashMap<String, SentinelConfig>()
    private val watchers = ConcurrentHashMap<String, CopyOnWriteArrayList<(SentinelConfig) -> Unit>>()

    override fun load(residentId: ResidentId): SentinelConfig {
        val id = residentId.value
        require(id.isNotBlank()) { "Resident ID must not be blank" }

        cache[id]?.let { return it }

        val localConfig = tomlSource.load(residentId)
        val sentinelConfig = localConfig.toSentinelConfig(id)
        cache[id] = sentinelConfig
        return sentinelConfig
    }

    override fun loadAll(): Map<ResidentId, SentinelConfig> {
        return tomlSource.loadAll().mapKeys { it.key }
            .mapValues { (id, config) ->
                cache.getOrPut(id.value) { config.toSentinelConfig(id.value) }
            }
    }

    override fun subscribe(residentId: ResidentId, onChange: (SentinelConfig) -> Unit): Subscription {
        val id = residentId.value
        require(id.isNotBlank()) { "Resident ID must not be blank" }

        val list = watchers.computeIfAbsent(id) { CopyOnWriteArrayList() }
        list.add(onChange)
        return CallbackSubscription { list.remove(onChange) }
    }

    override fun subscribeResponseRules(residentId: String, onChange: (SentinelConfig) -> Unit): Subscription {
        require(residentId.isNotBlank()) { "Resident ID must not be blank" }
        val list = watchers.computeIfAbsent(residentId) { CopyOnWriteArrayList() }
        list.add(onChange)
        return CallbackSubscription { list.remove(onChange) }
    }

    override fun unsubscribe(residentId: ResidentId) {
        watchers.remove(residentId.value)
    }

    public fun reload(residentId: String): SentinelConfig {
        require(residentId.isNotBlank()) { "Resident ID must not be blank" }

        val id = ResidentId(residentId)
        val localConfig = tomlSource.reload(id)
        val sentinelConfig = localConfig.toSentinelConfig(residentId)
        cache[residentId] = sentinelConfig

        watchers[residentId]?.forEach { callback ->
            callback(sentinelConfig)
        }

        return sentinelConfig
    }

    public fun exists(residentId: String): Boolean {
        return tomlSource.exists(ResidentId(residentId))
    }

    public fun shutdown() {
        tomlSource.close()
    }

    private fun LocalConfig.toSentinelConfig(residentId: String): SentinelConfig {
        return SentinelConfig(
            residentId = residentId,
            rules = emptyList(),
            maxAlertsPerShift = 5,
            fingerprint = "",
        )
    }
}
