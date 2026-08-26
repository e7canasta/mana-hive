package com.manahive.contracts.common

/**
 * Channels for delivering notices to staff.
 *
 * Shared vocabulary between Politica Engine (policy definition)
 * and Harbor Engine (notification delivery).
 *
 * This is a TYPE DEFINITION (like a C header), not implementation.
 * Both contracts and harbor depend on this enum — no circular dependency.
 *
 * Vernon: Value Object — no identity, compared by value.
 */
public enum class Channel {
    PUSH,       // mobile push notification
    TABLET,     // tablet app
    WARD_BOARD, // ward display board
    CONSOLE,    // monitoring console
}
