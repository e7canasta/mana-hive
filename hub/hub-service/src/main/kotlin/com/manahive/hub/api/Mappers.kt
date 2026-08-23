package com.manahive.hub.api

import com.manahive.contracts.policy.AlertRule
import com.manahive.kernel.ExplanationStep

/**
 * Extension functions for converting domain objects to DTOs.
 *
 * Fowler: "Extract Method" — mapping logic extracted to extension functions.
 * This keeps controllers thin and mapping logic reusable.
 */

public fun AlertRule.toResponse(): AlertRuleResponse {
    return AlertRuleResponse(
        id = id.value,
        trigger = trigger.name,
        severity = severity.name,
        closureCondition = closureCondition.name,
        reversible = reversible,
        requiresConfirmation = requiresConfirmation,
        requiresNvr = requiresNvr,
    )
}

public fun ExplanationStep.toResponse(): ExplanationStepResponse {
    return ExplanationStepResponse(
        rule = rule,
        observed = observed,
        conclusion = conclusion,
    )
}
