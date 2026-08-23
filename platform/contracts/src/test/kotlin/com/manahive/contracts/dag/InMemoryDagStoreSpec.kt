package com.manahive.contracts.dag

import com.manahive.kernel.DagId
import com.manahive.kernel.NodeId
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class InMemoryDagStoreSpec : DescribeSpec({
    describe("InMemoryDagStore") {
        it("should store and load a DAG") {
            val store = InMemoryDagStore()
            val node = SceneNode(NodeId("lying"), SceneState.LYING)
            val dag = SceneDag.create(
                id = DagId("dag-1"),
                nodes = setOf(node),
                edges = emptySet(),
            )

            store.store(dag)
            val loaded = store.load(DagId("dag-1"))

            loaded shouldBe dag
        }

        it("should return null for non-existent DAG") {
            val store = InMemoryDagStore()
            val loaded = store.load(DagId("non-existent"))
            loaded shouldBe null
        }

        it("should check existence") {
            val store = InMemoryDagStore()
            val node = SceneNode(NodeId("lying"), SceneState.LYING)
            val dag = SceneDag.create(
                id = DagId("dag-1"),
                nodes = setOf(node),
                edges = emptySet(),
            )

            store.exists(DagId("dag-1")) shouldBe false
            store.store(dag)
            store.exists(DagId("dag-1")) shouldBe true
        }

        it("should delete a DAG") {
            val store = InMemoryDagStore()
            val node = SceneNode(NodeId("lying"), SceneState.LYING)
            val dag = SceneDag.create(
                id = DagId("dag-1"),
                nodes = setOf(node),
                edges = emptySet(),
            )

            store.store(dag)
            store.delete(DagId("dag-1"))

            val loaded = store.load(DagId("dag-1"))
            loaded shouldBe null
        }

        it("should notify subscribers on delete with Deleted event") {
            val store = InMemoryDagStore()
            val node = SceneNode(NodeId("lying"), SceneState.LYING)
            val dag = SceneDag.create(
                id = DagId("dag-1"),
                nodes = setOf(node),
                edges = emptySet(),
            )

            var receivedChange: DagChange? = null
            store.subscribe(DagId("dag-1")) { receivedChange = it }

            store.store(dag)
            receivedChange.shouldBeInstanceOf<DagChange.Updated>()
            (receivedChange as DagChange.Updated).dag shouldBe dag

            store.delete(DagId("dag-1"))
            receivedChange.shouldBeInstanceOf<DagChange.Deleted>()
            (receivedChange as DagChange.Deleted).dagId shouldBe DagId("dag-1")
        }

        it("should subscribe to DAG changes") {
            val store = InMemoryDagStore()
            val node = SceneNode(NodeId("lying"), SceneState.LYING)
            val dag = SceneDag.create(
                id = DagId("dag-1"),
                nodes = setOf(node),
                edges = emptySet(),
            )

            var receivedChange: DagChange? = null
            val subscription = store.subscribe(DagId("dag-1")) { receivedChange = it }

            store.store(dag)

            receivedChange.shouldBeInstanceOf<DagChange.Updated>()
            subscription.isActive shouldBe true
        }

        it("should unsubscribe from DAG changes") {
            val store = InMemoryDagStore()
            val node = SceneNode(NodeId("lying"), SceneState.LYING)
            val dag = SceneDag.create(
                id = DagId("dag-1"),
                nodes = setOf(node),
                edges = emptySet(),
            )

            var callCount = 0
            val subscription = store.subscribe(DagId("dag-1")) { callCount++ }

            store.store(dag)
            callCount shouldBe 1

            subscription.cancel()
            store.store(dag.withVersion(DagVersion(2)))
            callCount shouldBe 1
        }

        it("should unsubscribe all callbacks for a DAG") {
            val store = InMemoryDagStore()
            val node = SceneNode(NodeId("lying"), SceneState.LYING)
            val dag = SceneDag.create(
                id = DagId("dag-1"),
                nodes = setOf(node),
                edges = emptySet(),
            )

            var callCount = 0
            store.subscribe(DagId("dag-1")) { callCount++ }

            store.store(dag)
            callCount shouldBe 1

            store.unsubscribe(DagId("dag-1"))
            store.store(dag.withVersion(DagVersion(2)))
            callCount shouldBe 1
        }

        it("should store with version check") {
            val store = InMemoryDagStore()
            val node = SceneNode(NodeId("lying"), SceneState.LYING)
            val dag = SceneDag.create(
                id = DagId("dag-1"),
                nodes = setOf(node),
                edges = emptySet(),
                version = DagVersion(1),
            )

            store.store(dag)

            val dag2 = dag.withVersion(DagVersion(2))
            val success = store.storeIfVersion(dag2, DagVersion(1))
            success shouldBe true

            val loaded = store.load(DagId("dag-1"))
            loaded?.version shouldBe DagVersion(2)
        }

        it("should reject version mismatch") {
            val store = InMemoryDagStore()
            val node = SceneNode(NodeId("lying"), SceneState.LYING)
            val dag = SceneDag.create(
                id = DagId("dag-1"),
                nodes = setOf(node),
                edges = emptySet(),
                version = DagVersion(1),
            )

            store.store(dag)

            val dag2 = dag.withVersion(DagVersion(3))
            val success = store.storeIfVersion(dag2, DagVersion(2))
            success shouldBe false

            val loaded = store.load(DagId("dag-1"))
            loaded?.version shouldBe DagVersion(1)
        }
    }
})
