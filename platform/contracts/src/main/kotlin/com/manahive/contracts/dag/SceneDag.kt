package com.manahive.contracts.dag

import com.manahive.kernel.DagId
import com.manahive.kernel.NodeId

/**
 * Scene DAG — the physical graph of person states.
 *
 * Defines:
 * - What physical positions exist
 * - Which are initial (safe), final (safe), intermediate
 * - What transitions are physically possible
 * - What transitions are invalid (anomalies)
 *
 * This is a SHARED graph — all engines hydrate from it.
 * It does NOT know about risk, severity, alerts — that's policy.
 *
 * Vernon: "Aggregate" — root entity that enforces invariants.
 */
public class SceneDag private constructor(
    public val id: DagId,
    public val nodes: Set<SceneNode>,
    public val edges: Set<SceneEdge>,
    public val version: DagVersion,
) {
    /** Precomputed node lookup by ID. O(1). */
    private val nodeById: Map<NodeId, SceneNode>

    /** Get a node by ID. O(1). */
    public fun nodeById(nodeId: NodeId): SceneNode? = nodeById[nodeId]

    /** Precomputed adjacency map for O(1) successor lookups. */
    private val successorsMap: Map<NodeId, Set<SceneNode>>

    /** Precomputed adjacency map for O(1) predecessor lookups. */
    private val predecessorsMap: Map<NodeId, Set<SceneNode>>

    /** Precomputed initial states (no predecessors). */
    private val initialsSet: Set<SceneNode>

    /** Precomputed final states (no successors). */
    private val finalsSet: Set<SceneNode>

    /** Precomputed states indexed by SceneState for O(1) lookup. */
    private val initialsByState: Set<SceneState>

    /** Precomputed final states indexed by SceneState for O(1) lookup. */
    private val finalsByState: Set<SceneState>

    init {
        nodeById = nodes.toSet().associateBy { it.id }

        require(nodeById.size == nodes.size) {
            "Duplicate node IDs found: ${nodes.groupBy { it.id }.filter { it.value.size > 1 }.keys}"
        }

        require(edges.all { it.from in nodeById && it.to in nodeById }) {
            "All edges must reference existing nodes"
        }

        successorsMap = edges.groupBy { it.from }
            .mapValues { (_, es) -> es.mapNotNull { nodeById[it.to] }.toSet() }

        predecessorsMap = edges.groupBy { it.to }
            .mapValues { (_, es) -> es.mapNotNull { nodeById[it.from] }.toSet() }

        initialsSet = nodes.filter { predecessorsMap[it.id].isNullOrEmpty() }.toSet()
        finalsSet = nodes.filter { successorsMap[it.id].isNullOrEmpty() }.toSet()
        initialsByState = initialsSet.map { it.state }.toSet()
        finalsByState = finalsSet.map { it.state }.toSet()

        require(!hasCycles()) { "DAG must not contain cycles" }
    }

    /** Internal constructor for withVersion — skips validation. */
    private constructor(
        id: DagId,
        nodes: Set<SceneNode>,
        edges: Set<SceneEdge>,
        version: DagVersion,
        @Suppress("UNUSED_PARAMETER") skipValidation: Boolean,
    ) : this(id, nodes, edges, version) {
        // Already validated — this path is for withVersion() only
    }

    /** Get successors of a node (what can follow). O(1). */
    public fun successors(nodeId: NodeId): Set<SceneNode> =
        successorsMap[nodeId] ?: emptySet()

    /** Get predecessors of a node (what came before). O(1). */
    public fun predecessors(nodeId: NodeId): Set<SceneNode> =
        predecessorsMap[nodeId] ?: emptySet()

    /** Get initial states (where episodes can start). */
    public fun initials(): Set<SceneNode> = initialsSet

    /** Get final states (where episodes can end). */
    public fun finals(): Set<SceneNode> = finalsSet

    /** Check if a state is initial (safe starting point). O(1). */
    public fun isInitial(state: SceneState): Boolean = state in initialsByState

    /** Check if a state is final (safe ending point). O(1). */
    public fun isFinal(state: SceneState): Boolean = state in finalsByState

    /** Check if a transition is valid. O(1). */
    public fun isValidTransition(from: NodeId, to: NodeId): Boolean =
        successorsMap[from]?.any { it.id == to } == true

    /** Get all paths from a node back to any final state. */
    public fun pathsToFinal(from: NodeId, maxPaths: Int = 100): List<List<SceneNode>> {
        val results = mutableListOf<List<SceneNode>>()
        dfsToFinal(from, mutableListOf(), results, maxPaths)
        return results
    }

    private fun dfsToFinal(
        current: NodeId,
        path: MutableList<SceneNode>,
        results: MutableList<List<SceneNode>>,
        maxPaths: Int,
    ) {
        if (results.size >= maxPaths) return

        val node = nodeById[current] ?: return
        path.add(node)

        if (isFinal(node.state)) {
            results.add(path.toList())
        } else {
            for (successor in successorsMap[current].orEmpty()) {
                if (results.size >= maxPaths) break
                dfsToFinal(successor.id, path, results, maxPaths)
            }
        }

        path.removeLast()
    }

    /** Add a node. Returns a new instance with bumped version. */
    public fun addNode(node: SceneNode): SceneDag {
        require(node.id !in nodeById) { "Node ${node.id} already exists" }
        return SceneDag(id, nodes + node, edges, version.next())
    }

    /** Add an edge. Returns a new instance with bumped version. */
    public fun addEdge(edge: SceneEdge): SceneDag {
        require(edge.from in nodeById) { "From node ${edge.from} not found" }
        require(edge.to in nodeById) { "To node ${edge.to} not found" }
        return SceneDag(id, nodes, edges + edge, version.next())
    }

    /** Create a copy with a new version. Skips re-validation. */
    public fun withVersion(newVersion: DagVersion): SceneDag =
        SceneDag(id, nodes, edges, newVersion, skipValidation = true)

    /** Check if the graph is acyclic using DFS. O(N+E). */
    private fun hasCycles(): Boolean {
        val visited = mutableSetOf<NodeId>()
        val recursionStack = mutableSetOf<NodeId>()

        fun dfs(nodeId: NodeId): Boolean {
            visited.add(nodeId)
            recursionStack.add(nodeId)

            for (successor in successorsMap[nodeId].orEmpty()) {
                if (successor.id !in visited) {
                    if (dfs(successor.id)) return true
                } else if (successor.id in recursionStack) {
                    return true
                }
            }

            recursionStack.remove(nodeId)
            return false
        }

        return nodes.any { it.id !in visited && dfs(it.id) }
    }

    /**
     * Equality by identity + version (logical identity, not structural).
     * Two DAGs with the same ID and version are considered equal,
     * even if they have different nodes/edges.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SceneDag) return false
        return id == other.id && version == other.version
    }

    override fun hashCode(): Int = 31 * id.hashCode() + version.hashCode()

    override fun toString(): String =
        "SceneDag(id=$id, nodes=${nodes.size}, edges=${edges.size}, version=$version)"

    public companion object {
        /** Create a new SceneDag. */
        public fun create(
            id: DagId,
            nodes: Set<SceneNode>,
            edges: Set<SceneEdge>,
            version: DagVersion = DagVersion(1),
        ): SceneDag = SceneDag(id, nodes, edges, version)
    }
}
