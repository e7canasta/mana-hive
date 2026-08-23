package com.manahive.infrastructure.config

import com.manahive.contracts.policy.ConfidenceConfig
import com.manahive.contracts.policy.DwellThreshold
import com.manahive.contracts.policy.RecordingQuality
import com.manahive.contracts.policy.StaffAssistMode
import com.manahive.contracts.scene.StateKind
import java.time.Duration

/**
 * Local configuration model for TOML parsing.
 *
 * Represents the structure of a TOML configuration file.
 * This is a pure data model with no behavior.
 *
 * Fowler: "Data Transfer Object" — carries data between processes.
 *
 * Vernon: "Immutable Value Object" — no identity, compared by value.
 *
 * Note: Validation is done in TomlConfigParser, not in the DTO.
 * This allows using copy() without validation errors.
 *
 * @property resident Resident information
 * @property calibration Calibration configuration
 * @property escalation Escalation configuration
 * @property recording Recording configuration
 */
public data class LocalConfig(
    val resident: ResidentInfo,
    val calibration: CalibrationConfig = CalibrationConfig(),
    val escalation: EscalationConfig = EscalationConfig(),
    val recording: RecordingConfig = RecordingConfig(),
) {
    /**
     * Resident information from TOML.
     *
     * @property name Resident name
     * @property bed Bed identifier
     */
    public data class ResidentInfo(
        val name: String,
        val bed: String,
    )

    /**
     * Calibration configuration from TOML.
     *
     * @property heartbeatTimeout Heartbeat timeout
     * @property dwellThresholds Dwell thresholds by state kind
     * @property confidence Confidence configuration (reuses contracts type)
     */
    public data class CalibrationConfig(
        val heartbeatTimeout: Duration = Duration.ofSeconds(90),
        val dwellThresholds: Map<StateKind, DwellThreshold> = emptyMap(),
        val confidence: ConfidenceConfig = ConfidenceConfig(
            minConfidence = emptyMap(),
            heartbeatTimeout = Duration.ofSeconds(90),
        ),
    )

    /**
     * Escalation configuration from TOML.
     *
     * @property escalationDelay Escalation delay
     * @property staffAssist Staff assist mode
     * @property maxLevel Maximum escalation level
     */
    public data class EscalationConfig(
        val escalationDelay: Duration = Duration.ofMinutes(5),
        val staffAssist: StaffAssistMode = StaffAssistMode.NONE,
        val maxLevel: Int = 3,
    )

    /**
     * Recording configuration from TOML.
     *
     * @property enabled Enable recording
     * @property preEventWindow Pre-event window
     * @property postEventWindow Post-event window
     * @property quality Recording quality
     */
    public data class RecordingConfig(
        val enabled: Boolean = false,
        val preEventWindow: Duration = Duration.ofSeconds(30),
        val postEventWindow: Duration = Duration.ofMinutes(2),
        val quality: RecordingQuality = RecordingQuality.MEDIUM,
    )
}
