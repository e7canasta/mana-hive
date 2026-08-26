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
 * Stable discriminator for [SentinelSignal] subtypes.
 *
 * The enum name IS the serialized form — backward compatible with
 * the previous `class.simpleName` approach, but compile-time safe:
 * adding a subtype without updating a `when` over [SignalType]
 * is a compile error, not a silent runtime mismatch.
 */
public enum class SignalType {
    EPISODE_OPENED,
    UMBRELLA_EVENT,
    AUTO_RECOVERY,
    EPISODE_CLOSED,
    SUPPRESSED_WITH_RECORD,
    DWELL_PRE_WARNING,
}

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
    public val type: SignalType
    public val bed: BedId
    public val resident: ResidentId?
    public val at: Instant
    public val rulesFingerprint: String

    /**
     * A new episode opens. The vigia/harbor listens for this to start
     * NVR recording, dispatch staff, or send notifications.
     */
    public data class EpisodeOpened(
        override val type: SignalType = SignalType.EPISODE_OPENED,
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
        override val type: SignalType = SignalType.UMBRELLA_EVENT,
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
        override val type: SignalType = SignalType.AUTO_RECOVERY,
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
        override val type: SignalType = SignalType.EPISODE_CLOSED,
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
        override val type: SignalType = SignalType.SUPPRESSED_WITH_RECORD,
        override val bed: BedId,
        override val resident: ResidentId?,
        override val at: Instant,
        override val rulesFingerprint: String,
        public val rule: RuleId,
        public val cause: SuppressionCause,
        public val evidence: EventRef,
    ) : SentinelSignal

    /**
     * Dwell warning: resident has been in a state for a prolonged period,
     * but hasn't exceeded the threshold yet. This is an informational
     * notification, not an episode opener.
     */
    public data class DwellPreWarning(
        override val type: SignalType = SignalType.DWELL_PRE_WARNING,
        override val bed: BedId,
        override val resident: ResidentId?,
        override val at: Instant,
        override val rulesFingerprint: String,
        public val state: StateKind,
        public val elapsed: Duration,
        public val threshold: Duration,
    ) : SentinelSignal
}

/** Why an episode closed. */
public enum class ClosureCause {
    /** Staff assisted AND resident safe. */
    STAFF_AND_SAFE,
    /** Staff present alone (STAFF_OR_SAFE closure). */
    STAFF_PRESENT,
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
    NOTIFICATION_BUDGET,
    /** Scene fact is not a trigger for any rule. */
    NO_MATCHING_RULE,
}

/**
 * Canonical map representation of a [SentinelSignal].
 *
 * The single source of truth for field extraction — every serializer,
 * writer, and formatter should consume this instead of duplicating
 * the `when` over subtypes.
 *
 * The `type` field uses [SignalType.name], not `class.simpleName`.
 */
public fun SentinelSignal.toMap(): Map<String, Any?> = linkedMapOf<String, Any?>(
    "type" to type.name,
    "at" to at.toString(),
    "bed" to bed.value,
    "rulesFingerprint" to rulesFingerprint,
    "resident" to resident?.value,
).apply {
    when (this@toMap) {
        is SentinelSignal.EpisodeOpened -> {
            put("episode", episode.value)
            put("rule", rule.value)
            put("trigger", trigger.name)
            put("severity", severity.name)
            put("reversible", reversible)
            put("requiresNvr", requiresNvr)
            confirmationWindow?.let { put("confirmationWindow", it.toString()) }
        }
        is SentinelSignal.EpisodeClosed -> {
            put("episode", episode.value)
            put("cause", cause.name)
            gapDuration?.let { put("gapDuration", it.toString()) }
        }
        is SentinelSignal.AutoRecovery -> {
            put("episode", episode.value)
            put("reversible", reversible)
            put("requiresConfirmation", requiresConfirmation)
        }
        is SentinelSignal.UmbrellaEvent -> {
            put("episode", episode.value)
            put("state", state.name)
            put("originalSeverity", originalSeverity.name)
        }
        is SentinelSignal.SuppressedWithRecord -> {
            put("rule", rule.value)
            put("cause", cause.name)
            put("evidenceStream", evidence.stream)
            put("evidenceSeq", evidence.seq)
        }
        is SentinelSignal.DwellPreWarning -> {
            put("state", state.name)
            put("elapsed", elapsed.toString())
            put("threshold", threshold.toString())
        }
    }
}
