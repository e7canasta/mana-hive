package com.manahive.recorder.config

import com.manahive.contracts.policy.RecordingQuality
import java.time.Duration

/**
 * Recorder Engine configuration for a resident.
 *
 * This is the domain-specific configuration that Recorder Engine uses.
 * It's derived from LocalConfig (TOML) or StoredSemanticBucket (Hub).
 *
 * Fowler: "Domain Model" — captures business rules for recording.
 *
 * @property residentId Resident identifier
 * @property enabled Whether recording is enabled
 * @property preEventWindow How long before the trigger to start recording
 * @property postEventWindow How long after the trigger to stop recording
 * @property quality Recording quality
 * @property fingerprint Configuration fingerprint for change detection
 */
public data class RecorderConfig(
    val residentId: String,
    val enabled: Boolean = false,
    val preEventWindow: Duration = Duration.ofSeconds(30),
    val postEventWindow: Duration = Duration.ofMinutes(2),
    val quality: RecordingQuality = RecordingQuality.MEDIUM,
    val fingerprint: String = "",
) {
    init {
        require(residentId.isNotBlank()) { "Resident ID must not be blank" }
        require(preEventWindow >= Duration.ZERO) { "Pre-event window must not be negative" }
        require(postEventWindow >= Duration.ZERO) { "Post-event window must not be negative" }
    }

    /**
     * Get the total recording window duration.
     *
     * @return Total duration (pre + post)
     */
    public fun totalWindow(): Duration = preEventWindow.plus(postEventWindow)
}
