Now I have a complete picture. Here is the thorough exploration report:

---

# Scene Engine Codebase — Complete Exploration Report

## 1. CONTRACTS (`../../../../platform/contracts`)

### 1.1 Scene Domain Contracts

**`/home/visiona/workspace/mana-hive/platform/contracts/src/main/kotlin/com/manahive/contracts/scene/SceneFact.kt`**
- **Description**: The sealed interface of all facts the scene engine can produce. Published on `scene.fact.v1.<bed>`.
- **Key types**:
  - `SceneFact` (sealed interface) — fields: `bed: BedId`, `night: NightId`, `at: Instant`
  - `NightOpened` — opening entry with `occupant`, `initialState`, `stateSince`
  - `TransitionDetected` — `from: PersonState`, `to: PersonState`
  - `DwellWarning` — early warning at ~80% of threshold: `state`, `threshold`, `since`
  - `DwellExceeded` — threshold exceeded: `state`, `threshold`, `since`
  - `SceneStateChanged` — scene field changed: `field`, `from`, `to`
  - `SceneDwellWarning` / `SceneDwellExceeded` — scene-level dwell facts
  - `StaffPresenceDetected` — `staff: StaffId?`
  - `SignalLost` — `monitor: MonitorId`, `lastHeartbeat: Instant`
  - `SignalRecovered` — `monitor: MonitorId`
  - `NightClosed` — `summary: NightSummary`
  - `NightSummary` — `transitions: Int`, `minutesUnknown: Long`, `episodes: Int`

**`/home/visiona/workspace/mana-hive/platform/contracts/src/main/kotlin/com/manahive/contracts/scene/PersonState.kt`**
- **Description**: The 13-state person FSM as a sealed interface.
- **Key types**:
  - `PersonState` (sealed interface) — 13 states: `Lying`, `SittingInBed`, `AttemptingExit`, `BedEdge`, `Standing`, `InBathroom`, `InRoom`, `InHallway`, `Outdoor`, `Absent`, `InChair`, `InWheelchair`, `Unknown(cause: UnknownCause)`
  - `UnknownCause` (enum) — `SIGNAL_LOST`, `SCENE`
  - `StateKind` (enum) — 13 values mirroring PersonState for stable map keys
  - `RiskGroup` (enum) — `SAFE`, `AT_RISK`, `UNKNOWN`
  - Extension: `PersonState.kind: StateKind`, `PersonState.riskGroup: RiskGroup`
  - Function: `personStateFromKind(kind: StateKind): PersonState`

**`/home/visiona/workspace/mana-hive/platform/contracts/src/main/kotlin/com/manahive/contracts/scene/SceneState.kt`**
- **Description**: Orthogonal dimension to PersonState — independent flags for environment (staff, wheelchair, walker, bed rails). Bitmask representation (10 bits).
- **Key types**:
  - `SceneState` — `staff: PresenceState`, `staffSince: Instant?`, `wheelchair: PresenceState`, `walker: PresenceState`, `bed: BedState`
  - `PresenceState` (sealed) — `NotPresent`, `Present`, `InReach`
  - `RailState` (sealed) — `Down`, `Up`, `Cover`
  - `BedState` — `left: RailState`, `right: RailState`
  - `SceneFieldChange` — `field: String`, `from: Any`, `to: Any`
  - DSL: `sceneState { ... }` builder

**`/home/visiona/workspace/mana-hive/platform/contracts/src/main/kotlin/com/manahive/contracts/scene/ObservationKindMapping.kt`**
- **Description**: Anti-Corruption Layer between perception and scene bounded contexts.
- **Key functions**:
  - `ObservationKind.toPersonState(): PersonState` — maps all 24 ObservationKinds to PersonState
  - `ObservationKind.toSceneStateChange(): ((SceneState) -> SceneState)?` — maps scene events to state transformations

**`/home/visiona/workspace/mana-hive/platform/contracts/src/main/kotlin/com/manahive/contracts/perception/Observation.kt`**
- **Description**: What the edge (ia-cell) claims to have seen. Published on `perception.observation.v1.<bed>`.
- **Key types**:
  - `Observation` — `sourceEventId: String`, `monitor: MonitorId`, `bed: BedId`, `kind: ObservationKind`, `confidence: Double` (0.0..1.0), `observedAt: Instant`
  - `ObservationKind` (enum) — 24 values: IN_BED, SITTING_IN_BED, ATTEMPTING_EXIT, BED_EDGE, STANDING, IN_BATHROOM, IN_ROOM, IN_HALLWAY, OUTDOOR, IN_CHAIR, IN_WHEELCHAIR, STAFF_ENTERED, STAFF_LEFT, STAFF_IN_REACH, WHEELCHAIR_PRESENT, WHEELCHAIR_ABSENT, WALKER_PRESENT, WALKER_ABSENT, BED_RAILS_UP, BED_RAILS_DOWN, COVER_ON, COVER_OFF, OUT_OF_ROOM, STAFF_IN_ROOM, HEARTBEAT, UNCLASSIFIED

### 1.2 DAG Contracts

**`/home/visiona/workspace/mana-hive/platform/contracts/src/main/kotlin/com/manahive/contracts/dag/SceneDag.kt`**
- **Description**: The physical graph of person states — aggregate root. Precomputed adjacency maps for O(1) lookups. Enforces acyclicity.
- **Key types**:
  - `SceneDag` — `id: DagId`, `nodes: Set<SceneNode>`, `edges: Set<SceneEdge>`, `version: DagVersion`
  - Methods: `nodeById()`, `successors()`, `predecessors()`, `initials()`, `finals()`, `isInitial()`, `isFinal()`, `isValidTransition()`, `pathsToFinal()`, `addNode()`, `addEdge()`, `withVersion()`

**`/home/visiona/workspace/mana-hive/platform/contracts/src/main/kotlin/com/manahive/contracts/dag/SceneNode.kt`**
- **Description**: Value Object — a node in the Scene DAG.
- **Key types**: `SceneNode(id: NodeId, state: SceneState)`, `SceneState` (enum: LYING, IN_BED, SITTING_IN_BED, STANDING, WALKING, IN_BATHROOM, IN_HALLWAY, ON_FLOOR)

**`/home/visiona/workspace/mana-hive/platform/contracts/src/main/kotlin/com/manahive/contracts/dag/SceneEdge.kt`**
- **Description**: Value Object — an edge (valid transition). Rejects self-loops.
- **Key types**: `SceneEdge(from: NodeId, to: NodeId)`

**`/home/visiona/workspace/mana-hive/platform/contracts/src/main/kotlin/com/manahive/contracts/dag/DagStore.kt`**
- **Description**: Port for DAG storage — Repository pattern.
- **Key types**: `DagStore` interface — `store()`, `storeIfVersion()`, `load()`, `exists()`, `delete()`, `subscribe()`, `unsubscribe()`

**`/home/visiona/workspace/mana-hive/platform/contracts/src/main/kotlin/com/manahive/contracts/dag/DagVersion.kt`**
- **Description**: Value Object — monotonically increasing version for optimistic concurrency.
- **Key types**: `DagVersion(value: Int)` — inline value class, `next()` method

**`/home/visiona/workspace/mana-hive/platform/contracts/src/main/kotlin/com/manahive/contracts/dag/DagChange.kt`**
- **Description**: Domain Event — sealed hierarchy for DAG changes.
- **Key types**: `DagChange` (sealed) — `Updated(dag: SceneDag)`, `Deleted(dagId: DagId)`

**`/home/visiona/workspace/mana-hive/platform/contracts/src/main/kotlin/com/manahive/contracts/dag/SceneDagSource.kt`**
- **Description**: Port for loading DAG from different sources (TOML or Hub).
- **Key types**: `SceneDagSource` interface — `load()`, `subscribe()`, `unsubscribe()`

**`/home/visiona/workspace/mana-hive/platform/contracts/src/main/kotlin/com/manahive/contracts/dag/InMemoryDagStore.kt`**
- **Description**: Thread-safe in-memory implementation of DagStore. Uses ConcurrentHashMap + ReentrantLock.
- **Key types**: `InMemoryDagStore` class

**`/home/visiona/workspace/mana-hive/platform/contracts/src/main/kotlin/com/manahive/contracts/dag/DagNotFoundException.kt`**
- **Description**: RuntimeException for missing DAGs.
- **Key types**: `DagNotFoundException(dagId: DagId)`

### 1.3 Policy Contracts

**`/home/visiona/workspace/mana-hive/platform/contracts/src/main/kotlin/com/manahive/contracts/policy/PolicyCalibration.kt`**
- **Description**: The calibration that Politica Engine produces for Scene Engine — the CONTRACT between engines.
- **Key types**:
  - `PolicyCalibration` — `residentId: ResidentId`, `hysteresis: Map<TransitionKey, Duration>`, `dwellThresholds: Map<StateKind, DwellThreshold>`, `confidence: ConfidenceConfig`
  - `ConfidenceConfig` — `minConfidence: Map<StateKind, Double>`, `heartbeatTimeout: Duration`
  - `TransitionKey(from: StateKind, to: StateKind)` — data class for map keys
  - `DwellThreshold(warning: Duration, exceeded: Duration)` — invariant: warning < exceeded

---

## 2. SCENE DOMAIN (`../../../../engines/scene-engine/scene-domain`)

### 2.1 Core

**`/home/visiona/workspace/mana-hive/engines/scene-engine/scene-domain/src/main/kotlin/com/manahive/scene/core/DigitalTwin.kt`**
- **Description**: The aggregate — living model of one bed. Immutable data class, evolves by folding SceneFacts.
- **Key types**:
  - `DigitalTwin` — `bed: BedId`, `night: NightId`, `occupant: ResidentId?`, `state: PersonState`, `stateSince: Instant`, `scene: SceneState`, `sceneSince: Instant`, `signal: SignalHealth`, `calibration: SceneCalibration?`
  - `SignalHealth` — `monitor: MonitorId`, `lastHeartbeat: Instant`, `lost: Boolean`
  - Methods: `evolve(fact)`, `evolveScene(change, at)`, `durationInState()`, `durationInSceneState()`, `toDwellMarkKey()`, `emitTransition()`, `emitSceneStateChanged()`, `emitSignalRecovered()`, `emitDwellExceeded()`, `emitDwellWarning()`, `emitSignalLost()`

**`/home/visiona/workspace/mana-hive/engines/scene-engine/scene-domain/src/main/kotlin/com/manahive/scene/core/TransitionTable.kt`**
- **Description**: The person FSM as a TOTAL table — legality plus minimum hysteresis per transition.
- **Key types**:
  - `TransitionTable(legal: Map<TransitionKey, Duration>)` — `isLegal()`, `hysteresis()`
  - Constants: `RELEASE_1` (5-state), `RELEASE_2` (13-state clinical catalog)
  - Factory: `TransitionTable.from(base, overrides)`

**`/home/visiona/workspace/mana-hive/engines/scene-engine/scene-domain/src/main/kotlin/com/manahive/scene/core/SceneDagToTransitionTable.kt`**
- **Description**: Adapter — converts SceneDag (graph model) to TransitionTable (FSM model).
- **Key types**: `SceneDagToTransitionTable` object — `convert()`, `isValidTransition()`, `isSafeState()`

### 2.2 Interpreter

**`/home/visiona/workspace/mana-hive/engines/scene-engine/scene-domain/src/main/kotlin/com/manahive/scene/interpreter/SceneInterpreter.kt`**
- **Description**: Interface — turns noisy observations into credible scene facts over one bed's digital twin.
- **Key types**: `SceneInterpreter` interface (extends `Engine`) — `interpret(twin, observation, now): Explained<SceneVerdict>`

**`/home/visiona/workspace/mana-hive/engines/scene-engine/scene-domain/src/main/kotlin/com/manahive/scene/interpreter/SceneInterpreterImpl.kt`**
- **Description**: The brain — implements the 6-step interpretation pipeline:
  1. CONFIANZA (confidence check)
  2. RECUPERACION DE SENSOR (sensor recovery)
  3. DUPLICADO (idempotency check)
  4. TRANSICION ILEGAL (legal transition check)
  5. HYSTERESIS (temporal specification)
  6. TRANSICION VALIDA (emit domain event)
- Also handles scene state events (orthogonal to person state).

**`/home/visiona/workspace/mana-hive/engines/scene-engine/scene-domain/src/main/kotlin/com/manahive/scene/interpreter/SceneVerdict.kt`**
- **Description**: Result of interpretation.
- **Key types**: `SceneVerdict(twin: DigitalTwin, facts: List<SceneFact>)`

### 2.3 ClockSweeper

**`/home/visiona/workspace/mana-hive/engines/scene-engine/scene-domain/src/main/kotlin/com/manahive/scene/sweeper/ClockSweeper.kt`**
- **Description**: Interface — the patrolman of silence. Produces facts only the passage of time reveals.
- **Key types**: `ClockSweeper` interface (extends `Engine`) — `sweep(twins, now, thresholds, marks): Explained<SweepResult>`

**`/home/visiona/workspace/mana-hive/engines/scene-engine/scene-domain/src/main/kotlin/com/manahive/scene/sweeper/ClockSweeperImpl.kt`**
- **Description**: Pure domain implementation. Checks person state dwell, scene state dwell, and signal lost.
- Uses `DwellThresholdConfig` parameter object for generic threshold checking.

**`/home/visiona/workspace/mana-hive/engines/scene-engine/scene-domain/src/main/kotlin/com/manahive/scene/sweeper/DwellMarks.kt`**
- **Description**: Idempotency marks — prevents duplicate facts across consecutive ticks.
- **Key types**: `DwellMarks`, `DwellMarkKey(bed, state, since, warning)`, `SceneDwellMarks`, `SceneDwellMarkKey(bed, field, since, warning)`, `AllDwellMarks`

**`/home/visiona/workspace/mana-hive/engines/scene-engine/scene-domain/src/main/kotlin/com/manahive/scene/sweeper/SweepResult.kt`**
- **Description**: Result of a sweep.
- **Key types**: `SweepResult(facts: List<SceneFact>, marks: DwellMarks)`

### 2.4 Calibration

**`/home/visiona/workspace/mana-hive/engines/scene-engine/scene-domain/src/main/kotlin/com/manahive/scene/calibration/SceneCalibration.kt`**
- **Description**: Compiled business rules for one bed's scene engine. Includes DSL builders.
- **Key types**: `SceneCalibration` — `table: TransitionTable`, `confidence: ConfidenceThresholds`, `heartbeatTimeout: Duration`, `dwellThresholds`, `sceneHysteresis`, `sceneThresholds`, `sceneConfidence`
- DSL: `sceneCalibration { ... }` builder

**`/home/visiona/workspace/mana-hive/engines/scene-engine/scene-domain/src/main/kotlin/com/manahive/scene/calibration/DwellCatalog.kt`**
- **Description**: Dwell thresholds keyed by state — derived from SceneCalibration.
- **Key types**: `DwellCatalog(byState, heartbeatTimeout, sceneThresholds)`, extension: `SceneCalibration.toDwellCatalog()`

**`/home/visiona/workspace/mana-hive/engines/scene-engine/scene-domain/src/main/kotlin/com/manahive/scene/calibration/Confidence.kt`**
- **Description**: Value Object — confidence level wrapper (0.0..1.0).
- **Key types**: `Confidence(value: Double)` — inline value class

**`/home/visiona/workspace/mana-hive/engines/scene-engine/scene-domain/src/main/kotlin/com/manahive/scene/calibration/ConfidenceThresholds.kt`**
- **Description**: Value Object — confidence thresholds per state kind with default fallback.
- **Key types**: `ConfidenceThresholds(byState, default)` — `forState(kind)`

### 2.5 Calibration DSL

**`/home/visiona/workspace/mana-hive/engines/scene-engine/scene-domain/src/main/kotlin/com/manahive/scene/calibration/dsl/CalibrationDsl.kt`**
- **Description**: DSL for building SceneCalibration. Entry point: `calibration { ... }`

**`/home/visiona/workspace/mana-hive/engines/scene-engine/scene-domain/src/main/kotlin/com/manahive/scene/calibration/dsl/TransitionTableDsl.kt`**
- **Description**: DSL for building TransitionTable. Entry point: `transitionTable { from(X) { to(Y) after duration } }`

**`/home/visiona/workspace/mana-hive/engines/scene-engine/scene-domain/src/main/kotlin/com/manahive/scene/calibration/dsl/DwellThresholdsDsl.kt`**
- **Description**: Shared DSL for dwell thresholds. `STANDING warning 4.minutes exceeded 5.minutes`

**`/home/visiona/workspace/mana-hive/engines/scene-engine/scene-domain/src/main/kotlin/com/manahive/scene/calibration/dsl/DwellCatalogDsl.kt`**
- **Description**: DSL for building DwellCatalog. Entry point: `dwellCatalog { ... }`

**`/home/visiona/workspace/mana-hive/engines/scene-engine/scene-domain/src/main/kotlin/com/manahive/scene/calibration/dsl/StateKindDsl.kt`**
- **Description**: Shared DSL interface providing all 13 StateKind properties.

**`/home/visiona/workspace/mana-hive/engines/scene-engine/scene-domain/src/main/kotlin/com/manahive/scene/calibration/dsl/SceneDsl.kt`**
- **Description**: DslMarker annotation for scene engine builders.

### 2.6 Adapter

**`/home/visiona/workspace/mana-hive/engines/scene-engine/scene-domain/src/main/kotlin/com/manahive/scene/adapter/PolicyCalibrationAdapter.kt`**
- **Description**: Converts PolicyCalibration (from Politica Engine) to SceneCalibration (for Scene Engine). Extension function: `PolicyCalibration.toSceneCalibration(base)`

### 2.7 Config

**`/home/visiona/workspace/mana-hive/engines/scene-engine/scene-domain/src/main/kotlin/com/manahive/scene/config/SceneConfig.kt`**
- **Description**: Scene Engine configuration for a resident.
- **Key types**: `SceneConfig(residentId, name, bed, heartbeatTimeout, dwellThresholds, confidence)`

**`/home/visiona/workspace/mana-hive/engines/scene-engine/scene-domain/src/main/kotlin/com/manahive/scene/config/SceneConfigSource.kt`**
- **Description**: Port — extends ResidentConfigSource with Scene-specific functionality.

**`/home/visiona/workspace/mana-hive/engines/scene-engine/scene-domain/src/main/kotlin/com/manahive/scene/config/TomlSceneConfigSource.kt`**
- **Description**: TOML-based config source adapter. Loads from `/etc/mana-hive/residents/`.

**`/home/visiona/workspace/mana-hive/engines/scene-engine/scene-domain/src/main/kotlin/com/manahive/scene/config/HubSceneConfigSource.kt`**
- **Description**: Hub-based config source adapter (placeholder — not yet implemented).

**`/home/visiona/workspace/mana-hive/engines/scene-engine/scene-domain/src/main/kotlin/com/manahive/scene/Main.kt`**
- **Description**: Playground — manual test of SceneInterpreter with 8 clinical scenarios.

---

## 3. TESTS (BDD-style, Kotest)

### 3.1 scene-domain Tests (20 spec files)

**Core Tests:**

| File | Style | Scenarios |
|------|-------|-----------|
| `DigitalTwinEvolutionSpec.kt` | BehaviorSpec | SE — Twin evolves with TransitionDetected, SignalLost, SignalRecovered, DwellWarning (no-op) |
| `DigitalTwinWithCalibrationSpec.kt` | BehaviorSpec | SE-15 — Twin includes calibration, calibration preserved after evolution |
| `TransitionTableThirteenSpec.kt` | BehaviorSpec | SE-19 — DSL-built 13-state table: all bed/out-of-bed/furniture/unknown transitions legal, illegal transitions fail, hysteresis values correct |
| `SceneDagToTransitionTableSpec.kt` | DescribeSpec | DAG→TransitionTable conversion, illegal transitions rejected, safe state check, default hysteresis |

**Interpreter Tests:**

| File | Style | Scenarios |
|------|-------|-----------|
| `SceneInterpreterTransitionSpec.kt` | BehaviorSpec | SE-7 — Valid transition: LYING→BED_EDGE produces TransitionDetected, correct stateSince, explanation contains "transition-table" |
| `SceneInterpreterSensorRecoverySpec.kt` | BehaviorSpec | SE-8 — Sensor recovery: signal.lost=true + same state → SignalRecovered + DUPLICATE; signal.lost=true + different state → SignalRecovered + TransitionDetected |
| `SceneInterpreterPerResidentSpec.kt` | BehaviorSpec | SE-16 — Per-resident calibration: María (0.9) rejects confidence 0.8, José (0.7) accepts it |
| `SceneInterpreterIllegalSpec.kt` | BehaviorSpec | SE-5 — Illegal transition: LYING→ABSENT rejected as ILLEGAL_TRANSITION |
| `SceneInterpreterHysteresisSpec.kt` | BehaviorSpec | SE-6 — Hysteresis: 1s too fast (HYSTERESIS_NOT_MET), 2s OK (transition accepted) |
| `SceneInterpreterDuplicateSpec.kt` | BehaviorSpec | SE-4 — Duplicate: same state → DUPLICATE discard |
| `SceneInterpreterConfidenceSpec.kt` | BehaviorSpec | SE-3 — Confidence: 0.7 < 0.8 → CONFIDENCE_TOO_LOW; 0.9 >= 0.8 → accepted |
| `PersonStateElevenSpec.kt` | BehaviorSpec | SE-17 — All 13 states supported: LYING→SITTING_IN_BED, ATTEMPTING_EXIT, BED_EDGE, STANDING→IN_BATHROOM/IN_ROOM/IN_HALLWAY/OUTDOOR/IN_CHAIR/IN_WHEELCHAIR/ABSENT |
| `ObservationKindMappingSpec.kt` | BehaviorSpec | ObservationKind→PersonState mapping: IN_BED→Lying, BED_EDGE→BedEdge, STANDING→Standing, OUT_OF_ROOM→Absent, HEARTBEAT→Lying, UNCLASSIFIED→Unknown(SCENE) |

**Sweeper Tests:**

| File | Style | Scenarios |
|------|-------|-----------|
| `ClockSweeperExceededSpec.kt` | BehaviorSpec | SE-10 — DwellExceeded: 5min standing → exceeded; 4min → not exceeded |
| `ClockSweeperIdempotentSpec.kt` | BehaviorSpec | SE-11 — Idempotency: two sweeps at same time → only 1 DwellExceeded |
| `ClockSweeperWarningSpec.kt` | BehaviorSpec | SE-9 — DwellWarning: 4min standing → warning; 3min → no warning |
| `ClockSweeperSignalLostSpec.kt` | BehaviorSpec | SE-12 — SignalLost: 2min since heartbeat (>90s timeout) → SignalLost; 30s → no SignalLost |

**Calibration Tests:**

| File | Style | Scenarios |
|------|-------|-----------|
| `SceneCalibrationReceivedSpec.kt` | BehaviorSpec | SE-14 — Calibration from Politica: low risk (0.7) accepts 0.8, high risk (0.9) rejects 0.8 |
| `DwellCatalogSpec.kt` | BehaviorSpec | SE-18+SE-20 — Per-resident dwell catalogs: María (3min warning) vs José (2min warning), fallback to default, CalibrationChanged regeneration |

**Adapter Tests:**

| File | Style | Scenarios |
|------|-------|-----------|
| `PoliticaToSceneIntegrationSpec.kt` | BehaviorSpec | Integration: PolicyCalibration→SceneCalibration conversion, interpreter uses converted calibration, two residents with different calibrations |

**Integration Tests:**

| File | Style | Scenarios |
|------|-------|-----------|
| `LaCaidaDeLas03Spec.kt` | BehaviorSpec | SE-13 — End-to-end "La caída de las 03:00": María LYING→BED_EDGE→STANDING, sweeper runs 5 minutes, produces 4 facts (2 transitions + DwellExceeded) |

**Config Tests:**

| File | Style | Scenarios |
|------|-------|-----------|
| `TomlSceneConfigSourceSpec.kt` | DescribeSpec | Load from TOML, loadAll, exists, reload, subscribe callback |
| `SceneConfigSpec.kt` | DescribeSpec | Valid creation, rejects blank fields, rejects negative timeout, rejects out-of-range confidence, default values |

**Test Support:**

| File | Description |
|------|-------------|
| `SceneTestDsl.kt` | Test DSL: `bed(3) occupiedBy maria at LYING since time`, `obs(BED_EDGE, 0.9) at time`, constants for times/residents |
| `SceneInterpreterDsl.kt` | `buildCalibration { ... }`, `buildTint { ... }` builders |

### 3.2 scene-batch Tests (1 spec file)

**`/home/visiona/workspace/mana-hive/engines/scene-engine/scene-batch/src/test/kotlin/com/manahive/scene/batch/SceneBatchIntegrationSpec.kt`**
- **Style**: BehaviorSpec
- **Scenarios**:
  - Given a valid scenario config → When running batch → Then produces facts.out, facts.jsonl, engine.log
  - Given a matching expected.out → When running diff → Then all match
  - Given a non-matching expected.out → When running diff → Then throws DiffFound
  - Given a missing config file → When running batch → Then throws ConfigNotFound
  - Given a missing events file → When running batch → Then throws EventsNotFound

### 3.3 Contract Tests (5 spec files)

| File | Style | Scenarios |
|------|-------|-----------|
| `SceneDagSpec.kt` | DescribeSpec | Create DAG, find successors/predecessors/initials/finals, check initial/final, valid transition, paths to final, reject cycles, reject bad edges, add node/edge, withVersion, empty DAG, single-node DAG, diamond graph, duplicate node ID, equals/hashCode |
| `SceneNodeEdgeSpec.kt` | DescribeSpec | SceneNode creation/equality, SceneEdge creation/reject self-loops |
| `InMemoryDagStoreSpec.kt` | DescribeSpec | Store/load, null for missing, exists, delete, subscribe/unsubscribe, storeIfVersion, reject version mismatch |
| `DagVersionSpec.kt` | DescribeSpec | Create, increment, reject zero/negative, toString, reject overflow |
| `PolicyPayloadDslSpec.kt` | DescribeSpec | buildCalibrationPayload, buildResponsePayload, buildEscalationPayload, buildRecordingPayload — fluent builders with validation |

---

## 4. DATA FILES (Golden Test Fixtures)

### 4.1 `events.dat` files

**`/home/visiona/workspace/mana-hive/engines/scene-engine/scene-batch/src/main/resources/scenarios/fall-at-03/events.dat`** (402 bytes, UTF-8 text)
```
# Scenario: Fall at 03:00
# María se levanta de la cama a las 03:00, cae en el pasillo.
# El monitor detecta la secuencia: Lying → BedEdge → Standing → InHallway → Absent
t=0s    OBS IN_BED confidence=0.95
t=2s    OBS BED_EDGE confidence=0.92
t=4s    OBS STANDING confidence=0.90
t=4m0s  OBS STANDING confidence=0.95
t=6s    OBS IN_HALLWAY confidence=0.88
t=8s    OBS OUTDOOR confidence=0.85
```

**`/home/visiona/workspace/mana-hive/engines/scene-engine/scene-batch/src/test/resources/test-scenario/events.dat`** (UTF-8 text)
```
# Test scenario: simple transition
t=0s    OBS IN_BED confidence=0.95
t=2s    OBS BED_EDGE confidence=0.92
t=4s    OBS STANDING confidence=0.90
```

### 4.2 `expected.out` file

**`/home/visiona/workspace/mana-hive/engines/scene-engine/scene-batch/src/main/resources/scenarios/fall-at-03/expected.out`**
```
t=2s      TRANSITION LYING → BED_EDGE                       # ← evento 6
t=4s      TRANSITION BED_EDGE → STANDING                    # ← evento 7
t=4m      SIGNAL_LOST monitor=m1                            # ← evento 8
t=6s      TRANSITION STANDING → IN_HALLWAY                  # ← evento 9
t=8s      TRANSITION IN_HALLWAY → OUTDOOR                   # ← evento 10
```

### 4.3 `facts.out` file (actual output)

**`/home/visiona/workspace/mana-hive/engines/scene-engine/scene-batch/src/main/resources/scenarios/fall-at-03/output/facts.out`**
- Contains 20 lines (4 repetitions of the 5 expected facts — from multiple batch runs)

### 4.4 `facts.jsonl` file

**`/home/visiona/workspace/mana-hive/engines/scene-engine/scene-batch/src/main/resources/scenarios/fall-at-03/output/facts.jsonl`**
- 180 lines of JSON objects — each fact as a self-contained JSON line with fields: `t`, `event`, `type`, `bed`, `night`, `from`/`to`/`monitor`/`lastHeartbeat`

### 4.5 `engine.log` file

**`/home/visiona/workspace/mana-hive/engines/scene-engine/scene-batch/src/main/resources/scenarios/fall-at-03/output/engine.log`**
- 288 lines of structured JSON logs showing batch run lifecycle: started → observation discarded (DUPLICATE) → interpreter facts → sweeper facts → completed (4 passed, 2 discarded, 6 total)

### 4.6 `run.yaml` config files

**`/home/visiona/workspace/mana-hive/engines/scene-engine/scene-batch/src/main/resources/scenarios/fall-at-03/run.yaml`**
```yaml
scene:
  bed: "bed-3"
  night: "night-1"
  resident: "maria"
  monitor: "m1"
calibration:
  transitions: RELEASE_2
  confidence:
    BED_EDGE: 0.8
    STANDING: 0.7
    ATTEMPTING_EXIT: 0.85
    IN_BATHROOM: 0.75
  dwell:
    STANDING: { warning: 4m, exceeded: 5m }
    IN_BATHROOM: { warning: 3m, exceeded: 4m }
    BED_EDGE: { warning: 1m, exceeded: 2m }
  heartbeat:
    timeout: 90s
events:
  source: "events.dat"
  output: "output"
  start: "2024-01-01T03:00:00Z"
```

**`/home/visiona/workspace/mana-hive/engines/scene-engine/scene-batch/src/test/resources/test-scenario/run.yaml`**
- Simplified version with bed-1, RELEASE_2, minimal confidence/dwell config.

---

## 5. SCENE-BATCH (`../../../../engines/scene-engine/scene-batch`)

### 5.1 CLI Entry Point

**`/home/visiona/workspace/mana-hive/engines/scene-engine/scene-batch/src/main/kotlin/com/manahive/scene/batch/SceneBatchApp.kt`**
- **Description**: CLI entry point with 3 commands: `run`, `verify`, `diff`
- Usage: `scene-batch run <run.yaml>`, `scene-batch verify <run.yaml> <expected.out>`, `scene-batch diff <expected.out> <actual.out>`

### 5.2 Commands

**`RunCommand.kt`** — Executes batch simulation: reads events.dat, processes through SceneInterpreter + ClockSweeper, writes facts.jsonl, facts.out, engine.log. Clock mode: event-time.

**`VerifyCommand.kt`** — Runs batch with expected facts verification. Stops on first mismatch (fail-fast).

**`DiffCommand.kt`** — Compares two .out files and shows differences line by line.

### 5.3 Core Processing

**`BatchProcessor.kt`** — Shared batch processing logic. For each event: (1) run sweeper between last event and this event, (2) run interpreter for this event. Uses `BatchContext` parameter object.

**`BatchState.kt`** — Mutable state: `twin: DigitalTwin`, `marks: DwellMarks`, `lastTime: Instant`, `passed: Int`, `discarded: Int`, `expectedIndex: Int`. Immutable context: `BatchContext(config, interpreter, sweeper, dwellCatalog, startTime)`.

**`BatchError.kt`** — Typed errors: `ConfigNotFound`, `EventsNotFound`, `ExpectedNotFound`, `InvalidDuration`, `InvalidTransitionTable`, `ParseError`, `VerifyFailed`, `DiffFound`, `MissingArguments`.

### 5.4 Events

**`Event.kt`** — Parsed event: `offset: EventOffset`, `kind: ObservationKind`, `confidence: Double`, `lineNumber: Int`

**`EventParser.kt`** — Parses `events.dat` format: `t=<offset> OBS <kind> confidence=<value>`. Supports `0s`, `2s`, `4m30s`, `1h5m` offsets.

**`EventOffset.kt`** — Value Object: time offset from simulation start. Inline value class wrapping `Duration`.

### 5.5 Output Writers

**`FactsWriter.kt`** — Writes SceneFact to JSONL. Each line is a self-contained JSON object.

**`FactsOutWriter.kt`** — Writes facts in events.dat format for easy diff: `t=<offset> TRANSITION LYING → BED_EDGE # ← evento 6`

**`LogWriter.kt`** — Writes engine logs to JSONL with structured fields.

### 5.6 Config

**`BatchConfig.kt`** — Root config: `BatchConfig(scene, calibration, events)`. Rich domain model that creates its own `SceneCalibration` and `DigitalTwin`.

**`BatchConfigLoader.kt`** — Loads BatchConfig from YAML files using Jackson.

**`BatchDsl.kt`** — DSL for building BatchConfig: `sceneConfig { scene { ... } calibration { ... } events { ... } }`

**`BatchSupport.kt`** — Utility functions: `formatDuration()`, `formatOffset()`, `resolveFile()`

---

## Summary Statistics

| Category | Count |
|----------|-------|
| Contract source files (main) | ~30 .kt files |
| Scene-domain source files (main) | 26 .kt files |
| Scene-batch source files (main) | 17 .kt files |
| Test spec files (scene-domain) | 20 specs |
| Test spec files (scene-batch) | 1 spec |
| Test spec files (contracts) | 5 specs |
| Test support files | 2 files |
| Golden data files (.dat) | 2 files |
| Golden output files (.out) | 1 file |
| Golden output files (.jsonl) | 1 file |
| Golden log files (.log) | 1 file |
| YAML config files | 2 files |
