package com.manahive.serialization

/**
 * TOML-serializable resident policy configuration.
 *
 * Example:
 * ```toml
 * name = "jose-301"
 * resident = "jose"
 * risk = "HIGH"
 * mobility = "NONE"
 * template = "standard"
 *
 * [rules.sitting]
 * alertAfter = "15m"
 *
 * [rules.bathroom]
 * alertAfter = "10m"
 *
 * [transitions]
 * lyingToStanding = { hysteresis = "1000ms" }
 * ```
 */
data class PolicyToml(
    val name: String,
    val resident: String,
    val risk: String = "MEDIUM",
    val mobility: String = "NONE",
    val template: String = "standard",
    val rules: Map<String, StateRuleToml> = emptyMap(),
    val transitions: Map<String, TransitionRuleToml> = emptyMap(),
)

/**
 * TOML-serializable catalog with multiple residents.
 *
 * Example:
 * ```toml
 * version = "2.1.0"
 *
 * [residents.jose]
 * risk = "HIGH"
 * template = "standard"
 *
 * [residents.jose.rules.sitting]
 * alertAfter = "15m"
 *
 * [residents.maria]
 * risk = "LOW"
 * template = "night-wandering"
 *
 * [residents.maria.rules.sitting]
 * alertAfter = "20m"
 * ```
 */
data class MultiResidentCatalogToml(
    val version: String = "1.0.0",
    val residents: Map<String, ResidentConfigToml> = emptyMap(),
)

data class ResidentConfigToml(
    val risk: String = "MEDIUM",
    val mobility: String = "NONE",
    val template: String = "standard",
    val rules: Map<String, StateRuleToml> = emptyMap(),
    val transitions: Map<String, TransitionRuleToml> = emptyMap(),
)
