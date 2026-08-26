package com.manahive.hub.api

import com.manahive.contracts.policy.WatchLevel
import com.manahive.hub.policy.MissingMotivo
import com.manahive.hub.policy.NoPolicyForResident
import com.manahive.hub.policy.PolicyEvent
import com.manahive.hub.policy.PolicyService
import com.manahive.kernel.ResidentId
import com.manahive.kernel.StaffId
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

@RestController
public class PolicyController(
    private val policyService: PolicyService,
) {

    // ── Read ──────────────────────────────────────────────────────────────

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
                "history" to "/api/policies/{residentId}/history",
                "changeLevel" to "PUT /api/policies/{residentId}/watch-level",
                "addAdjustment" to "POST /api/policies/{residentId}/adjustments",
                "revokeAdjustment" to "DELETE /api/policies/{residentId}/adjustments/{adjId}",
            ),
        )
    }

    @GetMapping("/api/policies/{residentId}/history")
    public fun getHistory(
        @PathVariable residentId: String,
    ): PolicyHistoryResponse {
        val events = policyService.historyFor(ResidentId(residentId))
        return PolicyHistoryResponse(
            residentId = residentId,
            events = events.map { it.toHistoryResponse() },
        )
    }

    // ── Write ─────────────────────────────────────────────────────────────

    @PutMapping("/api/policies/{residentId}/watch-level")
    public fun changeWatchLevel(
        @PathVariable residentId: String,
        @RequestBody body: ChangeWatchLevelRequest,
    ): ResponseEntity<EffectiveRulesResponse> {
        val rid = ResidentId(residentId)
        val at = Instant.now()
        policyService.changeWatchLevel(
            residentId = rid,
            level = body.level,
            actor = StaffId("api"),
            at = at,
            reason = body.reason,
        )
        val result = policyService.resolveEffectiveRules(rid, at)
        return ResponseEntity.ok(
            EffectiveRulesResponse(
                residentId = residentId,
                at = at.toString(),
                rules = result.value.rules.map { it.toResponse() },
                fingerprint = result.value.fingerprint,
                explanation = result.explanation.map { it.toResponse() },
            ),
        )
    }

    @PostMapping("/api/policies/{residentId}/adjustments")
    public fun addAdjustment(
        @PathVariable residentId: String,
        @RequestBody body: AddAdjustmentRequest,
    ): ResponseEntity<PolicyLayersResponse> {
        val rid = ResidentId(residentId)
        val at = Instant.now()
        val layers = policyService.addAdjustment(
            residentId = rid,
            adjustmentId = UUID.randomUUID().toString(),
            state = body.state,
            threshold = com.manahive.contracts.policy.DwellThreshold(
                warning = java.time.Duration.ofMinutes(body.warningMinutes),
                exceeded = java.time.Duration.ofMinutes(body.exceededMinutes),
            ),
            actor = StaffId("api"),
            at = at,
            reason = body.reason,
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(
            PolicyLayersResponse(
                residentId = residentId,
                level = layers.level.name,
                templateId = layers.template.id,
                adjustmentsCount = layers.adjustments.size,
                windowsCount = layers.windows.size,
            ),
        )
    }

    @DeleteMapping("/api/policies/{residentId}/adjustments/{adjId}")
    public fun revokeAdjustment(
        @PathVariable residentId: String,
        @PathVariable adjId: String,
    ): ResponseEntity<PolicyLayersResponse> {
        val rid = ResidentId(residentId)
        val at = Instant.now()
        val layers = policyService.revokeAdjustment(
            residentId = rid,
            adjustmentId = adjId,
            actor = StaffId("api"),
            at = at,
        )
        return ResponseEntity.ok(
            PolicyLayersResponse(
                residentId = residentId,
                level = layers.level.name,
                templateId = layers.template.id,
                adjustmentsCount = layers.adjustments.size,
                windowsCount = layers.windows.size,
            ),
        )
    }

    // ── Error handling ────────────────────────────────────────────────────

    @ExceptionHandler(NoPolicyForResident::class)
    fun handleNoPolicy(ex: NoPolicyForResident): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            mapOf("error" to ex.message!!),
        )

    @ExceptionHandler(MissingMotivo::class)
    fun handleMissingMotivo(ex: MissingMotivo): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            mapOf("error" to ex.message!!),
        )
}

// ── Mappers ────────────────────────────────────────────────────────────────

public data class PolicyLayersResponse(
    val residentId: String,
    val level: String,
    val templateId: String,
    val adjustmentsCount: Int,
    val windowsCount: Int,
)

private fun PolicyEvent.toHistoryResponse(): PolicyHistoryEvent = when (this) {
    is com.manahive.hub.policy.WatchLevelAssigned -> PolicyHistoryEvent(
        type = "WatchLevelAssigned",
        at = at.toString(),
        actor = actor.value,
        details = mapOf("level" to level.name, "reason" to reason),
    )
    is com.manahive.hub.policy.ManualAdjustmentAdded -> PolicyHistoryEvent(
        type = "ManualAdjustmentAdded",
        at = at.toString(),
        actor = actor.value,
        details = mapOf(
            "adjustmentId" to adjustmentId,
            "state" to state.name,
            "exceededMinutes" to threshold.exceeded.toMinutes(),
            "reason" to reason,
        ),
    )
    is com.manahive.hub.policy.ManualAdjustmentRevoked -> PolicyHistoryEvent(
        type = "ManualAdjustmentRevoked",
        at = at.toString(),
        actor = actor.value,
        details = mapOf("adjustmentId" to adjustmentId),
    )
    is com.manahive.hub.policy.TimeWindowDefined -> PolicyHistoryEvent(
        type = "TimeWindowDefined",
        at = at.toString(),
        actor = actor.value,
        details = mapOf(
            "windowId" to windowId,
            "from" to from.toString(),
            "to" to to.toString(),
        ),
    )
}
