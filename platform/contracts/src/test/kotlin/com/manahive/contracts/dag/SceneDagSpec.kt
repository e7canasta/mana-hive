package com.manahive.contracts.dag

import com.manahive.kernel.DagId
import com.manahive.kernel.NodeId
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.assertions.throwables.shouldThrow

class SceneDagSpec : DescribeSpec({
    describe("SceneDag") {
        it("should create a valid DAG") {
            val n1 = SceneNode(NodeId("lying"), SceneState.LYING)
            val n2 = SceneNode(NodeId("standing"), SceneState.STANDING)
            val edge = SceneEdge(NodeId("lying"), NodeId("standing"))

            val dag = SceneDag.create(
                id = DagId("scene-dag-base"),
                nodes = setOf(n1, n2),
                edges = setOf(edge),
            )

            dag.id shouldBe DagId("scene-dag-base")
            dag.nodes shouldHaveSize 2
            dag.edges shouldHaveSize 1
        }

        it("should find successors") {
            val lying = SceneNode(NodeId("lying"), SceneState.LYING)
            val standing = SceneNode(NodeId("standing"), SceneState.STANDING)
            val walking = SceneNode(NodeId("walking"), SceneState.WALKING)
            val e1 = SceneEdge(NodeId("lying"), NodeId("standing"))
            val e2 = SceneEdge(NodeId("standing"), NodeId("walking"))

            val dag = SceneDag.create(
                id = DagId("dag-1"),
                nodes = setOf(lying, standing, walking),
                edges = setOf(e1, e2),
            )

            val successors = dag.successors(NodeId("lying"))
            successors shouldHaveSize 1
            successors.map { it.state } shouldContain SceneState.STANDING
        }

        it("should find predecessors") {
            val lying = SceneNode(NodeId("lying"), SceneState.LYING)
            val standing = SceneNode(NodeId("standing"), SceneState.STANDING)
            val walking = SceneNode(NodeId("walking"), SceneState.WALKING)
            val e1 = SceneEdge(NodeId("lying"), NodeId("standing"))
            val e2 = SceneEdge(NodeId("standing"), NodeId("walking"))

            val dag = SceneDag.create(
                id = DagId("dag-1"),
                nodes = setOf(lying, standing, walking),
                edges = setOf(e1, e2),
            )

            val predecessors = dag.predecessors(NodeId("walking"))
            predecessors shouldHaveSize 1
            predecessors.map { it.state } shouldContain SceneState.STANDING
        }

        it("should find initials") {
            val lying = SceneNode(NodeId("lying"), SceneState.LYING)
            val standing = SceneNode(NodeId("standing"), SceneState.STANDING)
            val edge = SceneEdge(NodeId("lying"), NodeId("standing"))

            val dag = SceneDag.create(
                id = DagId("dag-1"),
                nodes = setOf(lying, standing),
                edges = setOf(edge),
            )

            val initials = dag.initials()
            initials shouldHaveSize 1
            initials.map { it.state } shouldContain SceneState.LYING
        }

        it("should find finals") {
            val lying = SceneNode(NodeId("lying"), SceneState.LYING)
            val standing = SceneNode(NodeId("standing"), SceneState.STANDING)
            val onFloor = SceneNode(NodeId("on_floor"), SceneState.ON_FLOOR)
            val e1 = SceneEdge(NodeId("lying"), NodeId("standing"))
            val e2 = SceneEdge(NodeId("standing"), NodeId("on_floor"))

            val dag = SceneDag.create(
                id = DagId("dag-1"),
                nodes = setOf(lying, standing, onFloor),
                edges = setOf(e1, e2),
            )

            val finals = dag.finals()
            finals shouldHaveSize 1
            finals.map { it.state } shouldContain SceneState.ON_FLOOR
        }

        it("should check if state is initial") {
            val lying = SceneNode(NodeId("lying"), SceneState.LYING)
            val standing = SceneNode(NodeId("standing"), SceneState.STANDING)
            val edge = SceneEdge(NodeId("lying"), NodeId("standing"))

            val dag = SceneDag.create(
                id = DagId("dag-1"),
                nodes = setOf(lying, standing),
                edges = setOf(edge),
            )

            dag.isInitial(SceneState.LYING) shouldBe true
            dag.isInitial(SceneState.STANDING) shouldBe false
        }

        it("should check if state is final") {
            val lying = SceneNode(NodeId("lying"), SceneState.LYING)
            val standing = SceneNode(NodeId("standing"), SceneState.STANDING)
            val onFloor = SceneNode(NodeId("on_floor"), SceneState.ON_FLOOR)
            val e1 = SceneEdge(NodeId("lying"), NodeId("standing"))
            val e2 = SceneEdge(NodeId("standing"), NodeId("on_floor"))

            val dag = SceneDag.create(
                id = DagId("dag-1"),
                nodes = setOf(lying, standing, onFloor),
                edges = setOf(e1, e2),
            )

            dag.isFinal(SceneState.ON_FLOOR) shouldBe true
            dag.isFinal(SceneState.STANDING) shouldBe false
        }

        it("should check valid transition") {
            val lying = SceneNode(NodeId("lying"), SceneState.LYING)
            val standing = SceneNode(NodeId("standing"), SceneState.STANDING)
            val edge = SceneEdge(NodeId("lying"), NodeId("standing"))

            val dag = SceneDag.create(
                id = DagId("dag-1"),
                nodes = setOf(lying, standing),
                edges = setOf(edge),
            )

            dag.isValidTransition(NodeId("lying"), NodeId("standing")) shouldBe true
            dag.isValidTransition(NodeId("standing"), NodeId("lying")) shouldBe false
        }

        it("should find paths to final") {
            val lying = SceneNode(NodeId("lying"), SceneState.LYING)
            val standing = SceneNode(NodeId("standing"), SceneState.STANDING)
            val onFloor = SceneNode(NodeId("on_floor"), SceneState.ON_FLOOR)
            val e1 = SceneEdge(NodeId("lying"), NodeId("standing"))
            val e2 = SceneEdge(NodeId("standing"), NodeId("on_floor"))

            val dag = SceneDag.create(
                id = DagId("dag-1"),
                nodes = setOf(lying, standing, onFloor),
                edges = setOf(e1, e2),
            )

            val paths = dag.pathsToFinal(NodeId("lying"))
            paths shouldHaveSize 1
            paths[0].map { it.state } shouldBe listOf(
                SceneState.LYING,
                SceneState.STANDING,
                SceneState.ON_FLOOR,
            )
        }

        it("should reject cycles") {
            val n1 = SceneNode(NodeId("n1"), SceneState.STANDING)
            val n2 = SceneNode(NodeId("n2"), SceneState.WALKING)
            val e1 = SceneEdge(NodeId("n1"), NodeId("n2"))
            val e2 = SceneEdge(NodeId("n2"), NodeId("n1"))

            shouldThrow<IllegalArgumentException> {
                SceneDag.create(
                    id = DagId("dag-1"),
                    nodes = setOf(n1, n2),
                    edges = setOf(e1, e2),
                )
            }
        }

        it("should reject edges referencing non-existent nodes") {
            val n1 = SceneNode(NodeId("n1"), SceneState.LYING)
            val edge = SceneEdge(NodeId("n1"), NodeId("n99"))

            shouldThrow<IllegalArgumentException> {
                SceneDag.create(
                    id = DagId("dag-1"),
                    nodes = setOf(n1),
                    edges = setOf(edge),
                )
            }
        }

        it("should add node") {
            val lying = SceneNode(NodeId("lying"), SceneState.LYING)
            val dag = SceneDag.create(
                id = DagId("dag-1"),
                nodes = setOf(lying),
                edges = emptySet(),
            )

            val standing = SceneNode(NodeId("standing"), SceneState.STANDING)
            val dag2 = dag.addNode(standing)
            dag2.nodes shouldHaveSize 2
        }

        it("should add edge") {
            val lying = SceneNode(NodeId("lying"), SceneState.LYING)
            val standing = SceneNode(NodeId("standing"), SceneState.STANDING)
            val dag = SceneDag.create(
                id = DagId("dag-1"),
                nodes = setOf(lying, standing),
                edges = emptySet(),
            )

            val edge = SceneEdge(NodeId("lying"), NodeId("standing"))
            val dag2 = dag.addEdge(edge)
            dag2.edges shouldHaveSize 1
        }

        it("should create copy with new version") {
            val lying = SceneNode(NodeId("lying"), SceneState.LYING)
            val dag = SceneDag.create(
                id = DagId("dag-1"),
                nodes = setOf(lying),
                edges = emptySet(),
            )

            val dag2 = dag.withVersion(DagVersion(2))
            dag2.version shouldBe DagVersion(2)
            dag2.id shouldBe dag.id
        }

        it("should allow empty DAG") {
            val dag = SceneDag.create(
                id = DagId("dag-1"),
                nodes = emptySet(),
                edges = emptySet(),
            )

            dag.nodes shouldBe emptySet()
            dag.edges shouldBe emptySet()
            dag.initials() shouldBe emptySet()
            dag.finals() shouldBe emptySet()
        }

        it("should handle single-node DAG (initial == final)") {
            val lying = SceneNode(NodeId("lying"), SceneState.LYING)
            val dag = SceneDag.create(
                id = DagId("dag-1"),
                nodes = setOf(lying),
                edges = emptySet(),
            )

            dag.initials() shouldHaveSize 1
            dag.finals() shouldHaveSize 1
            dag.isInitial(SceneState.LYING) shouldBe true
            dag.isFinal(SceneState.LYING) shouldBe true
        }

        it("should handle diamond graph with multiple paths") {
            val a = SceneNode(NodeId("a"), SceneState.LYING)
            val b = SceneNode(NodeId("b"), SceneState.STANDING)
            val c = SceneNode(NodeId("c"), SceneState.WALKING)
            val d = SceneNode(NodeId("d"), SceneState.ON_FLOOR)
            val e1 = SceneEdge(NodeId("a"), NodeId("b"))
            val e2 = SceneEdge(NodeId("a"), NodeId("c"))
            val e3 = SceneEdge(NodeId("b"), NodeId("d"))
            val e4 = SceneEdge(NodeId("c"), NodeId("d"))

            val dag = SceneDag.create(
                id = DagId("dag-1"),
                nodes = setOf(a, b, c, d),
                edges = setOf(e1, e2, e3, e4),
            )

            val paths = dag.pathsToFinal(NodeId("a"))
            paths shouldHaveSize 2
        }

        it("should return empty paths when no path to final") {
            val a = SceneNode(NodeId("a"), SceneState.LYING)
            val b = SceneNode(NodeId("b"), SceneState.STANDING)
            val c = SceneNode(NodeId("c"), SceneState.WALKING)
            val e1 = SceneEdge(NodeId("a"), NodeId("b"))
            val e2 = SceneEdge(NodeId("a"), NodeId("c"))

            val dag = SceneDag.create(
                id = DagId("dag-1"),
                nodes = setOf(a, b, c),
                edges = setOf(e1, e2),
            )

            // b and c are finals (no successors), so paths exist
            // Test with a disconnected node instead
            val d = SceneNode(NodeId("d"), SceneState.IN_HALLWAY)
            val dag2 = SceneDag.create(
                id = DagId("dag-2"),
                nodes = setOf(a, b, c, d),
                edges = setOf(e1, e2),
            )

            // d is disconnected — no path from d to any final reachable from a
            // Actually d IS a final (no edges), so pathsToFinal(d) returns [[d]]
            // Need a different test: a node that can't reach any final
            // In a DAG where a→b, c is isolated, paths from a to final: a→b (b is final)
            // Let's test that a node with only outgoing edges to non-finals returns empty
            val e = SceneNode(NodeId("e"), SceneState.IN_BATHROOM)
            val f = SceneNode(NodeId("f"), SceneState.IN_HALLWAY)
            val e3 = SceneEdge(NodeId("e"), NodeId("f"))
            // Both e and f have no further edges, so both are finals
            // Actually this won't work either — both are finals
            // The real test: a→b→c where c is final, a→d where d is not final and has no path to final
            // But in our model, every node with no successors IS a final
            // So the only way to have no path is if the node doesn't exist
            val paths = dag2.pathsToFinal(NodeId("nonexistent"))
            paths shouldHaveSize 0
        }

        it("should reject duplicate node ID") {
            val n1 = SceneNode(NodeId("lying"), SceneState.LYING)
            val n2 = SceneNode(NodeId("lying"), SceneState.STANDING)

            shouldThrow<IllegalArgumentException> {
                SceneDag.create(
                    id = DagId("dag-1"),
                    nodes = setOf(n1, n2),
                    edges = emptySet(),
                )
            }
        }

        it("should reject addNode with duplicate ID") {
            val lying = SceneNode(NodeId("lying"), SceneState.LYING)
            val dag = SceneDag.create(
                id = DagId("dag-1"),
                nodes = setOf(lying),
                edges = emptySet(),
            )

            shouldThrow<IllegalArgumentException> {
                dag.addNode(SceneNode(NodeId("lying"), SceneState.STANDING))
            }
        }

        it("should reject addEdge with non-existent node") {
            val lying = SceneNode(NodeId("lying"), SceneState.LYING)
            val dag = SceneDag.create(
                id = DagId("dag-1"),
                nodes = setOf(lying),
                edges = emptySet(),
            )

            shouldThrow<IllegalArgumentException> {
                dag.addEdge(SceneEdge(NodeId("lying"), NodeId("standing")))
            }
        }

        it("should have consistent equals/hashCode") {
            val lying = SceneNode(NodeId("lying"), SceneState.LYING)
            val dag1 = SceneDag.create(
                id = DagId("dag-1"),
                nodes = setOf(lying),
                edges = emptySet(),
                version = DagVersion(1),
            )
            val dag2 = SceneDag.create(
                id = DagId("dag-1"),
                nodes = setOf(lying),
                edges = emptySet(),
                version = DagVersion(1),
            )

            dag1 shouldBe dag2
            dag1.hashCode() shouldBe dag2.hashCode()
        }
    }
})
