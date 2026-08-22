Here is the complete analysis:

---

## 1. Complete File Inventory with Responsibilities

### Core Domain (Entities, Value Objects)

| File | Class(es) | Responsibility |
|------|-----------|----------------|
| `DigitalTwin.kt` | `DigitalTwin`, `SignalHealth` | Living model of one bed: occupant, state, timing, signal health. Immutable value. |
| `TransitionTable.kt` | `TransitionTable` | Person FSM as a total table: legality + minimum hysteresis per transition. Holds `RELEASE_1` (5-state) and `RELEASE_2` (13-state) as companion constants. |
| `DigitalTwinEvolution.kt` | `DigitalTwin.evolve()` | Pure fold: `SceneFact → DigitalTwin`. Event sourcing projection — the twin is the projection of all facts. |
| `SceneInterpreter.kt` | `SceneInterpreter` (interface), `SceneVerdict`, `SceneCalibration` | Interface contract + two value objects. `SceneCalibration` is the compiled business rules VO (table, confidence, heartbeat, dwells). |

### Interpreter (the engine)

| File | Class(es) | Responsibility |
|------|-----------|----------------|
| `SceneInterpreterImpl.kt` | `SceneInterpreterImpl` | 6-step pipeline: confidence → sensor recovery → duplicate → illegal transition → hysteresis → emit. Pure domain, no side effects. |

### Clock/Sweeper (dwell monitoring)

| File | Class(es) | Responsibility |
|------|-----------|----------------|
| `ClockSweeper.kt` | `ClockSweeper` (interface), `DwellCatalog`, `DwellMarks`, `DwellMarkKey`, `SweepResult`, `SceneCalibration.toDwellCatalog()` | Interface + 4 data classes + 1 extension. Time-based dwell monitoring. |
| `ClockSweeperImpl.kt` | `ClockSweeperImpl` | Sweeps twins for: `DwellWarning`, `DwellExceeded`, `SignalLost`. Pure function, no Spring/IO. |

### Adapter (bridges)

| File | Class(es) | Responsibility |
|------|-----------|----------------|
| `PolicyCalibrationAdapter.kt` | `PolicyCalibration.toSceneCalibration()` | Bridges Politica Engine → Scene Engine bounded context. |

### DSL Builders

| File | Class(es) | Responsibility |
|------|-----------|----------------|
| `SceneDsl.kt` | `@SceneDsl` | DslMarker annotation — prevents scope leakage. |
| `TransitionTableDsl.kt` | `transitionTable {}`, `TransitionTableBuilder`, `FromBuilder`, `TransitionRuleBuilder` | Fluent DSL for building `TransitionTable`. |
| `CalibrationDsl.kt` | `calibration {}`, `CalibrationBuilder`, `ConfidenceBuilder` | Fluent DSL for building `SceneCalibration`. |
| `DwellCatalogDsl.kt` | `dwellCatalog {}`, `DwellCatalogBuilder` | Fluent DSL for building `DwellCatalog`. |
| `DwellThresholdsDsl.kt` | `DwellThresholdsBuilder`, `DwellThresholdStateBuilder`, `DwellThresholdExceedsBuilder` | Shared DSL for dwell thresholds (used by both Calibration and DwellCatalog DSLs). |
| `SceneInterpreterDsl.kt` | `buildCalibration {}`, `buildTwin {}`, `TwinConfig` | Convenience/test DSL for `DigitalTwin` and `SceneCalibration`. |

---

## 2. Dependency Graph (within package)

```
                        ┌─────────────────────┐
                        │     SceneDsl.kt      │
                        │   @SceneDsl marker    │
                        └─────────┬───────────┘
                                  │ (annotation used by all DSL builders)
          ┌───────────────────────┼───────────────────────┐
          │                       │                       │
          ▼                       ▼                       ▼
┌──────────────────┐   ┌──────────────────┐   ┌──────────────────┐
│ TransitionTable  │   │  CalibrationDsl  │   │ DwellCatalogDsl  │
│     Dsl.kt       │   │     .kt          │   │      .kt         │
└────────┬─────────┘   └────────┬─────────┘   └────────┬─────────┘
         │                      │                       │
         │ builds               │ builds                │ builds
         ▼                      ▼                       ▼
┌──────────────────┐   ┌──────────────────┐   ┌──────────────────┐
│ TransitionTable  │◄──│ SceneInterpreter │◄──│  ClockSweeper.kt │
│     .kt          │   │     .kt          │   │  (interface +    │
│ (data class)     │   │ (SceneCalibration│   │   DwellCatalog,  │
└──────────────────┘   │  value object)   │   │   DwellMarks...) │
         ▲              └────────┬─────────┘   └────────┬─────────┘
         │                       │                       │
         │                       │ implements            │ implements
         │                       ▼                       ▼
         │              ┌──────────────────┐   ┌──────────────────┐
         │              │SceneInterpreter  │   │ ClockSweeperImpl │
         │              │     Impl.kt      │   │     .kt          │
         │              └────────┬─────────┘   └────────┬─────────┘
         │                       │                       │
         │                       │ uses                  │ uses
         ▼                       ▼                       ▼
┌──────────────────┐   ┌──────────────────┐   ┌──────────────────┐
│ DigitalTwin.kt   │◄──│DigitalTwinEvolution│  │DwellThresholdsDsl│
│ (data class)     │   │     .kt          │   │     .kt          │
└──────────────────┘   └──────────────────┘   └──────────────────┘
         ▲
         │ builds
┌──────────────────┐
│SceneInterpreter  │
│    Dsl.kt        │
│ (buildTwin)      │
└──────────────────┘

┌──────────────────────────┐
│ PolicyCalibrationAdapter │
│         .kt              │── uses ──► TransitionTable
│ (toSceneCalibration)     │── uses ──► SceneCalibration
└──────────────────────────┘
```

### Simplified dependency matrix (who imports from whom):

| File | Depends on (same package) |
|------|---------------------------|
| `DigitalTwin.kt` | *none* |
| `TransitionTable.kt` | *none* |
| `DigitalTwinEvolution.kt` | `DigitalTwin`, `SignalHealth` |
| `SceneInterpreter.kt` | `DigitalTwin`, `TransitionTable` |
| `SceneInterpreterImpl.kt` | `DigitalTwin`, `SceneCalibration`, `TransitionTable`, `SceneVerdict`, `SignalHealth` |
| `ClockSweeper.kt` | `DigitalTwin`, `SceneCalibration` |
| `ClockSweeperImpl.kt` | `DigitalTwin`, `DwellCatalog`, `DwellMarks`, `DwellMarkKey`, `SceneCalibration` (via `.toDwellCatalog()`) |
| `PolicyCalibrationAdapter.kt` | `TransitionTable`, `SceneCalibration` |
| `SceneDsl.kt` | *none* |
| `TransitionTableDsl.kt` | `TransitionTable` |
| `CalibrationDsl.kt` | `TransitionTable`, `SceneCalibration` |
| `DwellCatalogDsl.kt` | `DwellCatalog` |
| `DwellThresholdsDsl.kt` | *none* (only contracts) |
| `SceneInterpreterDsl.kt` | `SceneCalibration`, `DigitalTwin`, `SignalHealth`, `TransitionTable` |

---

## 3. Cohesion Analysis — Tightly Coupled

**Critical coupling points:**

1. **`SceneCalibration` is defined in `SceneInterpreter.kt`** but consumed by `ClockSweeper`, `ClockSweeperImpl`, `PolicyCalibrationAdapter`, `CalibrationDsl`, `SceneInterpreterDsl`. It's a shared value object masquerading as interpreter-specific. It's the most-imported type from the interpreter file.

2. **`ClockSweeper.kt` is a 5-concern file**: interface + `DwellCatalog` + `DwellMarks` + `DwellMarkKey` + `SweepResult` + extension function. High internal cohesion but poor separation — the VO bundle competes with the interface for attention.

3. **`SceneInterpreterImpl.kt` owns `SceneVerdict`** (defined in `SceneInterpreter.kt`) but the verdict is also the output type of `ClockSweeper.sweep()`. Actually, ClockSweeper returns `SweepResult`, so this is clean. However, `SceneVerdict` is used only by interpreter internals and tests.

4. **Signal health is cross-cutting**: `SignalHealth` lives in `DigitalTwin.kt`, `SignalLost` is emitted by `ClockSweeperImpl`, but `SignalRecovered` is emitted by `SceneInterpreterImpl`. Signal monitoring spans both engines.

5. **DSL builders duplicate StateKind properties**: `TransitionTableDsl.kt` (lines 37-49, 65-77) and `DwellThresholdsDsl.kt` (lines 24-35) both re-expose the same 13 `StateKind` constants. This duplication is structural, not accidental.

**What's loosely coupled (good):**
- `DigitalTwin.kt` and `TransitionTable.kt` are pure leaf nodes — zero same-package deps.
- `PolicyCalibrationAdapter` depends only on types it converts.
- `DwellThresholdsDsl` is shared infrastructure with no same-package deps.

---

## 4. Potential Domain Boundaries

### Boundary A: `scene-core` — Pure Domain Model
```
DigitalTwin.kt
SignalHealth (from DigitalTwin.kt)
TransitionTable.kt
DigitalTwinEvolution.kt
```
Zero dependencies on other scene-domain types. This is the kernel — pure value objects and a pure fold function.

### Boundary B: `scene-calibration` — Business Rules Configuration
```
SceneCalibration (currently in SceneInterpreter.kt)
DwellCatalog (currently in ClockSweeper.kt)
SceneCalibration.toDwellCatalog() (currently in ClockSweeper.kt)
CalibrationDsl.kt
DwellCatalogDsl.kt
DwellThresholdsDsl.kt
TransitionTableDsl.kt
SceneDsl.kt (marker)
```
Rationale: `SceneCalibration` is the most cross-cutting type. Extracting it + all DSL builders into a calibration module eliminates the coupling where `ClockSweeper` needs to import from `SceneInterpreter.kt`.

### Boundary C: `scene-interpreter` — Observation → Facts
```
SceneInterpreter.kt (interface only)
SceneInterpreterImpl.kt
SceneVerdict
```
Depends on: `scene-core` (DigitalTwin) + `scene-calibration` (SceneCalibration). Clean consumer.

### Boundary D: `scene-sweeper` — Time-Based Monitoring
```
ClockSweeper.kt (interface only)
ClockSweeperImpl.kt
DwellMarks, DwellMarkKey, SweepResult
```
Depends on: `scene-core` (DigitalTwin) + `scene-calibration` (DwellCatalog). Currently imports `SceneCalibration` for the extension function — extracting the extension to `scene-calibration` would break this.

### Boundary E: `scene-adapter` — Bounded Context Bridge
```
PolicyCalibrationAdapter.kt
```
Depends on: `scene-calibration` + `scene-core` (TransitionTable). Clean adapter.

### Boundary F: Test Helpers (separate source set)
```
SceneInterpreterDsl.kt (buildTwin, buildCalibration, TwinConfig)
```
Currently in main source set but only used by tests. Should move to `src/test/`.

### Key structural refactoring to unlock boundaries:
1. **Extract `SceneCalibration`** from `SceneInterpreter.kt` into its own file — it's imported by 5 other files.
2. **Split `ClockSweeper.kt`** — interface from data classes (DwellCatalog, DwellMarks, DwellMarkKey, SweepResult).
3. **Move `SceneInterpreterDsl.kt`** to test source set — `buildTwin` and `buildCalibration` are test-only convenience.
4. **Extract `toDwellCatalog()`** extension — it bridges calibration to sweeper, belongs in calibration boundary.