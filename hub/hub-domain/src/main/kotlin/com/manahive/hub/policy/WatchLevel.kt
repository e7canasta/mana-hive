package com.manahive.hub.policy

/**
 * Watch level for resident monitoring.
 *
 * Vernon: "Ubiquitous Language" — these are watch levels in the domain.
 * The Hub uses these to determine which rules apply to a resident.
 */
public enum class WatchLevel {
    /** Standard monitoring — normal rules apply */
    STANDARD,
    /** Enhanced monitoring — tighter rules, more frequent checks */
    ENHANCED,
    /** Critical monitoring — strictest rules, immediate response */
    CRITICAL,
}
