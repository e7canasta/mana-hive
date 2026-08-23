package com.manahive.infrastructure.config

import com.manahive.contracts.engine.CallbackSubscription
import com.manahive.contracts.engine.ConfigNotFoundException
import com.manahive.contracts.engine.ResidentConfigSource
import com.manahive.contracts.engine.Subscription
import com.manahive.kernel.ResidentId
import java.io.Closeable
import java.io.File
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * TOML configuration source for local engine configuration.
 *
 * Loads configuration from TOML files in /etc/mana-hive/residents/ directory.
 * Each resident has a separate TOML file named by their ID.
 *
 * Fowler: "Repository" — abstracts data access for configuration files.
 *
 * Vernon: "Infrastructure Adapter" — implements ResidentConfigSource for TOML files.
 *
 * @property basePath Base path for TOML files (default: /etc/mana-hive/residents)
 * @property cacheTtl Time-to-live for cached configurations (default: 5 minutes)
 */
public class TomlConfigSource(
    private val basePath: String = "/etc/mana-hive/residents",
    private val cacheTtl: Duration = Duration.ofMinutes(5),
) : ResidentConfigSource<LocalConfig>, Closeable {
    private val cache = ConcurrentHashMap<ResidentId, CacheEntry>()
    private val watchers = ConcurrentHashMap<ResidentId, CopyOnWriteArrayList<(LocalConfig) -> Unit>>()
    private val cacheTtlMs = cacheTtl.toMillis()
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "toml-config-watcher").apply { isDaemon = true }
    }

    init {
        scheduler.scheduleAtFixedRate(
            { cleanupCache() },
            cacheTtlMs,
            cacheTtlMs,
            TimeUnit.MILLISECONDS,
        )
    }

    /**
     * Load configuration for a resident from TOML file.
     *
     * Thread-safe: uses computeIfAbsent to prevent duplicate I/O.
     *
     * @param residentId The resident to load configuration for
     * @return The parsed LocalConfig
     * @throws IllegalArgumentException if residentId is blank
     * @throws ConfigNotFoundException if TOML file does not exist
     */
    override fun load(residentId: ResidentId): LocalConfig {
        require(residentId.value.isNotBlank()) { "Resident ID must not be blank" }

        // Thread-safe cache check with lazy loading
        val entry = cache.computeIfAbsent(residentId) { id ->
            val file = getFile(id)
            if (!file.exists()) {
                throw ConfigNotFoundException(id)
            }
            val config = TomlConfigParser.parse(file)
            CacheEntry(config, System.currentTimeMillis())
        }

        // Check if entry is expired (still under computeIfAbsent for thread safety)
        if (entry.isExpired()) {
            // Refresh expired entry
            val file = getFile(residentId)
            if (!file.exists()) {
                throw ConfigNotFoundException(residentId)
            }
            val config = TomlConfigParser.parse(file)
            val newEntry = CacheEntry(config, System.currentTimeMillis())
            cache[residentId] = newEntry
            return newEntry.config
        }

        return entry.config
    }

    /**
     * Load configuration for all residents.
     *
     * @return Map of residentId to configuration
     */
    override fun loadAll(): Map<ResidentId, LocalConfig> {
        val baseDir = File(basePath)
        if (!baseDir.exists() || !baseDir.isDirectory) {
            return emptyMap()
        }

        return baseDir.listFiles()
            ?.filter { it.isFile && it.extension == "toml" }
            ?.mapNotNull { file ->
                val residentId = ResidentId(file.nameWithoutExtension)
                try {
                    residentId to load(residentId)
                } catch (e: Exception) {
                    null
                }
            }
            ?.toMap()
            ?: emptyMap()
    }

    /**
     * Subscribe to configuration changes for a resident.
     *
     * Returns a Subscription that can cancel the individual callback.
     *
     * @param residentId The resident to subscribe to
     * @param onChange The callback to invoke when configuration changes
     * @return A Subscription that can cancel this specific callback
     */
    override fun subscribe(residentId: ResidentId, onChange: (LocalConfig) -> Unit): Subscription {
        require(residentId.value.isNotBlank()) { "Resident ID must not be blank" }

        val list = watchers.computeIfAbsent(residentId) { CopyOnWriteArrayList() }
        list.add(onChange)

        return CallbackSubscription {
            list.remove(onChange)
        }
    }

    /**
     * Unsubscribe all callbacks for a resident.
     *
     * @param residentId The resident to unsubscribe from
     */
    override fun unsubscribe(residentId: ResidentId) {
        watchers.remove(residentId)
    }

    /**
     * Reload configuration for a resident from TOML file.
     *
     * This can be used to refresh configuration when the file changes.
     *
     * @param residentId The resident to reload configuration for
     * @return The reloaded LocalConfig
     * @throws ConfigNotFoundException if TOML file does not exist
     */
    public fun reload(residentId: ResidentId): LocalConfig {
        require(residentId.value.isNotBlank()) { "Resident ID must not be blank" }

        val file = getFile(residentId)
        if (!file.exists()) {
            throw ConfigNotFoundException(residentId)
        }

        val config = TomlConfigParser.parse(file)
        cache[residentId] = CacheEntry(config, System.currentTimeMillis())

        watchers[residentId]?.forEach { callback ->
            callback(config)
        }

        return config
    }

    /**
     * Check if configuration exists for a resident.
     *
     * @param residentId The resident to check
     * @return true if configuration exists
     */
    public fun exists(residentId: ResidentId): Boolean {
        return getFile(residentId).exists()
    }

    override fun close() {
        scheduler.shutdown()
    }

    private fun getFile(residentId: ResidentId): File {
        return File("$basePath/${residentId.value}.toml")
    }

    private fun cleanupCache() {
        val now = System.currentTimeMillis()
        cache.entries.removeIf { entry ->
            entry.value.isExpired(now)
        }
    }

    private inner class CacheEntry(
        val config: LocalConfig,
        val loadedAt: Long,
    ) {
        fun isExpired(now: Long = System.currentTimeMillis()): Boolean {
            return now - loadedAt > cacheTtlMs
        }
    }
}
