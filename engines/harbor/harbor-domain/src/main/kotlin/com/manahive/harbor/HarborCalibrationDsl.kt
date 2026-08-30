package com.manahive.harbor

import com.manahive.contracts.common.Channel
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
 *     call {
 *         channels = setOf(Channel.PUSH, Channel.TABLET)
 *         escalationTimeout = 2.minutes
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
    private val budgetBudgets = mutableMapOf<Severity, BudgetEntry>()

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

    /**
     * Configura el llamado: alguien tiene que ir a la habitacion.
     *
     * Es el escalon entre [alert] —que avisa sin esperar que nadie se mueva— y
     * [incident] —que es una emergencia. Ahi vive la mayor parte del trabajo
     * nocturno real: la baranda que quedo baja, el andador fuera de alcance, el
     * bano que se esta estirando.
     */
    public fun call(init: SeverityConfig.() -> Unit): Unit = severity(Severity.HIGH, init)

    /** Configure incidents (require immediate action). */
    public fun incident(init: SeverityConfig.() -> Unit): Unit = severity(Severity.CRITICAL, init)

    /** Configure delivery budget per severity. CRITICAL is never suppressed. */
    public fun budget(init: NotificationBudgetConfigBuilder.() -> Unit) {
        NotificationBudgetConfigBuilder().apply(init).also { budgetBudgets.putAll(it.build()) }
    }

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
            budgetBudgets.entries.sortedBy { it.key.name }.forEach { (severity, budget) ->
                append("budget:${severity.name}=${budget.maxPerShift};")
            }
        }.hashCode().toString(16)
        return HarborCalibration(
            residentId = residentId,
            defaultChannels = channels.mapValues { it.value.toSet() },
            escalationTimeouts = timeouts,
            confirmationChannels = confirmChannels,
            budget = NotificationBudget(budgetBudgets.toMap()),
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

/**
 * Builder for delivery budget budgets per severity.
 *
 * CRITICAL is never configurable — it's always delivered (life-safety).
 */
@HarborDsl
public class NotificationBudgetConfigBuilder {
    private val budgets = mutableMapOf<Severity, BudgetEntry>()

    /** Set max notifications per shift for WARNING severity. */
    public fun warning(max: Int) {
        budgets[Severity.WARNING] = BudgetEntry(maxPerShift = max)
    }

    /** Set max notifications per shift for INFO severity. */
    public fun info(max: Int) {
        budgets[Severity.INFO] = BudgetEntry(maxPerShift = max)
    }

    /**
     * Tope por turno para los llamados (HIGH).
     *
     * Sin entrada no hay tope, y eso es peor de lo que parece: HIGH es el nivel
     * que manda a alguien a caminar hasta la habitacion, asi que es el que mas
     * cuesta cuando se dispara de mas. Que sea configurable no lo hace opcional
     * — lo hace explicito. CRITICAL sigue sin tope, por diseño.
     */
    public fun call(max: Int) {
        budgets[Severity.HIGH] = BudgetEntry(maxPerShift = max)
    }

    internal fun build(): Map<Severity, BudgetEntry> = budgets.toMap()
}

@DslMarker
public annotation class HarborDsl

// ── Duration extensions for DSL fluency ──

public val Int.minutes: Duration get() = Duration.ofMinutes(this.toLong())
public val Int.seconds: Duration get() = Duration.ofSeconds(this.toLong())
public val Int.hours: Duration get() = Duration.ofHours(this.toLong())
