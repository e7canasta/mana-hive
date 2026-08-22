use mana_sentinel::incident::{IncidentManager, IncidentStatus, IncidentEventType};
use mana_engine_v2::PersonState;
use mana_engine_v2::scene_event::{SceneEvent, SceneEventType, TriggerInfo, PersonOfInterest, Location, BedState, BedOccupancy, ChairState, ObjectOccupancy, WheelchairState, WalkerState, ObjectPresence, RoomState, RoomOccupancy};

fn create_test_scene_event(bed_id: &str, state: PersonState) -> SceneEvent {
    SceneEvent {
        event_type: SceneEventType::Transition,
        bed_id: bed_id.to_string(),
        resident_id: Some("res-001".to_string()),
        timestamp: chrono::Utc::now(),
        trace_id: None,
        trigger: TriggerInfo::TransitionDetected {
            from_state: PersonState::Lying,
            to_state: state.clone(),
        },
        poi: PersonOfInterest {
            resident_id: "res-001".to_string(),
            state: state.clone(),
            state_since: chrono::Utc::now(),
            location: if state.is_in_bed() { Location::Bed } else { Location::Room },
            sleeping: Some(state == PersonState::Lying),
            confidence: 0.95,
        },
        bed: BedState { occupancy: BedOccupancy::Occupied },
        chair: ChairState { occupancy: ObjectOccupancy::Empty },
        wheelchair: WheelchairState { occupancy: ObjectOccupancy::Empty },
        walker: WalkerState { presence: ObjectPresence::Present },
        room: RoomState {
            occupancy: RoomOccupancy::Resident,
            resident_count: 1,
            staff_count: 0,
            visitor_count: 0,
        },
        accompanied_by: None,
    }
}

#[test]
fn test_create_incident() {
    let mut manager = IncidentManager::new();
    let event = create_test_scene_event("118-A", PersonState::Standing);

    let incident = manager.create_incident(
        "118-A",
        Some("res-001"),
        "bed_exit",
        &event,
    ).unwrap();

    assert_eq!(incident.bed_id, "118-A");
    assert_eq!(incident.resident_id, Some("res-001".to_string()));
    assert_eq!(incident.preset_id, "bed_exit");
    assert_eq!(incident.status, IncidentStatus::WaitingForStaff);
    assert_eq!(incident.timeline.len(), 1);
    assert_eq!(incident.timeline[0].event_type, IncidentEventType::Primary);
}

#[test]
fn test_staff_arrival() {
    let mut manager = IncidentManager::new();
    let event = create_test_scene_event("118-A", PersonState::Standing);

    let incident = manager.create_incident(
        "118-A",
        Some("res-001"),
        "bed_exit",
        &event,
    ).unwrap();

    let incident_id = incident.id.clone();

    manager.staff_arrived(&incident_id, "nurse-001").unwrap();

    let incident = manager.get_incident(&incident_id).unwrap();
    assert_eq!(incident.status, IncidentStatus::StaffOnSite);
    assert_eq!(incident.timeline.len(), 2);
    assert_eq!(incident.timeline[1].event_type, IncidentEventType::StaffArrived);
}

#[test]
fn test_staff_departure_closes_incident() {
    let mut manager = IncidentManager::new();
    let event = create_test_scene_event("118-A", PersonState::Standing);

    let incident = manager.create_incident(
        "118-A",
        Some("res-001"),
        "bed_exit",
        &event,
    ).unwrap();

    let incident_id = incident.id.clone();

    manager.staff_arrived(&incident_id, "nurse-001").unwrap();
    manager.staff_departed(&incident_id, "nurse-001").unwrap();

    let incident = manager.get_incident(&incident_id).unwrap();
    assert_eq!(incident.status, IncidentStatus::Closed);
    assert!(incident.closed_at.is_some());
    assert_eq!(incident.timeline.len(), 3);
    assert_eq!(incident.timeline[2].event_type, IncidentEventType::StaffDeparted);
}

#[test]
fn test_open_incident_for_bed() {
    let mut manager = IncidentManager::new();
    let event = create_test_scene_event("118-A", PersonState::Standing);

    manager.create_incident("118-A", Some("res-001"), "bed_exit", &event).unwrap();

    let incident = manager.open_incident_for_bed("118-A").unwrap();
    assert_eq!(incident.bed_id, "118-A");
    assert_eq!(incident.status, IncidentStatus::WaitingForStaff);

    // No incident for other bed
    assert!(manager.open_incident_for_bed("119-B").is_none());
}

#[test]
fn test_requires_incident() {
    let manager = IncidentManager::new();

    // Dwell event requires incident
    let dwell_event = SceneEvent {
        event_type: SceneEventType::Dwell,
        bed_id: "118-A".to_string(),
        resident_id: Some("res-001".to_string()),
        timestamp: chrono::Utc::now(),
        trace_id: None,
        trigger: TriggerInfo::DwellCompleted {
            rule_id: "dwell_Standing".to_string(),
            duration_minutes: 6,
            threshold_minutes: 5,
        },
        poi: PersonOfInterest {
            resident_id: "res-001".to_string(),
            state: PersonState::Standing,
            state_since: chrono::Utc::now(),
            location: Location::Room,
            sleeping: Some(false),
            confidence: 0.95,
        },
        bed: BedState { occupancy: BedOccupancy::Occupied },
        chair: ChairState { occupancy: ObjectOccupancy::Empty },
        wheelchair: WheelchairState { occupancy: ObjectOccupancy::Empty },
        walker: WalkerState { presence: ObjectPresence::Present },
        room: RoomState {
            occupancy: RoomOccupancy::Resident,
            resident_count: 1,
            staff_count: 0,
            visitor_count: 0,
        },
        accompanied_by: None,
    };

    let preset = manager.requires_incident(&dwell_event);
    assert!(preset.is_some());
    assert_eq!(preset.unwrap().id, "dwell_exceeded");

    // Bed exit event requires incident
    let bed_exit_event = create_test_scene_event("118-A", PersonState::Standing);
    let preset = manager.requires_incident(&bed_exit_event);
    assert!(preset.is_some());
    assert_eq!(preset.unwrap().id, "bed_exit");

    // Lying event does not require incident
    let lying_event = create_test_scene_event("118-A", PersonState::Lying);
    let preset = manager.requires_incident(&lying_event);
    assert!(preset.is_none());
}

#[test]
fn test_add_event_to_timeline() {
    let mut manager = IncidentManager::new();
    let event = create_test_scene_event("118-A", PersonState::Standing);

    let incident = manager.create_incident(
        "118-A",
        Some("res-001"),
        "bed_exit",
        &event,
    ).unwrap();

    let incident_id = incident.id.clone();

    manager.add_event(
        &incident_id,
        IncidentEventType::ActionTaken,
        serde_json::json!({ "action": "resident_assisted" }),
    ).unwrap();

    let incident = manager.get_incident(&incident_id).unwrap();
    assert_eq!(incident.timeline.len(), 2);
    assert_eq!(incident.timeline[1].event_type, IncidentEventType::ActionTaken);
}

#[test]
fn test_cannot_add_event_to_closed_incident() {
    let mut manager = IncidentManager::new();
    let event = create_test_scene_event("118-A", PersonState::Standing);

    let incident = manager.create_incident(
        "118-A",
        Some("res-001"),
        "bed_exit",
        &event,
    ).unwrap();

    let incident_id = incident.id.clone();

    manager.staff_arrived(&incident_id, "nurse-001").unwrap();
    manager.staff_departed(&incident_id, "nurse-001").unwrap();

    let result = manager.add_event(
        &incident_id,
        IncidentEventType::ActionTaken,
        serde_json::json!({ "action": "too_late" }),
    );

    assert!(result.is_err());
}
