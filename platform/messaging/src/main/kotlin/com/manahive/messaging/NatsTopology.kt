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
        ensure("PERCEPTION", Subjects.PERCEPTION_WILDCARD, Duration.ofDays(7))
        ensure("SCENE", Subjects.SCENE_WILDCARD, Duration.ofDays(7))
        ensure("SENTINEL", Subjects.SENTINEL_WILDCARD, Duration.ofDays(7))
        ensure("ALARM", Subjects.ALARM_WILDCARD, Duration.ofDays(7))
        ensure("POLICY", Subjects.POLICY_WILDCARD, Duration.ofDays(7))
        ensure("RECORDER", Subjects.RECORDER_WILDCARD, Duration.ofDays(7))
        ensure("EVIDENCE", Subjects.EVIDENCE_WILDCARD, Duration.ofDays(7))
    }

    private fun ensure(name: String, subjects: String, maxAge: Duration) {
        val config = StreamConfiguration.builder()
            .name(name)
            .subjects(subjects)
            .storageType(StorageType.File)
            .retentionPolicy(RetentionPolicy.Limits)
            .maxAge(maxAge)
            .duplicateWindow(Duration.ofMinutes(10))
            .build()
        val existing = jsm.streamNames.contains(name)
        if (existing) jsm.updateStream(config) else jsm.addStream(config)
    }
}
