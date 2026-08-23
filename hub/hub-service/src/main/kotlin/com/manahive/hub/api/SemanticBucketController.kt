package com.manahive.hub.api

import com.manahive.contracts.policy.SemanticBucketStore
import com.manahive.kernel.ResidentId
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * REST controller for semantic bucket queries.
 *
 * Provides endpoints for engines to query semantic buckets.
 * This is the HTTP adapter for the SemanticBucketStore port.
 *
 * Fowler: "Adapter" — translates HTTP requests to port method calls.
 * Vernon: "Driving Adapter" — the primary adapter for the use case.
 */
@RestController
@RequestMapping("/api/semantic-buckets")
public class SemanticBucketController(
    private val semanticBucketStore: SemanticBucketStore,
) {

    /**
     * List all residents with semantic buckets.
     *
     * Must be defined before /{residentId} to avoid route collision.
     *
     * @return List of resident IDs
     */
    @GetMapping("/residents")
    public fun listResidents(): ResponseEntity<ResidentsListResponse> {
        val residents = semanticBucketStore.listAllResidents()

        return ResponseEntity.ok(
            ResidentsListResponse(
                residents = residents.map { it.value },
            )
        )
    }

    /**
     * Get all semantic buckets for a category across all residents.
     *
     * @param category The policy category
     * @return List of semantic buckets for this category
     */
    @GetMapping("/category/{category}")
    public fun getAllByCategory(
        @PathVariable category: String,
    ): ResponseEntity<List<SemanticBucketResponse>> {
        val parsedCategory = CategoryParser.parse(category)
            ?: return ResponseEntity.badRequest().build()

        val buckets = semanticBucketStore.getAllByCategory(parsedCategory)

        return ResponseEntity.ok(buckets.map { it.toResponse() })
    }

    /**
     * Get all semantic buckets for a resident.
     *
     * @param residentId The resident ID
     * @return All semantic buckets for this resident
     */
    @GetMapping("/{residentId}")
    public fun getAllBuckets(
        @PathVariable residentId: String,
    ): ResponseEntity<ResidentBucketsResponse> {
        val buckets = semanticBucketStore.getAllByResident(ResidentId(residentId))

        return ResponseEntity.ok(
            ResidentBucketsResponse(
                residentId = residentId,
                buckets = buckets.map { it.toResponse() },
            )
        )
    }

    /**
     * Get semantic bucket for a resident and category.
     *
     * @param residentId The resident ID
     * @param category The policy category (calibration, response, escalation, recording)
     * @return The semantic bucket, or 404 if not found
     */
    @GetMapping("/{residentId}/{category}")
    public fun getBucket(
        @PathVariable residentId: String,
        @PathVariable category: String,
    ): ResponseEntity<SemanticBucketResponse> {
        val parsedCategory = CategoryParser.parse(category)
            ?: return ResponseEntity.badRequest().build()

        val bucket = semanticBucketStore.get(ResidentId(residentId), parsedCategory)
            ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok(bucket.toResponse())
    }
}

/**
 * Extension function to convert StoredSemanticBucket to response DTO.
 */
private fun com.manahive.contracts.policy.StoredSemanticBucket.toResponse(): SemanticBucketResponse =
    SemanticBucketResponse(
        residentId = residentId.value,
        category = category.name.lowercase(),
        version = version.value,
        payload = payload,
        fingerprint = fingerprint.value,
        storedAt = storedAt,
    )
