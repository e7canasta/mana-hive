Now I have the complete picture. Here is the full trace of the dwell threshold policy path, with every relevant file and the answers to your key questions.

---

## FULL DWELL THRESHOLD POLICY PATH

### Step 1: The Director Defines Dwell Thresholds in a Profile

The director interacts with the system through two paths:

**Path A: Legacy `AlarmProfile` (current production path)**

**File:** `/home/visiona/workspace/mana-hive/platform/contracts/src/main/kotlin/com/manahive/contracts/policy/AlarmProfile.kt`
```kotlin
public data class AlarmProfile(
    public val residentId: ResidentId,
    public val riskLevel: RiskLevel,
    public val mobilityAid: MobilityAid,
    public val autopilot: Boolean,
    public val mode: PolicyMode,
    public val templateId: TemplateId?,          // <-- picks a template
    public val overrides: Map<RuleId, PolicyOverride>,  // <-- manual overrides
    public val catalogVersion: CatalogVersion,
    public val validFrom: Instant,
)
```

The director does NOT directly specify "warning at 5 min, exceeded at 15 min" in `AlarmProfile`. Instead, they either:
1. Pick a `templateId` (e.g., `"night-wandering"`) which bundles dwell thresholds
2. Provide `overrides` of type `PolicyOverride.DwellOverride`

**File:** `/home/visiona/workspace/mana-hive/platform/contracts/src/main/kotlin/com/manahive/contracts/policy/PolicyOverride.kt`
```kotlin
public sealed interface PolicyOverride {
    public val ruleId: RuleId

    public data class DwellOverride(
        override val ruleId: RuleId,
        public val state: StateKind,
        public val value: DwellThreshold,   // <-- director can set warning + exceeded here
    ) : PolicyOverride
}
```

**Path B: DAG-centric `ResidentProfileBuilder` DSL (newer API)**

**File:** `/home/visiona/workspace/mana-hive/platform/contracts/src/main/kotlin/com/manahive/contracts/policy/DagDsl.kt`
(lines 265-293)

When the director uses the DSL to set `alertAfter`, the `ResidentProfileBuilder.buildOverrides()` **auto-derives** the dwell override:
```kotlin
private fun buildOverrides(): Map<RuleId, PolicyOverride> {
    stateOverrides.forEach { (state, override) ->
        if (override.alertAfter != null) {
            overrides[RuleId("dwell-${state.name.lowercase()}")] = PolicyOverride.DwellOverride(
                ruleId = RuleId("dwell-${state.name.lowercase()}"),
                state = state,
                value = DwellThreshold(
                    warning = override.alertAfter,
                    exceeded = override.alertAfter.multipliedBy(2),  // warning doubled for exceeded
                ),
            )
        }
    }
    ...
}
```

So in the DSL path, if the director says `alertAfter = Duration.ofMinutes(5)`, the override becomes `DwellThreshold(warning=5min, exceeded=10min)`.

---

### Step 2: The Catalog Defines Dwell Thresholds

There are TWO catalog formats:

**Legacy `AlarmCatalog`:**

**File:** `/home/visiona/workspace/mana-hive/platform/contracts/src/main/kotlin/com/manahive/contracts/policy/AlarmCatalog.kt`
```kotlin
public data class AlarmCatalog(
    public val transitions: Map<TransitionKey, Duration>,
    public val dwellThresholds: Map<StateKind, DwellThreshold>,  // <-- base defaults
    public val templates: Map<TemplateId, Template>,              // <-- template overrides
    public val version: CatalogVersion,
)

public data class Template(
    public val id: TemplateId,
    public val hysteresis: Map<TransitionKey, Duration>,
    public val dwellThresholds: Map<StateKind, DwellThreshold>,  // <-- per-template dwell
)
```

Example values from the production catalog test data:

**File:** `/home/visiona/workspace/mana-hive/engines/politica-engine/politica-domain/src/test/kotlin/com/manahive/politica/ProductionCatalog.kt`
```kotlin
val PRODUCTION_CATALOG = AlarmCatalog(
    version = CatalogVersion("1.0.0"),
    dwellThresholds = mapOf(
        StateKind.SITTING_IN_BED to DwellThreshold(warning = 30min, exceeded = 45min),
        StateKind.IN_BATHROOM to DwellThreshold(warning = 20min, exceeded = 30min),
    ),
    templates = mapOf(
        TemplateId("night-wandering") to Template(
            dwellThresholds = mapOf(
                StateKind.SITTING_IN_BED to DwellThreshold(warning = 20min, exceeded = 30min),
                StateKind.IN_BATHROOM to DwellThreshold(warning = 15min, exceeded = 25min),
                StateKind.STANDING to DwellThreshold(warning = 10min, exceeded = 15min),  // <-- "warning at 10, exceeded at 15"
            ),
        ),
        TemplateId("fall-risk") to Template(
            dwellThresholds = mapOf(
                StateKind.STANDING to DwellThreshold(warning = 2min, exceeded = 3min),
                StateKind.BED_EDGE to DwellThreshold(warning = 1min, exceeded = 2min),
            ),
        ),
    ),
)
```

**DAG-centric `DagCatalog`:**

**File:** `/home/visiona/workspace/mana-hive/platform/contracts/src/main/kotlin/com/manahive/contracts/policy/DagDsl.kt`

The `DagCatalog` defines dwell via `ResidentStateRule`:
```kotlin
public data class ResidentStateRule(
    val state: StateKind,
    val warningAfter: Duration?,    // <-- explicit warning threshold
    val alertAfter: Duration?,      // <-- exceeded threshold
    val severity: Severity,
    val closureCondition: ClosureCondition,
)
```

The DAG builder exposes this as:
```kotlin
public class DagResidentStateRuleBuilder(private val state: StateKind) {
    private var warningAfter: Duration? = null
    private var alertAfter: Duration? = null
    ...
    public fun warningAfter(duration: Duration) { warningAfter = duration }
    public fun alertAfter(duration: Duration) { alertAfter = duration }
}
```

So in the DAG catalog, the director explicitly sets `warningAfter` and `alertAfter` per state.

---

### Step 3: PolicyResolver Resolves Dwell Thresholds

**File:** `/home/visiona/workspace/mana-hive/engines/politica-engine/politica-domain/src/main/kotlin/com/manahive/politica/PolicyResolver.kt`

**Legacy `AlarmCatalog` resolution (lines 200-209):**
```kotlin
private fun resolveDwellThresholds(
    catalog: AlarmCatalog,
    profile: AlarmProfile,
): Map<StateKind, DwellThreshold> {
    val base = resolveBase(profile.templateId, catalog)
        ?.dwellThresholds           // <-- 1st: template's dwell thresholds
        ?.takeIf { it.isNotEmpty() }
        ?: catalog.dwellThresholds  // <-- 2nd fallback: catalog base dwell thresholds
    return applyOverrides<PolicyOverride.DwellOverride, StateKind, DwellThreshold>(
        base, profile.overrides
    ) { it.state to it.value }      // <-- 3rd: manual overrides win
}
```

Resolution order: **catalog base -> template -> override**.

**DAG-centric resolution (lines 108-116):**
```kotlin
private fun resolveDwellThresholdsFromDag(
    catalog: DagCatalog,
    profile: AlarmProfile,
): Map<StateKind, DwellThreshold> {
    val base = catalog.residentStates.mapNotNull { (state, rule) ->
        rule.alertAfter?.let { state to DwellThreshold(
            warning = rule.warningAfter ?: it,  // <-- if no explicit warning, uses alertAfter
            exceeded = it
        ) }
    }.toMap()
    return applyOverrides<PolicyOverride.DwellOverride, StateKind, DwellThreshold>(
        base, profile.overrides
    ) { it.state to it.value }
}
```

Key detail: if `warningAfter` is not specified in the DAG catalog, it falls back to `alertAfter` (i.e., no separate warning level).

Both resolve methods produce a `PolicyCalibration`:

```kotlin
public fun resolve(catalog: AlarmCatalog, profile: AlarmProfile): PolicyCalibration {
    val dwellThresholds = resolveDwellThresholds(catalog, profile)
    return PolicyCalibration(
        residentId = profile.residentId,
        scene = ScenePolicy(
            hysteresis = ...,
            dwellThresholds = dwellThresholds,   // <-- carried here
            confidence = ...,
        ),
        ...
    )
}
```

---

### Step 4: ScenePolicy Carries Dwell Thresholds

**File:** `/home/visiona/workspace/mana-hive/platform/contracts/src/main/kotlin/com/manahive/contracts/policy/PolicyCalibration.kt`
(lines 31-35)

```kotlin
public data class ScenePolicy(
    val hysteresis: Map<TransitionKey, Duration>,
    val dwellThresholds: Map<StateKind, DwellThreshold>,   // <-- both warning + exceeded
    val confidence: ConfidenceConfig,
)
```

The `DwellThreshold` value object (lines 120-129):
```kotlin
public data class DwellThreshold(
    val warning: Duration,
    val exceeded: Duration,
) {
    init {
        require(warning < exceeded) {
            "warning ($warning) must be less than exceeded ($exceeded)"
        }
    }
}
```

Both `warning` and `exceeded` travel as a pair through the entire pipeline.

---

### Step 5: Adapter Transforms ScenePolicy -> SceneCalibration

There are TWO adapters:

**Adapter 1: Scene Engine's native adapter**

**File:** `/home/visiona/workspace/mana-hive/engines/scene-engine/scene-domain/src/main/kotlin/com/manahive/scene/adapter/PolicyCalibrationAdapter.kt`
```kotlin
public fun PolicyCalibration.toSceneCalibration(
    base: TransitionTable = TransitionTable.RELEASE_2,
): SceneCalibration = SceneCalibration(
    table = TransitionTable.from(base = base, overrides = scene.hysteresis),
    confidence = ConfidenceThresholds(scene.confidence.minConfidence.mapValues { Confidence(it.value) }),
    heartbeatTimeout = scene.confidence.heartbeatTimeout,
    dwellThresholds = scene.dwellThresholds,   // <-- 1:1 copy, both warning + exceeded
)
```

**Adapter 2: Pipeline BDD adapter**

**File:** `/home/visiona/workspace/mana-hive/engines/pipeline/pipeline-bdd/src/main/kotlin/com/manahive/politica/adapters/PolicyAdapters.kt`
```kotlin
public fun PolicyCalibration.toSceneCalibration(): SceneCalibration = sceneCalibration {
    val scenePolicy = this@toSceneCalibration.scene
    heartbeatTimeout = scenePolicy.confidence.heartbeatTimeout

    dwell {
        scenePolicy.dwellThresholds.forEach { (state, threshold) ->
            when (state) {
                StateKind.STANDING -> STANDING warning threshold.warning exceeded threshold.exceeded
                StateKind.SITTING_IN_BED -> SITTING_IN_BED warning threshold.warning exceeded threshold.exceeded
                StateKind.BED_EDGE -> BED_EDGE warning threshold.warning exceeded threshold.exceeded
                StateKind.IN_BATHROOM -> IN_BATHROOM warning threshold.warning exceeded threshold.exceeded
                StateKind.LYING -> LYING warning threshold.warning exceeded threshold.exceeded
                StateKind.IN_HALLWAY -> IN_HALLWAY warning threshold.warning exceeded threshold.exceeded
                else -> {}
            }
        }
    }
    ...
}
```

Both adapters faithfully copy both `warning` and `exceeded` through.

---

### Step 6: SceneCalibration Carries Dwell Thresholds

**File:** `/home/visiona/workspace/mana-hive/engines/scene-engine/scene-domain/src/main/kotlin/com/manahive/scene/calibration/SceneCalibration.kt`
```kotlin
public data class SceneCalibration(
    public val table: TransitionTable,
    public val confidence: ConfidenceThresholds,
    public val heartbeatTimeout: Duration,
    public val dwellThresholds: Map<StateKind, DwellThreshold> = emptyMap(),  // <-- both warning + exceeded
    public val comeBackThresholds: Map<StateKind, DwellThreshold> = emptyMap(),
    public val sceneHysteresis: Map<String, Duration> = emptyMap(),
    public val sceneThresholds: Map<String, DwellThreshold> = emptyMap(),
    public val sceneConfidence: Map<ObservationKind, Confidence> = emptyMap(),
)
```

The `DwellThreshold` here is the same `com.manahive.contracts.policy.DwellThreshold` -- both `warning` and `exceeded` Durations.

---

### Step 7: DwellCatalog -- The Bridge to ClockSweeper

**File:** `/home/visiona/workspace/mana-hive/engines/scene-engine/scene-domain/src/main/kotlin/com/manahive/scene/calibration/DwellCatalog.kt`
```kotlin
public data class DwellCatalog(
    public val byState: Map<StateKind, DwellThreshold>,          // <-- both warning + exceeded
    public val heartbeatTimeout: Duration = PolicyDefaults.heartbeatTimeout,
    public val sceneThresholds: Map<String, DwellThreshold> = emptyMap(),
    public val comeBackByBaseline: Map<StateKind, DwellThreshold> = emptyMap(),
)

public fun SceneCalibration.toDwellCatalog(): DwellCatalog = DwellCatalog(
    byState = dwellThresholds.toMap(),       // <-- 1:1 copy from SceneCalibration
    heartbeatTimeout = heartbeatTimeout,
    sceneThresholds = sceneThresholds,
    comeBackByBaseline = comeBackThresholds.toMap(),
)
```

---

### Step 8: ClockSweeper Uses Dwell Thresholds

**File:** `/home/visiona/workspace/mana-hive/engines/scene-engine/scene-domain/src/main/kotlin/com/manahive/scene/sweeper/ClockSweeperImpl.kt`

The critical method is `checkDwell` (lines 94-125) and `checkDwellThreshold` (lines 256-281):

```kotlin
private fun checkDwell(
    twin: DigitalTwin,
    ctx: SweepContext,
    marks: DwellMarks,
): DwellCheckResult {
    val stateKind = twin.state.kind
    val dwellThreshold = ctx.thresholds.byState[stateKind]  // <-- gets BOTH warning + exceeded
        ?: return DwellCheckResult(emptyList(), emptySet())
    val duration = twin.durationInState(ctx.now)

    checkDwellThreshold(
        config = DwellThresholdConfig(
            duration = duration,
            exceeded = dwellThreshold.exceeded,    // <-- exceeded threshold from DwellThreshold
            warning = dwellThreshold.warning,      // <-- WARNING threshold from DwellThreshold
            markKey = markKey,
            toWarningMark = { it.copy(warning = true) },
        ),
        emittedMarks = marks.emitted,
        emitExceeded = { twin.emitDwellExceeded(dwellThreshold.exceeded, ctx.now) },
        emitWarning = { twin.emitDwellWarning(dwellThreshold.exceeded, ctx.now) },  // <-- BUG?
        isExceeded = { it is DwellExceeded },
        facts = facts,
        newMarks = newMarks,
    )
    return DwellCheckResult(facts, newMarks)
}
```

The generic threshold check (lines 256-281):
```kotlin
private fun <K> checkDwellThreshold(
    config: DwellThresholdConfig<K>,
    emittedMarks: Set<K>,
    emitExceeded: () -> SceneEvent,
    emitWarning: () -> SceneEvent,
    isExceeded: (SceneEvent) -> Boolean,
    facts: MutableList<SceneEvent>,
    newMarks: MutableSet<K>,
) {
    if (config.duration >= config.exceeded) {        // <-- exceeded check first
        if (!emittedMarks.contains(config.markKey) && !newMarks.contains(config.markKey)) {
            facts += emitExceeded()
            newMarks += config.markKey
        }
    }

    if (config.duration >= config.warning) {          // <-- warning check second
        val warningMark = config.toWarningMark(config.markKey)
        if (!emittedMarks.contains(warningMark) && !newMarks.contains(warningMark)) {
            if (!facts.any(isExceeded)) {             // <-- suppressed if exceeded was just emitted
                facts += emitWarning()
                newMarks += warningMark
            }
        }
    }
}
```

**File:** `/home/visiona/workspace/mana-hive/platform/contracts/src/main/kotlin/com/manahive/contracts/scene/SceneEvent.kt`
```kotlin
/** Early warning at ~80% of the threshold: "on its way to expire". */
public data class DwellWarning(
    override val bed: BedId, override val night: NightId, override val at: Instant,
    public val state: PersonState,
    public val threshold: Duration,
    public val since: Instant,
) : SceneEvent
```

---

## ANSWERS TO KEY QUESTIONS

### 1. Where does the director say "warning at 5 min, exceeded at 15 min"?

The director says this in **three possible places**, depending on the configuration mode:

- **Template in AlarmCatalog** (e.g., `ProductionCatalog.kt`): The `"night-wandering"` template defines `STANDING to DwellThreshold(warning=10min, exceeded=15min)`. The director selects this template via `AlarmProfile.templateId = TemplateId("night-wandering")`.

- **Manual override in AlarmProfile**: The director creates a `PolicyOverride.DwellOverride(state=STANDING, value=DwellThreshold(warning=5min, exceeded=15min))` and places it in `AlarmProfile.overrides`.

- **DAG catalog DSL** (`DagDsl.kt`): The director uses `warningAfter(Duration.ofMinutes(5))` and `alertAfter(Duration.ofMinutes(15))` inside a `ResidentStateRuleBuilder`.

### 2. How does that flow through the policy system?

```
Director's choice (template + overrides)
  --> AlarmProfile (templateId + overrides)
    --> PolicyResolver.resolveDwellThresholds()  [catalog -> template -> override]
      --> ScenePolicy.dwellThresholds: Map<StateKind, DwellThreshold>
        --> PolicyCalibration (the contract between engines)
          --> toSceneCalibration() adapter
            --> SceneCalibration.dwellThresholds: Map<StateKind, DwellThreshold>
              --> SceneCalibration.toDwellCatalog()
                --> DwellCatalog.byState: Map<StateKind, DwellThreshold>
                  --> ClockSweeper.sweep()
                    --> checkDwell() uses BOTH warning + exceeded
```

### 3. Does the warning threshold currently flow all the way to ClockSweeper?

**YES, it does.** The `DwellThreshold` data class carries both `warning` and `exceeded` at every layer:

- `AlarmCatalog.dwellThresholds: Map<StateKind, DwellThreshold>` -- has both
- `Template.dwellThresholds: Map<StateKind, DwellThreshold>` -- has both
- `PolicyOverride.DwellOverride.value: DwellThreshold` -- has both
- `ScenePolicy.dwellThresholds: Map<StateKind, DwellThreshold>` -- has both
- `SceneCalibration.dwellThresholds: Map<StateKind, DwellThreshold>` -- has both
- `DwellCatalog.byState: Map<StateKind, DwellThreshold>` -- has both
- `ClockSweeperImpl.checkDwell()` uses `dwellThreshold.warning` and `dwellThreshold.exceeded`

**However, there is a BUG in the `emitWarning` lambda on line 118 of `ClockSweeperImpl.kt`:**

```kotlin
emitWarning = { twin.emitDwellWarning(dwellThreshold.exceeded, ctx.now) },
```

It passes `dwellThreshold.exceeded` instead of `dwellThreshold.warning` as the `threshold` parameter to `DwellWarning`. This means the `DwellWarning` event's `threshold` field records the **exceeded** duration rather than the **warning** duration. The *decision logic* (which threshold it fires at) is correct (it uses `dwellThreshold.warning` in the `DwellThresholdConfig`), but the *emitted event's metadata* is wrong. The `DwellWarning` event will report `threshold = exceeded` instead of `threshold = warning`.

Compare with the `ComeBack` check on line 175 which correctly passes:
```kotlin
emitWarning = { twin.emitComeBackWarning(comeBackThreshold.warning, ctx.now) },
```

### 4. Is the warning threshold configurable or hardcoded at 80%?

**The warning threshold is fully configurable, NOT hardcoded at 80%.** The `~80%` reference in `SceneEvent.kt` is just a documentation comment:

```kotlin
/** Early warning at ~80% of the threshold: "on its way to expire". */
public data class DwellWarning(...)
```

The actual warning threshold is explicitly set at every level of the catalog/profile hierarchy:

- In `AlarmCatalog`, templates define explicit `DwellThreshold(warning=..., exceeded=...)` pairs
- In `DagCatalog`, `ResidentStateRule.warningAfter` is an explicit `Duration?`
- In `AlarmProfile` overrides, `DwellOverride.value` is a `DwellThreshold` with explicit `warning`
- In the DAG DSL, if `warningAfter` is NOT set, it falls back to `alertAfter` (line 113 of PolicyResolver: `warning = rule.warningAfter ?: it`)

For example, the production catalog defines these explicit ratios:
- STANDING night-wandering: warning=10min, exceeded=15min (67% ratio)
- SITTING_IN_BED night-wandering: warning=20min, exceeded=30min (67% ratio)
- IN_BATHROOM night-wandering: warning=15min, exceeded=25min (60% ratio)
- STANDING fall-risk: warning=2min, exceeded=3min (67% ratio)
- STANDING default catalog: warning=4min, exceeded=5min (80% ratio)

The ratio varies per template and state. The `~80%` in the comment is a loose guideline, not a rule.