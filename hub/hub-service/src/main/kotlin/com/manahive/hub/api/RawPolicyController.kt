package com.manahive.hub.api

import com.manahive.contracts.policy.RawPolicyStore
import com.manahive.kernel.ResidentId
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * REST controller for raw policy queries.
 *
 * Provides endpoints to query raw master policies.
 * This is the HTTP adapter for the RawPolicyStore port.
 *
 * Fowler: "Adapter" — translates HTTP requests to port method calls.
 * Vernon: "Driving Adapter" — the primary adapter for the use case.
 */
@Deprecated("Superseded by /api/policies/{id} — raw policy is a SPEC-02 artifact. Remove after consumers migrate.")
@RestController
@RequestMapping("/api/policies/raw")
public class RawPolicyController(
    private val rawPolicyStore: RawPolicyStore,
) {

    /**
     * Get raw policy for a resident.
     *
     * @param residentId The resident ID
     * @return The raw policy, or 404 if not found
     */
    @GetMapping("/{residentId}")
    public fun getRawPolicy(
        @PathVariable residentId: String,
    ): ResponseEntity<RawPolicyResponse> {
        val raw = rawPolicyStore.get(ResidentId(residentId))
            ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok(raw.toResponse())
    }

    /**
     * List all residents with raw policies.
     *
     * @return List of resident IDs
     */
    @GetMapping
    public fun listResidents(): ResponseEntity<ResidentsListResponse> {
        val residents = rawPolicyStore.listAll()

        return ResponseEntity.ok(
            ResidentsListResponse(
                residents = residents.map { it.value },
            )
        )
    }
}

/**
 * Extension function to convert RawPolicy to response DTO.
 */
private fun com.manahive.contracts.policy.RawPolicy.toResponse(): RawPolicyResponse =
    RawPolicyResponse(
        residentId = residentId.value,
        version = version.value,
        payload = payload,
        receivedAt = receivedAt,
    )
