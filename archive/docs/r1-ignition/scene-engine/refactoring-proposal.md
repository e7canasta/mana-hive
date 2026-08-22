# Scene Domain — Refactoring Proposal (Design Expert Review)

**Reviewer:** Eric Evans, Martin Fowler, Kent Beck
**Date:** 2026-08-22
**Module:** `engines/scene-engine/scene-domain`
**Status:** Proposal — pending implementation

---

## 1. Current State Assessment

### What's Good (keep)

| Pattern | Evidence | Why it's good |
|---------|----------|---------------|
| Pure domain | Zero Spring/IO in domain | Testable, portable |
| Immutable value objects | `data class` everywhere | Thread-safe, predictable |
| Port interfaces | `SceneInterpreter`, `ClockSweeper` | Swappable implementations |
| DSL builders | `calibration {}`, `transitionTable {}` | Fluent, readable |
| BDD specs | 55 scenarios, all passing | Living documentation |

### What Needs Improvement

| Smell | Location | Severity |
|-------|----------|----------|
| Public implementations | `SceneInterpreterImpl`, `ClockSweeperImpl` | **High** |
| Data clump | `Map<StateKind, Double>` in `SceneCalibration.minConfidence` | **Medium** |
| Duplicated properties | 13 `StateKind` getters in 3 DSL builders | **Medium** |
| Unused import | `CalibrationDsl.kt:6` | **Low** |

---

## 2. Refactoring #1: Internal Implementations

### Problem

`SceneInterpreterImpl` and `ClockSweeperImpl` are `public`. Consumers can depend on concrete classes instead of port interfaces. This violates the Dependency Inversion Principle.

### Solution

```kotlin
// BEFORE (public)
class SceneInterpreterImpl(
    private val calibration: SceneCalibration
) : SceneInterpreter { ... }

// AFTER (internal)
internal class SceneInterpreterImpl(
    private val calibration: SceneCalibration
) : SceneInterpreter { ... }
```

```kotlin
// BEFORE (public)
class ClockSweeperImpl(
    private val thresholds: DwellCatalog,
    private val heartbeatTimeout: Duration
) : ClockSweeper { ... }

// AFTER (internal)
internal class ClockSweeperImpl(
    private val thresholds: DwellCatalog,
    private val heartbeatTimeout: Duration
) : ClockSweeper { ... }
```

### Impact

| Before | After |
|--------|-------|
| Tests import `SceneInterpreterImpl` directly | Tests import `SceneInterpreter` interface |
| Tests import `ClockSweeperImpl` directly | Tests import `ClockSweeper` interface |
| No compile-time enforcement | Kotlin `internal` enforces within module |

### Test Changes

```kotlin
// BEFORE
val interpreter = SceneInterpreterImpl(calibration)

// AFTER — factory function (internal, visible to tests)
internal fun createInterpreter(calibration: SceneCalibration): SceneInterpreter {
    return SceneInterpreterImpl(calibration)
}

// Tests use factory
val interpreter = createInterpreter(calibration)
```

**Why factory?** Tests need to construct implementations, but shouldn't know about `Impl` classes. The factory is `internal` — visible within the Gradle module but not outside.

---

## 3. Refactoring #2: ConfidenceThresholds Value Object

### Problem

`SceneCalibration.minConfidence` is `Map<StateKind, Double>`. The fallback logic (`?: MIN_CONFIDENCE_DEFAULT`) lives in `SceneInterpreterImpl:86` — it's a data clump with behavior scattered.

### Solution

```kotlin
// NEW: calibration/ConfidenceThresholds.kt
public data class ConfidenceThresholds(
    private val byState: Map<StateKind, Double>,
    private val default: Double = 0.8
) {
    public fun forState(kind: StateKind): Double = byState[kind] ?: default
}
```

```kotlin
// BEFORE: SceneCalibration
data class SceneCalibration(
    val table: TransitionTable,
    val minConfidence: Map<StateKind, Double>,  // raw map
    val heartbeatTimeout: Duration,
    val dwellThresholds: Map<StateKind, DwellThreshold>
)

// AFTER: SceneCalibration
data class SceneCalibration(
    val table: TransitionTable,
    val confidence: ConfidenceThresholds,       // value object
    val heartbeatTimeout: Duration,
    val dwellThresholds: Map<StateKind, DwellThreshold>
)
```

```kotlin
// BEFORE: SceneInterpreterImpl:86
val minConf = calibration.minConfidence[observation.kind] ?: MIN_CONFIDENCE_DEFAULT

// AFTER: SceneInterpreterImpl
val minConf = calibration.confidence.forState(observation.kind)
```

### Impact

| Before | After |
|--------|-------|
| Fallback logic in interpreter | Fallback logic in value object |
| `Map<StateKind, Double>` scattered | `ConfidenceThresholds` encapsulated |
| `MIN_CONFIDENCE_DEFAULT` constant in impl | Default in value object constructor |

---

## 4. Refactoring #3: Shared StateKind DSL Properties

### Problem

13 `StateKind` getter properties are copy-pasted across 3 DSL builders:

| Builder | Lines | Properties |
|---------|-------|------------|
| `TransitionTableBuilder` | 38-50 | 13 getters |
| `FromBuilder` | 66-78 | 13 identical getters |
| `DwellThresholdsBuilder` | 24-35 | 12 getters (no UNKNOWN) |

### Solution: Shared Interface

```kotlin
// NEW: calibration/dsl/StateKindDsl.kt
@SceneDsl
public interface StateKindDsl {
    val lying: StateKind get() = StateKind.LYING
    val sittingInBed: StateKind get() = StateKind.SITTING_IN_BED
    val attemptingExit: StateKind get() = StateKind.ATTEMPTING_EXIT
    val bedEdge: StateKind get() = StateKind.BED_EDGE
    val standing: StateKind get() = StateKind.STANDING
    val inBathroom: StateKind get() = StateKind.IN_BATHROOM
    val inRoom: StateKind get() = StateKind.IN_ROOM
    val inHallway: StateKind get() = StateKind.IN_HALLWAY
    val outdoor: StateKind get() = StateKind.OUTDOOR
    val absent: StateKind get() = StateKind.ABSENT
    val inChair: StateKind get() = StateKind.IN_CHAIR
    val inWheelchair: StateKind get() = StateKind.IN_WHEELCHAIR
    val unknown: StateKind get() = StateKind.UNKNOWN
}
```

```kotlin
// BEFORE: TransitionTableBuilder
@SceneDsl
class TransitionTableBuilder {
    val lying: StateKind get() = StateKind.LYING
    val sittingInBed: StateKind get() = StateKind.SITTING_IN_BED
    // ... 11 more
}

// AFTER: TransitionTableBuilder
@SceneDsl
class TransitionTableBuilder : StateKindDsl {
    // No duplicate properties — inherited from interface
}
```

```kotlin
// BEFORE: FromBuilder
@SceneDsl
class FromBuilder(private val from: StateKind) {
    val lying: StateKind get() = StateKind.LYING
    val sittingInBed: StateKind get() = StateKind.SITTING_IN_BED
    // ... 11 more
}

// AFTER: FromBuilder
@SceneDsl
class FromBuilder(private val from: StateKind) : StateKindDsl {
    // No duplicate properties — inherited from interface
}
```

### Impact

| Before | After |
|--------|-------|
| 39 lines of duplicated properties | 0 duplicated lines |
| Adding new state = 3 files to edit | Adding new state = 1 file to edit |
| Risk of inconsistency | Single source of truth |

---

## 5. Refactoring #4: Remove Unused Import

### Problem

`CalibrationDsl.kt:6` imports `DwellCatalog` but never uses it.

### Solution

```kotlin
// BEFORE
import com.manahive.scene.calibration.DwellCatalog  // unused

// AFTER
// (remove line)
```

---

## 6. Refactoring #5: Consider Internal Value Objects

### Problem

`SceneVerdict`, `SweepResult`, `DwellMarkKey` are `public` but have no external consumers. They're implementation details of the engine pipeline.

### Analysis

| Type | External consumers | Recommendation |
|------|-------------------|----------------|
| `SceneVerdict` | Tests only | Keep `public` — tests need to assert on it |
| `SweepResult` | Tests only | Keep `public` — tests need to assert on it |
| `DwellMarkKey` | Tests only | Keep `public` — tests need to construct marks |

**Decision:** Keep `public`. Tests are legitimate consumers of these types. Making them `internal` would still work (tests are in the same module), but `public` is clearer about intent.

---

## 7. Implementation Order

| # | Refactoring | Risk | Effort | Dependencies |
|---|-------------|------|--------|--------------|
| 1 | Remove unused import | None | 1 min | None |
| 2 | Mark impls `internal` | Low | 10 min | None |
| 3 | Extract `ConfidenceThresholds` | Medium | 30 min | None |
| 4 | Extract `StateKindDsl` interface | Medium | 20 min | None |
| 5 | Update tests to use factories | Low | 15 min | #2 |

**Total effort:** ~75 minutes
**Risk:** Low — all changes are additive or visibility-only

---

## 8. Final Package Structure

```
com.manahive.scene/
├── core/
│   ├── DigitalTwin.kt              ← public
│   ├── TransitionTable.kt          ← public
│   └── DigitalTwinEvolution.kt     ← public
│
├── calibration/
│   ├── SceneCalibration.kt         ← public
│   ├── ConfidenceThresholds.kt     ← public (NEW)
│   ├── DwellCatalog.kt             ← public
│   └── dsl/
│       ├── SceneDsl.kt             ← public
│       ├── StateKindDsl.kt         ← public (NEW)
│       ├── CalibrationDsl.kt       ← public
│       ├── DwellCatalogDsl.kt      ← public
│       ├── DwellThresholdsDsl.kt   ← public
│       └── TransitionTableDsl.kt   ← public
│
├── interpreter/
│   ├── SceneInterpreter.kt         ← public (interface)
│   ├── SceneInterpreterImpl.kt     ← internal (CHANGED)
│   └── SceneVerdict.kt             ← public
│
├── sweeper/
│   ├── ClockSweeper.kt             ← public (interface)
│   ├── ClockSweeperImpl.kt         ← internal (CHANGED)
│   ├── DwellMarks.kt               ← public
│   └── SweepResult.kt              ← public
│
└── adapter/
    └── PolicyCalibrationAdapter.kt ← public
```

---

## 9. Design Principles Applied

| Principle | Source | How Applied |
|-----------|--------|-------------|
| **Dependency Inversion** | Martin | Impl classes internal, consumers use interfaces |
| **Encapsulate Variation** | Fowler | `ConfidenceThresholds` hides map + default logic |
| **DRY** | Hunt & Thomas | `StateKindDsl` eliminates 39 duplicated lines |
| **Information Hiding** | Parnas | Internal classes hide implementation details |
| **Single Responsibility** | Martin | Each value object has one reason to change |
| **Ubiquitous Language** | Evans | `ConfidenceThresholds` matches domain term |

---

## 10. What We're NOT Doing

| Temptation | Why Not |
|-----------|---------|
| Gradle submodules | Premature — single module is fine for now |
| ArchUnit/Konsist rules | Premature — `internal` visibility is enough |
| Extract `SceneFact` to core | It's already in contracts — correct location |
| Make DSL builders internal | They're the public API for construction |

---

*Expert review complete. Ready for implementation.*
