package com.manahive.recorder.config

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
 * TOML-based configuration source for Recorder Engine.
 *
 * Loads configuration from TOML files and converts to RecorderConfig.
 *
 * Fowler: "Adapter" — adapts TomlConfigSource to RecorderConfigSource.
 *
 * @property basePath Base path for TOML files
 * @property cacheTtl Time-to-live for cached configurations
 */
public class TomlRecorderConfigSource(
    private val basePath: String = "/etc/mana-hive/residents",
    private val cacheTtl: Duration = Duration.ofMinutes(5),
) : RecorderConfigSource {
    private val tomlSource = TomlConfigSource(basePath, cacheTtl)
    private val cache = ConcurrentHashMap<String, RecorderConfig>()
    private val watchers = ConcurrentHashMap<String, CopyOnWriteArrayList<(RecorderConfig) -> Unit>>()

    override fun load(residentId: ResidentId): RecorderConfig {
        val id = residentId.value
        require(id.isNotBlank()) { "Resident ID must not be blank" }

        cache[id]?.let { return it }

        val localConfig = tomlSource.load(residentId)
        val recorderConfig = localConfig.toRecorderConfig(id)
        cache[id] = recorderConfig
        return recorderConfig
    }

    override fun loadAll(): Map<ResidentId, RecorderConfig> {
        return tomlSource.loadAll().mapKeys { it.key }
            .mapValues { (id, config) ->
                cache.getOrPut(id.value) { config.toRecorderConfig(id.value) }
            }
    }

    override fun subscribe(residentId: ResidentId, onChange: (RecorderConfig) -> Unit): Subscription {
        val id = residentId.value
        require(id.isNotBlank()) { "Resident ID must not be blank" }

        val list = watchers.computeIfAbsent(id) { CopyOnWriteArrayList() }
        list.add(onChange)
        return CallbackSubscription { list.remove(onChange) }
    }

    override fun subscribeRecording(residentId: String, onChange: (RecorderConfig) -> Unit): Subscription {
        require(residentId.isNotBlank()) { "Resident ID must not be blank" }
        val list = watchers.computeIfAbsent(residentId) { CopyOnWriteArrayList() }
        list.add(onChange)
        return CallbackSubscription { list.remove(onChange) }
    }

    override fun unsubscribe(residentId: ResidentId) {
        watchers.remove(residentId.value)
    }

    public fun reload(residentId: String): RecorderConfig {
        require(residentId.isNotBlank()) { "Resident ID must not be blank" }

        val id = ResidentId(residentId)
        val localConfig = tomlSource.reload(id)
        val recorderConfig = localConfig.toRecorderConfig(residentId)
        cache[residentId] = recorderConfig

        watchers[residentId]?.forEach { callback ->
            callback(recorderConfig)
        }

        return recorderConfig
    }

    public fun exists(residentId: String): Boolean {
        return tomlSource.exists(ResidentId(residentId))
    }

    public fun shutdown() {
        tomlSource.close()
    }

    private fun LocalConfig.toRecorderConfig(residentId: String): RecorderConfig {
        return RecorderConfig(
            residentId = residentId,
            enabled = recording.enabled,
            preEventWindow = recording.preEventWindow,
            postEventWindow = recording.postEventWindow,
            quality = recording.quality,
            fingerprint = "",
        )
    }
}
