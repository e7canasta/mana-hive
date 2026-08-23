package com.manahive.contracts.policy

import com.manahive.contracts.common.Fingerprint
import com.manahive.contracts.common.buildFingerprint
import com.manahive.contracts.scene.StateKind
import com.manahive.kernel.RuleId
import java.time.Duration

/**
 * Build a [ResponsePayload] with type-safe DSL.
 *
 * ```kotlin
 * val payload = buildResponsePayload {
 *     rule(RuleId("fall"), StateKind.STANDING, Severity.CRITICAL, ClosureCondition.STAFF_AND_SAFE) {
 *         requiresNvr = true
 *         requiresConfirmation = true
 *         confirmationWindow = 5.minutes
 *     }
 * }
 * ```
 */
public fun buildResponsePayload(
    init: ResponseBuilder.() -> Unit,
): ResponsePayload {
    val builder = ResponseBuilder()
    builder.init()
    return builder.build()
}

@PolicyDsl
public class ResponseBuilder {
    private val rules = mutableListOf<AlertRule>()

    /** Add an alert rule with optional configuration. */
    public fun rule(
        id: RuleId,
        trigger: StateKind,
        severity: Severity,
        closureCondition: ClosureCondition,
        init: AlertRuleBuilder.() -> Unit = {},
    ) {
        val builder = AlertRuleBuilder(id, trigger, severity, closureCondition)
        builder.init()
        rules.add(builder.build())
    }

    /** Build the payload with fingerprint. */
    public fun build(): ResponsePayload {
        val fingerprint: Fingerprint = buildFingerprint(
            *rules.map { rule ->
                rule.id.value to buildFingerprint(
                    "trigger" to rule.trigger.name,
                    "severity" to rule.severity.name,
                    "closureCondition" to rule.closureCondition.name,
                    "reversible" to rule.reversible,
                    "requiresConfirmation" to rule.requiresConfirmation,
                    "requiresNvr" to rule.requiresNvr,
                    "confirmationWindow" to (rule.confirmationWindow?.toString() ?: ""),
                    "umbrellaEvents" to rule.umbrellaEvents.map { it.name }.toSet(),
                )
            }.toTypedArray()
        )
        return ResponsePayload(
            rules = rules.toList(),
            fingerprint = fingerprint,
        )
    }
}

@PolicyDsl
public class AlertRuleBuilder(
    private val id: RuleId,
    private val trigger: StateKind,
    private val severity: Severity,
    private val closureCondition: ClosureCondition,
) {
    public var reversible: Boolean = false
    public var requiresConfirmation: Boolean = false
    public var requiresNvr: Boolean = false
    public var confirmationWindow: Duration? = null
    public var umbrellaEvents: Set<StateKind> = emptySet()

    public fun build(): AlertRule = AlertRule(
        id = id,
        trigger = trigger,
        severity = severity,
        closureCondition = closureCondition,
        reversible = reversible,
        requiresConfirmation = requiresConfirmation,
        requiresNvr = requiresNvr,
        confirmationWindow = confirmationWindow,
        umbrellaEvents = umbrellaEvents,
    )
}
