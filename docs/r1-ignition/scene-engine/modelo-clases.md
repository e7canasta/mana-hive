# Scene Engine — Class Model & DSL Reference

**Last updated:** 2026-08-22
**Status:** Sprint 3 complete (SE-1 through SE-20)

---

## 1. Complete Class Inventory

### platform/domain-kernel

| Type | Kind | File |
|------|------|------|
| `Engine` | interface | `Engine.kt` |
| `EngineVersion` | data class | `Engine.kt` |
| `Explained<T>` | data class | `Engine.kt` |
| `ExplanationStep` | data class | `Engine.kt` |
| `Discard` | data class | `Engine.kt` |
| `DiscardCause` | enum (8 values) | `Engine.kt` |
| `BedId` | value class | `Ids.kt` |
| `ResidentId` | value class | `Ids.kt` |
| `MonitorId` | value class | `Ids.kt` |
| `StaffId` | value class | `Ids.kt` |
| `NightId` | value class | `Ids.kt` |
| `RuleId` | value class | `Ids.kt` |
| `EpisodeId` | value class | `Ids.kt` |
| `EventRef` | data class | `Ids.kt` |

### platform/contracts

| Type | Kind | File |
|------|------|------|
| `Observation` | data class | `perception/Observation.kt` |
| `ObservationKind` | enum (14 values) | `perception/Observation.kt` |
| `ObservationKind.toPersonState()` | ext function | `scene/ObservationKindMapping.kt` |
| `PersonState` | sealed interface (13 cases) | `scene/PersonState.kt` |
| `StateKind` | enum (13 values) | `scene/PersonState.kt` |
| `UnknownCause` | enum (2 values) | `scene/PersonState.kt` |
| `RiskGroup` | enum (3 values) | `scene/PersonState.kt` |
| `PersonState.kind` | ext property | `scene/PersonState.kt` |
| `PersonState.riskGroup` | ext property | `scene/PersonState.kt` |
| `SceneFact` | sealed interface (8 cases) | `scene/SceneFact.kt` |
| `NightSummary` | data class | `scene/SceneFact.kt` |
| `PolicyCalibration` | data class | `policy/PolicyCalibration.kt` |
| `ConfidenceConfig` | data class | `policy/PolicyCalibration.kt` |
| `TransitionKey` | data class | `policy/PolicyCalibration.kt` |
| `DwellThreshold` | data class | `policy/PolicyCalibration.kt` |
| `CalibrationChanged` | data class | `policy/CalibrationChanged.kt` |
| `HeartbeatBuilder` | class | `shared/HeartbeatBuilder.kt` |
| `Int.ms` | ext property | `shared/DurationExtensions.kt` |
| `Int.seconds` | ext property | `shared/DurationExtensions.kt` |
| `Int.minutes` | ext property | `shared/DurationExtensions.kt` |
| `Int.hours` | ext property | `shared/DurationExtensions.kt` |

### engines/scene-engine/scene-domain

| Type | Kind | File |
|------|------|------|
| `DigitalTwin` | data class | `DigitalTwin.kt` |
| `SignalHealth` | data class | `DigitalTwin.kt` |
| `DigitalTwin.evolve()` | ext function | `DigitalTwinEvolution.kt` |
| `SceneInterpreter` | interface | `SceneInterpreter.kt` |
| `SceneInterpreterImpl` | class | `SceneInterpreterImpl.kt` |
| `SceneVerdict` | data class | `SceneInterpreter.kt` |
| `SceneCalibration` | data class | `SceneInterpreter.kt` |
| `ClockSweeper` | interface | `ClockSweeper.kt` |
| `ClockSweeperImpl` | class | `ClockSweeperImpl.kt` |
| `DwellCatalog` | data class | `ClockSweeper.kt` |
| `SceneCalibration.toDwellCatalog()` | ext function | `ClockSweeper.kt` |
| `DwellMarks` | data class | `ClockSweeper.kt` |
| `DwellMarkKey` | data class | `ClockSweeper.kt` |
| `SweepResult` | data class | `ClockSweeper.kt` |
| `TransitionTable` | data class | `TransitionTable.kt` |
| `PolicyCalibration.toSceneCalibration()` | ext function | `PolicyCalibrationAdapter.kt` |

---

## 2. UML Class Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                     SCENE DOMAIN — CLASS MODEL                       │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌─── Contracts (shared vocabulary) ────────────────────────────┐   │
│  │                                                              │   │
│  │  sealed interface PersonState                                │   │
│  │    Lying | SittingInBed | AttemptingExit | BedEdge |         │   │
│  │    Standing | InBathroom | InRoom | InHallway | Outdoor |    │   │
│  │    Absent | InChair | InWheelchair | Unknown(cause)          │   │
│  │                                                              │   │
│  │  enum StateKind (13 values)                                  │   │
│  │                                                              │   │
│  │  data class DwellThreshold                                   │   │
│  │    - warning: Duration                                       │   │
│  │    - exceeded: Duration                                      │   │
│  │                                                              │   │
│  │  sealed interface SceneFact (8 cases)                        │   │
│  │    NightOpened | TransitionDetected | DwellWarning |         │   │
│  │    DwellExceeded | StaffPresenceDetected | SignalLost |      │   │
│  │    SignalRecovered | NightClosed                             │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                      │
│  ┌─── Scene Domain ─────────────────────────────────────────────┐   │
│  │                                                              │   │
│  │  data class DigitalTwin                                      │   │
│  │    - bed: BedId                                              │   │
│  │    - night: NightId                                          │   │
│  │    - occupant: ResidentId?                                   │   │
│  │    - state: PersonState                                      │   │
│  │    - stateSince: Instant                                     │   │
│  │    - signal: SignalHealth                                    │   │
│  │    - calibration: SceneCalibration? = null                   │   │
│  │                                                              │   │
│  │  data class SceneCalibration                                 │   │
│  │    - table: TransitionTable                                  │   │
│  │    - minConfidence: Map<StateKind, Double>                   │   │
│  │    - heartbeatTimeout: Duration                              │   │
│  │    - dwellThresholds: Map<StateKind, DwellThreshold>        │   │
│  │                                                              │   │
│  │  data class TransitionTable                                  │   │
│  │    - rules: Map<TransitionKey, Duration>                     │   │
│  │    - isLegal(from, to): Boolean                              │   │
│  │    - hysteresis(from, to): Duration                          │   │
│  │    - RELEASE_1: companion (5-state legacy)                   │   │
│  │    - RELEASE_2: companion (13-state clinical)                │   │
│  │                                                              │   │
│  │  data class DwellCatalog                                     │   │
│  │    - byState: Map<StateKind, DwellThreshold>                 │   │
│  │    - heartbeatTimeout: Duration                              │   │
│  │                                                              │   │
│  │  fun SceneCalibration.toDwellCatalog(): DwellCatalog         │   │
│  │                                                              │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                      │
│  ┌─── DSLs ─────────────────────────────────────────────────────┐   │
│  │                                                              │   │
│  │  transitionTable { }  → TransitionTable                      │   │
│  │  calibration { }      → SceneCalibration                     │   │
│  │  dwellCatalog { }     → DwellCatalog                         │   │
│  │  buildTwin { }        → DigitalTwin                          │   │
│  │                                                              │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                      │
│  ┌─── Adapter ──────────────────────────────────────────────────┐   │
│  │                                                              │   │
│  │  PolicyCalibration.toSceneCalibration(base)                  │   │
│  │    → SceneCalibration                                        │   │
│  │    default base = TransitionTable.RELEASE_2                  │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 3. Data Flow

```
Observation (sensor)
    │
    ▼
SceneInterpreter.interpret(twin, observation, now)
    │
    ├── 1. CONFIDENCE check
    ├── 2. SENSOR RECOVERY check
    ├── 3. DUPLICATE check
    ├── 4. ILLEGAL TRANSITION check
    ├── 5. HYSTERESIS check
    └── 6. VALID TRANSITION
    │
    ▼
SceneVerdict { twin (updated), facts }
    │
    ├── facts → Hub → Sentinel
    │
    └── twin → ClockSweeper.sweep(twins, now, catalog, marks)
                    │
                    ├── DwellWarning (warning threshold)
                    ├── DwellExceeded (exceeded threshold)
                    └── SignalLost (heartbeat timeout)
                    │
                    ▼
              SweepResult { facts, marks }
                    │
                    ▼
              Hub → Sentinel
```

---

## 4. DSL Reference

### TransitionTable DSL

```kotlin
val table = transitionTable {
    from(LYING) {
        to(BED_EDGE) after 1500.ms
        to(STANDING) after 2000.ms
    }
    from(BED_EDGE) {
        to(STANDING) after 1000.ms
        to(LYING) after 500.ms
    }
    from(STANDING) {
        to(LYING) after 800.ms
    }
}
```

### Calibration DSL

```kotlin
val cal = calibration {
    table = TransitionTable.RELEASE_2
    confidence(BED_EDGE) min 0.8
    confidence(STANDING) min 0.7
    dwell {
        STANDING warning 3.minutes exceeded 5.minutes
        BED_EDGE warning 8.minutes exceeded 10.minutes
    }
    heartbeat { timeout = 90.seconds }
}
```

### DwellCatalog DSL

```kotlin
val catalog = dwellCatalog {
    dwell {
        STANDING warning 4.minutes exceeded 5.minutes
        BED_EDGE warning 8.minutes exceeded 10.minutes
    }
    heartbeat { timeout = 90.seconds }
}
```

### BuildTwin DSL

```kotlin
val twin = buildTwin {
    bed = 3
    occupant = ResidentId("maria")
    state = StateKind.STANDING
    since = Instant.parse("2024-01-01T03:00:00Z")
    signalLost = false
}
```

### Test DSL

```kotlin
// In tests only — infix style
val twin = bed(3) occupiedBy maria at StateKind.STANDING since time03_00_00
val twinWithCal = bed(3) occupiedBy maria at StateKind.STANDING withCalibration(cal) since time03_00_00
```

### Duration Extensions

```kotlin
90.seconds    // Duration.ofSeconds(90)
5.minutes     // Duration.ofMinutes(5)
1500.ms       // Duration.ofMillis(1500)
8.hours       // Duration.ofHours(8)
```

---

## 5. Design Decisions (Vernon — no reopen)

| Decision | Rationale |
|----------|-----------|
| SceneCalibration lives in scene-domain | Engine config, not a contract |
| DigitalTwin.evolve() is extension | Avoids circular dependency |
| DwellMarks in service layer | Sweeper is pure; marks are mutable state |
| SceneInterpreter receives calibration via constructor | Per-resident calibration |
| ClockSweeperImpl has no state | Pure function; marks external |
| DwellCatalog is DERIVED from SceneCalibration | Never stored; recomputed on CalibrationChanged |
| Two calibration types (Policy vs Scene) | Bounded context separation |
| RELEASE_1 kept for backward compat | Legacy 5-state table |
| Shared DwellThresholdsBuilder | Eliminates parallel hierarchy smell (Fowler) |
| DwellThreshold per state | Eliminates data clumps smell (Fowler) |

---

*Derived from code. The code is the truth.*
