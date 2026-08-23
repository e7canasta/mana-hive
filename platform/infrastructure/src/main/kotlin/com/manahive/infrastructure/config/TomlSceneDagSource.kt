package com.manahive.infrastructure.config

import com.manahive.contracts.dag.SceneDag
import com.manahive.contracts.dag.SceneDagSource
import com.manahive.contracts.dag.SceneEdge
import com.manahive.contracts.dag.SceneNode
import com.manahive.contracts.dag.SceneState
import com.manahive.contracts.dag.DagNotFoundException
import com.manahive.contracts.engine.CallbackSubscription
import com.manahive.contracts.engine.Subscription
import com.manahive.kernel.DagId
import com.manahive.kernel.NodeId
import java.io.Closeable
import java.io.File
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * TOML source for the shared Scene DAG.
 *
 * Loads the Scene DAG from a TOML file.
 * The DAG is shared by all engines — not per-resident.
 *
 * Fowler: "Repository" — abstracts data access for DAG files.
 * Vernon: "Infrastructure Adapter" — implements SceneDagSource for TOML files.
 *
 * @property filePath Path to the TOML file
 * @property cacheTtl Time-to-live for cached DAG (default: 5 minutes)
 */
public class TomlSceneDagSource(
    private val filePath: String = "/etc/mana-hive/scene-dag.toml",
    private val cacheTtl: Duration = Duration.ofMinutes(5),
) : SceneDagSource, Closeable {
    @Volatile
    private var cachedDag: SceneDag? = null
    @Volatile
    private var lastLoadedAt: Long = 0
    private val cacheTtlMs = cacheTtl.toMillis()
    private val watchers = CopyOnWriteArrayList<(SceneDag) -> Unit>()
    private val loadLock = Any()
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "scene-dag-watcher").apply { isDaemon = true }
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
     * Load the Scene DAG from TOML file.
     *
     * Thread-safe: uses volatile + lazy loading.
     *
     * @return The parsed SceneDag
     * @throws ConfigNotFoundException if TOML file does not exist
     */
    override fun load(): SceneDag {
        // Double-checked locking for thread-safe cache
        cachedDag?.let { dag ->
            if (!isExpired()) return dag
        }

        synchronized(loadLock) {
            cachedDag?.let { dag ->
                if (!isExpired()) return dag
            }

            val file = File(filePath)
            if (!file.exists()) {
                throw DagNotFoundException(DagId("scene-dag"))
            }

            val dag = parseToml(file)
            cachedDag = dag
            lastLoadedAt = System.currentTimeMillis()
            return dag
        }
    }

    /**
     * Subscribe to DAG changes.
     *
     * @param onChange The callback to invoke when DAG changes
     * @return A Subscription that can cancel this specific callback
     */
    override fun subscribe(onChange: (SceneDag) -> Unit): Subscription {
        watchers.add(onChange)

        return CallbackSubscription {
            watchers.remove(onChange)
        }
    }

    /**
     * Unsubscribe all callbacks.
     */
    override fun unsubscribe() {
        watchers.clear()
    }

    /**
     * Reload the DAG from file and notify watchers.
     *
     * @return The reloaded SceneDag
     * @throws ConfigNotFoundException if TOML file does not exist
     */
    public fun reload(): SceneDag {
        val file = File(filePath)
        if (!file.exists()) {
            throw DagNotFoundException(DagId("scene-dag"))
        }

        val dag = parseToml(file)
        cachedDag = dag
        lastLoadedAt = System.currentTimeMillis()

        watchers.forEach { callback ->
            callback(dag)
        }

        return dag
    }

    /**
     * Check if the DAG file exists.
     */
    public fun exists(): Boolean {
        return File(filePath).exists()
    }

    override fun close() {
        scheduler.shutdown()
        try {
            scheduler.awaitTermination(1, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            scheduler.shutdownNow()
        }
    }

    private fun isExpired(): Boolean {
        return System.currentTimeMillis() - lastLoadedAt > cacheTtlMs
    }

    private fun cleanupCache() {
        if (isExpired()) {
            cachedDag = null
        }
    }

    /**
     * Parse a TOML file into a SceneDag.
     *
     * Expected format:
     * ```toml
     * [dag]
     * id = "scene-dag-base"
     * version = 1
     *
     * [[dag.nodes]]
     * id = "lying"
     * state = "LYING"
     *
     * [[dag.edges]]
     * from = "lying"
     * to = "standing"
     * ```
     */
    private fun parseToml(file: File): SceneDag {
        val content = file.readText()
        val lines = content.lines()

        var dagId = "scene-dag-base"
        var dagVersion = 1
        val nodes = mutableListOf<SceneNode>()
        val edges = mutableListOf<SceneEdge>()
        var currentNodeId: String? = null
        var currentNodeState: SceneState? = null
        var currentEdgeFrom: String? = null
        var currentEdgeTo: String? = null
        var inDagSection = false
        var inNodesSection = false
        var inEdgesSection = false

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue

            when {
                trimmed == "[dag]" -> {
                    inDagSection = true
                    inNodesSection = false
                    inEdgesSection = false
                }
                trimmed == "[[dag.nodes]]" -> {
                    // Flush previous node
                    val id = currentNodeId
                    val state = currentNodeState
                    if (id != null && state != null) {
                        nodes.add(SceneNode(NodeId(id), state))
                    }
                    currentNodeId = null
                    currentNodeState = null
                    inDagSection = false
                    inNodesSection = true
                    inEdgesSection = false
                }
                trimmed == "[[dag.edges]]" -> {
                    // Flush previous edge
                    val from = currentEdgeFrom
                    val to = currentEdgeTo
                    if (from != null && to != null) {
                        edges.add(SceneEdge(NodeId(from), NodeId(to)))
                    }
                    currentEdgeFrom = null
                    currentEdgeTo = null
                    inDagSection = false
                    inNodesSection = false
                    inEdgesSection = true
                }
                trimmed.startsWith("id ") && trimmed.contains("=") -> {
                    val value = trimmed.split("=", limit = 2)[1].trim().removeSurrounding("\"")
                    when {
                        inDagSection -> dagId = value
                        inNodesSection -> currentNodeId = value
                        inEdgesSection -> currentEdgeFrom = value
                    }
                }
                trimmed.startsWith("version ") && trimmed.contains("=") -> {
                    dagVersion = trimmed.split("=", limit = 2)[1].trim().toIntOrNull() ?: 1
                }
                trimmed.startsWith("state ") && trimmed.contains("=") -> {
                    val value = trimmed.split("=", limit = 2)[1].trim().removeSurrounding("\"")
                    currentNodeState = try {
                        SceneState.valueOf(value)
                    } catch (e: IllegalArgumentException) {
                        throw IllegalArgumentException("Invalid scene state '$value' in TOML", e)
                    }
                }
                trimmed.startsWith("from ") && trimmed.contains("=") -> {
                    currentEdgeFrom = trimmed.split("=", limit = 2)[1].trim().removeSurrounding("\"")
                }
                trimmed.startsWith("to ") && trimmed.contains("=") -> {
                    currentEdgeTo = trimmed.split("=", limit = 2)[1].trim().removeSurrounding("\"")
                }
            }
        }

        // Flush last node/edge
        val lastNodeId = currentNodeId
        val lastNodeState = currentNodeState
        if (lastNodeId != null && lastNodeState != null) {
            nodes.add(SceneNode(NodeId(lastNodeId), lastNodeState))
        }
        val lastEdgeFrom = currentEdgeFrom
        val lastEdgeTo = currentEdgeTo
        if (lastEdgeFrom != null && lastEdgeTo != null) {
            edges.add(SceneEdge(NodeId(lastEdgeFrom), NodeId(lastEdgeTo)))
        }

        return SceneDag.create(
            id = DagId(dagId),
            nodes = nodes.toSet(),
            edges = edges.toSet(),
            version = com.manahive.contracts.dag.DagVersion(dagVersion),
        )
    }
}
