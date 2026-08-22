# Scene Engine — Design

**Last updated:** 2026-08-22
**Status:** Sprint 3 complete (SE-1 through SE-20)

---

## 1. Components — Who Does What

```mermaid
C4Component
    title Scene Engine — Components

    Container_Boundary(scene, "Scene Engine") {
        Component(interpreter, "SceneInterpreter", "Pure function", "Translates observation → facts. Checks confidence, hysteresis, legal transitions")
        Component(sweeper, "ClockSweeper", "Pure function", "Counts time in state. Emits DwellWarning/DwellExceeded/SignalLost")
        Component(twins, "DigitalTwin", "Data class", "Living record of a bed: who, what state, since when, sensor health")
    }

    System_Ext(sensor, "Sensor / AI Cell", "Sees what happens in the room")
    System_Ext(hub, "Hub", "Central system — receives facts and decides")
    System_Ext(sentinel, "Sentinel", "Judges if alert is needed per clinical rules")

    Rel(sensor, interpreter, "Sends: 'I saw someone at bed edge'")
    Rel(interpreter, twins, "Updates bed record")
    Rel(interpreter, hub, "Emits: 'this happened' or 'discarded this because'")
    Rel(sweeper, twins, "Reads all records and counts time")
    Rel(sweeper, hub, "Emits: 'has been X minutes in this state'")
    Rel(hub, sentinel, "Passes facts for judgment")
```

### Responsibility Table

| Component | What it does | What it does NOT do | Example |
|-----------|-------------|---------------------|---------|
| **Interpreter** | Translates sensor → fact | Decides if alerting | "Maria went from lying to standing" |
| **Sweeper** | Counts time in state | Knows clinical rules | "Has been standing 5 minutes" |
| **Twin** | Stores current bed state | Stores history | "Bed 3: Maria, standing, since 03:00:02" |

---

## 2. Night Lifecycle

```mermaid
flowchart TD
    START[Anochece] --> OPEN["Director says: 'Open shift'<br/>Each bed has its twin"]
    OPEN --> WAIT[Waiting for sensors]
    
    WAIT --> OBS["Sensor sees something<br/>Observation arrives"]
    OBS --> INTERPRET{"What does sensor see?"}
    
    INTERPRET -->|"Normal"| UPDATE["Interpreter updates twin<br/>Emits fact"]
    INTERPRET -->|"Noise / tremor"| DISCARD["Interpreter discards<br/>Records why"]
    INTERPRET -->|"Duplicate"| NOP["No-op<br/>Already seen"]
    
    UPDATE --> SWEEP["Sweeper checks all beds<br/>Any been too long?"]
    DISCARD --> SWEEP
    NOP --> SWEEP
    
    SWEEP -->|"Nothing new"| WAIT
    SWEEP -->|"Time exceeded"| FACT["Dwell fact<br/>Sent to Hub"]
    
    FACT --> WAIT
    
    WAIT -->|"30 min no signal"| LOST["Sensor lost<br/>SignalLost fact"]
    LOST --> WAIT
    
    WAIT -->|"Shift changes"| CLOSE["Close shift<br/>Night summary"]
    
    style START fill:#1a1a2e,color:#fff
    style CLOSE fill:#1a1a2e,color:#fff
    style DISCARD fill:#e74c3c,color:#fff
    style NOP fill:#95a5a6,color:#fff
    style FACT fill:#e67e22,color:#fff
    style LOST fill:#c0392b,color:#fff
```

---

## 3. Sequence: Maria's Fall at 03:00

```mermaid
sequenceDiagram
    autonumber
    participant S as Sensor
    participant I as Interpreter
    participant G as Twin (Bed 3)
    participant R as Sweeper
    participant H as Hub
    participant SN as Sentinel

    Note over H: 03:00 — Maria sleeps in bed 3

    S->>I: "BED_EDGE, confidence 0.9, 03:00:00"
    I->>I: Legal LYING → BED_EDGE? YES<br/>Confidence ≥ min? YES<br/>Hysteresis met? YES (first time)
    I->>G: Update: state = BED_EDGE, since = 03:00:00
    I->>H: TransitionDetected(LYING → BED_EDGE)

    S->>I: "STANDING, confidence 0.95, 03:00:02"
    I->>I: Legal BED_EDGE → STANDING? YES
    I->>G: Update: state = STANDING, since = 03:00:02
    I->>H: TransitionDetected(BED_EDGE → STANDING)

    Note over R: 03:00:07 — Tick (every 5s)

    R->>G: How long standing? 5 seconds
    R->>R: 5s < 5min → nothing

    Note over R: 03:05:02 — 5 minutes passed

    R->>G: How long standing? 5 minutes
    R->>R: 5min ≥ 5min → fact!
    R->>H: DwellExceeded(STANDING, 5min, since 03:00:02)
    H->>SN: "Maria 5 min standing at 3am"

    SN->>SN: Rule: "Night + Standing + 5min = CRITICAL"
    SN->>H: IncidentDeclared → Alert

    Note over H: 03:05:10 — Nurse arrives, Maria returns to bed

    S->>I: "LYING, confidence 0.98, 03:05:10"
    I->>I: Legal STANDING → LYING? YES
    I->>G: Update: state = LYING, since = 03:05:10
    I->>H: TransitionDetected(STANDING → LYING)
```

---

## 4. Sequence: Sensor Loss

```mermaid
sequenceDiagram
    autonumber
    participant S as Sensor
    participant I as Interpreter
    participant G as Twin (Bed 3)
    participant R as Sweeper
    participant H as Hub

    Note over S,H: Maria standing, sensor working

    S->>I: "STANDING, 03:00:02"
    I->>G: state = STANDING, since = 03:00:02

    Note over S: 03:03:00 — Sensor goes off

    Note over R: 03:03:05 — Tick

    R->>G: Last heartbeat?
    R->>R: 03:00:02 → 3 minutes ago
    R->>R: 3min > 90s (threshold) → Signal lost
    R->>G: Update: signal.lost = true
    R->>H: SignalLost(monitor, lastHeartbeat 03:00:02)

    Note over R: 03:05:00 — Sensor returns

    S->>I: "STANDING, 03:05:00"
    I->>G: Update: signal.lost = false
    I->>H: SignalRecovered(monitor)
```

---

## 5. Sequence: Sensor Noise

```mermaid
sequenceDiagram
    autonumber
    participant S as Sensor
    participant I as Interpreter
    participant G as Twin (Bed 3)
    participant H as Hub

    Note over S,H: Maria quiet in bed

    S->>I: "BED_EDGE, confidence 0.7, 03:01:00"
    I->>I: Confidence 0.7 ≥ min (0.8)? NO
    I->>H: Discard: CONFIDENCE_TOO_LOW

    S->>I: "LYING, confidence 0.95, 03:01:01"
    I->>I: Legal LYING → LYING? Same state
    I->>H: Discard: DUPLICATE

    Note over S: 03:01:03 — Sensor flickers

    S->>I: "BED_EDGE, confidence 0.9, 03:01:03"
    I->>I: Legal LYING → BED_EDGE? YES<br/>Hysteresis? 1500ms minimum
    I->>G: Note: transition pending since 03:01:03

    S->>I: "LYING, confidence 0.95, 03:01:04"
    I->>I: 1500ms passed? NO (only 1s)
    I->>H: Discard: HYSTERESIS_NOT_MET
```

---

## 6. Interpreter Pipeline — Pseudocode

```
interpret(twin, observation, now):

  1. CONFIDENCE
     if observation.confidence < calibration.minConfidence[observation.kind]:
         DISCARD → CONFIDENCE_TOO_LOW

  2. SENSOR RECOVERY
     if twin.signal.lost AND kind != HEARTBEAT:
         twin = twin.copy(signal = signal.copy(lost = false))
         facts += SignalRecovered
         // continue evaluating

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
     facts += TransitionDetected(from, to)
```

---

## 7. Sweeper Pipeline — Pseudocode

```
sweep(twins, now, catalog, marks):

  FOR EACH twin IN twins:

    // Per-twin catalog resolution (SE-18)
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
               facts += DwellWarning

    6. EXCEEDED
       if duration >= threshold.exceeded:
           facts += DwellExceeded

    7. SIGNAL LOST
       if heartbeat timeout exceeded:
           facts += SignalLost

  return SweepResult(facts, DwellMarks(marks.emitted + newMarks))
```

---

## 8. Test Scenarios

### A. The Fall at 03:00 ✅
```
GIVEN: Maria in bed 3, asleep, sensor alive
WHEN: sensor sees bed edge → standing → 5 min standing
THEN:
  1. TransitionDetected(LYING → BED_EDGE)
  2. TransitionDetected(BED_EDGE → STANDING)
  3. DwellExceeded(STANDING, 5min)
  → Hub receives 3 facts and can alert
```

### B. Quiet Night ✅
```
GIVEN: 30 beds, all residents asleep
WHEN: 8 hours pass with nothing
THEN:
  1. Zero transition facts
  2. Zero dwell facts
  3. System doesn't scream
  → Silence = normalcy
```

### C. Sensor Loss ✅
```
GIVEN: Maria standing, sensor alive
WHEN: sensor loses signal for 90 seconds
THEN:
  1. SignalLost(monitor, last heartbeat)
  2. Twin goes to UNKNOWN(SIGNAL_LOST)
  → System says "I don't know" instead of assuming all is well
```

### D. Sensor Noise ✅
```
GIVEN: Maria lying
WHEN: BED_EDGE 800ms, back to LYING
THEN: DISCARD (hysteresis not met)
```

### E. Illegal Transition ✅
```
GIVEN: Maria lying
WHEN: OUT_OF_ROOM (without going through standing)
THEN: DISCARD (illegal transition)
```

---

## 9. What Scene Engine Does NOT Need to Know

| Doesn't need | Why |
|--------------|-----|
| Maria's name | Only knows "bed 3 has someone standing" |
| That it's night | Only knows "the observation arrived at 03:00" |
| That 5 minutes is serious | Only knows "has been 5 min in this state" — someone else decides |
| That a nurse is in the hallway | That's staff sensor, suppressed by Sentinel |
| That an alert was sent before | That's Sentinel with its episodes |

---

*Design reference — derived from code. The code is the truth.*
