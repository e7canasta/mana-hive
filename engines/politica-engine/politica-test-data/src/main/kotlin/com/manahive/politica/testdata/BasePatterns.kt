package com.manahive.politica.testdata

import com.manahive.contracts.policy.*
import com.manahive.contracts.scene.StateKind
import com.manahive.kernel.ResidentId
import com.manahive.kernel.RuleId
import java.time.Duration
import java.time.Instant

// ── Catalog patterns ─────────────────────────────────────────────────────────

fun testCatalog(
    version: String = "1.0",
    transitions: Map<TransitionKey, Duration> = defaultTransitions(),
    dwellThresholds: Map<StateKind, DwellThreshold> = defaultDwell(),
    templates: Map<TemplateId, Template> = emptyMap(),
): AlarmCatalog = AlarmCatalog(
    transitions = transitions,
    dwellThresholds = dwellThresholds,
    templates = templates,
    version = CatalogVersion(version),
)

fun defaultTransitions(): Map<TransitionKey, Duration> = mapOf(
    TransitionKey(StateKind.LYING, StateKind.SITTING_IN_BED) to Duration.ofSeconds(3),
    TransitionKey(StateKind.SITTING_IN_BED, StateKind.STANDING) to Duration.ofSeconds(3),
    TransitionKey(StateKind.STANDING, StateKind.IN_BATHROOM) to Duration.ofSeconds(5),
    TransitionKey(StateKind.IN_BATHROOM, StateKind.IN_ROOM) to Duration.ofSeconds(5),
    TransitionKey(StateKind.IN_ROOM, StateKind.LYING) to Duration.ofSeconds(3),
    TransitionKey(StateKind.STANDING, StateKind.BED_EDGE) to Duration.ofSeconds(3),
    TransitionKey(StateKind.BED_EDGE, StateKind.STANDING) to Duration.ofSeconds(3),
)

fun defaultDwell(): Map<StateKind, DwellThreshold> = mapOf(
    StateKind.STANDING to DwellThreshold(warning = Duration.ofMinutes(4), exceeded = Duration.ofMinutes(5)),
    StateKind.IN_BATHROOM to DwellThreshold(warning = Duration.ofMinutes(20), exceeded = Duration.ofMinutes(30)),
    StateKind.BED_EDGE to DwellThreshold(warning = Duration.ofMinutes(2), exceeded = Duration.ofMinutes(3)),
    StateKind.SITTING_IN_BED to DwellThreshold(warning = Duration.ofMinutes(30), exceeded = Duration.ofMinutes(45)),
)

// ── Template patterns ────────────────────────────────────────────────────────

fun testTemplate(
    id: String = "t-senior",
    hysteresis: Map<TransitionKey, Duration> = defaultTransitions(),
    dwellThresholds: Map<StateKind, DwellThreshold> = defaultDwell(),
): Template = Template(
    id = TemplateId(id),
    hysteresis = hysteresis,
    dwellThresholds = dwellThresholds,
)

fun fallRiskTemplate(): Template = Template(
    id = TemplateId("t-fall-risk"),
    hysteresis = mapOf(
        TransitionKey(StateKind.LYING, StateKind.SITTING_IN_BED) to Duration.ofSeconds(5),
        TransitionKey(StateKind.SITTING_IN_BED, StateKind.STANDING) to Duration.ofSeconds(5),
        TransitionKey(StateKind.STANDING, StateKind.BED_EDGE) to Duration.ofSeconds(5),
        TransitionKey(StateKind.BED_EDGE, StateKind.STANDING) to Duration.ofSeconds(5),
    ),
    dwellThresholds = mapOf(
        StateKind.STANDING to DwellThreshold(warning = Duration.ofMinutes(2), exceeded = Duration.ofMinutes(3)),
        StateKind.BED_EDGE to DwellThreshold(warning = Duration.ofMinutes(1), exceeded = Duration.ofMinutes(2)),
    ),
)

// ── Profile patterns ─────────────────────────────────────────────────────────

fun testProfile(
    residentId: String = "test",
    riskLevel: RiskLevel = RiskLevel.MEDIUM,
    mobilityAid: MobilityAid = MobilityAid.NONE,
    autopilot: Boolean = true,
    mode: PolicyMode = PolicyMode.PRESET,
    templateId: TemplateId? = null,
    overrides: Map<RuleId, PolicyOverride> = emptyMap(),
    catalogVersion: String = "1.0",
    validFrom: Instant = Instant.parse("2024-01-01T00:00:00Z"),
): AlarmProfile = AlarmProfile(
    residentId = ResidentId(residentId),
    riskLevel = riskLevel,
    mobilityAid = mobilityAid,
    autopilot = autopilot,
    mode = mode,
    templateId = templateId,
    overrides = overrides,
    catalogVersion = CatalogVersion(catalogVersion),
    validFrom = validFrom,
)

fun presetProfile(
    residentId: String = "test",
    templateId: String = "t-senior",
): AlarmProfile = testProfile(
    residentId = residentId,
    mode = PolicyMode.PRESET,
    templateId = TemplateId(templateId),
)

fun customProfile(
    residentId: String = "test",
    overrides: Map<RuleId, PolicyOverride> = emptyMap(),
): AlarmProfile = testProfile(
    residentId = residentId,
    mode = PolicyMode.CUSTOM,
    templateId = null,
    overrides = overrides,
)

// ── Override helpers ──────────────────────────────────────────────────────────

fun hysteresisOverride(
    ruleId: String,
    from: StateKind,
    to: StateKind,
    value: Duration,
): Pair<RuleId, PolicyOverride.HysteresisOverride> = Pair(
    RuleId(ruleId),
    PolicyOverride.HysteresisOverride(
        ruleId = RuleId(ruleId),
        key = TransitionKey(from, to),
        value = value,
    ),
)

fun dwellOverride(
    ruleId: String,
    state: StateKind,
    warning: Duration,
    exceeded: Duration,
): Pair<RuleId, PolicyOverride.DwellOverride> = Pair(
    RuleId(ruleId),
    PolicyOverride.DwellOverride(
        ruleId = RuleId(ruleId),
        state = state,
        value = DwellThreshold(warning = warning, exceeded = exceeded),
    ),
)
