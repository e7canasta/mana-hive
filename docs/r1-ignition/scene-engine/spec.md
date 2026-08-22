# Scene Engine — Functional Spec (BDD + User Stories + Use Cases)

**Authors:** Kent Beck, Dan North, Alistair Cockburn, Eric Evans
**Last updated:** 2026-08-22
**Status:** Sprint 3 complete (SE-1 through SE-20, 55 scenarios passing)

---

## 1. Bounded Context (Evans)

### What We Are

The Scene Engine is a **pure domain engine** within the `mana-hive` bounded context. It maintains the state of the scene — who is where, since when, and what the sensors see.

### What We Are NOT

| We are NOT | Because |
|-----------|---------|
| Sentinel | We don't judge, alert, or notify |
| Hub | We don't store AlarmProfiles or AlarmCatalogs |
| Politica Engine | We don't resolve policies |
| Sensor | We don't detect — we interpret |

### Ubiquitous Language

| Term | Definition | Example |
|------|-----------|---------|
| **Hysteresis** | Minimum time before confirming a state change | "Maria went from lying to standing — do I confirm after 1.5s?" |
| **Dwell** | Time in state before raising a fact | "Maria has been standing 5 min — do I report after 5 min?" |
| **Confidence** | How certain the sensor is about an observation | "Sensor says Maria is standing — how confident?" |
| **DigitalTwin** | Living record of a bed: who, what state, since when | "Bed 3: Maria, standing, since 03:00:02" |
| **SceneFact** | What the engine asserts to the Hub | "Maria transitioned from lying to standing" |
| **Discard** | What the engine rejected and why | "Confidence too low (0.7 < 0.8)" |
| **Transition** | Change of state | "LYING → BED_EDGE" |
| **StateKind** | Enum of all possible states | "STANDING, BED_EDGE, LYING..." |

### Domain Events (Evans)

```
NightOpened         → A shift begins, twins initialized
TransitionDetected  → Someone changed state (lying → standing)
DwellWarning        → Someone has been in state too long (80%)
DwellExceeded       → Someone has been in state way too long (100%)
StaffPresenceDetected → Staff entered a room
SignalLost          → Sensor stopped reporting
SignalRecovered     → Sensor came back online
NightClosed         → Shift ends, night summary
```

---

## 2. User Stories (Beck/Cohn)

### Epic: Sensor Interpretation

| ID | Story | Acceptance Criteria |
|----|-------|-------------------|
| **SE-1** | AS a nurse, I WANT the system to translate sensor observations INTO clinical states, SO THAT I know what's happening in each room | `ObservationKind.toPersonState()` maps all 14 kinds correctly |
| **SE-2** | AS a nurse, I WANT the system to update bed records when events happen, SO THAT the state is always current | `DigitalTwin.evolve()` handles all 8 SceneFact types immutably |
| **SE-3** | AS a nurse, I WANT the system to ignore low-confidence observations, SO THAT noise doesn't trigger false alerts | Observations below min confidence are discarded with CONFIDENCE_TOO_LOW |
| **SE-4** | AS a nurse, I WANT the system to ignore duplicate observations, SO THAT the same event isn't processed twice | Same-state observations are discarded with DUPLICATE |
| **SE-5** | AS a nurse, I WANT the system to reject illegal state changes, SO THAT impossible transitions don't create false facts | Invalid transitions are discarded with ILLEGAL_TRANSITION |
| **SE-6** | AS a nurse, I WANT the system to require minimum time in state before confirming changes, SO THAT sensor flicker doesn't trigger alerts | Transitions below hysteresis threshold are discarded with HYSTERESIS_NOT_MET |
| **SE-7** | AS a nurse, I WANT the system to emit clear facts when valid transitions occur, SO THAT I can respond appropriately | TransitionDetected emitted with correct from/to states |
| **SE-8** | AS a nurse, I WANT the system to recover when sensors come back online, SO THAT I don't lose visibility | SignalRecovered emitted, twin state restored |

### Epic: Dwell Monitoring

| ID | Story | Acceptance Criteria |
|----|-------|-------------------|
| **SE-9** | AS a nurse, I WANT early warnings when someone has been in a state too long, SO THAT I can intervene before it becomes critical | DwellWarning emitted at warning threshold |
| **SE-10** | AS a nurse, I WANT alerts when someone has been in a state way too long, SO THAT I know immediate action is needed | DwellExceeded emitted at exceeded threshold |
| **SE-11** | AS a nurse, I WANT the system to not duplicate dwell alerts, SO THAT I don't get flooded with the same warning | Idempotent via DwellMarks |
| **SE-12** | AS a nurse, I WANT to know when a sensor goes offline, SO THAT I can send someone to check | SignalLost emitted after heartbeat timeout |

### Epic: Integration

| ID | Story | Acceptance Criteria |
|----|-------|-------------------|
| **SE-13** | AS a nurse, I WANT the full pipeline to work end-to-end, SO THAT the fall at 03:00 is detected correctly | LaCaidaDeLas03Spec: 4 facts emitted correctly |
| **SE-14** | AS a nurse, I WANT different residents to have different sensitivity levels, SO THAT high-risk patients get more attention | Low-risk accepts 0.8 confidence, high-risk requires 0.9 |
| **SE-15** | AS a nurse, I WANT calibration to be preserved through state changes, SO THAT the system remembers each resident's rules | Calibration persists through DigitalTwin.evolve() |
| **SE-16** | AS a nurse, I WANT per-resident interpreters, SO THAT each resident gets appropriate treatment | Different interpreters for different calibrations |

### Epic: Clinical States

| ID | Story | Acceptance Criteria |
|----|-------|-------------------|
| **SE-17** | AS a clinician, I WANT 13 clinical states (not 5), SO THAT I can distinguish between different patient positions | All 13 states work with RELEASE_2 table |

### Epic: Per-Resident Dwell

| ID | Story | Acceptance Criteria |
|----|-------|-------------------|
| **SE-18** | AS a nurse, I WANT each resident to have their own dwell thresholds, SO THAT high-risk patients are monitored more closely | Per-twin catalog resolution via calibration |
| **SE-19** | AS a nurse, I WANT the transition table to cover all 13 states, SO THAT any valid movement is tracked | RELEASE_2 has ~48 transitions covering all state pairs |
| **SE-20** | AS a nurse, I WANT dwell thresholds to update when calibration changes, SO THAT the system adapts to policy updates | DwellCatalog regenerated from updated calibration |

---

## 3. Use Cases (Cockburn)

### Use Case 1: The Fall at 03:00

**Primary Actor:** Nurse
**Stakeholders:** Patient (safety), Nurse (response time), Facility (liability)
**Precondition:** Maria is in bed, sensor is active
**Postcondition:** facts emitted, alert created

**Main Success Scenario:**
```
1. Maria is lying in bed 3 (LYING state)
2. Sensor detects bed edge (BED_EDGE, confidence 0.9)
3. Interpreter validates: confidence OK, legal transition, hysteresis met
4. Twin updated: state = BED_EDGE, since = 03:00:00
5. Fact emitted: TransitionDetected(LYING → BED_EDGE)
6. Sensor detects standing (STANDING, confidence 0.95)
7. Interpreter validates: confidence OK, legal transition, hysteresis met
8. Twin updated: state = STANDING, since = 03:00:02
9. Fact emitted: TransitionDetected(BED_EDGE → STANDING)
10. Sweeper checks at 03:05:02 (5 minutes elapsed)
11. Dwell threshold exceeded (5min ≥ 5min)
12. Fact emitted: DwellExceeded(STANDING, 5min)
13. Hub receives 3 facts → creates alert
```

**Extensions:**
- 2a. Confidence too low (0.7 < 0.8): observation discarded
- 6a. Sensor flickers (BED_EDGE for 800ms): hysteresis not met, discarded
- 6b. Illegal transition (LYING → OUT_OF_ROOM): discarded

### Use Case 2: Sensor Loss

**Primary Actor:** Nurse
**Stakeholders:** Patient (safety), Nurse (visibility)
**Precondition:** Maria is standing, sensor is active
**Postcondition:** SignalLost fact emitted

**Main Success Scenario:**
```
1. Maria is standing (STANDING, since 03:00:02)
2. Sensor goes offline at 03:03:00
3. Sweeper checks at 03:03:05
4. Heartbeat timeout exceeded (3min > 90s)
5. Fact emitted: SignalLost(monitor, lastHeartbeat 03:00:02)
6. Twin updated: signal.lost = true
```

**Extensions:**
- 5a. Sensor comes back online: SignalRecovered emitted, twin restored

### Use Case 3: Per-Resident Calibration

**Primary Actor:** System (Policy Engine)
**Stakeholders:** Nurse (personalized care), Facility (clinical protocols)
**Precondition:** PolicyEngine has resolved calibration for Maria
**Postcondition:** Maria's dwell thresholds updated

**Main Success Scenario:**
```
1. Hub updates Maria's AlarmProfile
2. PolicyEngine resolves PolicyCalibration
3. CalibrationChanged arrives at Scene Engine
4. DigitalTwin.calibration updated for Maria
5. Next sweep uses Maria's custom thresholds
6. Maria gets exceeded at 3min (not default 5min)
```

---

## 4. BDD Specs (North)

### Spec: Observation Mapping

```gherkin
Feature: ObservationKind to PersonState translation
  As a nurse
  I want sensor observations translated to clinical states
  So that I understand what's happening

  Scenario: IN_BED maps to LYING
    Given an ObservationKind IN_BED
    When translated to PersonState
    Then the result is Lying

  Scenario: BED_EDGE maps to BedEdge
    Given an ObservationKind BED_EDGE
    When translated to PersonState
    Then the result is BedEdge

  Scenario: OUT_OF_ROOM maps to Absent
    Given an ObservationKind OUT_OF_ROOM
    When translated to PersonState
    Then the result is Absent

  Scenario: HEARTBEAT does not change state
    Given an ObservationKind HEARTBEAT
    When translated to PersonState
    Then the result is Lying

  Scenario: UNCLASSIFIED maps to Unknown
    Given an ObservationKind UNCLASSIFIED
    When translated to PersonState
    Then the result is Unknown(SCENE)
```

### Spec: Confidence Filtering

```gherkin
Feature: Confidence-based observation filtering
  As a nurse
  I want low-confidence observations ignored
  So that noise doesn't trigger false alerts

  Scenario: Observation below minimum confidence
    Given an interpreter with minConfidence BED_EDGE = 0.8
    And a twin in LYING state
    When BED_EDGE observation arrives with confidence 0.7
    Then the observation is discarded with CONFIDENCE_TOO_LOW
    And the twin does not change
    And no facts are emitted

  Scenario: Observation above minimum confidence
    Given an interpreter with minConfidence BED_EDGE = 0.8
    And a twin in LYING state
    When BED_EDGE observation arrives with confidence 0.9
    Then no confidence discard occurs
```

### Spec: Hysteresis

```gherkin
Feature: Hysteresis enforcement
  As a nurse
  I want minimum time in state before confirming changes
  So that sensor flicker doesn't trigger alerts

  Scenario: Transition rejected before hysteresis
    Given an interpreter
    And a twin in LYING state since 1 second ago
    When BED_EDGE observation arrives (hysteresis = 1500ms)
    Then the observation is discarded with HYSTERESIS_NOT_MET
    And the twin does not change

  Scenario: Transition accepted after hysteresis
    Given an interpreter
    And a twin in LYING state since 2 seconds ago
    When BED_EDGE observation arrives (hysteresis = 1500ms)
    Then no hysteresis discard occurs
    And the state changes to BedEdge
    And TransitionDetected is emitted
```

### Spec: Dwell Warning

```gherkin
Feature: Dwell warning threshold
  As a nurse
  I want early warnings when someone has been in a state too long
  So that I can intervene before it becomes critical

  Scenario: Warning emitted at threshold
    Given a sweeper
    And a twin in STANDING state since 4 minutes
    And STANDING warning threshold = 5 minutes
    When sweep occurs at 03:04:00
    Then DwellWarning is emitted
    And the warning mark is recorded

  Scenario: Warning not emitted before threshold
    Given a sweeper
    And a twin in STANDING state since 3 minutes
    And STANDING warning threshold = 5 minutes
    When sweep occurs at 03:03:00
    Then no DwellWarning is emitted
```

### Spec: Dwell Exceeded

```gherkin
Feature: Dwell exceeded threshold
  As a nurse
  I want alerts when someone has been in a state way too long
  So that I know immediate action is needed

  Scenario: Exceeded emitted at threshold
    Given a sweeper
    And a twin in STANDING state since 5 minutes
    And STANDING exceeded threshold = 5 minutes
    When sweep occurs at 03:05:00
    Then DwellExceeded is emitted
    And the exceeded mark is recorded

  Scenario: Exceeded not emitted before threshold
    Given a sweeper
    And a twin in STANDING state since 4 minutes
    And STANDING exceeded threshold = 5 minutes
    When sweep occurs at 03:04:00
    Then no DwellExceeded is emitted
```

### Spec: Idempotency

```gherkin
Feature: Dwell idempotency
  As a nurse
  I want the system to not duplicate dwell alerts
  So that I don't get flooded with the same warning

  Scenario: Second sweep produces no new facts
    Given a sweeper
    And a twin in STANDING state since 5 minutes
    When sweep occurs twice with the same timestamp
    Then only 1 DwellExceeded is emitted total
    And marks has only 1 entry
```

### Spec: Signal Lost

```gherkin
Feature: Sensor loss detection
  As a nurse
  I want to know when a sensor goes offline
  So that I can send someone to check

  Scenario: Signal lost after timeout
    Given a sweeper
    And a twin with lastHeartbeat = 2 minutes ago
    And heartbeatTimeout = 90 seconds
    When sweep occurs
    Then SignalLost is emitted
    And signal.lost is true

  Scenario: Signal alive within timeout
    Given a sweeper
    And a twin with lastHeartbeat = 30 seconds ago
    And heartbeatTimeout = 90 seconds
    When sweep occurs
    Then no SignalLost is emitted
```

### Spec: 13 Clinical States

```gherkin
Feature: Clinical state transitions (RELEASE_2)
  As a clinician
  I want 13 clinical states
  So that I can distinguish between different patient positions

  Scenario Outline: Valid transitions
    Given an interpreter with RELEASE_2 table
    And a twin in <from> state
    When <observation> arrives
    Then the state changes to <to>

    Examples:
      | from    | observation     | to            |
      | LYING   | SITTING_IN_BED  | SittingInBed  |
      | LYING   | ATTEMPTING_EXIT | AttemptingExit|
      | LYING   | BED_EDGE        | BedEdge       |
      | BED_EDGE| STANDING        | Standing      |
      | STANDING| IN_BATHROOM     | InBathroom    |
      | STANDING| IN_ROOM         | InRoom        |
      | STANDING| IN_HALLWAY      | InHallway     |
      | STANDING| OUTDOOR         | Outdoor       |
      | STANDING| IN_CHAIR        | InChair       |
      | STANDING| IN_WHEELCHAIR   | InWheelchair  |
      | STANDING| OUT_OF_ROOM     | Absent        |

  Scenario: Illegal transition rejected
    Given an interpreter with RELEASE_2 table
    And a twin in LYING state
    When STANDING observation arrives
    Then the observation is discarded with ILLEGAL_TRANSITION
```

### Spec: Per-Resident Dwell

```gherkin
Feature: Per-resident dwell thresholds
  As a nurse
  I want each resident to have their own dwell thresholds
  So that high-risk patients are monitored more closely

  Scenario: Different thresholds for different residents
    Given Maria with calibration warning=3min exceeded=5min
    And Jose with calibration warning=2min exceeded=3min
    And both twins in STANDING state for 4 minutes
    When sweep occurs
    Then Maria receives DwellWarning (threshold 3min, 4min >= 3min)
    And Jose receives DwellExceeded (threshold 3min, 4min >= 3min)

  Scenario: Fallback to default catalog
    Given a twin without calibration
    And default catalog warning=4min exceeded=5min
    And twin in STANDING state for 4 minutes
    When sweep occurs
    Then DwellWarning is emitted (default threshold)

  Scenario: Calibration update triggers regeneration
    Given a twin with calibration warning=2min exceeded=3min
    And twin in STANDING state for 3 minutes
    When sweep occurs
    Then DwellExceeded is emitted (calibration threshold 3min)
```

---

## 5. Domain Model (Evans)

### Entities

| Entity | Identity | Lifecycle |
|--------|----------|-----------|
| **DigitalTwin** | BedId + NightId | Created at NightOpened, evolves with facts |
| **DwellMarks** | Set of DwellMarkKey | Accumulated across sweeps |

### Value Objects

| Value Object | Properties | Immutable |
|-------------|-----------|-----------|
| **PersonState** | kind: StateKind | Yes |
| **SceneCalibration** | table, confidence, heartbeat, dwell | Yes |
| **DwellCatalog** | byState: Map, heartbeatTimeout | Yes |
| **TransitionTable** | rules: Map | Yes |
| **DwellThreshold** | warning, exceeded | Yes |
| **SignalHealth** | monitor, lastHeartbeat, lost | Yes |

### Aggregates

```
DigitalTwin (Root)
├── bed: BedId
├── night: NightId
├── occupant: ResidentId?
├── state: PersonState
├── stateSince: Instant
├── signal: SignalHealth
└── calibration: SceneCalibration?
```

### Bounded Context Map

```
┌─────────────────────────────────────────────────────────────────┐
│  Hub (System of Record)                                         │
│  ├── AlarmProfile (versioned entity)                            │
│  ├── AlarmCatalog (TOML catalog)                                │
│  └── Emits: PolicyChanged                                       │
└─────────────────────────────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│  Politica Engine (new)                                          │
│  ├── PolicyChangeProcessor (Integration Pattern)                │
│  ├── PolicyResolver (pure service)                              │
│  ├── CalibrationProvider (port)                                 │
│  └── Emits: CalibrationChanged                                  │
└─────────────────────────────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│  Scene Engine                                                   │
│  ├── SceneInterpreter (per resident)                            │
│  ├── DigitalTwin (with calibration)                             │
│  ├── ClockSweeper (with DwellCatalog)                           │
│  └── Emits: SceneFact                                           │
└─────────────────────────────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│  Sentinel                                                       │
│  └── Judges SceneFact per clinical rules                        │
└─────────────────────────────────────────────────────────────────┘
```

---

## 6. Acceptance Test Matrix

| Story | Spec | Scenarios | Status |
|-------|------|-----------|--------|
| SE-1 | ObservationKindMappingSpec | 7 | ✅ |
| SE-2 | DigitalTwinEvolutionSpec | 4 | ✅ |
| SE-3 | SceneInterpreterConfidenceSpec | 2 | ✅ |
| SE-4 | SceneInterpreterDuplicateSpec | 1 | ✅ |
| SE-5 | SceneInterpreterIllegalSpec | 1 | ✅ |
| SE-6 | SceneInterpreterHysteresisSpec | 2 | ✅ |
| SE-7 | SceneInterpreterTransitionSpec | 1 | ✅ |
| SE-8 | SceneInterpreterSensorRecoverySpec | 2 | ✅ |
| SE-9 | ClockSweeperWarningSpec | 2 | ✅ |
| SE-10 | ClockSweeperExceededSpec | 2 | ✅ |
| SE-11 | ClockSweeperIdempotentSpec | 1 | ✅ |
| SE-12 | ClockSweeperSignalLostSpec | 2 | ✅ |
| SE-13 | LaCaidaDeLas03Spec | 1 | ✅ |
| SE-14 | SceneCalibrationReceivedSpec | 1 | ✅ |
| SE-15 | DigitalTwinWithCalibrationSpec | 1 | ✅ |
| SE-16 | SceneInterpreterPerResidentSpec | 1 | ✅ |
| SE-17 | PersonStateElevenSpec | 12 | ✅ |
| SE-18 | DwellCatalogSpec | 5 | ✅ |
| SE-19 | TransitionTableThirteenSpec | 6 | ✅ |
| SE-20 | DwellCatalogSpec | (included) | ✅ |
| **Total** | **20 specs** | **~55 scenarios** | **✅** |

---

## 7. Build

```bash
./gradlew :engines:politica-engine:politica-domain:test :engines:scene-engine:scene-domain:test
```

---

*Spec-driven, behavior-first, domain-aligned.*
