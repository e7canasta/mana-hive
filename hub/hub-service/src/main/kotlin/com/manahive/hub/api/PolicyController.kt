package com.manahive.hub.api

import com.manahive.hub.policy.PolicyService
import com.manahive.kernel.ResidentId
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
public class PolicyController(
    private val policyService: PolicyService,
) {

    @GetMapping("/api/policies/{residentId}")
    public fun getEffectiveRules(
        @PathVariable residentId: String,
        @RequestParam(name = "at", required = false) at: Instant?,
    ): EffectiveRulesResponse {
        val instant = at ?: Instant.now()
        val result = policyService.resolveEffectiveRules(ResidentId(residentId), instant)
        val rules = result.value

        return EffectiveRulesResponse(
            residentId = residentId,
            at = instant.toString(),
            rules = rules.rules.map { rule -> rule.toResponse() },
            fingerprint = rules.fingerprint,
            explanation = result.explanation.map { step -> step.toResponse() },
        )
    }

    @GetMapping("/api/policies")
    public fun listPolicies(): PolicyEndpointsResponse {
        return PolicyEndpointsResponse(
            message = "Use GET /api/policies/{residentId} to get rules for a specific resident",
            endpoints = mapOf(
                "getRules" to "/api/policies/{residentId}",
                "getRulesAtTime" to "/api/policies/{residentId}?at=2026-01-01T00:00:00Z",
            ),
        )
    }
}
