package com.manahive.hub.api

import com.manahive.contracts.policy.WatchLevel
import com.manahive.contracts.scene.StateKind
import java.time.Duration

// ── Write request DTOs ─────────────────────────────────────────────────────

/**
 * Request body for PUT /residents/{id}/watch-level.
 *
 * `reason` is mandatory — a level change without a reason is a change
 * that nobody can explain six months later.
 */
public data class ChangeWatchLevelRequest(
    val level: WatchLevel,
    val reason: String,
)

/**
 * Request body for POST /residents/{id}/adjustments.
 *
 * `reason` is mandatory — same contract as level changes.
 */
public data class AddAdjustmentRequest(
    val state: StateKind,
    val warningMinutes: Long,
    val exceededMinutes: Long,
    val reason: String,
)

// ── History response DTOs ──────────────────────────────────────────────────

/**
 * A single event in the policy history of a resident.
 *
 * The `details` field varies by event type:
 * - `WatchLevelAssigned`: `{ "level": "FALL_RISK" }`
 * - `ManualAdjustmentAdded`: `{ "adjustmentId": "...", "state": "LYING", "exceededMinutes": 15 }`
 * - `ManualAdjustmentRevoked`: `{ "adjustmentId": "..." }`
 * - `TimeWindowDefined`: `{ "windowId": "...", "from": "22:00", "to": "07:00" }`
 */
public data class PolicyHistoryEvent(
    val type: String,
    val at: String,
    val actor: String,
    val details: Map<String, Any>,
)

/**
 * Response for GET /residents/{id}/policy/history.
 */
public data class PolicyHistoryResponse(
    val residentId: String,
    val events: List<PolicyHistoryEvent>,
)
