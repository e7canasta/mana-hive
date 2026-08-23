package com.manahive.scene.core

import com.manahive.contracts.dag.DagVersion
import com.manahive.contracts.dag.SceneDag
import com.manahive.contracts.dag.SceneEdge
import com.manahive.contracts.dag.SceneNode
import com.manahive.contracts.dag.SceneState
import com.manahive.contracts.scene.StateKind
import com.manahive.kernel.DagId
import com.manahive.kernel.NodeId
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class SceneDagToTransitionTableSpec : DescribeSpec({
    describe("SceneDagToTransitionTable") {
        val lying = SceneNode(NodeId("lying"), SceneState.LYING)
        val standing = SceneNode(NodeId("standing"), SceneState.STANDING)
        val walking = SceneNode(NodeId("walking"), SceneState.WALKING)
        val bathroom = SceneNode(NodeId("bathroom"), SceneState.IN_BATHROOM)

        val dag = SceneDag.create(
            id = DagId("test-dag"),
            nodes = setOf(lying, standing, walking, bathroom),
            edges = setOf(
                SceneEdge(NodeId("lying"), NodeId("standing")),
                SceneEdge(NodeId("standing"), NodeId("walking")),
                SceneEdge(NodeId("standing"), NodeId("bathroom")),
                SceneEdge(NodeId("walking"), NodeId("bathroom")),
            ),
        )

        it("should convert DAG to TransitionTable") {
            val table = SceneDagToTransitionTable.convert(dag)

            table.isLegal(StateKind.LYING, StateKind.STANDING) shouldBe true
            table.isLegal(StateKind.STANDING, StateKind.IN_ROOM) shouldBe true // walking maps to IN_ROOM
            table.isLegal(StateKind.STANDING, StateKind.IN_BATHROOM) shouldBe true
            table.isLegal(StateKind.IN_ROOM, StateKind.IN_BATHROOM) shouldBe true
        }

        it("should reject illegal transitions") {
            val table = SceneDagToTransitionTable.convert(dag)

            table.isLegal(StateKind.LYING, StateKind.IN_ROOM) shouldBe false
            table.isLegal(StateKind.LYING, StateKind.IN_BATHROOM) shouldBe false
        }

        it("should check valid transition via DAG") {
            SceneDagToTransitionTable.isValidTransition(dag, StateKind.LYING, StateKind.STANDING) shouldBe true
            SceneDagToTransitionTable.isValidTransition(dag, StateKind.LYING, StateKind.IN_ROOM) shouldBe false
        }

        it("should check safe state via DAG") {
            // LYING is not a final state in this DAG (it has edges going out)
            // In a real DAG, LYING would be both initial and final
            SceneDagToTransitionTable.isSafeState(dag, StateKind.LYING) shouldBe false
            SceneDagToTransitionTable.isSafeState(dag, StateKind.STANDING) shouldBe false
        }

        it("should use default hysteresis") {
            val table = SceneDagToTransitionTable.convert(dag)

            val hysteresis = table.hysteresis(StateKind.LYING, StateKind.STANDING)
            hysteresis shouldBe java.time.Duration.ofMillis(1200)
        }
    }
})
