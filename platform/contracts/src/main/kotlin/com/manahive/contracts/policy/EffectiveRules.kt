package com.manahive.contracts.policy

import com.manahive.kernel.ResidentId
import com.manahive.kernel.RuleId
import com.manahive.kernel.StaffId
import java.time.Instant

/**
 * The result of resolving the policy layers (watch level -> level template ->
 * manual adjustments -> time windows) for one resident at one instant.
 * Resolved by the hub (owner of clinical policy) and distributed to the
 * sentinel on `hub.policy.effective-rules.v1.<resident>`.
 *
 * `fingerprint` is the stable hash of the whole set: every downstream decision
 * cites it, so replay can reconstruct exactly which policy governed.
 */
public data class EffectiveRules(
    public val resident: ResidentId,
    public val at: Instant,
    public val rules: List<EffectiveRule>,
    public val fingerprint: String,
)

public data class EffectiveRule(
    public val id: RuleId,
    public val kind: RuleKind,
    public val params: Map<String, String>,
    public val provenance: Provenance,
)

public enum class RuleKind { DWELL_LIMIT, NIGHT_EXIT, SIGNAL_WATCH }

/** Every effective rule knows which layer put it there, who and when. */
public sealed interface Provenance {
    public data class FromLevel(public val level: WatchLevel) : Provenance
    public data class FromTemplate(public val templateId: String) : Provenance
    public data class FromManualAdjustment(public val actor: StaffId, public val at: Instant) : Provenance
    public data class FromTimeWindow(public val windowId: String) : Provenance
}

public enum class WatchLevel { LOW, MEDIUM, HIGH }
