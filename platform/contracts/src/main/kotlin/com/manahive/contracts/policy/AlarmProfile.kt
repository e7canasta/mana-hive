package com.manahive.contracts.policy

import com.manahive.kernel.ResidentId
import com.manahive.kernel.RuleId
import java.time.Instant

/**
 * The policy for one resident: what risk level, what mobility aid, what template,
 * and any manual overrides. Immutable — versions are entities, this is a snapshot.
 *
 * Linked to ResidentId, never to BedId. At most one valid version per resident
 * at any instant.
 */
public data class AlarmProfile(
    public val residentId: ResidentId,
    public val riskLevel: RiskLevel,
    public val mobilityAid: MobilityAid,
    public val autopilot: Boolean,
    public val mode: PolicyMode,
    public val templateId: TemplateId?,
    public val overrides: Map<RuleId, PolicyOverride>,
    public val catalogVersion: CatalogVersion,
    public val validFrom: Instant,
)

public enum class RiskLevel { LOW, MEDIUM, HIGH }
public enum class MobilityAid { NONE, WALKER, WHEELCHAIR }
public enum class PolicyMode { PRESET, CUSTOM }

@JvmInline public value class TemplateId(public val value: String)
@JvmInline public value class CatalogVersion(public val value: String)
