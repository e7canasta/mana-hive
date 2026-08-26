package com.manahive.serialization

/**
 * TOML-serializable catalog configuration.
 *
 * Example:
 * ```toml
 * version = "2.1.0"
 *
 * [resident.sitting]
 * warningAfter = "30m"
 * alertAfter = "45m"
 * severity = "WARNING"
 * closure = "SAFE_ONLY"
 *
 * [resident.bathroom]
 * warningAfter = "20m"
 * alertAfter = "30m"
 * severity = "WARNING"
 * closure = "SAFE_ONLY"
 *
 * [transitions]
 * lyingToSitting = { hysteresis = "1500ms" }
 * lyingToStanding = { hysteresis = "2000ms", recordBefore = "2m", recordAfter = "5m" }
 * ```
 */
data class CatalogToml(
    val version: String = "1.0.0",
    val resident: Map<String, StateRuleToml> = emptyMap(),
    val transitions: Map<String, TransitionRuleToml> = emptyMap(),
    val room: RoomRulesToml = RoomRulesToml(),
)

data class StateRuleToml(
    val warningAfter: String? = null,
    val alertAfter: String? = null,
    /**
     * "ENTRY" | "DWELL". Absent means DWELL — the safe reading, since a rule that
     * does not say it fires on entry must not fire on entry. Never infer it from
     * the presence of alertAfter: that inference is what SPEC-01 removed.
     */
    val triggerOn: String? = null,
    val severity: String? = null,
    val closure: String? = null,
)

data class TransitionRuleToml(
    val hysteresis: String = "1500ms",
    val recordBefore: String? = null,
    val recordAfter: String? = null,
)

data class RoomRulesToml(
    val staffEnters: StaffEntersRuleToml? = null,
)

data class StaffEntersRuleToml(
    val closeEpisode: Boolean = false,
)
