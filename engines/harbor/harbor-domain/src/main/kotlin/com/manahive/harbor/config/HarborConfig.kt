package com.manahive.harbor.config

import com.manahive.contracts.policy.Severity
import com.manahive.harbor.Channel
import java.time.Duration

/**
 * Harbor Engine configuration for a resident.
 *
 * This is the domain-specific configuration that Harbor Engine uses.
 * It's derived from LocalConfig (TOML) or StoredSemanticBucket (Hub).
 *
 * Fowler: "Domain Model" — captures business rules for notification routing.
 *
 * @property residentId Resident identifier
 * @property channels Notification channels by severity level
 * @property escalationTimeouts Escalation timeouts by severity level
 * @property fingerprint Configuration fingerprint for change detection
 */
public data class HarborConfig(
    val residentId: String,
    val channels: Map<Severity, Set<Channel>> = emptyMap(),
    val escalationTimeouts: Map<Severity, Duration> = emptyMap(),
    val fingerprint: String = "",
) {
    init {
        require(residentId.isNotBlank()) { "Resident ID must not be blank" }
        channels.values.forEach { channelSet ->
            require(channelSet.isNotEmpty()) { "Channels must not be empty" }
        }
        escalationTimeouts.values.forEach { timeout ->
            require(timeout >= Duration.ZERO) { "Escalation timeout must not be negative" }
        }
    }

    /**
     * Get channels for a severity level.
     *
     * @param severity The severity level
     * @return Set of channels, or empty set if not configured
     */
    public fun channelsFor(severity: Severity): Set<Channel> =
        channels[severity] ?: emptySet()

    /**
     * Get escalation timeout for a severity level.
     *
     * @param severity The severity level
     * @return Escalation timeout, or Duration.ZERO if not configured
     */
    public fun escalationTimeoutFor(severity: Severity): Duration =
        escalationTimeouts[severity] ?: Duration.ZERO

    /**
     * Get confirmation channels (for WARNING severity).
     *
     * @return Set of confirmation channels
     */
    public val confirmationChannels: Set<Channel>
        get() = channelsFor(Severity.WARNING)
}
