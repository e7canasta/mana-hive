package com.manahive.hub.api

import com.manahive.contracts.policy.PolicyCategory

/**
 * Shared utility for parsing policy categories from strings.
 *
 * Matches against both enum name (CALIBRATION) and subject (calibration).
 * Case-insensitive matching for flexibility.
 *
 * Fowler: "Extract Method" — shared parsing logic.
 * DRY: single source of truth for category parsing.
 */
internal object CategoryParser {
    /**
     * Parse a category string to PolicyCategory.
     * Matches against name (e.g., "CALIBRATION") or subject (e.g., "calibration").
     *
     * @param category The category string
     * @return The PolicyCategory, or null if not found
     */
    fun parse(category: String): PolicyCategory? =
        PolicyCategory.entries.find {
            it.name.equals(category, ignoreCase = true) ||
                it.subject.equals(category, ignoreCase = true)
        }
}
