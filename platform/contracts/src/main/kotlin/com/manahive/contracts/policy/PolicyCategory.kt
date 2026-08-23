package com.manahive.contracts.policy

/**
 * Semantic categories for policy buckets.
 *
 * Each engine subscribes to the category it needs:
 * - CALIBRATION → Scene Engine (dwell, hysteresis, confidence)
 * - RESPONSE → Sentinel (severity, closure, trigger)
 * - ESCALATION → Harbor (escalation, staff_assist)
 * - RECORDING → Recorder (record, window, quality)
 *
 * Vernon: separation is SEMANTIC, not by engine name.
 * The publisher doesn't know who subscribes.
 * The subscriber doesn't know who publishes.
 */
public enum class PolicyCategory(
    /** NATS subject segment: hub.policy.{subject}.v1.{residentId} */
    public val subject: String,
    public val description: String,
) {
    CALIBRATION(
        subject = "calibration",
        description = "dwell, hysteresis, confidence",
    ),
    RESPONSE(
        subject = "response",
        description = "severity, closure, trigger",
    ),
    ESCALATION(
        subject = "escalation",
        description = "escalation, staff_assist",
    ),
    RECORDING(
        subject = "recording",
        description = "record, window, quality",
    );

    /** Build the full NATS subject for a resident. */
    public fun subjectFor(residentId: String): String =
        "hub.policy.$subject.v1.$residentId"
}
