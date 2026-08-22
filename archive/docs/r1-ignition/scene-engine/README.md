# Scene Engine — Design Reference

**Last updated:** 2026-08-22
**Status:** Sprint 3 complete (SE-1 through SE-20)

---

## 1. Bounded Context (Vernon)

The Scene Engine is a **pure domain engine** within the `mana-hive` bounded context. It maintains the state of the scene — who is where, since when, and what the sensors see.

### Ubiquitous Language

| Term | Definition | NOT this |
|------|-----------|----------|
| **Hysteresis** | Minimum time before confirming a state change | "Delay" — it's domain-specific confirmation |
| **Dwell** | Time spent in a state before raising a fact | "Timer" — it's derived from (now − stateSince) |
| **Confidence** | How certain the sensor is about an observation | "Probability" — it's per-observation |
| **DigitalTwin** | Living record of a bed: who, what state, since when | "Object" — it's domain-specific |
| **SceneFact** | What the engine asserts to the Hub | "Event" — it's a fact about the world |
| **Discard** | What the engine rejected and why | "Error" — it's expected behavior |
| **Transition** | Change of state (lying → standing) | "Move" — it's a state machine concept |
| **StateKind** | Enum of all possible states | "Status" — it's the FSM vocabulary |

### What is NOT in our vocabulary

| Term | Belongs to |
|------|-----------|
| Alarm, Alert, Notification | Sentinel |
| RiskLevel, AlarmProfile | Hub |
| EffectivePolicy | Politica Engine |
| Autopilot, Preset | UI |

---

## 2. The Domain — What Scene Engine Does

```
┌─────────────────────────────────────────────────────────────┐
│                     SCENE ENGINE                             │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  DOES:                                                       │
│  ├── Receives perception events (from sensors)              │
│  ├── Updates digital twins (bed state)                      │
│  ├── Detects state changes (transitions)                    │
│  ├── Detects dwell time (time in state)                     │
│  └── Emits scene facts (to Hub)                             │
│                                                              │
│  DOES NOT:                                                   │
│  ├── Decide what is important                               │
│  ├── Know clinical rules                                    │
│  ├── Alert or notify                                        │
│  └── Judge                                                  │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### The Three Components

| Component | Responsibility | Pattern |
|-----------|---------------|---------|
| **SceneInterpreter** | Translates sensor → facts | Pure function: (twin, observation, now, calibration) → Explained\<SceneVerdict\> |
| **ClockSweeper** | Counts time in state | Pure function: (twins, now, catalog, marks) → Explained\<SweepResult\> |
| **DigitalTwin** | State of a bed | Data class + evolve() extension |

---

## 3. State Model — 13 States

```
┌─────────────────────────────────────────────────────────┐
│                     PERSON STATES (13)                    │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  EN CAMA (in_bed)                                       │
│  ├── LYING              → Acostada                      │
│  ├── SITTING_IN_BED     → Incorporada                   │
│  └── ATTEMPTING_EXIT    → Gusanito (brazos al borde)    │
│  └── BED_EDGE           → Sentada al borde              │
│                                                          │
│  FUERA DE CAMA (out_of_bed)                             │
│  ├── STANDING           → De pie                        │
│  ├── IN_BATHROOM        → En el baño                    │
│  ├── IN_ROOM            → En la habitación              │
│  ├── IN_HALLWAY         → En el pasillo                 │
│  └── OUTDOOR            → Afuera                        │
│                                                          │
│  MUEBLES                                                │
│  ├── IN_CHAIR           → En la silla                   │
│  └── IN_WHEELCHAIR      → En la silla de ruedas         │
│                                                          │
│  ESPECIALES                                             │
│  ├── ABSENT             → OUT_OF_ROOM (ubicación N/I)   │
│  └── UNKNOWN            → Sensor perdido / recovery     │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

### StateKind ↔ PersonState Mapping

```
StateKind          PersonState
──────────────     ───────────────────
LYING              PersonState.Lying
SITTING_IN_BED     PersonState.SittingInBed
ATTEMPTING_EXIT    PersonState.AttemptingExit
BED_EDGE           PersonState.BedEdge
STANDING           PersonState.Standing
IN_BATHROOM        PersonState.InBathroom
IN_ROOM            PersonState.InRoom
IN_HALLWAY         PersonState.InHallway
OUTDOOR            PersonState.Outdoor
ABSENT             PersonState.Absent
IN_CHAIR           PersonState.InChair
IN_WHEELCHAIR      PersonState.InWheelchair
UNKNOWN            PersonState.Unknown(cause)
```

---

## 4. Collaborating Packages — Context Map

```
┌─────────────────────────────────────────────────────────────────┐
│                     MANA-HIVE SYSTEM                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  platform/domain-kernel                                 │   │
│  │  Engine, Explained<T>, Discard, DiscardCause, Ids       │   │
│  │  (shared primitives, no domain logic)                   │   │
│  └─────────────────────────────────────────────────────────┘   │
│           ▲                                                       │
│  ┌────────┴────────────────────────────────────────────────┐   │
│  │  platform/contracts                                     │   │
│  │  Observation, PersonState, StateKind, SceneFact,        │   │
│  │  RiskGroup, ObservationKind, ObservationKindMapping     │   │
│  │  (shared vocabulary, no implementations)                │   │
│  └─────────────────────────────────────────────────────────┘   │
│           ▲                                                       │
│  ┌────────┴────────────────────────────────────────────────┐   │
│  │  engines/politica-engine/politica-domain                │   │
│  │  PolicyCalibration, CalibrationChanged, ConfidenceConfig│   │
│  │  (raw rules, policy domain)                             │   │
│  └─────────────────────────────────────────────────────────┘   │
│           │                                                       │
│           │ PolicyCalibrationAdapter.toSceneCalibration()       │
│           ▼                                                       │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  engines/scene-engine/scene-domain                      │   │
│  │                                                          │   │
│  │  DigitalTwin ──► SceneInterpreter ──► SceneFact          │   │
│  │       │                                      │           │   │
│  │       ▼                                      ▼           │   │
│  │  ClockSweeper ──────────────────────► SweepResult        │   │
│  │                                                          │   │
│  │  SceneCalibration (compiled, scene-domain)              │   │
│  │  DwellCatalog (derived from calibration)                │   │
│  │  TransitionTable (RELEASE_1 = 5 states, RELEASE_2 = 13)│   │
│  └─────────────────────────────────────────────────────────┘   │
│           │                                                       │
│           │ SceneFact                                            │
│           ▼                                                       │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  Hub → Sentinel                                          │   │
│  │  (judges facts, decides alerts)                         │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Dependency Rules

| From | To | Allowed |
|------|----|---------|
| scene-domain | contracts | ✅ (PersonState, Observation, SceneFact) |
| scene-domain | domain-kernel | ✅ (Engine, Explained, Ids) |
| scene-domain | politica-domain | ✅ (PolicyCalibration, DwellThreshold) |
| contracts | scene-domain | ❌ (no backward dependency) |
| contracts | domain-kernel | ✅ (shared primitives) |

---

## 5. UML Class Diagram — Scene Domain

```
┌─────────────────────────────────────────────────────────────────────┐
│                     SCENE DOMAIN — CLASS MODEL                       │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌─── DigitalTwin.kt ──────────────────────────────────────────┐   │
│  │  data class DigitalTwin                                      │   │
│  │    - bed: BedId                                              │   │
│  │    - night: NightId                                          │   │
│  │    - occupant: ResidentId?                                   │   │
│  │    - state: PersonState                                      │   │
│  │    - stateSince: Instant                                     │   │
│  │    - signal: SignalHealth                                    │   │
│  │    - calibration: SceneCalibration? = null                   │   │
│  │                                                              │   │
│  │  data class SignalHealth                                     │   │
│  │    - monitor: MonitorId                                      │   │
│  │    - lastHeartbeat: Instant                                  │   │
│  │    - lost: Boolean                                           │   │
│  │                                                              │   │
│  │  fun DigitalTwin.evolucionar(fact: SceneFact): DigitalTwin   │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                      │
│  ┌─── SceneInterpreter.kt ─────────────────────────────────────┐   │
│  │  interface SceneInterpreter : Engine                         │   │
│  │    fun interpret(twin, obs, now, cal): Explained<SceneVerdict>│  │
│  │                                                              │   │
│  │  data class SceneCalibration                                 │   │
│  │    - table: TransitionTable                                  │   │
│  │    - confidence: Map<StateKind, Double>                      │   │
│  │    - heartbeatTimeout: Duration                              │   │
│  │    - dwellThresholds: Map<StateKind, DwellThreshold>        │   │
│  │                                                              │   │
│  │  data class SceneVerdict                                     │   │
│  │    - twin: DigitalTwin                                       │   │
│  │    - facts: List<SceneFact>                                  │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                      │
│  ┌─── ClockSweeper.kt ─────────────────────────────────────────┐   │
│  │  interface ClockSeper : Engine                               │   │
│  │    fun sweep(twins, now, catalog, marks): Explained<SweepResult>│
│  │                                                              │   │
│  │  data class DwellCatalog                                     │   │
│  │    - byState: Map<StateKind, DwellThreshold>                 │   │
│  │    - heartbeatTimeout: Duration                              │   │
│  │                                                              │   │
│  │  fun SceneCalibration.toDwellCatalog(): DwellCatalog         │   │
│  │                                                              │   │
│  │  data class DwellMarks                                       │   │
│  │    - emitted: Set<DwellMarkKey>                              │   │
│  │                                                              │   │
│  │  data class DwellMarkKey                                     │   │
│  │    - bed: BedId, state: StateKind, since: Instant,           │   │
│  │      warning: Boolean                                        │   │
│  │                                                              │   │
│  │  data class SweepResult                                      │   │
│  │    - facts: List<SceneFact>                                  │   │
│  │    - marks: DwellMarks                                       │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                      │
│  ┌─── TransitionTable.kt ──────────────────────────────────────┐   │
│  │  data class TransitionTable                                  │   │
│  │    - rules: Map<TransitionKey, Duration>                     │   │
│  │    - isLegal(from, to): Boolean                              │   │
│  │    - hysteresis(from, to): Duration                          │   │
│  │                                                              │   │
│  │  companion object                                             │   │
│  │    - RELEASE_1: 5-state table (legacy)                      │   │
│  │    - RELEASE_2: 13-state table (clinical catalog)           │   │
│  │    - from(base, overrides): TransitionTable                  │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                      │
│  ┌─── Contracts (shared vocabulary) ────────────────────────────┐   │
│  │  sealed interface PersonState                                 │   │
│  │    Lying | SittingInBed | AttemptingExit | BedEdge |         │   │
│  │    Standing | InBathroom | InRoom | InHallway | Outdoor |    │   │
│  │    Absent | InChair | InWheelchair | Unknown(cause)          │   │
│  │                                                              │   │
│  │  enum StateKind { LYING, SITTING_IN_BED, ATTEMPTING_EXIT,   │   │
│  │    BED_EDGE, STANDING, IN_BATHROOM, IN_ROOM, IN_HALLWAY,    │   │
│  │    OUTDOOR, ABSENT, IN_CHAIR, IN_WHEELCHAIR, UNKNOWN }      │   │
│  │                                                              │   │
│  │  sealed interface SceneFact                                   │   │
│  │    NightOpened | TransitionDetected | DwellWarning |         │   │
│  │    DwellExceeded | StaffPresenceDetected | SignalLost |      │   │
│  │    SignalRecovered | NightClosed                             │   │
│  │                                                              │   │
│  │  data class DwellThreshold                                   │   │
│  │    - warning: Duration                                       │   │
│  │    - exceeded: Duration                                      │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 6. Data Flow — How It All Connects

```
Observation (sensor)
    │
    ▼
SceneInterpreter.interpret(twin, observation, now, calibration)
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
                    ├── DwellWarning (80% threshold)
                    ├── DwellExceeded (100% threshold)
                    └── SignalLost (heartbeat timeout)
                    │
                    ▼
              SweepResult { facts, marks }
                    │
                    ▼
              Hub → Sentinel
```

---

## 7. Interpret Pipeline — Pseudocode

```
interpret(twin, observation, now, calibration):

  1. CONFIDENCE
     if observation.confidence < calibration.confidence[kind]:
         DISCARD → CONFIDENCE_TOO_LOW

  2. SENSOR RECOVERY
     if twin.signal.lost AND kind != HEARTBEAT:
         twin = twin.copy(signal = signal.copy(lost = false))
         emit SignalRecovered
         // continue evaluating — observation may also indicate state

  3. DUPLICATE
     if observation.kind.toPersonState() == twin.state:
         DISCARD → DUPLICATE

  4. ILLEGAL TRANSITION
     if NOT calibration.table.isLegal(twin.state.kind, newState.kind):
         DISCARD → ILLEGAL_TRANSITION

  5. HYSTERESIS
     durationInState = now - twin.stateSince
     minimum = calibration.table.hysteresis(twin.state.kind, newState.kind)
     if durationInState < minimum:
         DISCARD → HYSTERESIS_NOT_MET

  6. VALID TRANSITION
     newTwin = twin.copy(state = newState, stateSince = now)
     emit TransitionDetected(from, to)
```

---

## 8. Sweep Pipeline — Pseudocode

```
sweep(twins, now, catalog, marks):

  FOR EACH twin IN twins:

    // SE-18: Per-twin catalog resolution
    catalog = twin.calibration?.toDwellCatalog() ?: defaults

    1. SENSOR LOST — skip
       if twin.signal.lost: continue

    2. DURATION IN STATE
       duration = now - twin.stateSince

    3. THRESHOLD EXISTS?
       threshold = catalog.byState[twin.state.kind]
       if threshold == null: continue

    4. ALREADY EMITTED?
       mark = DwellMarkKey(twin.bed, state, since, warning=false)
       if mark in marks.emitted: continue

    5. WARNING (explicit threshold from DwellThreshold)
       if duration >= threshold.warning:
           warningMark = mark.copy(warning=true)
           if warningMark NOT in marks.emitted:
               emit DwellWarning

    6. EXCEEDED
       if duration >= threshold.exceeded:
           emit DwellExceeded

    7. SIGNAL LOST
       if Duration.between(twin.signal.lastHeartbeat, now) > catalog.heartbeatTimeout:
           emit SignalLost

  return SweepResult(facts, DwellMarks(marks.emitted + newMarks))
```

---

## 9. Design Decisions (Vernon — no reopen)

| Decision | Rationale |
|----------|-----------|
| SceneCalibration lives in scene-domain | It's engine config, not a contract |
| DigitalTwin.evolucionar() is extension | Avoids circular dependency |
| DwellMarks in service layer | Sweeper is pure; marks are mutable state |
| SceneInterpreter receives calibration per method | Allows per-resident calibration |
| ClockSweeperImpl has no state | Pure function; marks external |
| DwellCatalog is DERIVED from SceneCalibration | Never stored; recomputed on CalibrationChanged |
| Two calibration types (Policy vs Scene) | Bounded context separation |
| RELEASE_1 kept for backward compat | Legacy 5-state table |

---

## 10. DSL Reference

### TransitionTable DSL

```kotlin
val table = transitionTable {
    from(LYING) {
        to(BED_EDGE) after 1500.ms
        to(STANDING) after 2000.ms
    }
    from(BED_EDGE) {
        to(STANDING) after 1000.ms
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

### Test DSL

```kotlin
val twin = bed(3) occupiedBy maria at StateKind.STANDING since time03_00_00
val twinWithCal = bed(3) occupiedBy maria at StateKind.STANDING withCalibration(cal) since time03_00_00
```

---

## 11. Test Matrix

| Spec | Stories | Scenarios |
|------|---------|-----------|
| ObservationKindMappingSpec | SE-1 | 7 |
| DigitalTwinEvolutionSpec | SE-2 | 4 |
| SceneInterpreterConfidenceSpec | SE-3 | 2 |
| SceneInterpreterDuplicateSpec | SE-4 | 1 |
| SceneInterpreterIllegalSpec | SE-5 | 1 |
| SceneInterpreterHysteresisSpec | SE-6 | 2 |
| SceneInterpreterTransitionSpec | SE-7 | 1 |
| SceneInterpreterSensorRecoverySpec | SE-8 | 2 |
| ClockSweeperSpec | SE-9..12 | 4 |
| PersonStateSpec | SE-17 | 12 |
| DwellCatalogSpec | SE-18, SE-20 | 8 |
| TransitionTableThirteenSpec | SE-19 | 6 |
| PoliticaToSceneIntegrationSpec | SE-16 | 2 |

---

## 12. Build

```bash
./gradlew :engines:politica-engine:politica-domain:test :engines:scene-engine:scene-domain:test
```
