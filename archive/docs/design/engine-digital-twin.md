# Design: Engine Digital Twin

## Purpose

Defines the in-memory digital twin the engine maintains per bed/scene. It is the source of truth for the engine — state is never re-hydrated from the hub on every evaluation.

## Core Structs

```rust
struct DigitalTwin {
    beds: HashMap<String, BedTwin>,
    dwell_rules: DwellRules,
}

struct BedTwin {
    bed_id: String,
    resident_id: Option<String>,
    person: PersonTwin,
    objects: ObjectStates,
    room: RoomTwin,
    transitions: Vec<FsmTransition>,
    extremities: Extremities,
    timers: Vec<Timer>,
}

struct PersonTwin {
    state: PersonState,       // Lying, SittingInBed, BedEdge, Standing, InBathroom, etc.
    state_since: DateTime<Utc>,
    location: Location,
    sleeping: Option<bool>,
    confidence: f64,
}

struct ObjectStates {
    bed: BedOccupancy,
    chair: ObjectOccupancy,
    wheelchair: ObjectOccupancy,
    walker: ObjectPresence,
}

struct RoomTwin {
    occupancy: RoomOccupancy,
    resident_count: i32,
    staff_count: i32,
    visitor_count: i32,
}

struct Extremities {
    out_of_bed: bool,
    body_parts_out: Vec<String>,
}

struct Timer {
    rule_id: String,           // e.g. "dwell_Lying", "dwell_Standing"
    bed_id: String,
    state: TimerState,
    started_at: DateTime<Utc>,
    threshold_minutes: i32,
}

enum TimerState { Active, Fired, Cancelled }

struct DwellRules {
    out_of_bed: i32,           // default 10 min
    in_bed: i32,               // default 300 min
    standing: i32,             // default 5 min
    in_bathroom: i32,          // default 30 min
}
```

`DwellRules::threshold_for_state` maps each `PersonState` to its threshold:
- Lying / SittingInBed / BedEdge → `in_bed`
- Standing → `standing`
- InBathroom → `in_bathroom`
- InRoom / InHallway / Outdoor / InChair / InWheelchair → `out_of_bed`
- Unknown → `None` (no timer)

## `on_perception_event` flow

```
1. Extract bed_id (return early if None)
2. Clone dwell_rules, get_or_create_bed
3. Map event → new PersonState + new Location
4. If state changed:
   a. cancel_timers_for_state(old_state)  — marks Active timers with matching rule_id as Cancelled
   b. Update person.state, state_since, location, confidence
   c. start_timers_for_state(new_state)  — pushes a new Timer with threshold from DwellRules
   d. Record FsmTransition in bed.transitions
   e. Emit SceneEvent::transition (scene event type = Transition)
5. update_objects(event) — parses event.objects JSON for bed/chair/wheelchair/walker
6. update_room(event)    — parses event.objects JSON for room occupancy and counts
7. Update extremities from event
8. Emit SceneEvent::perception (scene event type = Perception)
```

Return value: always 2 events (transition + perception) when state changed, or 1 event (perception only) when state unchanged. Returns empty if bed_id is None.

## `tick` flow

```
1. For each bed:
   a. Collect fired rules: for each Active timer, if elapsed >= threshold → mark Fired
   b. For each fired rule, emit SceneEvent::dwell (trigger = DwellCompleted)
2. After processing all beds, clean up:
   - bed.timers.retain(|t| t.state == Active)  — removes Fired and Cancelled timers
3. Return all emitted SceneEvents
```

Timer cancellation on tick is explicit: only Active timers that cross their threshold are consumed. Fired timers are cleaned after the loop completes.

## Invariants

1. Twin is the source of truth — never re-hydrated from hub per evaluation
2. State transitions always cancel old timers and start new ones
3. Each PersonState maps to exactly one dwell threshold (or None for Unknown)
4. Timers are per-bed, keyed by `rule_id = "dwell_{state:?}"`
5. Extremities are set directly from perception events, not derived from FSM
6. transitions is append-only (Vec<FsmTransition>), never pruned
7. Persistence is in-memory only; no DB or event-sourcing in current code
