package com.manahive.harbor

import com.manahive.contracts.policy.Severity
import com.manahive.kernel.ResidentId
import java.time.Duration

/**
 * Type-safe DSL for building [HarborCalibration] instances.
 *
 * Example:
 * ```kotlin
 * val calibration = harborCalibration {
 *     resident("maria")
 *
 *     notice {
 *         channels = setOf(Channel.CONSOLE)
 *         escalationTimeout = 30.minutes
 *     }
 *     alert {
 *         channels = setOf(Channel.PUSH, Channel.TABLET)
 *         escalationTimeout = 5.minutes
 *     }
 *     incident {
 *         channels = setOf(Channel.PUSH, Channel.TABLET, Channel.WARD_BOARD, Channel.CONSOLE)
 *         escalationTimeout = 0.seconds
 *     }
 * }
 * ```
 *
 * Vernon: "Ubiquitous Language" — the DSL reads like business language.
 * Fowler: "Internal DSL" — type-safe, IDE completion, no parsing.
 */
public fun harborCalibration(init: HarborCalibrationBuilder.() -> Unit): HarborCalibration =
    HarborCalibrationBuilder().apply(init).build()

@HarborDsl
public class HarborCalibrationBuilder {
    private var residentId: ResidentId = ResidentId("default")
    private val channels = mutableMapOf<Severity, MutableSet<Channel>>()
    private val timeouts = mutableMapOf<Severity, Duration>()

    /** Set the resident ID for this calibration. */
    public fun resident(id: String) {
        residentId = ResidentId(id)
    }

    /** Set the resident ID for this calibration. */
    public fun resident(id: ResidentId) {
        residentId = id
    }

    /** Configure a specific severity level. */
    public fun severity(level: Severity, init: SeverityConfig.() -> Unit) {
        SeverityConfig(level).apply(init).also {
            require(it.channels.isNotEmpty()) { "channels must not be empty for $level" }
            channels[level] = it.channels.toMutableSet()
            timeouts[level] = it.escalationTimeout
        }
    }

    /** Configure informational notices (no action required). */
    public fun notice(init: SeverityConfig.() -> Unit): Unit = severity(Severity.INFO, init)

    /** Configure alerts (require confirmation). */
    public fun alert(init: SeverityConfig.() -> Unit): Unit = severity(Severity.WARNING, init)

    /** Configure incidents (require immediate action). */
    public fun incident(init: SeverityConfig.() -> Unit): Unit = severity(Severity.CRITICAL, init)

    internal fun build(): HarborCalibration {
        // Ensure all severity levels are configured
        for (level in Severity.entries) {
            require(channels.containsKey(level)) { "missing configuration for severity $level" }
        }
        val confirmChannels = channels[Severity.WARNING]?.toSet() ?: emptySet()
        // Fowler: "Intact Contract" — fingerprint hashes complete config, not just keys
        val fingerprint = buildString {
            append("resident=${residentId.value};")
            channels.entries.sortedBy { it.key.name }.forEach { (severity, chs) ->
                append("${severity.name}=${chs.sortedBy { it.name }.joinToString(",")};")
            }
            timeouts.entries.sortedBy { it.key.name }.forEach { (severity, timeout) ->
                append("timeout:${severity.name}=${timeout.toMillis()};")
            }
        }.hashCode().toString(16)
        return HarborCalibration(
            residentId = residentId,
            defaultChannels = channels.mapValues { it.value.toSet() },
            escalationTimeouts = timeouts,
            confirmationChannels = confirmChannels,
            fingerprint = fingerprint,
        )
    }
}

/**
 * Configuration for a single severity level.
 */
@HarborDsl
public class SeverityConfig(
    public val severity: Severity,
) {
    /** Channels to deliver this severity level. */
    public var channels: Set<Channel> = emptySet()

    /** Time before escalating if no acknowledgment. */
    public var escalationTimeout: Duration = Duration.ZERO
}

@DslMarker
public annotation class HarborDsl

// ── Duration extensions for DSL fluency ──

public val Int.minutes: Duration get() = Duration.ofMinutes(this.toLong())
public val Int.seconds: Duration get() = Duration.ofSeconds(this.toLong())
public val Int.hours: Duration get() = Duration.ofHours(this.toLong())
