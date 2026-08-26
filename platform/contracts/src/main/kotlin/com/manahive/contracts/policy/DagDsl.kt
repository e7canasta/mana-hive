package com.manahive.contracts.policy

import com.manahive.contracts.scene.StateKind
import com.manahive.kernel.ResidentId
import com.manahive.kernel.RuleId
import java.time.Duration

/**
 * DAG-centric alarm catalog: rules defined on resident states and room states.
 */
public fun buildDagCatalog(block: DagCatalogBuilder.() -> Unit): DagCatalog =
    DagCatalogBuilder().apply(block).build()

@DslMarker
public annotation class DagCatalogDsl

@DagCatalogDsl
public class DagCatalogBuilder {
    private var version: CatalogVersion = CatalogVersion("1.0.0")
    private val residentStates = mutableMapOf<StateKind, ResidentStateRule>()
    private val roomStates = mutableMapOf<String, RoomStateRule>()
    private val transitions = mutableMapOf<TransitionKey, DagTransitionRule>()

    public fun version(value: String) {
        version = CatalogVersion(value)
    }

    public fun resident(block: DagResidentStatesBuilder.() -> Unit) {
        DagResidentStatesBuilder(residentStates).apply(block)
    }

    public fun room(block: DagRoomStatesBuilder.() -> Unit) {
        DagRoomStatesBuilder(roomStates).apply(block)
    }

    public fun transitions(block: DagTransitionRulesBuilder.() -> Unit) {
        DagTransitionRulesBuilder(transitions).apply(block)
    }

    internal fun build(): DagCatalog = DagCatalog(
        version = version,
        residentStates = residentStates.toMap(),
        roomStates = roomStates.toMap(),
        transitions = transitions.toMap(),
    )
}

@DagCatalogDsl
public class DagResidentStatesBuilder(
    private val states: MutableMap<StateKind, ResidentStateRule>,
) {
    public fun lying(block: DagResidentStateRuleBuilder.() -> Unit) {
        states[StateKind.LYING] = DagResidentStateRuleBuilder(StateKind.LYING).apply(block).build()
    }

    public fun sitting(block: DagResidentStateRuleBuilder.() -> Unit) {
        states[StateKind.SITTING_IN_BED] = DagResidentStateRuleBuilder(StateKind.SITTING_IN_BED).apply(block).build()
    }

    public fun bedEdge(block: DagResidentStateRuleBuilder.() -> Unit) {
        states[StateKind.BED_EDGE] = DagResidentStateRuleBuilder(StateKind.BED_EDGE).apply(block).build()
    }

    public fun standing(block: DagResidentStateRuleBuilder.() -> Unit) {
        states[StateKind.STANDING] = DagResidentStateRuleBuilder(StateKind.STANDING).apply(block).build()
    }

    public fun bathroom(block: DagResidentStateRuleBuilder.() -> Unit) {
        states[StateKind.IN_BATHROOM] = DagResidentStateRuleBuilder(StateKind.IN_BATHROOM).apply(block).build()
    }

    public fun absent(block: DagResidentStateRuleBuilder.() -> Unit) {
        states[StateKind.ABSENT] = DagResidentStateRuleBuilder(StateKind.ABSENT).apply(block).build()
    }
}

@DagCatalogDsl
public class DagResidentStateRuleBuilder(private val state: StateKind) {
    private var warningAfter: Duration? = null
    private var alertAfter: Duration? = null
    private var severity: Severity = Severity.WARNING
    private var closureCondition: ClosureCondition = ClosureCondition.SAFE_ONLY

    public fun warningAfter(duration: Duration) {
        warningAfter = duration
    }

    public fun alertAfter(duration: Duration) {
        alertAfter = duration
    }

    public fun severity(value: Severity) {
        severity = value
    }

    public fun closure(value: ClosureCondition) {
        closureCondition = value
    }

    internal fun build(): ResidentStateRule = ResidentStateRule(
        state = state,
        warningAfter = warningAfter,
        alertAfter = alertAfter,
        severity = severity,
        closureCondition = closureCondition,
    )
}

public data class ResidentStateRule(
    val state: StateKind,
    val warningAfter: Duration?,
    val alertAfter: Duration?,
    val severity: Severity,
    val closureCondition: ClosureCondition,
)

@DagCatalogDsl
public class DagRoomStatesBuilder(
    private val states: MutableMap<String, RoomStateRule>,
) {
    public fun staffEnters(block: DagRoomStateRuleBuilder.() -> Unit) {
        states["staff-enters"] = DagRoomStateRuleBuilder("staff-enters").apply(block).build()
    }

    public fun staffLeaves(block: DagRoomStateRuleBuilder.() -> Unit) {
        states["staff-leaves"] = DagRoomStateRuleBuilder("staff-leaves").apply(block).build()
    }
}

@DagCatalogDsl
public class DagRoomStateRuleBuilder(private val event: String) {
    private var closeEpisode: Boolean = false

    public fun closeEpisode() {
        closeEpisode = true
    }

    internal fun build(): RoomStateRule = RoomStateRule(
        event = event,
        closeEpisode = closeEpisode,
    )
}

public data class RoomStateRule(
    val event: String,
    val closeEpisode: Boolean,
)

@DagCatalogDsl
public class DagTransitionRulesBuilder(
    private val rules: MutableMap<TransitionKey, DagTransitionRule>,
) {
    public fun from(from: StateKind, block: DagTransitionFromBuilder.() -> Unit) {
        DagTransitionFromBuilder(from, rules).apply(block)
    }
}

@DagCatalogDsl
public class DagTransitionFromBuilder(
    private val from: StateKind,
    private val rules: MutableMap<TransitionKey, DagTransitionRule>,
) {
    public fun to(to: StateKind, block: DagTransitionRuleBuilder.() -> Unit) {
        rules[TransitionKey(from, to)] = DagTransitionRuleBuilder(from, to).apply(block).build()
    }
}

@DagCatalogDsl
public class DagTransitionRuleBuilder(
    private val from: StateKind,
    private val to: StateKind,
) {
    private var hysteresis: Duration = Duration.ofMillis(1500)
    private var recordBefore: Duration? = null
    private var recordAfter: Duration? = null

    public fun hysteresis(duration: Duration) {
        hysteresis = duration
    }

    public fun record(before: Duration, after: Duration) {
        recordBefore = before
        recordAfter = after
    }

    internal fun build(): DagTransitionRule = DagTransitionRule(
        from = from,
        to = to,
        hysteresis = hysteresis,
        recordBefore = recordBefore,
        recordAfter = recordAfter,
    )
}

public data class DagTransitionRule(
    val from: StateKind,
    val to: StateKind,
    val hysteresis: Duration,
    val recordBefore: Duration?,
    val recordAfter: Duration?,
)

public data class DagCatalog(
    val version: CatalogVersion,
    val residentStates: Map<StateKind, ResidentStateRule>,
    val roomStates: Map<String, RoomStateRule>,
    val transitions: Map<TransitionKey, DagTransitionRule>,
)

// ── Resident Profile DSL ───────────────────────────────────────────────────

public fun buildResidentProfile(
    residentId: String,
    block: ResidentProfileBuilder.() -> Unit,
): ResidentProfileConfig =
    ResidentProfileBuilder(ResidentId(residentId)).apply(block).build()

@DslMarker
public annotation class ResidentProfileDsl

@ResidentProfileDsl
public class ResidentProfileBuilder(private val residentId: ResidentId) {
    private var riskLevel: RiskLevel = RiskLevel.MEDIUM
    private var mobilityAid: MobilityAid = MobilityAid.NONE
    private var templateId: TemplateId? = null
    private val stateOverrides = mutableMapOf<StateKind, ProfileStateOverride>()
    private val transitionOverrides = mutableMapOf<TransitionKey, ProfileTransitionOverride>()

    public fun risk(level: RiskLevel) {
        riskLevel = level
    }

    public fun mobility(aid: MobilityAid) {
        mobilityAid = aid
    }

    public fun template(id: String) {
        templateId = TemplateId(id)
    }

    public fun resident(block: ResidentStateOverridesBuilder.() -> Unit) {
        ResidentStateOverridesBuilder(stateOverrides).apply(block)
    }

    public fun transitions(block: TransitionOverridesBuilder.() -> Unit) {
        TransitionOverridesBuilder(transitionOverrides).apply(block)
    }

    internal fun build(): ResidentProfileConfig = ResidentProfileConfig(
        profile = AlarmProfile(
            residentId = residentId,
            riskLevel = riskLevel,
            mobilityAid = mobilityAid,
            autopilot = false,
            mode = if (templateId != null) PolicyMode.PRESET else PolicyMode.CUSTOM,
            templateId = templateId,
            overrides = buildOverrides(),
            catalogVersion = CatalogVersion("2.1.0"),
            validFrom = java.time.Instant.now(),
        ),
        stateOverrides = stateOverrides.toMap(),
        transitionOverrides = transitionOverrides.toMap(),
    )

    private fun buildOverrides(): Map<RuleId, PolicyOverride> {
        val overrides = mutableMapOf<RuleId, PolicyOverride>()

        stateOverrides.forEach { (state, override) ->
            if (override.alertAfter != null) {
                val exceeded = override.alertAfter
                val warning = override.warningAfter ?: exceeded.dividedBy(2)
                overrides[RuleId("dwell-${state.name.lowercase()}")] = PolicyOverride.DwellOverride(
                    ruleId = RuleId("dwell-${state.name.lowercase()}"),
                    state = state,
                    value = DwellThreshold(
                        warning = warning,
                        exceeded = exceeded,
                    ),
                )
            }
        }

        transitionOverrides.forEach { (key, override) ->
            if (override.hysteresis != null) {
                overrides[RuleId("hysteresis-${key.from}-${key.to}")] = PolicyOverride.HysteresisOverride(
                    ruleId = RuleId("hysteresis-${key.from}-${key.to}"),
                    key = key,
                    value = override.hysteresis,
                )
            }
        }

        return overrides
    }
}

@ResidentProfileDsl
public class ResidentStateOverridesBuilder(
    private val overrides: MutableMap<StateKind, ProfileStateOverride>,
) {
    public fun sitting(block: ProfileStateOverrideBuilder.() -> Unit) {
        overrides[StateKind.SITTING_IN_BED] = ProfileStateOverrideBuilder().apply(block).build()
    }

    public fun bathroom(block: ProfileStateOverrideBuilder.() -> Unit) {
        overrides[StateKind.IN_BATHROOM] = ProfileStateOverrideBuilder().apply(block).build()
    }

    public fun standing(block: ProfileStateOverrideBuilder.() -> Unit) {
        overrides[StateKind.STANDING] = ProfileStateOverrideBuilder().apply(block).build()
    }

    public fun absent(block: ProfileStateOverrideBuilder.() -> Unit) {
        overrides[StateKind.ABSENT] = ProfileStateOverrideBuilder().apply(block).build()
    }

    public fun bedEdge(block: ProfileStateOverrideBuilder.() -> Unit) {
        overrides[StateKind.BED_EDGE] = ProfileStateOverrideBuilder().apply(block).build()
    }
}

@ResidentProfileDsl
public class ProfileStateOverrideBuilder {
    private var warningAfter: Duration? = null
    private var alertAfter: Duration? = null
    private var severity: Severity? = null

    public fun warningAfter(duration: Duration) {
        warningAfter = duration
    }

    public fun alertAfter(duration: Duration) {
        alertAfter = duration
    }

    public fun severity(value: Severity) {
        severity = value
    }

    internal fun build(): ProfileStateOverride = ProfileStateOverride(
        warningAfter = warningAfter,
        alertAfter = alertAfter,
        severity = severity,
    )
}

public data class ProfileStateOverride(
    val warningAfter: Duration?,
    val alertAfter: Duration?,
    val severity: Severity?,
)

@ResidentProfileDsl
public class TransitionOverridesBuilder(
    private val overrides: MutableMap<TransitionKey, ProfileTransitionOverride>,
) {
    public fun lyingToStanding(block: ProfileTransitionOverrideBuilder.() -> Unit) {
        overrides[TransitionKey(StateKind.LYING, StateKind.STANDING)] =
            ProfileTransitionOverrideBuilder().apply(block).build()
    }

    public fun sittingToStanding(block: ProfileTransitionOverrideBuilder.() -> Unit) {
        overrides[TransitionKey(StateKind.SITTING_IN_BED, StateKind.STANDING)] =
            ProfileTransitionOverrideBuilder().apply(block).build()
    }
}

@ResidentProfileDsl
public class ProfileTransitionOverrideBuilder {
    private var hysteresis: Duration? = null

    public fun hysteresis(duration: Duration) {
        hysteresis = duration
    }

    internal fun build(): ProfileTransitionOverride = ProfileTransitionOverride(
        hysteresis = hysteresis,
    )
}

public data class ProfileTransitionOverride(
    val hysteresis: Duration?,
)

public data class ResidentProfileConfig(
    val profile: AlarmProfile,
    val stateOverrides: Map<StateKind, ProfileStateOverride>,
    val transitionOverrides: Map<TransitionKey, ProfileTransitionOverride>,
)
