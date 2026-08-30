package com.manahive.scene.core

import com.manahive.contracts.dag.SceneDag
import com.manahive.contracts.dag.SceneState
import com.manahive.contracts.policy.TransitionKey
import com.manahive.contracts.scene.StateKind
import java.time.Duration
import com.manahive.contracts.dag.toStateKind
import com.manahive.contracts.dag.toSceneState

/**
 * Factory to create TransitionTable from SceneDag.
 *
 * Bridges the SceneDag (graph model) to the TransitionTable (FSM model)
 * used by the Scene Interpreter.
 *
 * Fowler: "Adapter" — converts between two representations.
 */
public object SceneDagToTransitionTable {

    private val DEFAULT_HYSTERESIS = Duration.ofMillis(1200)

    /**
     * Convert a SceneDag to a TransitionTable.
     *
     * Each edge in the DAG becomes a legal transition in the table.
     * Hysteresis defaults to 1200ms for all transitions.
     *
     * @param dag The SceneDag to convert
     * @param defaultHysteresis Default hysteresis for all transitions
     * @return A TransitionTable with all legal transitions from the DAG
     */
    public fun convert(
        dag: SceneDag,
        defaultHysteresis: Duration = DEFAULT_HYSTERESIS,
    ): TransitionTable {
        val legal = mutableMapOf<TransitionKey, Duration>()

        for (edge in dag.edges) {
            val from = edge.from.toStateKind(dag) ?: continue
            val to = edge.to.toStateKind(dag) ?: continue
            legal[TransitionKey(from, to)] = defaultHysteresis
        }

        return TransitionTable(legal)
    }

    /**
     * Convert a SceneDag to a TransitionTable with per-transition hysteresis.
     *
     * @param dag The SceneDag to convert
     * @param hysteresisMap Map of (from, to) pairs to hysteresis durations
     * @param defaultHysteresis Default hysteresis for transitions not in the map
     * @return A TransitionTable with all legal transitions from the DAG
     */
    public fun convert(
        dag: SceneDag,
        hysteresisMap: Map<Pair<SceneState, SceneState>, Duration>,
        defaultHysteresis: Duration = DEFAULT_HYSTERESIS,
    ): TransitionTable {
        val legal = mutableMapOf<TransitionKey, Duration>()

        for (edge in dag.edges) {
            val from = edge.from.toStateKind(dag) ?: continue
            val to = edge.to.toStateKind(dag) ?: continue
            val fromState = dag.nodeById(edge.from)?.state ?: continue
            val toState = dag.nodeById(edge.to)?.state ?: continue
            val hysteresis = hysteresisMap[Pair(fromState, toState)] ?: defaultHysteresis
            legal[TransitionKey(from, to)] = hysteresis
        }

        return TransitionTable(legal)
    }

    /**
     * Check if a transition is valid according to the DAG.
     *
     * @param dag The SceneDag to check against
     * @param from The source state
     * @param to The target state
     * @return true if the transition is valid
     */
    public fun isValidTransition(dag: SceneDag, from: StateKind, to: StateKind): Boolean {
        val fromNode = dag.nodes.find { it.state.toStateKind() == from }?.id ?: return false
        val toNode = dag.nodes.find { it.state.toStateKind() == to }?.id ?: return false
        return dag.isValidTransition(fromNode, toNode)
    }

    /**
     * Check if a state is safe (final) according to the DAG.
     *
     * @param dag The SceneDag to check against
     * @param state The state to check
     * @return true if the state is safe (final)
     */
    public fun isSafeState(dag: SceneDag, state: StateKind): Boolean {
        val sceneState = state.toSceneState() ?: return false
        return dag.isFinal(sceneState)
    }

    // ── Mapping helpers ──────────────────────────────────────────────

    private fun com.manahive.kernel.NodeId.toStateKind(dag: SceneDag): StateKind? {
        val sceneState = dag.nodes.find { it.id == this }?.state ?: return null
        return sceneState.toStateKind()
    }

    // El mapeo vive en contracts, junto a los enums que une: es una decision
    // de dominio, no un detalle de como se arma esta tabla.
}
