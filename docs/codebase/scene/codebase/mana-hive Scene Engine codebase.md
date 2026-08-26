Here is a comprehensive summary of the mana-hive Scene Engine codebase.
Thoroughly explore the mana-hive Scene Engine codebase. I need to understand:

1. The actual source code structure under `../../../../engines/scene-engine` - find all Kotlin files
2. The domain model: DigitalTwin, SceneInterpreter, ClockSweeper, SceneDag, TransitionTable, SceneCalibration
3. The contracts/events: Observation, SceneFact types
4. The domain-kernel: Decider interface
5. How the scene-engine connects to other engines (Sentinel, etc.)
6. Any existing tests or specs

Search broadly across the entire project for:
- All .kt files under engines/scene-engine/
- All .kt files under platform/domain-kernel/
- All .kt files under platform/contracts/
- Any existing user stories, specs, or tests

Return a comprehensive summary of the code structure, key classes, interfaces, their relationships, and the domain model.


---

## 1. Project Structure Overview

The project is a Gradle multi-module Kotlin codebase for a clinical night-care monitoring platform. It follows Hexagonal Architecture ("Pure Domain + Thin Shell") across three layers:

```
mana-hive/
  platform/
    domain-kernel/      -- Core abstractions: Decider, Engine, Ids
    contracts/          -- Shared value types: Observation, SceneFact, SceneDag, PolicyCalibration, SentinelSignal
  engines/
    scene-engine/       -- The Scene Engine (primary focus)
      scene-domain/     -- Pure domain logic (no Spring, no IO)
      scene-service/    -- Spring Boot + NATS shell
      scene-batch/      -- CLI offline replay/verify tool
    sentinel/           -- Clinical alert evaluator (consumes SceneFacts)
    harbor/             -- Alert delivery (notifications, NVR)
    politica-engine/    -- Policy translation engine
    recorder/           -- Evidence/NVR recording
```

---

## 2. All Kotlin Files Under `../../../../engines/scene-engine`

### scene-domain/src/main/kotlin (core logic)

| Path | Purpose |
|------|---------|
| `/home/visiona/workspace/mana-hive/engines/scene-engine/scene-domain/src/main/kotlin/com/manahive/scene/core/DigitalTwin.kt` | The central aggregate -- an immutable, event-sourced value for one bed |
| `/home/visiona/workspace/mana-hive/engines/scene-engine/scene-domain/src/main/kotlin/com/manahive/scene/core/TransitionTable.kt` | Person FSM as a total table: legality + hysteresis per transition |
| `/home/visiona/workspace/mana-hive/engines/scene-engine/scene-domain/src/main/kotlin/com/manahive/scene/core/SceneDagToTransitionTable.kt` | Adapter: bridges SceneDag (graph) to TransitionTable (FSM) |
| `/home/visiona/workspace/mana-hive/engines/scene-engine/scene-domain/src/main/kotlin/com/manahive/scene/interpreter/SceneInterpreter.kt` | Interface: turns observations into scene facts |
| `/home/visiona/workspace/mana-hive/engines/scene-engine/scene-domain/src/main/kotlin/com/manahive/scene/interpreter/SceneInterpreterImpl.kt` | Implementation: confidence, illegal-transition, hysteresis, sensor recovery pipeline |
| `/home/visiona/workspace/mana-hive/engines/scene-engine/scene-domain/src/main/kotlin/com/manahive/scene/interpreter/SceneVerdict.kt` | Output value: updated twin + emitted facts |
| `/home/visiona/workspace/mana-hive/engines/scene-engine/scene-domain/src/main/kotlin/com/manahive/scene/sweeper/ClockSweeper.kt` | Interface: time-based patrol for dwells and signal loss |
| `/home/visiona/workspace/mana-hive/engines/scene-engine/scene-domain/src/main/kotlin/com/manahive/scene/sweeper/ClockSweeperImpl.kt` | Implementation: DwellWarning/DwellExceeded/SignalLost/SceneDwell detection |
| `/home/visiona/workspace/mana-hive/engines/scene-engine/scene-domain/src/main/kotlin/com/manahive/scene/sweeper/SweepResult.kt` | Output: emitted facts + updated marks |
| `/home/visiona/workspace/mana-hive/engines/scene-engine/scene-domain/src/main/kotlin/com/manahive/scene/sweeper/DwellMarks.kt` | Idempotency marks: prevent duplicate facts across consecutive ticks |
| `/home/visiona/workspace/mana-hive/engines/scene-engine/scene-domain/src/main/kotlin/com/manahive/scene/calibration/SceneCalibration.kt` | Compiled business rules: table + confidence + heartbeat + dwells + DSL |
| `/home/visiona/workspace/mana-hive/engines/scene-engine/scene-domain/src/main/kotlin/com/manahive/scene/calibration/DwellCatalog.kt` | Derived value: dwell thresholds keyed by state, consumed by ClockSweeper |
| `/home/visiona/workspace/mana-hive/engines/scene-engine/scene-domain/src/main/kotlin/com/manahive/scene/calibration/Confidence.kt` | Value Object: 0..1 confidence level |
| `/home/visiona/workspace/mana-hive/engines/scene-engine/scene-domain/src/main/kotlin/com/manahive/scene/calibration/ConfidenceThresholds.kt` | Value Object: per-state-kind confidence thresholds |
| `/home/visiona/workspace/mana-hive/engines/scene-engine/scene-domain/src/main/kotlin/com/manahive/scene/calibration/dsl/CalibrationDsl.kt` | DSL builder for SceneCalibration |
| `/home/visiona/workspace/mana-hive/engines/scene-engine/scene-domain/src/main/kotlin/com/manahive/scene/calibration/dsl/DwellCatalogDsl.kt` | DSL builder for DwellCatalog |
| `/home/visiona/workspace/mana-hive/engines/scene-engine/scene-domain/src/main/kotlin/com/manahive/scene/calibration/dsl/DwellThresholdsDsl.kt` | Shared DSL for dwell threshold maps |
| `/home/visiona/workspace/mana-hive/engines/scene-engine/scene-domain/src/main/kotlin/com/manahive/scene/calibration/dsl/StateKindDsl.kt` | Shared interface providing all StateKind constants |
| `/home/visiona/workspace/mana-hive/engines/scene-engine/scene-domain/src/main/kotlin/com/manahive/scene/calibration/dsl/TransitionTableDsl.kt` | DSL for building TransitionTable instances |
| `/home/visiona/workspace/mana-hive/engines/scene-engine/scene-domain/src/main/kotlin/com/manahive/scene/calibration/dsl/SceneDsl.kt` | @DslMarker annotation |
| `/home/visiona/workspace/mana-hive/engines/scene-engine/scene-domain/src/main/kotlin/com/manahive/scene/adapter/PolicyCalibrationAdapter.kt` | ACL adapter: converts PolicyCalibration (politica) to SceneCalibration |
| `/home/visiona/workspace/mana-hive/engines/scene-engine/scene-domain/src/main/kotlin/com/manahive/scene/config/SceneConfig.kt` | Resident configuration value object |
| `/home/visiona/workspace/mana-hive/engines/scene-engine/scene-domain/src/main/kotlin/com/manahive/scene/config/SceneConfigSource.kt` | Port: config loading contract (TOML or Hub) |
| `/home/visiona/workspace/mana-hive/engines/scene-engine/scene-domain/src/main/kotlin/com/manahive/scene/config/TomlSceneConfigSource.kt` | TOML config adapter |
| `/home/visiona/workspace/mana-hive/engines/scene-engine/scene-domain/src/main/kotlin/com/manahive/scene/config/HubSceneConfigSource.kt` | Hub config adapter |
| `/home/visiona/workspace/mana-hive/engines/scene-engine/scene-domain/src/main/kotlin/com/manahive/scene/Main.kt` | CLI playground with 8 clinical scenarios |

### scene-batch/src/main/kotlin

| Path | Purpose |
|------|---------|
| `/home/visiona/workspace/mana-hive/engines/scene-engine/scene-batch/src/main/kotlin/com/manahive/scene/batch/SceneBatchApp.kt` | Batch CLI entry point |
| `/home/visiona/workspace/mana-hive/engines/scene-engine/scene-batch/src/main/kotlin/com/manahive/scene/batch/BatchProcessor.kt` | Core processing loop: sweep + interpret per event |
| `/home/visiona/workspace/mana-hive/engines/scene-engine/scene-batch/src/main/kotlin/com/manahive/scene/batch/BatchState.kt` | Mutable state carried across batch events |
| `/home/visiona/workspace/mana-hive/engines/scene-engine/scene-batch/src/main/kotlin/com/manahive/scene/batch/BatchError.kt` | Batch error types |
| `/home/visiona/workspace/mana-hive/engines/scene-engine/scene-batch/src/main/kotlin/com/manahive/scene/batch/config/BatchConfig.kt` | Batch run configuration |
| `/home/visiona/workspace/mana-hive/engines/scene-engine/scene-batch/src/main/kotlin/com/manahive/scene/batch/config/BatchConfigLoader.kt` | TOML config loader |
| `/home/visiona/workspace/mana-hive/engines/scene-engine/scene-batch/src/main/kotlin/com/manahive/scene/batch/config/BatchDsl.kt` | Batch config DSL |
| `/home/visiona/workspace/mana-hive/engines/scene-engine/scene-batch/src/main/kotlin/com/manahive/scene/batch/config/BatchSupport.kt` | Shared utilities |
| `/home/visiona/workspace/mana-hive/engines/scene-engine/scene-batch/src/main/kotlin/com/manahive/scene/batch/events/Event.kt` | Parsed event from events.dat |
| `/home/visiona/workspace/mana-hive/engines/scene-engine/scene-batch/src/main/kotlin/com/manahive/scene/batch/events/EventParser.kt` | Event line parser |
| `/home/visiona/workspace/mana-hive/engines/scene-engine/scene-batch/src/main/kotlin/com/manahive/scene/batch/events/EventOffset.kt` | Time offset value type |
| `/home/visiona/workspace/mana-hive/engines/scene-engine/scene-batch/src/main/kotlin/com/manahive/scene/batch/output/FactsWriter.kt` | JSONL facts writer |
| `/home/visiona/workspace/mana-hive/engines/scene-engine/scene-batch/src/main/kotlin/com/manahive/scene/batch/output/FactsOutWriter.kt` | Structured facts writer |
| `/home/visiona/workspace/mana-hive/engines/scene-engine/scene-batch/src/main/kotlin/com/manahive/scene/batch/output/LogWriter.kt` | Batch log writer |
| `/home/visiona/workspace/mana-hive/engines/scene-engine/scene-batch/src/main/kotlin/com/manahive/scene/batch/commands/RunCommand.kt` | Run command |
| `/home/visiona/workspace/mana-hive/engines/scene-engine/scene-batch/src/main/kotlin/com/manahive/scene/batch/commands/VerifyCommand.kt` | Verify command (golden file comparison) |
| `/home/visiona/workspace/mana-hive/engines/scene-engine/scene-batch/src/main/kotlin/com/manahive/scene/batch/commands/DiffCommand.kt` | Diff command |

### scene-service/src/main/kotlin

| Path | Purpose |
|------|---------|
| `/home/visiona/workspace/mana-hive/engines/scene-engine/scene-service/src/main/kotlin/com/manahive/scene/service/SceneEngineApplication.kt` | Spring Boot entry point + bean wiring |
| `/home/visiona/workspace/mana-hive/engines/scene-engine/scene-service/src/main/kotlin/com/manahive/scene/service/nats/SceneNatsIngest.kt` | NATS inbound: subscribes to `perception.observation.v1.>`, maintains DigitalTwin per bed, runs sweep |
| `/home/visiona/workspace/mana-hive/engines/scene-engine/scene-service/src/main/kotlin/com/manahive/scene/service/nats/SceneNatsEgress.kt` | NATS outbound: publishes SceneFacts to `scene.fact.v1.<bed>` |

---

## 3. Domain Model

### DigitalTwin (`scene.core.DigitalTwin`)

The central aggregate. An immutable `data class` representing one bed's state at a point in time:

```kotlin
data class DigitalTwin(
    val bed: BedId,
    val night: NightId,
    val occupant: ResidentId?,
    val state: PersonState,          // 13-state FSM
    val stateSince: Instant,
    val scene: SceneState,           // orthogonal flags (staff, wheelchair, walker, bed rails)
    val sceneSince: Instant,
    val signal: SignalHealth,        // monitor heartbeat tracking
    val calibration: SceneCalibration?,
)
```

Key methods:
- `evolve(fact: SceneFact): DigitalTwin` -- pure fold: given a fact, produce an updated twin (event sourcing)
- `evolveScene(change, at)` -- separate fold for scene state changes
- `emitTransition(to, at)` -- factory for TransitionDetected facts
- `emitSceneStateChanged(...)` -- factory for SceneStateChanged facts
- `emitSignalLost(at)`, `emitSignalRecovered(at)`, `emitDwellExceeded(...)`, `emitDwellWarning(...)`

Vernon quote from source: *"In event-sourced aggregates, the entity folds its own events."*

### SceneInterpreter (`scene.interpreter.SceneInterpreter`)

Interface:
```kotlin
interface SceneInterpreter : Engine {
    fun interpret(twin: DigitalTwin, observation: Observation, now: Instant): Explained<SceneVerdict>
}
```

The implementation (`SceneInterpreterImpl`) runs a pipeline of specification checks:
1. **Confidence** -- `observation.confidence >= minConfidence[kind]`
2. **Sensor Recovery** -- if signal was lost and a new observation arrives, emit SignalRecovered
3. **Duplicate** -- same state = no-op
4. **Illegal Transition** -- `table.isLegal(from, to)`
5. **Hysteresis** -- `durationInState >= table.hysteresis(from, to)`
6. **Valid Transition** -- emit TransitionDetected

Each rejection is recorded as a `Discard(subject, DiscardCause)`.

### ClockSweeper (`scene.sweeper.ClockSweeper`)

Interface:
```kotlin
interface ClockSweeper : Engine {
    fun sweep(
        twins: Collection<DigitalTwin>,
        now: Instant,
        thresholds: DwellCatalog,
        marks: DwellMarks,
    ): Explained<SweepResult>
}
```

Produces facts that only the passage of time can reveal:
- **Person state dwells**: DwellWarning, DwellExceeded (e.g., "Standing for 5 minutes")
- **Scene state dwells**: SceneDwellWarning, SceneDwellExceeded (e.g., "Staff present for 30 minutes")
- **Signal lost**: SignalLost (heartbeat timeout exceeded)
- Uses **DwellMarks** for idempotency -- same dwell never emits twice

### TransitionTable (`scene.core.TransitionTable`)

A total table mapping `(StateKind, StateKind) -> Duration` (hysteresis):
```kotlin
data class TransitionTable(private val legal: Map<TransitionKey, Duration>) {
    fun isLegal(from: StateKind, to: StateKind): Boolean
    fun hysteresis(from: StateKind, to: StateKind): Duration
}
```

Two built-in tables:
- `RELEASE_1` -- 5-state table (LYING, BED_EDGE, STANDING, ABSENT, UNKNOWN)
- `RELEASE_2` -- 13-state clinical catalog with all 13 PersonState kinds

### SceneDag (`contracts.dag.SceneDag`)

The physical graph of person states, shared across all engines:
```kotlin
class SceneDag(
    val id: DagId,
    val nodes: Set<SceneNode>,   // each has a SceneState (LYING, IN_BED, STANDING, etc.)
    val edges: Set<SceneEdge>,   // from -> to valid transitions
    val version: DagVersion,
)
```

Features: cycle detection, O(1) successor/predecessor lookup, path finding to final states, immutable with versioning. The `SceneDagToTransitionTable` adapter converts DAG edges into TransitionTable entries.

### SceneCalibration (`scene.calibration.SceneCalibration`)

Compiled business rules for one bed:
```kotlin
data class SceneCalibration(
    val table: TransitionTable,
    val confidence: ConfidenceThresholds,
    val heartbeatTimeout: Duration,
    val dwellThresholds: Map<StateKind, DwellThreshold>,
    val sceneHysteresis: Map<String, Duration>,
    val sceneThresholds: Map<String, DwellThreshold>,
    val sceneConfidence: Map<ObservationKind, Confidence>,
)
```

Built via type-safe DSL:
```kotlin
val cal = calibration {
    table = TransitionTable.RELEASE_2
    confidence(BED_EDGE) min 0.8
    dwell { STANDING warning 4.minutes exceeded 5.minutes }
    heartbeat { timeout = 90.seconds }
}
```

---

## 4. Contracts/Events

### Observation (`contracts.perception.Observation`)

What the edge sensor claims to have seen:
```kotlin
data class Observation(
    val sourceEventId: String,
    val monitor: MonitorId,
    val bed: BedId,
    val kind: ObservationKind,
    val confidence: Double,     // 0..1
    val observedAt: Instant,
)
```

`ObservationKind` has 21 values covering person states (IN_BED, BED_EDGE, STANDING, etc.), scene states (STAFF_ENTERED, WHEELCHAIR_PRESENT, BED_RAILS_UP, etc.), and meta (HEARTBEAT, UNCLASSIFIED).

### SceneFact (`contracts.scene.SceneFact`)

The language the sentinel judges against policies. Published on `scene.fact.v1.<bed>`:

| Fact Type | Description |
|-----------|-------------|
| `NightOpened` | Opening entry for a bed's night |
| `TransitionDetected` | Person moved from one state to another |
| `DwellWarning` | Early warning (~80% of threshold) |
| `DwellExceeded` | Dwell threshold exceeded |
| `SceneStateChanged` | A scene field changed (staff, wheelchair, bed rails) |
| `SceneDwellWarning` | Scene dwell warning |
| `SceneDwellExceeded` | Scene dwell exceeded |
| `StaffPresenceDetected` | Staff presence as a fact (suppression is sentinel's job) |
| `SignalLost` | Monitor heartbeat timeout |
| `SignalRecovered` | Monitor came back alive |
| `NightClosed` | Closing-the-books with NightSummary |

### PersonState (`contracts.scene.PersonState`)

13-state sealed interface:
- **In Bed**: Lying, SittingInBed, AttemptingExit, BedEdge
- **Out of Bed**: Standing, InBathroom, InRoom, InHallway, Outdoor, Absent
- **Furniture**: InChair, InWheelchair
- **Unknown**: Unknown(cause: SIGNAL_LOST | SCENE)

### SceneState (`contracts.scene.SceneState`)

Orthogonal to PersonState -- a collection of independent flags:
- `staff`: NotPresent / Present / InReach
- `wheelchair`: NotPresent / Present
- `walker`: NotPresent / Present
- `bed.left`, `bed.right`: Down / Up / Cover (RailState)
- Serialized as 10-bit bitmask

---

## 5. Domain Kernel

### Decider (`platform/domain-kernel/Decider.kt`)

```kotlin
interface Decider<C, S, E> {
    val initial: S
    fun decide(command: C, state: S): Decision<E>
    fun evolve(state: S, event: E): S
}
```

The uniform shape for event-sourced aggregates. `DigitalTwin.evolve()` follows this pattern (though it does not implement Decider directly -- it uses SceneFact events).

### Engine (`platform/domain-kernel/Engine.kt`)

```kotlin
interface Engine {
    val version: EngineVersion
}
```

Every pure domain service extends this. Both `SceneInterpreter` and `ClockSweeper` extend `Engine`.

### Explained<T> (`platform/domain-kernel/Engine.kt`)

```kotlin
data class Explained<T>(
    val value: T,
    val explanation: List<ExplanationStep>,
    val discards: List<Discard>,
)
```

Every engine output carries its "why". Discards include: ILLEGAL_TRANSITION, CONFIDENCE_TOO_LOW, HYSTERESIS_NOT_MET, DUPLICATE, NO_OCCUPANT, STAFF_PRESENT, EPISODE_ALREADY_ALERTED, FATIGUE_BUDGET_EXCEEDED.

### DecisionRecord

Durable trace of one engine invocation for audit/debugging. Persisted by the hub, outside the domain ledger.

### Ids (`platform/domain-kernel/Ids.kt`)

Strongly-typed value classes: `BedId`, `ResidentId`, `MonitorId`, `StaffId`, `AlertId`, `RuleId`, `EpisodeId`, `NoticeId`, `NightId`, `EventRef`, `DagId`, `NodeId`.

---

## 6. How Scene Engine Connects to Other Engines

The connection is through **NATS JetStream** pub/sub with `SceneFact` as the shared event type:

```
Edge Sensors
    |
    v
[perception.observation.v1.<bed>]
    |
    v
Scene Engine (SceneInterpreter + ClockSweeper)
    | produces
    v
[scene.fact.v1.<bed>]  <-- SceneFact sealed interface
    |
    +----> Sentinel Engine (SentinelEvaluator)
    |         - Evaluates facts against AlertRules
    |         - Manages Episode lifecycle
    |         - Produces SentinelSignal (EpisodeOpened, UmbrellaEvent, AutoRecovery, EpisodeClosed, SuppressedWithRecord)
    |         - Tracks FatigueBudget
    |
    +----> Harbor Engine (NoticeRouter)
    |         - Delivers notifications to staff
    |         - Manages NVR recording triggers
    |
    +----> Recorder Engine
              - Manages evidence recording
```

**Scene -> Sentinel connection** (the critical path):
- Scene Engine publishes `SceneFact` to `scene.fact.v1.<bed>`
- Sentinel Engine subscribes to this stream
- `SentinelEvaluator.evaluate(fact: SceneFact, episodes: EpisodeLedger, now: Instant): Explained<SentinelVerdict>`
- Handles: `TransitionDetected`, `StaffPresenceDetected`, `DwellExceeded`
- Other fact types (SignalLost, DwellWarning, etc.) are ignored by sentinel

**Scene <- Politica Engine connection** (calibration):
- Politica Engine produces `PolicyCalibration` (hysteresis overrides, dwell thresholds, confidence config)
- Scene Engine receives it via `PolicyCalibrationAdapter.toSceneCalibration()` -- an Anti-Corruption Layer
- Per-resident calibration can override the default TransitionTable

**Scene <- Hub connection** (configuration + census):
- `SceneConfigSource` loads per-resident config (TOML or Hub API)
- Hub provides `CensusSnapshot` for occupant binding
- Scene Engine pushes `DecisionRecord` to Hub for audit

---

## 7. Tests and Specs

All tests use **Kotest** (BehaviorSpec style) with Spanish-language scenario names (Given/When/Then).

### scene-domain tests (24 spec files)

| Spec | What it tests |
|------|---------------|
| `DigitalTwinEvolutionSpec` | Twin fold: TransitionDetected, SignalLost, SignalRecovered, DwellWarning |
| `DigitalTwinWithCalibrationSpec` | Twin with per-bed calibration |
| `SceneDagToTransitionTableSpec` | DAG-to-FSM conversion |
| `TransitionTableThirteenSpec` | 13-state DSL table: all legal/illegal transitions |
| `SceneInterpreterTransitionSpec` | SE-7: valid transition produces TransitionDetected |
| `SceneInterpreterIllegalSpec` | SE-5: illegal transition rejected (ILLEGAL_TRANSITION) |
| `SceneInterpreterHysteresisSpec` | SE-6: hysteresis blocks early transitions |
| `SceneInterpreterConfidenceSpec` | SE-3: confidence below threshold rejected |
| `SceneInterpreterDuplicateSpec` | Duplicate observation is no-op |
| `SceneInterpreterPerResidentSpec` | Per-resident calibration |
| `SceneInterpreterSensorRecoverySpec` | SignalLost -> SignalRecovered on new observation |
| `SceneInterpreterIllegalSpec` | Illegal transition handling |
| `PersonStateElevenSpec` | PersonState enum coverage |
| `ObservationKindMappingSpec` | ObservationKind -> PersonState mapping |
| `ClockSweeperExceededSpec` | SE-10: dwell threshold exceeded detection |
| `ClockSweeperWarningSpec` | Dwell warning detection |
| `ClockSweeperIdempotentSpec` | Idempotency marks prevent duplicate facts |
| `ClockSweeperSignalLostSpec` | Heartbeat timeout -> SignalLost |
| `SceneCalibrationReceivedSpec` | SE-14: PolicyCalibration from Politica applied correctly |
| `DwellCatalogSpec` | DwellCatalog derivation from SceneCalibration |
| `SceneConfigSpec` | SceneConfig validation |
| `TomlSceneConfigSourceSpec` | TOML loading |
| `PoliticaToSceneIntegrationSpec` | End-to-end: Politica -> Scene calibration |
| `LaCaidaDeLas03Spec` | Integration: the 03:00 fall scenario |

### scene-batch test

| Spec | What it tests |
|------|---------------|
| `SceneBatchIntegrationSpec` | Full batch pipeline |

### platform/contracts tests (4 spec files)

| Spec | What it tests |
|------|---------------|
| `SceneDagSpec` | DAG creation, cycle detection, path finding |
| `SceneNodeEdgeSpec` | Node/edge validation |
| `DagVersionSpec` | Version incrementing |
| `InMemoryDagStoreSpec` | In-memory store operations |

### Test Support DSL

`SceneTestDsl` provides a fluent builder:
```kotlin
val twin = bed(3) occupiedBy maria at LYING since time03_00_00
val obs = obs(BED_EDGE, 0.9) at time03_00_02
```

`SceneInterpreterDsl` provides `buildCalibration {}` and `buildTwin {}` helpers.

---

## 8. Key Design Patterns

| Pattern | Where | Ref |
|---------|-------|-----|
| **Event Sourcing** | DigitalTwin.evolve() folds SceneFacts | Vernon |
| **Specification Pattern** | Confidence, LegalTransition, Hysteresis checks | Vernon |
| **Anti-Corruption Layer** | PolicyCalibrationAdapter bridges politica -> scene | Vernon |
| **Pure Domain + Thin Shell** | scene-domain (pure) vs scene-service (Spring) | Vernon |
| **Strategy Pattern** | TransitionTable is interchangeable | Gamma |
| **Data Clumps** | DwellThreshold bundles warning + exceeded | Fowler |
| **Value Objects** | Confidence, BedId, PersonState, SceneState | Evans |
| **Type-Safe Builder/DSL** | sceneCalibration {}, transitionTable {} | Kotlin idiomatic |
| **Idempotency via Marks** | DwellMarks prevent duplicate sweep facts | Fowler |
| **Domain Events** | SceneFact subtypes as immutable facts | Vernon |
| **Extract Method/Parameter Object** | SweepContext, BatchWriters, DwellThresholdConfig | Fowler |