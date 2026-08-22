# Design: Engine FSM

## Purpose

Define the finite state machine that models resident state transitions within the DigitalTwin.

## Overview

There is no standalone FSM struct. FSM logic is embedded in `DigitalTwin::on_perception_event()` and driven by two modules:
- `fsm.rs` — defines `PersonState`, `FsmTransition`, and `valid_transitions()`.
- `digital_twin.rs` — applies transitions inside `on_perception_event()` and manages timers in `tick()`.

## PersonState

11 variants organized into groups:

**in_bed:** `Lying`, `SittingInBed`, `BedEdge`
**out_of_bed:** `Standing`, `InBathroom`, `InRoom`, `InHallway`, `Outdoor`
**furniture:** `InChair`, `InWheelchair`
**sentinel:** `Unknown`

Helper methods: `is_in_bed()`, `is_out_of_bed()`, `parse(&str)`, `as_str()`.

## FsmTransition

```rust
pub struct FsmTransition {
    pub from: PersonState,
    pub to: PersonState,
    pub at: chrono::DateTime<chrono::Utc>,
    pub confidence: f64,
}
```

No `trigger` field. Transitions are appended to `BedTwin.transitions: Vec<FsmTransition>`.

## valid_transitions()

A standalone function that returns the legal destination states for a given source state:

| From | Can transition to |
|---|---|
| Lying | SittingInBed, BedEdge, Standing, InBathroom, InRoom, InChair, InWheelchair, Unknown |
| SittingInBed | Lying, BedEdge, Standing, InBathroom, InRoom, InChair, InWheelchair, Unknown |
| BedEdge | Lying, SittingInBed, Standing, InBathroom, InRoom, InChair, InWheelchair, Unknown |
| Standing | Lying, SittingInBed, BedEdge, InBathroom, InRoom, InHallway, Outdoor, InChair, InWheelchair, Unknown |
| InBathroom | Lying, SittingInBed, BedEdge, Standing, InRoom, InHallway, Unknown |
| InRoom | Lying, SittingInBed, BedEdge, Standing, InBathroom, InHallway, Outdoor, InChair, InWheelchair, Unknown |
| InHallway | Standing, InBathroom, InRoom, Outdoor, Unknown |
| Outdoor | Standing, InRoom, InHallway, Unknown |
| InChair | Lying, SittingInBed, BedEdge, Standing, InRoom, Unknown |
| InWheelchair | Lying, SittingInBed, BedEdge, Standing, InRoom, Unknown |
| Unknown | All 10 non-Unknown states |

No state transitions to itself.

## FSM Embedded in DigitalTwin

### on_perception_event(event)

1. Extract `bed_id` from event (return early if missing).
2. Get or create `BedTwin` for that bed.
3. Compare `old_state` (current) vs `new_state` from `event.map_state_to_person_state()`.
4. If different:
   - Cancel timers for old state.
   - Update `PersonTwin` fields (state, state_since, location, confidence).
   - Start timers for new state via `DwellRules`.
   - Push `FsmTransition` to `bed.transitions`.
   - Emit `SceneEvent::transition()`.
5. Update objects, room occupancy, and extremities from event.
6. Emit `SceneEvent::perception()`.

### tick(now)

Scan loop over all beds:
1. For each active timer, check if elapsed >= threshold.
2. If fired: set `TimerState::Fired`, emit `SceneEvent::Dwell`.
3. After scan, remove non-active timers from each bed.

### Timer lifecycle

- Created when state changes via `start_timers_for_state()`.
- Cancelled when state changes away via `cancel_timers_for_state()`.
- Evaluated each tick; once fired, it is cleaned up on next tick.

### DwellRules thresholds

| State group | Default (minutes) |
|---|---|
| Lying, SittingInBed, BedEdge | 300 (5h) |
| Standing | 5 |
| InBathroom | 30 |
| InRoom, InHallway, Outdoor, InChair, InWheelchair | 10 |
| Unknown | None (no timer) |

## PerceptionEvent mapping

`PerceptionEvent::map_state_to_person_state()` in `perception.rs` maps the event's `state: Option<String>` to `PersonState`:

`"lying"` → Lying, `"sitting"/"sitting_in_bed"` → SittingInBed, `"bed_edge"` → BedEdge, `"standing"` → Standing, `"in_bathroom"` → InBathroom, `"in_room"` → InRoom, `"in_hallway"` → InHallway, `"outdoor"` → Outdoor, `"in_chair"` → InChair, `"in_wheelchair"` → InWheelchair, `_` → Unknown.

## Invariants

1. Each `BedTwin` holds exactly one active person state at a time.
2. A perception event that repeats the current state does NOT generate a transition.
3. `valid_transitions()` is the single source of truth for legal state changes.
4. `state_since` updates only when the state actually changes.
5. Timers are cancelled on old state, started on new state — never duplicated for the same state.
6. `Unknown` can transition to any other state; most states can transition to `Unknown`.
7. `BedTwin.transitions` is append-only (history of all state changes for that bed).
