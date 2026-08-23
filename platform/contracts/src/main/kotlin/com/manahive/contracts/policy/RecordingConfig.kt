package com.manahive.contracts.policy

import java.time.Duration

/**
 * Recording configuration.
 * Defines when and how to record video evidence.
 *
 * Used by Recorder Engine to manage NVR recording.
 */
public data class RecordingConfig(
    /** Whether recording is enabled. */
    public val enabled: Boolean,
    /** Pre-event window: how long before trigger to start recording. */
    public val preEventWindow: Duration,
    /** Post-event window: how long after trigger to keep recording. */
    public val postEventWindow: Duration,
    /** Recording quality: low, medium, high. */
    public val quality: RecordingQuality,
)

/**
 * Recording quality levels.
 *
 * LOW: 480p, minimal storage
 * MEDIUM: 720p, balanced
 * HIGH: 1080p, maximum detail
 */
public enum class RecordingQuality {
    LOW,
    MEDIUM,
    HIGH,
}
