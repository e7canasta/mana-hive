Now I have all the information needed. Here is the comprehensive analysis:

---

## How Dwell-Based Episodes Work in mana-hive

### 1. File Inventory (Absolute Paths)

| Component | File |
|-----------|------|
| SceneEvent definitions | `/home/visiona/workspace/mana-hive/platform/contracts/src/main/kotlin/com/manahive/contracts/scene/SceneEvent.kt` |
| SentinelEvaluator (DwellExceeded handling) | `/home/visiona/workspace/mana-hive/engines/sentinel/sentinel-domain/src/main/kotlin/com/manahive/sentinel/SentinelEvaluatorImpl.kt` |
| ClockSweeper (DwellExceeded emission) | `/home/visiona/workspace/mana-hive/engines/scene-engine/scene-domain/src/main/kotlin/com/manahive/scene/sweeper/ClockSweeperImpl.kt` |
| ClockSweeper interface | `/home/visiona/workspace/mana-hive/engines/scene-engine/scene-domain/src/main/kotlin/com/manahive/scene/sweeper/ClockSweeper.kt` |
| DigitalTwin (dwell tracking) | `/home/visiona/workspace/mana-hive/engines/scene-engine/scene-domain/src/main/kotlin/com/manahive/scene/core/DigitalTwin.kt` |
| SceneInterpreter | `/home/visiona/workspace/mana-hive/engines/scene-engine/scene-domain/src/main/kotlin/com/manahive/scene/interpreter/SceneInterpreterImpl.kt` |
| DwellThreshold definition | `/home/visiona/workspace/mana-hive/platform/contracts/src/main/kotlin/com/manahive/contracts/policy/PolicyCalibration.kt` |
| DwellCatalog | `/home/visiona/workspace/mana-hive/engines/scene-engine/scene-domain/src/main/kotlin/com/manahive/scene/calibration/DwellCatalog.kt` |
| SceneCalibration DSL | `/home/visiona/workspace/mana-hive/engines/scene-engine/scene-domain/src/main/kotlin/com/manahive/scene/calibration/SceneCalibration.kt` |
| DwellThresholdsDsl | `/home/visiona/workspace/mana-hive/engines/scene-engine/scene-domain/src/main/kotlin/com/manahive/scene/calibration/dsl/DwellThresholdsDsl.kt` |
| Episode + EpisodeLedger | `/home/visiona/workspace/mana-hive/engines/sentinel/sentinel-domain/src/main/kotlin/com/manahive/sentinel/EpisodeLedger.kt` |
| SentinelCalibration | `/home/visiona/workspace/mana-hive/engines/sentinel/sentinel-domain/src/main/kotlin/com/manahive/sentinel/SentinelCalibration.kt` |
| AlertRule definition | `/home/visiona/workspace/mana-hive/platform/contracts/src/main/kotlin/com/manahive/contracts/policy/EffectiveRules.kt` |
| DwellMarks (idempotency) | `/home/visiona/workspace/mana-hive/engines/scene-engine/scene-domain/src/main/kotlin/com/manahive/scene/sweeper/DwellMarks.kt` |
| RecordingCalibration | `/home/visiona/workspace/mana-hive/engines/recorder/recorder-domain/src/main/kotlin/com/manahive/recorder/RecordingCalibration.kt` |
| RecordingCalibrationDsl | `/home/visiona/workspace/mana-hive/engines/recorder/recorder-domain/src/main/kotlin/com/manahive/recorder/RecordingCalibrationDsl.kt` |
| SceneConfig | `/home/visiona/workspace/mana-hive/engines/scene-engine/scene-domain/src/main/kotlin/com/manahive/scene/config/SceneConfig.kt` |
| SentinelEvaluatorSpec (test) | `/home/visiona/workspace/mana-hive/engines/sentinel/sentinel-domain/src/test/kotlin/com/manahive/sentinel/SentinelEvaluatorSpec.kt` |
| ClockSweeperExceededSpec (test) | `/home/visiona/workspace/mana-hive/engines/scene-engine/scene-domain/src/test/kotlin/com/manahive/scene/sweeper/ClockSweeperExceededSpec.kt` |
| ClockSweeperWarningSpec (test) | `/home/visiona/workspace/mana-hive/engines/scene-engine/scene-domain/src/test/kotlin/com/manahive/scene/sweeper/ClockSweeperWarningSpec.kt` |
| ClockSweeperIdempotentSpec (test) | `/home/visiona/workspace/mana-hive/engines/scene-engine/scene-domain/src/test/kotlin/com/manahive/scene/sweeper/ClockSweeperIdempotentSpec.kt` |

---

### 2. The Complete Dwell-Based Episode Lifecycle

#### Step 1: SceneEvent Definitions

From `/home/visiona/workspace/mana-hive/platform/contracts/src/main/kotlin/com/manahive/contracts/scene/SceneEvent.kt`:

```kotlin
/** Early warning at ~80% of the threshold: "on its way to expire". */
public data class DwellWarning(
    override val bed: BedId, override val night: NightId, override val at: Instant,
    public val state: PersonState,
    public val threshold: Duration,
    public val since: Instant,
) : SceneEvent

public data class DwellExceeded(
    override val bed: BedId, override val night: NightId, override val at: Instant,
    public val state: PersonState,
    public val threshold: Duration,
    public val since: Instant,
) : SceneEvent
```

Key fields:
- `state` -- which PersonState the dwell applies to (e.g., `IN_BATHROOM`, `STANDING`)
- `threshold` -- the configured threshold duration (e.g., `PT5M`)
- `since` -- when the person entered the state (= `DigitalTwin.stateSince`)
- `at` -- when the ClockSweeper detected the threshold was exceeded

Also defined in the same file: `ComeBackWarning` and `ComeBackExceeded` (inverse dwell):

```kotlin
public data class ComeBackWarning(
    override val bed: BedId, override val night: NightId, override val at: Instant,
    public val baseline: PersonState,
    public val threshold: Duration,
    public val since: Instant,
) : SceneEvent

public data class ComeBackExceeded(
    override val bed: BedId, override val night: NightId, override val at: Instant,
    public val baseline: PersonState,
    public val threshold: Duration,
    public val since: Instant,
) : SceneEvent
```

---

#### Step 2: How DwellExceeded is Emitted from ClockSweeper

From `/home/visiona/workspace/mana-hive/engines/scene-engine/scene-domain/src/main/kotlin/com/manahive/scene/sweeper/ClockSweeperImpl.kt`:

The ClockSweeper does NOT get called by the SceneInterpreter. The SceneInterpreter (in `SceneInterpreterImpl.kt`, line 191) explicitly states:

```kotlin
println("  Note: DwellWarning/DwellExceeded emitted by ClockSweeper, not Interpreter")
```

The ClockSweeper runs periodically on its own tick. The core logic in `ClockSweeperImpl.kt`:

```kotlin
override fun sweep(
    twins: Collection<DigitalTwin>,
    now: Instant,
    thresholds: DwellCatalog,
    marks: DwellMarks,
): Explained<SweepResult> {
    // ...
    for (twin in twins) {
        val (dwellFacts, dwellMarks) = checkDwell(twin, twinCtx, marks)
        val (comeBackFacts, comeBackMarks) = checkComeBack(twin, twinCtx, marks)
        val (signalFacts, signalMarks) = checkSignalLost(twin, twinCtx, marks)
        val (sceneDwellFacts, sceneDwellMarks) = checkSceneDwell(twin, twinCtx)
        // ...
    }
}
```

The `checkDwell` method:

```kotlin
private fun checkDwell(
    twin: DigitalTwin,
    ctx: SweepContext,
    marks: DwellMarks,
): DwellCheckResult {
    val stateKind = twin.state.kind
    val dwellThreshold = ctx.thresholds.byState[stateKind]
        ?: return DwellCheckResult(emptyList(), emptySet())

    val duration = twin.durationInState(ctx.now)   // <-- durationInState = now - stateSince
    val markKey = twin.toDwellMarkKey()

    checkDwellThreshold(
        config = DwellThresholdConfig(
            duration = duration,
            exceeded = dwellThreshold.exceeded,
            warning = dwellThreshold.warning,
            markKey = markKey,
            toWarningMark = { it.copy(warning = true) },
        ),
        emittedMarks = marks.emitted,
        emitExceeded = { twin.emitDwellExceeded(dwellThreshold.exceeded, ctx.now) },
        emitWarning = { twin.emitDwellWarning(dwellThreshold.exceeded, ctx.now) },
        isExceeded = { it is DwellExceeded },
        facts = facts,
        newMarks = newMarks,
    )
    return DwellCheckResult(facts, newMarks)
}
```

The threshold check logic (the core decision):

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
    if (config.duration >= config.exceeded) {
        if (!emittedMarks.contains(config.markKey) && !newMarks.contains(config.markKey)) {
            facts += emitExceeded()
            newMarks += config.markKey
        }
    }

    if (config.duration >= config.warning) {
        val warningMark = config.toWarningMark(config.markKey)
        if (!emittedMarks.contains(warningMark) && !newMarks.contains(warningMark)) {
            if (!facts.any(isExceeded)) {
                facts += emitWarning()
                newMarks += warningMark
            }
        }
    }
}
```

**Critical insight**: If the exceeded threshold is already met, only `DwellExceeded` is emitted (no `DwellWarning`). The warning is suppressed when exceeded fires in the same sweep tick (line 275: `if (!facts.any(isExceeded))`).

---

#### Step 3: How DigitalTwin Tracks Dwell Time

From `/home/visiona/workspace/mana-hive/engines/scene-engine/scene-domain/src/main/kotlin/com/manahive/scene/core/DigitalTwin.kt`:

```kotlin
public data class DigitalTwin(
    public val bed: BedId,
    public val night: NightId,
    public val occupant: ResidentId?,
    public val state: PersonState,
    public val stateSince: Instant,              // <-- KEY: when the current state was entered
    public val scene: SceneState = SceneState(),
    public val sceneSince: Instant = stateSince,
    public val signal: SignalHealth,
    public val calibration: SceneCalibration? = null,
    public val leftStateAt: Instant? = null,     // <-- KEY: for inverse dwell (ComeBack)
    public val baselineState: PersonState = PersonState.Lying,
)
```

The dwell duration is computed as:

```kotlin
/** Duration in current person state. */
public fun durationInState(now: Instant): Duration = Duration.between(stateSince, now)
```

The `stateSince` is set on every transition by the SceneInterpreter in `SceneInterpreterImpl.kt`:

```kotlin
private fun emitTransition(
    twin: DigitalTwin,
    targetState: PersonState,
    now: Instant,
    recoveryFacts: List<SceneEvent>,
): Explained<SceneVerdict> {
    val isReturningToBaseline = targetState == twin.baselineState
    val updatedTwin = twin.copy(
        state = targetState,
        stateSince = now,                        // <-- stateSince reset to transition time
        leftStateAt = if (isReturningToBaseline) null else twin.leftStateAt ?: now,
    )
    val fact = twin.emitTransition(targetState, now)
    // ...
}
```

The DigitalTwin also emits the DwellExceeded event:

```kotlin
public fun emitDwellExceeded(threshold: Duration, at: Instant): DwellExceeded = DwellExceeded(
    bed = bed,
    night = night,
    at = at,
    state = state,
    threshold = threshold,
    since = stateSince,        // <-- the `since` field = when the person entered the state
)
```

---

#### Step 4: Calibration for Dwell Thresholds

From `/home/visiona/workspace/mana-hive/platform/contracts/src/main/kotlin/com/manahive/contracts/policy/PolicyCalibration.kt`:

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

From `/home/visiona/workspace/mana-hive/engines/scene-engine/scene-domain/src/main/kotlin/com/manahive/scene/calibration/SceneCalibration.kt`:

```kotlin
public data class SceneCalibration(
    public val table: TransitionTable,
    public val confidence: ConfidenceThresholds,
    public val heartbeatTimeout: Duration,
    public val dwellThresholds: Map<StateKind, DwellThreshold> = emptyMap(),
    public val comeBackThresholds: Map<StateKind, DwellThreshold> = emptyMap(),
    public val sceneHysteresis: Map<String, Duration> = emptyMap(),
    public val sceneThresholds: Map<String, DwellThreshold> = emptyMap(),
    public val sceneConfidence: Map<ObservationKind, Confidence> = emptyMap(),
)
```

DSL usage example from the test defaults:

```kotlin
fun defaultDwell(): Map<StateKind, DwellThreshold> = mapOf(
    StateKind.STANDING to DwellThreshold(warning = Duration.ofMinutes(4), exceeded = Duration.ofMinutes(5)),
    StateKind.IN_BATHROOM to DwellThreshold(warning = Duration.ofMinutes(20), exceeded = Duration.ofMinutes(30)),
    StateKind.BED_EDGE to DwellThreshold(warning = Duration.ofMinutes(2), exceeded = Duration.ofMinutes(3)),
    StateKind.SITTING_IN_BED to DwellThreshold(warning = Duration.ofMinutes(30), exceeded = Duration.ofMinutes(45)),
)
```

The `DwellCatalog` is what the ClockSweeper actually consumes:

```kotlin
public data class DwellCatalog(
    public val byState: Map<StateKind, DwellThreshold>,
    public val heartbeatTimeout: Duration = PolicyDefaults.heartbeatTimeout,
    public val sceneThresholds: Map<String, DwellThreshold> = emptyMap(),
    public val comeBackByBaseline: Map<StateKind, DwellThreshold> = emptyMap(),
)

public fun SceneCalibration.toDwellCatalog(): DwellCatalog = DwellCatalog(
    byState = dwellThresholds.toMap(),
    heartbeatTimeout = heartbeatTimeout,
    sceneThresholds = sceneThresholds,
    comeBackByBaseline = comeBackThresholds.toMap(),
)
```

---

#### Step 5: How Sentinel Handles DwellExceeded

From `/home/visiona/workspace/mana-hive/engines/sentinel/sentinel-domain/src/main/kotlin/com/manahive/sentinel/SentinelEvaluatorImpl.kt`:

```kotlin
is SceneEvent.DwellExceeded -> {
    val result = evaluateDwellExceeded(fact, state, now)
    signals.addAll(result.signals)
    explanation.addAll(result.explanation)
    state = result.episodes
}
```

The `evaluateDwellExceeded` method:

```kotlin
private fun evaluateDwellExceeded(
    fact: SceneEvent.DwellExceeded,
    episodes: EpisodeLedger,
    now: Instant,
): EvalResult {
    val state = fact.state.kind
    val open = episodes.openForBed(fact.bed)

    if (open == null) {
        // No episode open → OPEN A NEW EPISODE using the rule for this state
        val rule = calibration.ruleFor(state)
            ?: return EvalResult(episodes = episodes)
        return openEpisode(fact.bed, rule, now, episodes)
    }

    // Episode already open → emit UmbrellaEvent (dwell is a "continuation" under the umbrella)
    val notifiable = calibration.notifiableStatesFor(open.trigger)
    val isNotifiable = state in notifiable || calibration.ruleFor(state) != null
    if (isNotifiable) {
        val signal = SentinelSignal.UmbrellaEvent(
            bed = fact.bed,
            resident = calibration.residentId,
            at = now,
            rulesFingerprint = calibration.fingerprint,
            episode = open.id,
            state = state,
            originalSeverity = open.severity,
        )
        return EvalResult(episodes = episodes, signals = listOf(signal))
    }

    return EvalResult(episodes = episodes)
}
```

---

### 6. Answering Your Key Questions

#### Q: When Jose enters BATHROOM at T=0 and stays until T=5:01 (dwell exceeded), what happens?

The sequence is:

1. **T=0**: SceneInterpreter processes observation, emits `TransitionDetected(from=STANDING, to=IN_BATHROOM)`. The DigitalTwin updates: `state = IN_BATHROOM, stateSince = T=0`. Sentinel evaluates the `TransitionDetected`. If there is a rule for `IN_BATHROOM`, an episode is opened immediately.

2. **T=0 to T=5:01**: The ClockSweeper ticks periodically. On each tick it computes `durationInState(now) = now - stateSince`. If the configured dwell threshold for `IN_BATHROOM` is, say, 5 minutes:
    - At ~4 minutes (80% of threshold): ClockSweeper emits `DwellWarning(state=IN_BATHROOM, threshold=5min, since=T=0)`
    - At T=5:01 (>=5 minutes): ClockSweeper emits `DwellExceeded(state=IN_BATHROOM, threshold=5min, since=T=0)`

3. **T=5:01**: Sentinel receives the `DwellExceeded`. If no episode is already open, it opens one using `calibration.ruleFor(IN_BATHROOM)`. If an episode was already open (from the transition at T=0), it emits an `UmbrellaEvent`.

#### Q: Does the episode start at T=0 (when he entered) or T=5:01 (when dwell was exceeded)?

**Two different things are happening:**

- **If there is a rule for `IN_BATHROOM` as a transition trigger**, the episode opens at **T=0** when the `TransitionDetected` is processed. The `DwellExceeded` at T=5:01 then becomes an `UmbrellaEvent` under that already-open episode.

- **If there is NO rule for `IN_BATHROOM` as a transition, but there IS a dwell rule for `IN_BATHROOM`**, the episode opens at **T=5:01** when the `DwellExceeded` is processed. In this case, `evaluateDwellExceeded` finds `open == null` and calls `openEpisode(fact.bed, rule, now, episodes)` with `now = T=5:01`.

**The episode's `openedAt` is always `now` (the time of the fact that triggers it), NOT the `since` field.** The `since` field in `DwellExceeded` is metadata about when the dwell started, but the episode opens at the current clock time.

From `Episode.open()`:
```kotlin
public fun open(
    bed: BedId,
    residentId: ResidentId,
    at: Instant,          // <-- this is `now` = when DwellExceeded fires
    rule: AlertRule,
): Episode {
    return Episode(
        // ...
        openedAt = at,     // <-- T=5:01, not T=0
        trigger = rule.trigger,
        // ...
    )
}
```

#### Q: What is the "safe state" for a dwell episode?

**The safe state is always `LYING`** -- regardless of whether the episode was triggered by a transition or by a dwell event. The `LYING` state is the universal safe state in the sentinel. This is visible in `evaluateUnderUmbrella`:

```kotlin
private fun evaluateUnderUmbrella(
    fact: SceneEvent.TransitionDetected,
    state: StateKind,
    open: Episode,
    episodes: EpisodeLedger,
    now: Instant,
): EvalResult {
    if (state == StateKind.LYING) {
        return handleSafeState(fact.bed, open, episodes, now)
    }
    // ...
}
```

**It is NOT the state BEFORE the dwell state.** The safe state is not contextual to the episode trigger. `LYING` is always safe. So if Jose was STANDING before entering BATHROOM, and then returns to LYING, the episode closes. But if he returns to STANDING (the pre-bathroom state), the episode stays open -- STANDING is not a safe state.

This is confirmed by test 15 in `SentinelEvaluatorSpec.kt`:

```kotlin
// Unsafe transition: IN_BATHROOM -> STANDING (not a safe state)
val unsafeFact = SceneEvent.TransitionDetected(
    bed = bed, night = NightId("night-1"), at = now.plusSeconds(60),
    from = PersonState.InBathroom,
    to = PersonState.Standing,
)

// Then("episode remains open") {
//     result.value.episodes.openForBed(bed).shouldNotBeNull()
// }
// Then("no EpisodeClosed signal") { ... }
```

#### Q: How does the recording window work for dwell episodes?

The `DwellExceeded` event can be used as a recording trigger. From `RecordingCalibration.kt`:

```kotlin
public data class DwellExceededMatcher(
    public val state: PersonState,
) : RecordingTriggerMatcher {
    override public fun matches(trigger: RecordingTrigger): Boolean {
        if (trigger !is SceneEventTrigger) return false
        val fact = trigger.fact
        if (fact !is SceneEvent.DwellExceeded) return false
        return fact.state == state
    }
}
```

And the DSL in `RecordingCalibrationDsl.kt`:

```kotlin
rule("r-bathroom-recording") {
    trigger {
        dwellExceeded(state = PersonState.InBathroom)
    }
    recordingWindow {
        before = 5.minutes   // record 5 minutes BEFORE the DwellExceeded event
        after = 10.minutes   // record 10 minutes AFTER the DwellExceeded event
    }
    quality = Quality.HD
    monitors = listOf(MonitorId("CAMERA_12A_MAIN"))
}
```

**The recording window is anchored to the `DwellExceeded.at` time (T=5:01), NOT the `since` time (T=0).** So `before=5.minutes` would start recording at T=0:01, and `after=10.minutes` would stop at T=15:01. This means the recording window captures the entire dwell period plus extra time after.

The `TransitionWindow` definition from `PolicyCalibration.kt`:
```kotlin
public data class TransitionWindow(
    val before: Duration,
    val after: Duration,
)
```

---

### 7. How ComeBack (Inverse Dwell) Works

From `SceneCalibration.kt`:

```kotlin
public val comeBackThresholds: Map<StateKind, DwellThreshold> = emptyMap(),
```

```kotlin
/**
 * Unlike normal dwell which measures time IN a state,
 * come-back measures time SINCE LEAVING a baseline state.
 */
```

The DigitalTwin tracks this via `leftStateAt`:

```kotlin
public val leftStateAt: Instant? = null,
```

From `DigitalTwin.evolve()`:

```kotlin
is TransitionDetected -> {
    val isReturningToBaseline = fact.to == baselineState
    copy(
        state = fact.to,
        stateSince = fact.at,
        // Mine planted on departure, disarmed on return
        leftStateAt = if (isReturningToBaseline) null else leftStateAt ?: fact.at,
    )
}
```

The ClockSweeper checks ComeBack in `checkComeBack`:

```kotlin
private fun checkComeBack(
    twin: DigitalTwin,
    ctx: SweepContext,
    marks: DwellMarks,
): DwellCheckResult {
    val baselineKind = twin.baselineState.kind
    val comeBackThreshold = ctx.thresholds.comeBackByBaseline[baselineKind]
        ?: return DwellCheckResult(emptyList(), emptySet())

    // Mine not planted: person IS in baseline state
    val duration = twin.durationSinceLeftBaseline(ctx.now)
        ?: return DwellCheckResult(emptyList(), emptySet())
    // ...
}
```

And `durationSinceLeftBaseline`:

```kotlin
public fun durationSinceLeftBaseline(now: Instant): Duration? {
    if (state == baselineState) return null  // Mine not planted
    val since = leftStateAt ?: return null
    return Duration.between(since, now)
}
```

The metaphor: The "mine is planted when the person LEAVES the baseline state (LYING). It explodes if they don't return within the threshold. It's disarmed if they return before it explodes."

---

### 8. How Dwell-Based Differs from Transition-Based Episodes

| Aspect | Transition-Based | Dwell-Based |
|--------|-----------------|-------------|
| **Trigger** | `SceneEvent.TransitionDetected` | `SceneEvent.DwellExceeded` |
| **Source engine** | `SceneInterpreterImpl` (per-observation) | `ClockSweeperImpl` (periodic tick) |
| **When it fires** | Immediately when observation passes all filters | After `durationInState(now) >= threshold` |
| **Episode `trigger` field** | The target `StateKind` of the transition | The `StateKind` of the prolonged state |
| **Episode `openedAt`** | The transition time | The sweep time when threshold was detected |
| **Preceding event** | `TransitionDetected` fires first | `DwellWarning` may fire at ~80% threshold first |
| **Idempotency** | Duplicate observations are filtered by SceneInterpreter | `DwellMarks` prevent duplicate DwellExceeded per (bed, state, stateSince) |
| **Closure** | Same: `LYING` = safe state, `ClosureCondition` rules apply | Same: `LYING` = safe state, `ClosureCondition` rules apply |

The critical architectural distinction: The **SceneInterpreter never emits dwell facts**. It only emits transitions. The **ClockSweeper is the sole source** of dwell facts. They are two independent observation-processing paths that both feed into the Sentinel.

From `ClockSweeper.kt`:
```kotlin
/**
 * The patrolman of silence: produces the facts only the passage of time
 * reveals. Dwells are DERIVED state (now - stateSince >= threshold), never
 * persisted timers — a process restart can neither shorten nor extend one.
 *
 * Invariant: sweep idempotency — two consecutive ticks without state change
 * emit nothing new; one DwellExceeded per (bed, state, stateSince).
 */
```

---

### 9. Idempotency: How DwellMarks Prevent Duplicate Episodes

From `DwellMarks.kt`:

```kotlin
public data class DwellMarkKey(
    public val bed: BedId,
    public val state: StateKind,
    public val since: Instant,
    public val warning: Boolean,
)
```

The mark key is `(bed, state, since, warning)`. Since `since = stateSince`, the identity is unique per dwell period. Once a `DwellExceeded` is emitted for a given `(bed, state, stateSince)`, the mark is recorded and subsequent sweeps will not emit another one for the same dwell period. This is tested in `ClockSweeperIdempotentSpec.kt`:

```kotlin
When("sweep dos veces con el mismo now") {
    val marks1 = DwellMarks(emptySet())
    val result1 = sweeper.sweep(listOf(twin), time03_05_00, catalog, marks1)
    val result2 = sweeper.sweep(listOf(twin), time03_05_00, catalog, result1.value.marks)

    Then("solo 1 DwellExceeded en total") {
        val total = result1.value.facts.filterIsInstance<DwellExceeded>().size +
                result2.value.facts.filterIsInstance<DwellExceeded>().size
        total shouldBe 1
    }
}
```

---

### 10. Summary: The Complete Data Flow

```
Sensor Observation
    |
    v
SceneInterpreter (per-observation)
    |
    +---> TransitionDetected -----> Sentinel.evaluate()
    |                                  |
    |                                  +--> Episode opened (if rule matches)
    |                                  +--> UmbrellaEvent (if episode already open)
    |                                  +--> Safe state handling (LYING = safe)
    |
    v
DigitalTwin.evolve() (stateSince updated)
    |
    v
ClockSweeper.sweep() (periodic tick)
    |
    +---> checkDwell()
    |       durationInState = now - stateSince
    |       if duration >= exceeded --> DwellExceeded
    |       if duration >= warning  --> DwellWarning (unless exceeded already fired)
    |
    +---> checkComeBack() (inverse dwell)
    |       durationSinceLeftBaseline = now - leftStateAt
    |       if duration >= exceeded --> ComeBackExceeded
    |       if duration >= warning  --> ComeBackWarning
    |
    v
Sentinel.evaluate(DwellExceeded)
    |
    +--> No open episode? --> openEpisode(rule, now)
    +--> Episode already open? --> UmbrellaEvent
```