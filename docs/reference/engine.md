# Engine Reference

`mana-engine-v2` maintains per-bed digital twins, processes perception events, and emits scene events.

## DigitalTwin

```rust
struct DigitalTwin {
    beds: HashMap<String, BedTwin>,
    dwell_rules: DwellRules,
}
```

Top-level container. Beds are lazily created via `get_or_create_bed()`.

## BedTwin

```rust
struct BedTwin {
    bed_id: String,
    resident_id: Option<String>,
    person: PersonTwin,
    objects: ObjectStates,
    room: RoomTwin,
    extremities: Extremities,
    transitions: Vec<FsmTransition>,
    timers: Vec<Timer>,
}
```

**PersonTwin** — `state: PersonState`, `state_since: DateTime<Utc>`, `location: Location`, `sleeping: Option<bool>`, `confidence: f64`.

**ObjectStates** — `bed: BedOccupancy`, `chair: ObjectOccupancy`, `wheelchair: ObjectOccupancy`, `walker: ObjectPresence`. Occupancy is `Occupied | Empty | Unknown`; presence is `Present | Absent | Unknown`.

**RoomTwin** — `occupancy: RoomOccupancy` (`Empty | Resident | Staff | ResidentAndStaff | ResidentAndVisitor`), `resident_count`, `staff_count`, `visitor_count`.

**Extremities** — `out_of_bed: bool`, `body_parts_out: Vec<String>`.

## FSM

Embedded in `BedTwin.transitions`. Uses `PersonState` (11 variants):

| Group | States |
|-------|--------|
| In bed | `Lying`, `SittingInBed`, `BedEdge` |
| Out of bed | `Standing`, `InBathroom`, `InRoom`, `InHallway`, `Outdoor` |
| Furniture | `InChair`, `InWheelchair` |
| Unknown | `Unknown` |

`valid_transitions(from)` returns legal targets. `is_in_bed()` / `is_out_of_bed()` classify states. `PersonState::parse(s)` maps strings.

```rust
struct FsmTransition { from: PersonState, to: PersonState, at: DateTime<Utc>, confidence: f64 }
```

`on_perception_event()` maps raw `state` string to `PersonState`. If changed: cancels old-state timers, updates state/location/timestamp, starts new-state timers, records transition, emits `Transition` scene event. Objects and room are always updated.

## DwellRules

```rust
struct DwellRules { out_of_bed: i32, in_bed: i32, standing: i32, in_bathroom: i32 }
// defaults: 10, 300, 5, 30 minutes
```

`threshold_for_state()` maps each `PersonState` to its threshold. `Lying`/`SittingInBed`/`BedEdge` use `in_bed`; `Standing` uses `standing`; `InBathroom` uses `in_bathroom`; other out-of-bed states use `out_of_bed`. `Unknown` returns `None`.

### Timer

```rust
struct Timer { rule_id: String, bed_id: String, state: TimerState, started_at: DateTime<Utc>, threshold_minutes: i32 }
```

`TimerState`: `Active | Fired | Cancelled`. `evaluate(now)` fires if elapsed >= threshold. `cancel()` cancels active timers. `rule_id` format: `"dwell_{state:?}"`. Old-state timers are cancelled on transition.

## SceneEvent

Self-contained scene snapshot emitted by the engine.

```rust
struct SceneEvent {
    event_type: SceneEventType, // Perception | Dwell | Transition | Change
    bed_id: String, resident_id: Option<String>,
    timestamp: DateTime<Utc>, trace_id: Option<String>,
    trigger: TriggerInfo,
    poi: PersonOfInterest,
    bed: BedState, chair: ChairState, wheelchair: WheelchairState, walker: WalkerState,
    room: RoomState, accompanied_by: Option<StaffRef>,
}
```

`TriggerInfo` variants: `Perception { event_id, confidence }`, `DwellCompleted { rule_id, duration_minutes, threshold_minutes }`, `TransitionDetected { from, to }`, `ObjectChange { object, from, to }`.

Constructors: `SceneEvent::perception(...)`, `SceneEvent::transition(...)`.

## PerceptionEvent

Input from edge (AI Server) cameras.

```rust
struct PerceptionEvent {
    event_id: String, trace_id: Option<String>, monitor_key: String,
    bed_id: Option<String>, resident_id: Option<String>,
    state: Option<String>, sleeping: Option<bool>, zone: Option<String>,
    extremities_out_of_bed: Option<bool>, body_parts_out: Option<Vec<String>>,
    objects: Option<Value>, room: Option<String>,
    confidence: f64, occurred_at: DateTime<Utc>,
}
```

`map_state_to_person_state()` maps `state` to `PersonState`. `map_zone_to_location()` maps `zone` to `Location`.

## Flow

### on_perception_event(event) -> Vec\<SceneEvent\>

```
receive PerceptionEvent
  -> resolve bed_id (skip if None)
  -> get_or_create_bed(bed_id)
  -> map state -> PersonState, zone -> Location
  -> if state changed:
       cancel old timers -> update state/location/timestamp
       start new timers -> record FsmTransition -> emit Transition event
  -> update objects, room, extremities, resident_id
  -> emit Perception event (always)
```

### tick(now) -> Vec\<SceneEvent\>

```
for each bed:
  for each Active timer: fire if elapsed >= threshold, emit Dwell event
  clean up Fired/Cancelled timers
```

Dwell events carry `rule_id`, `duration_minutes`, `threshold_minutes`. Timers are removed after firing to prevent duplicate emissions.
