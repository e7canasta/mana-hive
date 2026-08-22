use mana_engine_v2::{DigitalTwin, PerceptionEvent, TimerState};

fn parse_time(s: &str) -> chrono::DateTime<chrono::Utc> {
    chrono::DateTime::parse_from_rfc3339(s)
        .unwrap()
        .with_timezone(&chrono::Utc)
}

/// Helper: crear perception event
fn perception(
    bed_id: &str,
    resident_id: &str,
    state: &str,
    zone: &str,
    time: &str,
) -> PerceptionEvent {
    PerceptionEvent {
        event_id: format!("evt-{}", time),
        trace_id: None,
        monitor_key: format!("mon-{}", bed_id),
        bed_id: Some(bed_id.to_string()),
        resident_id: Some(resident_id.to_string()),
        state: Some(state.to_string()),
        sleeping: Some(state == "lying"),
        zone: Some(zone.to_string()),
        extremities_out_of_bed: None,
        body_parts_out: None,
        objects: None,
        room: None,
        confidence: 0.95,
        occurred_at: parse_time(time),
    }
}

// ============================================================
// TEST 1: Dwell de standing se dispara
// ============================================================
#[test]
fn dwell_standing_fires_after_threshold() {
    let mut engine = DigitalTwin::new();

    // 02:00 - lying
    engine.on_perception_event(perception("118-A", "res-001", "lying", "bed", "2026-08-20T02:00:00Z"));

    // 02:05 - standing
    engine.on_perception_event(perception("118-A", "res-001", "standing", "bed", "2026-08-20T02:05:00Z"));

    // Verificar: standing con timer activo
    let bed = engine.get_bed("118-A").unwrap();
    assert_eq!(bed.person.state.as_str(), "standing");
    assert!(!bed.person.state.is_in_bed());
    // 2 timers: lying(1, cancelado) + standing(1, activo)
    assert_eq!(bed.timers.len(), 2);
    assert_eq!(bed.timers[0].state, TimerState::Cancelled); // lying
    assert_eq!(bed.timers[1].state, TimerState::Active);    // standing

    // Tick a los 4 min: no se dispara
    let events = engine.tick(parse_time("2026-08-20T02:09:00Z"));
    assert_eq!(events.len(), 0);

    // Tick a los 6 min: se dispara
    let events = engine.tick(parse_time("2026-08-20T02:11:00Z"));
    assert_eq!(events.len(), 1);
    assert_eq!(events[0].event_type, mana_engine_v2::SceneEventType::Dwell);
    if let mana_engine_v2::TriggerInfo::DwellCompleted { rule_id, duration_minutes, .. } = &events[0].trigger {
        assert_eq!(rule_id, "dwell_Standing");
        assert!((duration_minutes - 6).abs() <= 1);
    } else {
        panic!("Expected DwellCompleted trigger");
    }

    // Timer limpiado
    let bed = engine.get_bed("118-A").unwrap();
    assert_eq!(bed.timers.len(), 0);
}

// ============================================================
// TEST 2: Transición cancela timer anterior
// ============================================================
#[test]
fn transition_cancels_previous_timer() {
    let mut engine = DigitalTwin::new();

    // lying → standing → lying (antes de 5 min)
    engine.on_perception_event(perception("118-A", "res-001", "lying", "bed", "2026-08-20T02:00:00Z"));
    engine.on_perception_event(perception("118-A", "res-001", "standing", "bed", "2026-08-20T02:05:00Z"));
    engine.on_perception_event(perception("118-A", "res-001", "lying", "bed", "2026-08-20T02:08:00Z"));

    // Verificar: lying activo, standing cancelado
    let bed = engine.get_bed("118-A").unwrap();
    assert_eq!(bed.person.state.as_str(), "lying");
    assert!(bed.person.state.is_in_bed());
    // 3 timers: lying(1) + standing(1) + lying(2) - los cancelados se limpian en tick()
    assert_eq!(bed.timers.len(), 3);
    assert_eq!(bed.timers[0].state, TimerState::Cancelled); // lying (1)
    assert_eq!(bed.timers[1].state, TimerState::Cancelled); // standing
    assert_eq!(bed.timers[2].state, TimerState::Active);    // lying (2)

    // Tick: limpia cancelados, solo queda lying activo
    let events = engine.tick(parse_time("2026-08-20T02:08:01Z"));
    assert_eq!(events.len(), 0); // lying dwell no se dispara (8 min < 300)
    let bed = engine.get_bed("118-A").unwrap();
    assert_eq!(bed.timers.len(), 1); // solo el activo
}

// ============================================================
// TEST 3: Dos camas independientes
// ============================================================
#[test]
fn multi_bed_independent_timers() {
    let mut engine = DigitalTwin::new();

    // 118-A: lying
    engine.on_perception_event(perception("118-A", "res-001", "lying", "bed", "2026-08-20T02:00:00Z"));
    // 119-B: standing
    engine.on_perception_event(perception("119-B", "res-002", "standing", "bed", "2026-08-20T02:00:00Z"));

    // Tick a los 6 min: solo 119-B dispara
    let events = engine.tick(parse_time("2026-08-20T02:06:00Z"));
    assert_eq!(events.len(), 1);
    assert_eq!(events[0].bed_id, "119-B");

    // 118-A sigue con timer activo
    let bed_a = engine.get_bed("118-A").unwrap();
    assert_eq!(bed_a.timers.len(), 1);
    assert_eq!(bed_a.timers[0].state, TimerState::Active);
}

// ============================================================
// TEST 4: Unknown → cualquier estado siempre es válido
// ============================================================
#[test]
fn unknown_to_any_state_is_valid() {
    let mut engine = DigitalTwin::new();

    // Primera vez: Unknown → lying (válida)
    engine.on_perception_event(perception("118-A", "res-001", "lying", "bed", "2026-08-20T02:00:00Z"));
    let bed = engine.get_bed("118-A").unwrap();
    assert_eq!(bed.transitions.len(), 1);
    assert_eq!(bed.transitions[0].from.as_str(), "unknown");
    assert_eq!(bed.transitions[0].to.as_str(), "lying");

    // Segunda vez: lying → lying (sin transición)
    engine.on_perception_event(perception("118-A", "res-001", "lying", "bed", "2026-08-20T02:01:00Z"));
    let bed = engine.get_bed("118-A").unwrap();
    assert_eq!(bed.transitions.len(), 1); // no se agrega
}

// ============================================================
// TEST 5: Múltiples dwells con diferentes thresholds
// ============================================================
#[test]
fn multiple_dwells_with_different_thresholds() {
    use mana_engine_v2::DwellRules;

    let rules = DwellRules {
        out_of_bed: 10,
        in_bed: 300,
        standing: 5,
        in_bathroom: 30,
    };
    let mut engine = DigitalTwin::with_dwell_rules(rules);

    // lying → standing
    engine.on_perception_event(perception("118-A", "res-001", "lying", "bed", "2026-08-20T02:00:00Z"));
    engine.on_perception_event(perception("118-A", "res-001", "standing", "bed", "2026-08-20T02:05:00Z"));

    // Tick a los 6 min: standing dwell (5 min) se dispara
    let events = engine.tick(parse_time("2026-08-20T02:11:00Z"));
    assert_eq!(events.len(), 1);

    // standing → in_bathroom
    engine.on_perception_event(perception("118-A", "res-001", "in_bathroom", "bathroom", "2026-08-20T02:15:00Z"));

    // Tick a los 46 min (30 min en bathroom): bathroom dwell se dispara
    let events = engine.tick(parse_time("2026-08-20T02:46:00Z"));
    assert_eq!(events.len(), 1);
    if let mana_engine_v2::TriggerInfo::DwellCompleted { rule_id, .. } = &events[0].trigger {
        assert_eq!(rule_id, "dwell_InBathroom");
    }
}
