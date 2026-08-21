package com.manahive.kernel

/**
 * An engine is a pure domain service: same input -> same output, today and in
 * the replay of 2030. No Spring, no IO, no Instant.now() — verified by the
 * purity guard and Konsist, not promised.
 */
public interface Engine {
    public val version: EngineVersion
}

/** What code decided: semver plus the build fingerprint that seals it. */
public data class EngineVersion(
    public val name: String,
    public val semver: String,
    public val buildFingerprint: String,
)

/**
 * Every engine output travels with its explanation. A decision without its
 * "why" does not exist — including what was DISCARDED and why.
 */
public data class Explained<out T>(
    public val value: T,
    public val explanation: List<ExplanationStep>,
    public val discards: List<Discard> = emptyList(),
)

public data class ExplanationStep(
    public val rule: String,
    public val observed: String,
    public val conclusion: String,
)

public data class Discard(public val subject: String, public val cause: DiscardCause)

public enum class DiscardCause {
    ILLEGAL_TRANSITION,
    CONFIDENCE_TOO_LOW,
    HYSTERESIS_NOT_MET,
    DUPLICATE,
    NO_OCCUPANT,
    STAFF_PRESENT,
    EPISODE_ALREADY_ALERTED,
    FATIGUE_BUDGET_EXCEEDED,
}
