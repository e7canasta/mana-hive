package com.manahive.hub.api

import com.manahive.contracts.ledger.StoredEvent

public data class EventsResponse(
    public val events: List<StoredEvent>,
    public val total: Int,
    public val from: Long,
)

public data class StreamEventsResponse(
    public val stream: String,
    public val events: List<StoredEvent>,
    public val total: Int,
)

public data class WatermarkResponse(
    public val consumer: String,
    public val position: Long,
)

public data class WatermarksResponse(
    public val watermarks: Map<String, WatermarkResponse>,
    public val total: Int,
)

public data class StatusResponse(
    public val status: String,
    public val timestamp: String,
    public val totalEvents: Long,
    public val globalSequence: Long,
    public val streams: Map<String, Long>,
)

public data class PolicyEndpointsResponse(
    public val message: String,
    public val endpoints: Map<String, String>,
)

public data class AlertRuleResponse(
    public val id: String,
    public val trigger: String,
    public val severity: String,
    public val closureCondition: String,
    public val reversible: Boolean,
    public val requiresConfirmation: Boolean,
    public val requiresNvr: Boolean,
)

public data class ExplanationStepResponse(
    public val rule: String,
    public val observed: String,
    public val conclusion: String,
)

public data class EffectiveRulesResponse(
    public val residentId: String,
    public val at: String,
    public val rules: List<AlertRuleResponse>,
    public val fingerprint: String,
    public val explanation: List<ExplanationStepResponse>,
)

public data class HealthComponentResponse(
    public val status: String,
    public val type: String? = null,
)

public data class HealthResponse(
    public val status: String,
    public val timestamp: String,
    public val components: Map<String, HealthComponentResponse> = emptyMap(),
)
