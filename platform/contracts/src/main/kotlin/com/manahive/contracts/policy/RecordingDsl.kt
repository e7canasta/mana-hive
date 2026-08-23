package com.manahive.contracts.policy

import com.manahive.contracts.common.Fingerprint
import com.manahive.contracts.common.buildFingerprint
import java.time.Duration

/**
 * Build a [RecordingPayload] with type-safe DSL.
 *
 * ```kotlin
 * val payload = buildRecordingPayload {
 *     enabled(true)
 *     preEventWindow(30.seconds)
 *     postEventWindow(2.minutes)
 *     quality(RecordingQuality.HIGH)
 * }
 * ```
 */
public fun buildRecordingPayload(
    init: RecordingBuilder.() -> Unit,
): RecordingPayload {
    val builder = RecordingBuilder()
    builder.init()
    return builder.build()
}

@PolicyDsl
public class RecordingBuilder {
    private var enabled: Boolean = false
    private var preEventWindow: Duration = Duration.ofSeconds(30)
    private var postEventWindow: Duration = Duration.ofMinutes(2)
    private var quality: RecordingQuality = RecordingQuality.MEDIUM

    public fun enabled(value: Boolean) {
        enabled = value
    }

    public fun preEventWindow(window: Duration) {
        require(window >= Duration.ZERO) { "preEventWindow must not be negative" }
        preEventWindow = window
    }

    public fun postEventWindow(window: Duration) {
        require(window >= Duration.ZERO) { "postEventWindow must not be negative" }
        postEventWindow = window
    }

    public fun quality(value: RecordingQuality) {
        quality = value
    }

    public fun build(): RecordingPayload {
        val fingerprint: Fingerprint = buildFingerprint(
            "enabled" to enabled,
            "pre" to preEventWindow,
            "post" to postEventWindow,
            "q" to quality,
        )
        return RecordingPayload(
            config = RecordingConfig(
                enabled = enabled,
                preEventWindow = preEventWindow,
                postEventWindow = postEventWindow,
                quality = quality,
            ),
            fingerprint = fingerprint,
        )
    }
}
