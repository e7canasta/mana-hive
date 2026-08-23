package com.manahive.scene.config

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
 * TOML-based configuration source for Scene Engine.
 *
 * Loads configuration from TOML files and converts to SceneConfig.
 *
 * Fowler: "Adapter" — adapts TomlConfigSource to SceneConfigSource.
 *
 * @property basePath Base path for TOML files
 * @property cacheTtl Time-to-live for cached configurations
 */
public class TomlSceneConfigSource(
    private val basePath: String = "/etc/mana-hive/residents",
    private val cacheTtl: Duration = Duration.ofMinutes(5),
) : SceneConfigSource {
    private val tomlSource = TomlConfigSource(basePath, cacheTtl)
    private val cache = ConcurrentHashMap<String, SceneConfig>()
    private val watchers = ConcurrentHashMap<String, CopyOnWriteArrayList<(SceneConfig) -> Unit>>()

    override fun load(residentId: ResidentId): SceneConfig {
        val id = residentId.value
        require(id.isNotBlank()) { "Resident ID must not be blank" }

        cache[id]?.let { return it }

        val localConfig = tomlSource.load(residentId)
        val sceneConfig = localConfig.toSceneConfig(id)
        cache[id] = sceneConfig
        return sceneConfig
    }

    override fun loadAll(): Map<ResidentId, SceneConfig> {
        return tomlSource.loadAll().mapKeys { it.key }
            .mapValues { (id, config) ->
                cache.getOrPut(id.value) { config.toSceneConfig(id.value) }
            }
    }

    override fun subscribe(residentId: ResidentId, onChange: (SceneConfig) -> Unit): Subscription {
        val id = residentId.value
        require(id.isNotBlank()) { "Resident ID must not be blank" }

        val list = watchers.computeIfAbsent(id) { CopyOnWriteArrayList() }
        list.add(onChange)
        return CallbackSubscription { list.remove(onChange) }
    }

    override fun subscribeCalibration(residentId: String, onChange: (SceneConfig) -> Unit): Subscription {
        require(residentId.isNotBlank()) { "Resident ID must not be blank" }
        val list = watchers.computeIfAbsent(residentId) { CopyOnWriteArrayList() }
        list.add(onChange)
        return CallbackSubscription { list.remove(onChange) }
    }

    override fun unsubscribe(residentId: ResidentId) {
        watchers.remove(residentId.value)
    }

    public fun reload(residentId: String): SceneConfig {
        require(residentId.isNotBlank()) { "Resident ID must not be blank" }

        val id = ResidentId(residentId)
        val localConfig = tomlSource.reload(id)
        val sceneConfig = localConfig.toSceneConfig(residentId)
        cache[residentId] = sceneConfig

        watchers[residentId]?.forEach { callback ->
            callback(sceneConfig)
        }

        return sceneConfig
    }

    public fun exists(residentId: String): Boolean {
        return tomlSource.exists(ResidentId(residentId))
    }

    public fun shutdown() {
        tomlSource.close()
    }

    private fun LocalConfig.toSceneConfig(residentId: String): SceneConfig {
        return SceneConfig(
            residentId = residentId,
            name = resident.name,
            bed = resident.bed,
            heartbeatTimeout = calibration.heartbeatTimeout,
            dwellThresholds = calibration.dwellThresholds,
            confidence = calibration.confidence.minConfidence,
        )
    }
}
