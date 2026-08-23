package com.manahive.contracts.dag

import com.manahive.contracts.engine.CallbackSubscription
import com.manahive.contracts.engine.Subscription
import com.manahive.kernel.DagId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * In-memory implementation of DagStore.
 *
 * Thread-safe: uses ConcurrentHashMap + ReentrantLock for atomic operations.
 * Notifications happen OUTSIDE the lock to prevent deadlock.
 *
 * Fowler: "Repository" — in-memory persistence.
 */
public class InMemoryDagStore : DagStore {
    private val dags = ConcurrentHashMap<DagId, SceneDag>()
    private val watchers = ConcurrentHashMap<DagId, CopyOnWriteArrayList<(DagChange) -> Unit>>()
    private val locks = ConcurrentHashMap<DagId, ReentrantLock>()

    private fun getLock(dagId: DagId): ReentrantLock =
        locks.computeIfAbsent(dagId) { ReentrantLock() }

    override fun store(dag: SceneDag) {
        val lock = getLock(dag.id)
        var shouldNotify = false
        lock.withLock {
            val current = dags[dag.id]
            dags[dag.id] = dag
            shouldNotify = current == null || current.version != dag.version
        }
        if (shouldNotify) {
            notifyWatchers(DagChange.Updated(dag))
        }
    }

    override fun storeIfVersion(dag: SceneDag, expectedVersion: DagVersion): Boolean {
        val lock = getLock(dag.id)
        lock.withLock {
            val current = dags[dag.id]
            if (current != null && current.version != expectedVersion) {
                return false
            }
            dags[dag.id] = dag
        }
        // Only notify if we actually stored (version matched or new DAG)
        notifyWatchers(DagChange.Updated(dag))
        return true
    }

    override fun load(dagId: DagId): SceneDag? {
        return dags[dagId]
    }

    override fun exists(dagId: DagId): Boolean {
        return dags.containsKey(dagId)
    }

    override fun delete(dagId: DagId) {
        val removed: SceneDag?
        val callbacks: List<(DagChange) -> Unit>
        val lock = getLock(dagId)
        lock.withLock {
            removed = dags.remove(dagId)
            callbacks = watchers[dagId]?.toList().orEmpty()
            watchers.remove(dagId)
            locks.remove(dagId)
        }
        if (removed != null) {
            callbacks.forEach { it(DagChange.Deleted(dagId)) }
        }
    }

    override fun subscribe(dagId: DagId, onChange: (DagChange) -> Unit): Subscription {
        val list = watchers.computeIfAbsent(dagId) { CopyOnWriteArrayList() }
        list.add(onChange)

        return CallbackSubscription {
            list.remove(onChange)
            if (list.isEmpty()) {
                watchers.remove(dagId)
            }
        }
    }

    override fun unsubscribe(dagId: DagId) {
        watchers.remove(dagId)
    }

    private fun notifyWatchers(change: DagChange) {
        watchers[change.dagId]?.forEach { callback ->
            callback(change)
        }
    }
}
