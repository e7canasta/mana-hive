package com.manahive.contracts.shared

import java.time.Duration

/**
 * Duration extensions shared across all bounded contexts.
 * Fowler: "Duplicated Code" → Extract into shared module.
 *
 * Public — both politica-domain and scene-domain depend on contracts.
 * Each domain module can re-export with its own DSL marker if needed.
 */
public val Int.ms: Duration get() = Duration.ofMillis(toLong())
public val Int.seconds: Duration get() = Duration.ofSeconds(toLong())
public val Int.minutes: Duration get() = Duration.ofMinutes(toLong())
public val Int.hours: Duration get() = Duration.ofHours(toLong())
