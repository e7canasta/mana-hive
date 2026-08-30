package com.manahive.harbor.config

import com.manahive.contracts.engine.CallbackSubscription
import com.manahive.contracts.engine.ConfigNotFoundException
import com.manahive.contracts.engine.ResidentConfigSource
import com.manahive.contracts.engine.Subscription
import com.manahive.contracts.policy.Severity
import com.manahive.contracts.common.Channel
import com.manahive.infrastructure.config.LocalConfig
import com.manahive.infrastructure.config.TomlConfigSource
import com.manahive.kernel.ResidentId
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * TOML-based configuration source for Harbor Engine.
 *
 * Loads configuration from TOML files and converts to HarborConfig.
 *
 * Fowler: "Adapter" — adapts TomlConfigSource to HarborConfigSource.
 *
 * @property basePath Base path for TOML files
 * @property cacheTtl Time-to-live for cached configurations
 */
public class TomlHarborConfigSource(
    private val basePath: String = "/etc/mana-hive/residents",
    private val cacheTtl: Duration = Duration.ofMinutes(5),
) : HarborConfigSource {
    private val tomlSource = TomlConfigSource(basePath, cacheTtl)
    private val cache = ConcurrentHashMap<String, HarborConfig>()
    private val watchers = ConcurrentHashMap<String, CopyOnWriteArrayList<(HarborConfig) -> Unit>>()

    override fun load(residentId: ResidentId): HarborConfig {
        val id = residentId.value
        require(id.isNotBlank()) { "Resident ID must not be blank" }

        cache[id]?.let { return it }

        val localConfig = tomlSource.load(residentId)
        val harborConfig = localConfig.toHarborConfig(id)
        cache[id] = harborConfig
        return harborConfig
    }

    override fun loadAll(): Map<ResidentId, HarborConfig> {
        return tomlSource.loadAll().mapKeys { it.key }
            .mapValues { (id, config) ->
                cache.getOrPut(id.value) { config.toHarborConfig(id.value) }
            }
    }

    override fun subscribe(residentId: ResidentId, onChange: (HarborConfig) -> Unit): Subscription {
        val id = residentId.value
        require(id.isNotBlank()) { "Resident ID must not be blank" }

        val list = watchers.computeIfAbsent(id) { CopyOnWriteArrayList() }
        list.add(onChange)
        return CallbackSubscription { list.remove(onChange) }
    }

    override fun subscribeEscalation(residentId: String, onChange: (HarborConfig) -> Unit): Subscription {
        require(residentId.isNotBlank()) { "Resident ID must not be blank" }
        val list = watchers.computeIfAbsent(residentId) { CopyOnWriteArrayList() }
        list.add(onChange)
        return CallbackSubscription { list.remove(onChange) }
    }

    override fun unsubscribe(residentId: ResidentId) {
        watchers.remove(residentId.value)
    }

    public fun reload(residentId: String): HarborConfig {
        require(residentId.isNotBlank()) { "Resident ID must not be blank" }

        val id = ResidentId(residentId)
        val localConfig = tomlSource.reload(id)
        val harborConfig = localConfig.toHarborConfig(residentId)
        cache[residentId] = harborConfig

        watchers[residentId]?.forEach { callback ->
            callback(harborConfig)
        }

        return harborConfig
    }

    public fun exists(residentId: String): Boolean {
        return tomlSource.exists(ResidentId(residentId))
    }

    public fun shutdown() {
        tomlSource.close()
    }

    private fun LocalConfig.toHarborConfig(residentId: String): HarborConfig {
        // Use escalation delay from config for WARNING timeout
        val warningTimeout = escalation.escalationDelay

        return HarborConfig(
            residentId = residentId,
            channels = mapOf(
                Severity.INFO to setOf(Channel.CONSOLE),
                Severity.WARNING to setOf(Channel.PUSH, Channel.TABLET),
                Severity.HIGH to setOf(Channel.PUSH, Channel.TABLET),
                Severity.CRITICAL to setOf(Channel.PUSH, Channel.TABLET, Channel.WARD_BOARD, Channel.CONSOLE),
            ),
            escalationTimeouts = mapOf(
                Severity.INFO to Duration.ofMinutes(30),
                Severity.WARNING to warningTimeout,
                Severity.HIGH to Duration.ofMinutes(2),
                Severity.CRITICAL to Duration.ZERO,
            ),
            fingerprint = "",
        )
    }
}
