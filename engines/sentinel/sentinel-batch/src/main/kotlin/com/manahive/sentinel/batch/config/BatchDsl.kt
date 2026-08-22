package com.manahive.sentinel.batch.config

import com.manahive.contracts.policy.ClosureCondition
import com.manahive.contracts.policy.Severity
import com.manahive.contracts.scene.StateKind
import java.time.Duration

// ── DSL for programmatic config ─────────────────────────────────────────────

/**
 * Type-safe DSL for building [BatchConfig] instances.
 *
 * Example:
 * ```kotlin
 * val config = sentinelBatchConfig {
 *     resident {
 *         id = "maria"
 *         bed = "301"
 *         night = "night-1"
 *     }
 *     rule("r-fall") {
 *         trigger = StateKind.BED_EDGE
 *         severity = Severity.CRITICAL
 *         closure = ClosureCondition.STAFF_AND_SAFE
 *         reversible = false
 *         nvr = true
 *         umbrella(StateKind.STANDING, StateKind.ATTEMPTING_EXIT)
 *     }
 *     fatigue { maxPerShift = 5 }
 *     events { source = "events.dat" }
 * }
 * ```
 */
fun sentinelBatchConfig(init: BatchConfigBuilder.() -> Unit): BatchConfig =
    BatchConfigBuilder().apply(init).build()

@DslMarker
annotation class SentinelBatchDsl

@SentinelBatchDsl
class BatchConfigBuilder {
    private var resident: ResidentConfig? = null
    private val rules = mutableListOf<RuleConfig>()
    private var fatigue = FatigueConfig()
    private var events = EventsConfig(source = "events.dat")

    fun resident(init: ResidentConfigBuilder.() -> Unit) {
        resident = ResidentConfigBuilder().apply(init).build()
    }

    fun rule(id: String, init: RuleConfigBuilder.() -> Unit) {
        rules.add(RuleConfigBuilder(id).apply(init).build())
    }

    fun fatigue(init: FatigueConfigBuilder.() -> Unit) {
        fatigue = FatigueConfigBuilder().apply(init).build()
    }

    fun events(init: EventsConfigBuilder.() -> Unit) {
        events = EventsConfigBuilder().apply(init).build()
    }

    internal fun build(): BatchConfig = BatchConfig(
        resident = requireNotNull(resident) { "resident{} must be called" },
        rules = rules.toList(),
        fatigue = fatigue,
        events = events,
    )
}

@SentinelBatchDsl
class ResidentConfigBuilder {
    var id: String = ""
    var bed: String = ""
    var night: String = ""

    internal fun build(): ResidentConfig = ResidentConfig(id, bed, night)
}

@SentinelBatchDsl
class RuleConfigBuilder(private val ruleId: String) {
    var trigger: StateKind = StateKind.LYING
    var severity: Severity = Severity.WARNING
    var closure: ClosureCondition = ClosureCondition.SAFE_ONLY
    var reversible: Boolean = true
    var nvr: Boolean = false
    var confirmation: Boolean = false
    var confirmationWindow: Duration? = null
    private val umbrella = mutableSetOf<StateKind>()

    fun umbrella(vararg states: StateKind) {
        umbrella.addAll(states)
    }

    internal fun build(): RuleConfig = RuleConfig(
        id = ruleId,
        trigger = trigger,
        severity = severity,
        closure = closure,
        reversible = reversible,
        nvr = nvr,
        confirmation = confirmation,
        confirmationWindow = confirmationWindow,
        umbrella = umbrella.toSet(),
    )
}

@SentinelBatchDsl
class FatigueConfigBuilder {
    var maxPerShift: Int = 5

    internal fun build(): FatigueConfig = FatigueConfig(maxPerShift)
}

@SentinelBatchDsl
class EventsConfigBuilder {
    var source: String = "events.dat"
    var output: String = "output"
    var start: java.time.Instant? = null

    internal fun build(): EventsConfig = EventsConfig(source, output, start)
}
