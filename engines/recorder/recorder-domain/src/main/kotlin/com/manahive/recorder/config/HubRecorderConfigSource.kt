package com.manahive.recorder.config

import com.manahive.contracts.engine.CallbackSubscription
import com.manahive.contracts.engine.Subscription
import com.manahive.contracts.policy.HubConfigSource
import com.manahive.kernel.ResidentId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Hub-based configuration source for Recorder Engine.
 *
 * Note: Conversion from StoredSemanticBucket is not yet implemented.
 * This adapter is a placeholder for future Hub integration.
 *
 * @property hubConfigSource The underlying Hub config source
 */
public class HubRecorderConfigSource(
    private val hubConfigSource: HubConfigSource,
) : RecorderConfigSource {
    private val cache = ConcurrentHashMap<String, RecorderConfig>()
    private val watchers = ConcurrentHashMap<String, CopyOnWriteArrayList<(RecorderConfig) -> Unit>>()

    override fun load(residentId: ResidentId): RecorderConfig {
        throw UnsupportedOperationException(
            "Hub mode for Recorder Engine not yet implemented. " +
                "StoredSemanticBucket to RecorderConfig conversion requires payload parsing."
        )
    }

    override fun loadAll(): Map<ResidentId, RecorderConfig> {
        throw UnsupportedOperationException("Hub mode for Recorder Engine not yet implemented.")
    }

    override fun subscribe(residentId: ResidentId, onChange: (RecorderConfig) -> Unit): Subscription {
        throw UnsupportedOperationException("Hub mode for Recorder Engine not yet implemented.")
    }

    override fun subscribeRecording(residentId: String, onChange: (RecorderConfig) -> Unit): Subscription {
        throw UnsupportedOperationException("Hub mode for Recorder Engine not yet implemented.")
    }

    override fun unsubscribe(residentId: ResidentId) {
        // No-op until Hub mode is implemented
    }
}
