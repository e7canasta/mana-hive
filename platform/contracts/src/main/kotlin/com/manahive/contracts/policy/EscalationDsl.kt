package com.manahive.contracts.policy

import com.manahive.contracts.common.Fingerprint
import com.manahive.contracts.common.buildFingerprint
import java.time.Duration

/**
 * Build an [EscalationPayload] with type-safe DSL.
 *
 * ```kotlin
 * val payload = buildEscalationPayload {
 *     escalationDelay(10.minutes)
 *     staffAssist(StaffAssistMode.OBLIGATORY)
 *     maxLevel(3)
 * }
 * ```
 */
public fun buildEscalationPayload(
    init: EscalationBuilder.() -> Unit,
): EscalationPayload {
    val builder = EscalationBuilder()
    builder.init()
    return builder.build()
}

@PolicyDsl
public class EscalationBuilder {
    private var escalationDelay: Duration = Duration.ofMinutes(5)
    private var staffAssist: StaffAssistMode = StaffAssistMode.NONE
    private var maxLevel: Int = 3

    public fun escalationDelay(delay: Duration) {
        require(delay >= Duration.ZERO) { "escalationDelay must not be negative" }
        escalationDelay = delay
    }

    public fun staffAssist(mode: StaffAssistMode) {
        staffAssist = mode
    }

    public fun maxLevel(level: Int) {
        require(level > 0) { "maxLevel must be positive" }
        maxLevel = level
    }

    public fun build(): EscalationPayload {
        val fingerprint: Fingerprint = buildFingerprint(
            "delay" to escalationDelay,
            "assist" to staffAssist,
            "level" to maxLevel,
        )
        return EscalationPayload(
            config = EscalationConfig(
                escalationDelay = escalationDelay,
                staffAssist = staffAssist,
                maxLevel = maxLevel,
            ),
            fingerprint = fingerprint,
        )
    }
}
