# Scene Engine — Big Picture

**Last updated:** 2026-08-22
**Status:** Sprint 3 complete (SE-1 through SE-20)

---

## 1. The Domain Truth

### What Scene Engine Does

```
┌─────────────────────────────────────────────────────────────────────┐
│                     SCENE ENGINE                                     │
│                     Maintains the state of the scene                │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  DOES:                                                               │
│  ├── Receives perception events (from sensors)                      │
│  ├── Updates digital twins (bed state)                              │
│  ├── Detects state changes (transitions)                            │
│  ├── Detects dwell time (time in state)                             │
│  └── Emits scene facts (to Hub)                                     │
│                                                                      │
│  DOES NOT:                                                          │
│  ├── Decide what is important                                       │
│  ├── Know clinical rules                                            │
│  ├── Alert or notify                                                │
│  └── Judge                                                         │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### Our Vocabulary

| Term | What it means | Example |
|------|--------------|---------|
| **Hysteresis** | Time to confirm a state change | "Maria went from lying to standing — do I confirm after 1.5s?" |
| **Dwell** | Time in state before raising a fact | "Maria has been standing 5 min — do I report after 5 min?" |
| **Confidence** | How certain the sensor is | "Sensor says Maria is standing — how confident?" |
| **State** | What is happening in the world | "Maria is standing" |
| **Transition** | Change of state | "Maria went from lying to standing" |
| **Twin** | Living record of a bed | "Bed 3: Maria, standing, since 03:00:02" |

### What is NOT our vocabulary

| Term | Belongs to |
|------|-----------|
| Alarm, Alert, Notification | Sentinel |
| RiskLevel, AlarmProfile | Hub |
| EffectivePolicy | Politica Engine |

---

## 2. The World States — 13 PersonStates

```
┌─────────────────────────────────────────────────────────────────────┐
│                     PERSON STATES (13)                               │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  IN BED (in_bed)                                                    │
│  ├── LYING              → Acostada                                  │
│  ├── SITTING_IN_BED     → Incorporada en la cama                    │
│  ├── ATTEMPTING_EXIT    → Gusanito (arms/face at bed edge)          │
│  └── BED_EDGE           → Sentada al borde de la cama               │
│                                                                      │
│  OUT OF BED (out_of_bed)                                            │
│  ├── STANDING           → De pie                                    │
│  ├── IN_BATHROOM        → En el baño                                │
│  ├── IN_ROOM            → En la habitación                          │
│  ├── IN_HALLWAY         → En el pasillo                             │
│  └── OUTDOOR            → Afuera                                    │
│                                                                      │
│  FURNITURE                                                           │
│  ├── IN_CHAIR           → En la silla                               │
│  └── IN_WHEELCHAIR      → En la silla de ruedas                     │
│                                                                      │
│  SPECIAL                                                             │
│  ├── ABSENT             → OUT_OF_ROOM (location undetermined)       │
│  └── UNKNOWN            → Sensor lost / recovery                    │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### StateKind ↔ PersonState Mapping

```
StateKind            PersonState
──────────────       ──────────────────────
LYING                PersonState.Lying
SITTING_IN_BED       PersonState.SittingInBed
ATTEMPTING_EXIT      PersonState.AttemptingExit
BED_EDGE             PersonState.BedEdge
STANDING             PersonState.Standing
IN_BATHROOM          PersonState.InBathroom
IN_ROOM              PersonState.InRoom
IN_HALLWAY           PersonState.InHallway
OUTDOOR              PersonState.Outdoor
ABSENT               PersonState.Absent
IN_CHAIR             PersonState.InChair
IN_WHEELCHAIR        PersonState.InWheelchair
UNKNOWN              PersonState.Unknown(cause)
```

### Clinical Decisions

| Decision | Rationale |
|----------|-----------|
| **AttemptingExit** kept as state | "Gusanito" — arms/face at bed edge trying to exit. High fall risk state. |
| **Absent** kept as 12th state | Maps from `OUT_OF_ROOM` perception. Location undetermined. |
| **BedEdge** not renamed | Retains name from original design. |

### ObservationKind → PersonState (14 kinds)

```
IN_BED         → LYING
SITTING_IN_BED → SITTING_IN_BED
ATTEMPTING_EXIT→ ATTEMPTING_EXIT
BED_EDGE       → BED_EDGE
STANDING       → STANDING
IN_BATHROOM    → IN_BATHROOM
IN_ROOM        → IN_ROOM
IN_HALLWAY     → IN_HALLWAY
OUTDOOR        → OUTDOOR
IN_CHAIR       → IN_CHAIR
IN_WHEELCHAIR  → IN_WHEELCHAIR
OUT_OF_ROOM    → ABSENT
STAFF_IN_ROOM  → LYING  (staff doesn't change person state)
HEARTBEAT      → LYING  (heartbeat doesn't change state)
UNCLASSIFIED   → Unknown(SCENE)
```

---

## 3. The Scene Objects

### DigitalTwin — The Living Record of a Bed

```
┌─────────────────────────────────────────────────────────────────────┐
│                     DIGITAL TWIN                                     │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  bed: BedId                                                         │
│  night: NightId                                                     │
│  occupant: ResidentId?                                              │
│  state: PersonState                                                 │
│  stateSince: Instant                                                │
│  signal: SignalHealth                                               │
│    ├── monitor: MonitorId                                           │
│    ├── lastHeartbeat: Instant                                       │
│    └── lost: Boolean                                                │
│  calibration: SceneCalibration? = null                              │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 4. Scene Facts — What We Emit

```
┌─────────────────────────────────────────────────────────────────────┐
│                     SCENE FACT (8 cases)                             │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  NightOpened(bed, night, at, occupant, initialState, stateSince)    │
│  TransitionDetected(bed, night, at, from, to)                       │
│  DwellWarning(bed, night, at, state, threshold, since)              │
│  DwellExceeded(bed, night, at, state, threshold, since)             │
│  StaffPresenceDetected(bed, night, at, staff?)                      │
│  SignalLost(bed, night, at, monitor, lastHeartbeat)                 │
│  SignalRecovered(bed, night, at, monitor)                           │
│  NightClosed(bed, night, at, summary)                               │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 5. Use Cases from Blueprints

### Bed Exit

```
1. Initial: Maria lying in bed 118-A
   → Perception: state="IN_BED"
   → SceneFact: Perception (no alert)

2. Maria stands up:
   → Perception: state="STANDING"
   → SceneFact: TransitionDetected(LYING → STANDING)
   → Rule "bed_exit" activates → Hub creates alert

3. Maria still standing (repeated event):
   → Perception: state="STANDING" (new)
   → SceneFact: (none — DUPLICATE discarded)
   → No duplicate alert (idempotency)
```

### Dwell (Time in State)

```
1. Initial: Maria lying (10 min ago)
   → Timer: standing starts at 0 min

2. Maria stands up:
   → Timer: standing starts (0 min)

3. After 5 min:
   → DwellExceeded(STANDING, 5min)
   → Rule "out_of_bed_dwell" activates → alert
```

### Per-Resident Calibration (Sprint 2+)

```
1. Hub updates Maria's AlarmProfile
   → PolicyChanged(residentId: "maria", version: 2)

2. PolicyChangeProcessor receives PolicyChangeDetected
   → Resolves PolicyCalibration via PolicyResolver
   → Converts to SceneCalibration

3. CalibrationChanged arrives at Scene Engine
   → DigitalTwin.calibration updated
   → Next sweep uses new DwellCatalog automatically
```

---

## 6. How It All Fits Together

```
┌─────────────────────────────────────────────────────────────────────┐
│                     DATA FLOW                                        │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  SENSOR (Perception)                                                │
│  ├── Detects: "Maria is standing"                                   │
│  ├── Confidence: 0.95                                               │
│  └── Emits: Observation                                             │
│                           │                                         │
│                           ▼                                         │
│  SCENE ENGINE                                                       │
│  ├── SceneInterpreter: translates observation → facts               │
│  ├── DigitalTwin: updated with new state                            │
│  ├── ClockSweeper: counts time in state                             │
│  └── Emits: SceneFact                                               │
│                           │                                         │
│                           ▼                                         │
│  HUB                                                                │
│  ├── Receives: SceneFact                                            │
│  ├── Evaluates: SceneCalibration per resident                       │
│  └── Decides: "bed_exit" → create alert                             │
│                           │                                         │
│                           ▼                                         │
│  SENTINEL                                                           │
│  ├── Receives: alert                                                │
│  ├── Evaluates: clinical rules                                      │
│  └── Decides: notify or not                                         │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### What We Take From Policy

| From Policy | What we do with it |
|-------------|-------------------|
| **Hysteresis** | Validate transitions |
| **Dwell thresholds** | Configure timers |
| **Min confidence** | Filter observations |
| **Heartbeat timeout** | Detect sensor lost |

### What We Do NOT Take From Policy

| From Policy | Why not |
|-------------|---------|
| RiskLevel | That's for Sentinel |
| MobilityAid | That's for UI |
| AlarmProfile | That's for Hub |
| AlarmCatalog | That's for Hub |
| EffectivePolicy | That's for Politica Engine |

---

## 7. The Two Engines (Sprint 2+)

```
┌─────────────────────────────────────────────────────────────────────┐
│                     TWO ENGINES                                      │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  POLITICA ENGINE                                                    │
│  ├── Receives: PolicyChanged (from Hub)                             │
│  ├── Resolves: PolicyCalibration per resident                       │
│  └── Emits: CalibrationChanged                                      │
│                           │                                         │
│                           ▼                                         │
│  SCENE ENGINE                                                       │
│  ├── Receives: CalibrationChanged                                   │
│  ├── Updates: DigitalTwin.calibration                               │
│  ├── Interprets: observations per resident                          │
│  ├── Sweeps: dwell per resident                                     │
│  └── Emits: SceneFact                                               │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

---

*Destilado del dominio, los blueprints, y la referencia Rust.*
