use mana_engine_v2::{
    scene_event::{
        BedOccupancy, BedState, ChairState, Location, ObjectOccupancy, ObjectPresence,
        PersonOfInterest, RoomOccupancy, RoomState, SceneEvent, SceneEventType,
        TriggerInfo, WalkerState, WheelchairState,
    },
    PersonState,
};

use mana_sentinel::evaluator::{EventCategory, PresetEvaluator};
use mana_sentinel::clip_window::{ClipWindowManager, WindowState};

// ============================================================
// Helper: crear scene event
// ============================================================
fn scene_event(
    event_type: SceneEventType,
    bed_id: &str,
    state: PersonState,
    trigger: TriggerInfo,
) -> SceneEvent {
    SceneEvent {
        event_type,
        bed_id: bed_id.to_string(),
        resident_id: Some("res-001".to_string()),
        timestamp: chrono::Utc::now(),
        trace_id: None,
        trigger,
        poi: PersonOfInterest {
            resident_id: "res-001".to_string(),
            state: state.clone(),
            state_since: chrono::Utc::now(),
            location: if state.is_in_bed() {
                Location::Bed
            } else {
                Location::Room
            },
            sleeping: Some(state == PersonState::Lying),
            confidence: 0.95,
        },
        bed: BedState {
            occupancy: BedOccupancy::Occupied,
        },
        chair: ChairState {
            occupancy: ObjectOccupancy::Empty,
        },
        wheelchair: WheelchairState {
            occupancy: ObjectOccupancy::Empty,
        },
        walker: WalkerState {
            presence: ObjectPresence::Present,
        },
        room: RoomState {
            occupancy: RoomOccupancy::Resident,
            resident_count: 1,
            staff_count: 0,
            visitor_count: 0,
        },
        accompanied_by: None,
    }
}

// ============================================================
// TEST 1: Evaluator - perception in_bed → Off
// ============================================================
#[test]
fn evaluator_perception_in_bed_is_off() {
    let evaluator = PresetEvaluator;
    let event = scene_event(
        SceneEventType::Perception,
        "118-A",
        PersonState::Lying,
        TriggerInfo::Perception {
            perception_event_id: "evt-001".to_string(),
            confidence: 0.95,
        },
    );

    let result = evaluator.evaluate(&event);
    assert_eq!(result.category, EventCategory::Off);
}

// ============================================================
// TEST 2: Evaluator - perception out_of_bed → Notify
// ============================================================
#[test]
fn evaluator_perception_out_of_bed_is_notify() {
    let evaluator = PresetEvaluator;
    let event = scene_event(
        SceneEventType::Perception,
        "118-A",
        PersonState::Standing,
        TriggerInfo::Perception {
            perception_event_id: "evt-001".to_string(),
            confidence: 0.95,
        },
    );

    let result = evaluator.evaluate(&event);
    assert_eq!(result.category, EventCategory::Notify);
}

// ============================================================
// TEST 3: Evaluator - transition in→out → Alarm
// ============================================================
#[test]
fn evaluator_transition_in_to_out_is_alarm() {
    let evaluator = PresetEvaluator;
    let event = scene_event(
        SceneEventType::Transition,
        "118-A",
        PersonState::Standing,
        TriggerInfo::TransitionDetected {
            from_state: PersonState::Lying,
            to_state: PersonState::Standing,
        },
    );

    let result = evaluator.evaluate(&event);
    assert_eq!(result.category, EventCategory::Alarm);
}

// ============================================================
// TEST 4: Evaluator - dwell → Alarm
// ============================================================
#[test]
fn evaluator_dwell_is_alarm() {
    let evaluator = PresetEvaluator;
    let event = scene_event(
        SceneEventType::Dwell,
        "118-A",
        PersonState::Standing,
        TriggerInfo::DwellCompleted {
            rule_id: "dwell_Standing".to_string(),
            duration_minutes: 6,
            threshold_minutes: 5,
        },
    );

    let result = evaluator.evaluate(&event);
    assert_eq!(result.category, EventCategory::Alarm);
}

// ============================================================
// TEST 5: ClipWindowManager - open/close
// ============================================================
#[test]
fn clip_window_open_and_close() {
    let mut manager = ClipWindowManager::new();

    // Abrir ventana
    let window = manager.open_window("118-A", Some("res-001"), "2026-08-20T02:00:00Z");
    assert_eq!(window.state, WindowState::Open);
    assert_eq!(window.bed_id, "118-A");

    // Agregar evento
    let event = serde_json::json!({"type": "perception", "state": "standing"});
    manager.add_event(&window.window_id, event).unwrap();

    // Cerrar ventana
    let closed = manager.close_window(&window.window_id).unwrap();
    assert_eq!(closed.state, WindowState::Closed);
    assert_eq!(closed.events.len(), 1);

    // No hay ventanas abiertas
    let open = manager.open_windows_for_bed("118-A");
    assert_eq!(open.len(), 0);
}

// ============================================================
// TEST 6: ClipWindowManager - multiple windows per bed
// ============================================================
#[test]
fn clip_window_multiple_per_bed() {
    let mut manager = ClipWindowManager::new();

    let w1 = manager.open_window("118-A", Some("res-001"), "2026-08-20T02:00:00Z");
    let w2 = manager.open_window("118-A", Some("res-001"), "2026-08-20T02:05:00Z");

    let open = manager.open_windows_for_bed("118-A");
    assert_eq!(open.len(), 2);

    manager.close_window(&w1.window_id).unwrap();
    let open = manager.open_windows_for_bed("118-A");
    assert_eq!(open.len(), 1);

    manager.close_window(&w2.window_id).unwrap();
    let open = manager.open_windows_for_bed("118-A");
    assert_eq!(open.len(), 0);
}

// ============================================================
// TEST 7: ClipWindowManager - add event to closed window fails
// ============================================================
#[test]
fn clip_window_add_event_to_closed_fails() {
    let mut manager = ClipWindowManager::new();
    let window = manager.open_window("118-A", Some("res-001"), "2026-08-20T02:00:00Z");
    manager.close_window(&window.window_id).unwrap();

    let event = serde_json::json!({"type": "perception"});
    let result = manager.add_event(&window.window_id, event);
    assert!(result.is_err());
}

// ============================================================
// TEST 8: ClipWindowManager - add event to nonexistent window fails
// ============================================================
#[test]
fn clip_window_add_event_to_nonexistent_fails() {
    let mut manager = ClipWindowManager::new();
    let event = serde_json::json!({"type": "perception"});
    let result = manager.add_event("nonexistent", event);
    assert!(result.is_err());
}
