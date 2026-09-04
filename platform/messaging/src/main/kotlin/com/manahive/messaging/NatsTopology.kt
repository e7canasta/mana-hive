package com.manahive.messaging

import io.nats.client.JetStreamManagement
import io.nats.client.api.RetentionPolicy
import io.nats.client.api.StorageType
import io.nats.client.api.StreamConfiguration
import java.time.Duration

/**
 * Declares the JetStream streams idempotently. Every service calls
 * `ensure(...)` for the streams it touches on startup; first one wins,
 * the rest verify.
 *
 * Retention is limits-based ON PURPOSE: the bus is transport with a buffer,
 * the hub ledger is the system of record. Nothing relies on bus retention
 * for truth — replay and audit come from the hub.
 */
public class NatsTopology(private val jsm: JetStreamManagement) {

    public fun ensureAll() {
        ensure("PERCEPTION", Duration.ofDays(7), Subjects.PERCEPTION_WILDCARD)
        ensure("SCENE", Duration.ofDays(7), Subjects.SCENE_WILDCARD)
        ensure("SENTINEL", Duration.ofDays(7), Subjects.SENTINEL_WILDCARD)
        ensure("ALARM", Duration.ofDays(7), Subjects.ALARM_WILDCARD)
        ensure("POLICY", Duration.ofDays(7), Subjects.POLICY_WILDCARD)
        ensure("RECORDER", Duration.ofDays(7), Subjects.RECORDER_WILDCARD)
        ensure("EVIDENCE", Duration.ofDays(7), Subjects.EVIDENCE_WILDCARD)
        ensure("NOTICE", Duration.ofDays(7), Subjects.NOTICE_WILDCARD)
        ensure("HUB", Duration.ofDays(30), Subjects.HUB_EPISODE_WILDCARD, Subjects.HUB_SCENE_WILDCARD)
    }

    private fun ensure(name: String, maxAge: Duration, vararg subjects: String) {
        val config = StreamConfiguration.builder()
            .name(name)
            .subjects(*subjects)
            .storageType(StorageType.File)
            .retentionPolicy(RetentionPolicy.Limits)
            .maxAge(maxAge)
            .duplicateWindow(Duration.ofMinutes(10))
            .build()
        val existing = jsm.streamNames.contains(name)
        if (existing) jsm.updateStream(config) else jsm.addStream(config)
    }
}
