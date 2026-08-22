package com.manahive.contracts.sentinel

import com.manahive.contracts.policy.Severity
import com.manahive.contracts.scene.StateKind
import com.manahive.kernel.BedId
import com.manahive.kernel.EpisodeId
import com.manahive.kernel.EventRef
import com.manahive.kernel.ResidentId
import com.manahive.kernel.RuleId
import java.time.Duration
import java.time.Instant

/**
 * What the sentinel distilled from a scene fact under the effective rules.
 * Published on `sentinel.signal.v1.<bed>`. Every signal cites the rules
 * fingerprint that governed it: decisions are reproducible.
 *
 * The signal types map to the episode lifecycle:
 * - EpisodeOpened: a new episode opens (trigger + rule → severity)
 * - UmbrellaEvent: event under an open episode's umbrella
 * - AutoRecovery: resident returned to safe state (with or without confirmation)
 * - EpisodeClosed: episode closes (staff+safe, or auto-recovery)
 * - SuppressedWithRecord: suppressed (staff present, already alerted, fatigue)
 */
public sealed interface SentinelSignal {
    public val bed: BedId
    public val resident: ResidentId?
    public val at: Instant
    public val rulesFingerprint: String

    /**
     * A new episode opens. The vigia/harbor listens for this to start
     * NVR recording, dispatch staff, or send notifications.
     */
    public data class EpisodeOpened(
        override val bed: BedId,
        override val resident: ResidentId?,
        override val at: Instant,
        override val rulesFingerprint: String,
        public val episode: EpisodeId,
        public val rule: RuleId,
        public val trigger: StateKind,
        public val severity: Severity,
        public val reversible: Boolean,
        public val requiresNvr: Boolean,
        public val confirmationWindow: Duration?,
    ) : SentinelSignal

    /**
     * An event under an open episode's umbrella. The event is reported
     * with its original criticality, not as a new episode.
     */
    public data class UmbrellaEvent(
        override val bed: BedId,
        override val resident: ResidentId?,
        override val at: Instant,
        override val rulesFingerprint: String,
        public val episode: EpisodeId,
        public val state: StateKind,
        public val originalSeverity: Severity,
    ) : SentinelSignal

    /**
     * Resident returned to safe state without staff assistance.
     * If reversible: episode closes automatically.
     * If non-reversible: staff must go verify (confirmation alert).
     */
    public data class AutoRecovery(
        override val bed: BedId,
        override val resident: ResidentId?,
        override val at: Instant,
        override val rulesFingerprint: String,
        public val episode: EpisodeId,
        public val reversible: Boolean,
        /** If false, staff must still go verify even though resident is safe. */
        public val requiresConfirmation: Boolean,
    ) : SentinelSignal

    /**
     * Episode closes. Staff assisted and resident is safe, or auto-recovery
     * completed. The gap duration tracks how long without staff presence.
     */
    public data class EpisodeClosed(
        override val bed: BedId,
        override val resident: ResidentId?,
        override val at: Instant,
        override val rulesFingerprint: String,
        public val episode: EpisodeId,
        public val cause: ClosureCause,
        /** Time without staff presence (null if staff was present from the start). */
        public val gapDuration: Duration?,
    ) : SentinelSignal

    /**
     * The fact happened; the alarm did not — and we can prove why.
     * Every suppression has a record for audit and debugging.
     */
    public data class SuppressedWithRecord(
        override val bed: BedId,
        override val resident: ResidentId?,
        override val at: Instant,
        override val rulesFingerprint: String,
        public val rule: RuleId,
        public val cause: SuppressionCause,
        public val evidence: EventRef,
    ) : SentinelSignal
}

/** Why an episode closed. */
public enum class ClosureCause {
    /** Staff assisted AND resident safe. */
    STAFF_AND_SAFE,
    /** Resident returned to safe state (only for SOLO_SEGURO closure). */
    AUTO_RECOVERY,
}

/** Why a signal was suppressed. */
public enum class SuppressionCause {
    /** Staff is present — no alarm needed. */
    STAFF_PRESENT,
    /** This rule already fired for the current open episode. */
    EPISODE_ALREADY_ALERTED,
    /** Fatigue budget exceeded for this shift. */
    FATIGUE_BUDGET,
    /** Scene fact is not a trigger for any rule. */
    NO_MATCHING_RULE,
}
