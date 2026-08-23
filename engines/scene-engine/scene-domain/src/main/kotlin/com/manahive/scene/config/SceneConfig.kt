package com.manahive.scene.config

import com.manahive.contracts.policy.DwellThreshold
import com.manahive.contracts.scene.StateKind
import java.time.Duration

/**
 * Scene Engine configuration for a resident.
 *
 * This is the domain-specific configuration that Scene Engine uses.
 * It's derived from LocalConfig (TOML) or StoredSemanticBucket (Hub).
 *
 * Fowler: "Domain Model" — captures business rules for scene interpretation.
 *
 * @property residentId Resident identifier
 * @property name Resident name
 * @property bed Bed identifier
 * @property heartbeatTimeout Heartbeat timeout
 * @property dwellThresholds Dwell thresholds by state kind
 * @property confidence Confidence thresholds by state kind
 */
public data class SceneConfig(
    val residentId: String,
    val name: String,
    val bed: String,
    val heartbeatTimeout: Duration = Duration.ofSeconds(90),
    val dwellThresholds: Map<StateKind, DwellThreshold> = emptyMap(),
    val confidence: Map<StateKind, Double> = emptyMap(),
) {
    init {
        require(residentId.isNotBlank()) { "Resident ID must not be blank" }
        require(name.isNotBlank()) { "Resident name must not be blank" }
        require(bed.isNotBlank()) { "Bed must not be blank" }
        require(heartbeatTimeout >= Duration.ZERO) { "Heartbeat timeout must not be negative" }
        confidence.values.forEach { value ->
            require(value in 0.0..1.0) { "Confidence must be in 0.0..1.0, got $value" }
        }
    }
}
