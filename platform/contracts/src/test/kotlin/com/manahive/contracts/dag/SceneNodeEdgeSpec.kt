package com.manahive.contracts.dag

import com.manahive.kernel.NodeId
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.assertions.throwables.shouldThrow

class SceneNodeEdgeSpec : DescribeSpec({
    describe("SceneNode") {
        it("should create a valid node") {
            val node = SceneNode(NodeId("lying"), SceneState.LYING)
            node.id shouldBe NodeId("lying")
            node.state shouldBe SceneState.LYING
        }

        it("should have equality by value") {
            val n1 = SceneNode(NodeId("lying"), SceneState.LYING)
            val n2 = SceneNode(NodeId("lying"), SceneState.LYING)
            n1 shouldBe n2
        }
    }

    describe("SceneEdge") {
        it("should create a valid edge") {
            val edge = SceneEdge(NodeId("lying"), NodeId("standing"))
            edge.from shouldBe NodeId("lying")
            edge.to shouldBe NodeId("standing")
        }

        it("should reject self-loops") {
            shouldThrow<IllegalArgumentException> {
                SceneEdge(NodeId("lying"), NodeId("lying"))
            }
        }
    }
})
