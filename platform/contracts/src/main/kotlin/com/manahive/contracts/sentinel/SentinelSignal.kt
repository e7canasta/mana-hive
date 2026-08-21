package com.manahive.contracts.sentinel

import com.manahive.kernel.BedId
import com.manahive.kernel.EpisodeId
import com.manahive.kernel.EventRef
import com.manahive.kernel.ResidentId
import com.manahive.kernel.RuleId
import java.time.Instant

/**
 * What the sentinel distilled from a scene fact under the effective rules:
 * an incident (deserves a human NOW — the vigia takes it from here), an
 * occurrence (recorded, aggregated into the round digest), or a suppression
 * WITH RECORD (the fact happened; the alarm did not — and we can prove why).
 * Published on `sentinel.signal.v1.<bed>`. Every signal cites the rules
 * fingerprint that governed it: decisions are reproducible.
 */
public sealed interface SentinelSignal {
    public val bed: BedId
    public val resident: ResidentId?
    public val at: Instant
    public val rulesFingerprint: String

    public data class IncidentDeclared(
        override val bed: BedId, override val resident: ResidentId?,
        override val at: Instant, override val rulesFingerprint: String,
        public val rule: RuleId,
        public val episode: EpisodeId,
        public val severity: Severity,
        public val evidence: EventRef,
    ) : SentinelSignal

    public data class OccurrenceNoted(
        override val bed: BedId, override val resident: ResidentId?,
        override val at: Instant, override val rulesFingerprint: String,
        public val rule: RuleId,
        public val evidence: EventRef,
    ) : SentinelSignal

    public data class SuppressedWithRecord(
        override val bed: BedId, override val resident: ResidentId?,
        override val at: Instant, override val rulesFingerprint: String,
        public val rule: RuleId,
        public val cause: SuppressionCause,
        public val evidence: EventRef,
    ) : SentinelSignal
}

public enum class Severity { INFO, WARNING, CRITICAL }

public enum class SuppressionCause { STAFF_PRESENT, EPISODE_ALREADY_ALERTED, FATIGUE_BUDGET }
