package com.manahive.politica

import com.manahive.contracts.policy.CalibrationChanged
import com.manahive.contracts.policy.CalibrationPayload
import com.manahive.contracts.policy.EscalationChanged
import com.manahive.contracts.policy.EscalationPayload
import com.manahive.contracts.policy.PolicyEvent
import com.manahive.contracts.policy.RecordingChanged
import com.manahive.contracts.policy.RecordingPayload
import com.manahive.contracts.policy.ResponseChanged
import com.manahive.contracts.policy.ResponsePayload
import java.time.Instant

/**
 * Maps SemanticBucket to PolicyEvent.
 *
 * Separated from SemanticBucket (SRP): the bucket is pure data,
 * the mapper knows how to convert to events.
 *
 * Fowler: "Extract Class" — mapper is a cohesive unit of behavior.
 * Vernon: Application Service — orchestrates conversion.
 */
internal object PolicyBucketMapper {

    /**
     * Convert a SemanticBucket to a PolicyEvent.
     *
     * @param bucket The bucket to convert
     * @param at Timestamp for the event
     * @return The corresponding PolicyEvent subtype
     */
    fun toEvent(bucket: SemanticBucket, at: Instant): PolicyEvent =
        when (bucket.payload) {
            is CalibrationPayload -> CalibrationChanged(
                residentId = bucket.residentId,
                at = at,
                version = bucket.version,
                fingerprint = bucket.payload.fingerprint,
                calibration = bucket.payload.toPolicyCalibration(bucket.residentId),
            )
            is ResponsePayload -> ResponseChanged(
                residentId = bucket.residentId,
                at = at,
                version = bucket.version,
                fingerprint = bucket.payload.fingerprint,
                rules = bucket.payload.rules,
            )
            is EscalationPayload -> EscalationChanged(
                residentId = bucket.residentId,
                at = at,
                version = bucket.version,
                fingerprint = bucket.payload.fingerprint,
                escalation = bucket.payload.config,
            )
            is RecordingPayload -> RecordingChanged(
                residentId = bucket.residentId,
                at = at,
                version = bucket.version,
                fingerprint = bucket.payload.fingerprint,
                recording = bucket.payload.config,
            )
        }

    /**
     * Convert a list of buckets to events.
     *
     * @param buckets The buckets to convert
     * @param at Timestamp for all events
     * @return List of PolicyEvents
     */
    fun toEvents(buckets: List<SemanticBucket>, at: Instant): List<PolicyEvent> =
        buckets.map { toEvent(it, at) }
}
