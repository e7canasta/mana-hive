package com.manahive.contracts.engine

/**
 * Engine mode for configuration loading.
 *
 * Each engine can operate in two modes:
 * - LOCAL: Load configuration from TOML files on disk
 * - HUB: Load configuration from Hub API + subscribe to changes
 *
 * Fowler: "Replace Type Code with Subclasses" — enum enables
 * exhaustive when() expressions.
 *
 * Vernon: Value Object — no identity, compared by value.
 */
public enum class EngineMode {
    /** Load configuration from TOML files on disk. */
    LOCAL,
    /** Load configuration from Hub API + subscribe to changes. */
    HUB,
}
