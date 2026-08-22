use std::sync::atomic::{AtomicUsize, Ordering};

use mana_app::AppState;
use mana_engine_v2::{DigitalTwin, PerceptionEvent, SceneEventType};
use mana_observation::EventInput;
use mana_kernel::Instante;
use mana_sentinel::evaluator::{EventCategory, PresetEvaluator};
use mana_sentinel::clip_window::ClipWindowManager;

static COUNTER: AtomicUsize = AtomicUsize::new(0);

/// Helper: crear AppState con DB en memoria (única por test)
fn test_app() -> AppState {
    let id = COUNTER.fetch_add(1, Ordering::SeqCst);
    let database_url = format!("file:/tmp/sentinel_integ_{}_{}.sqlite?mode=memory&cache=shared", std::process::id(), id);
    let pool = mana_storage::build_pool(&database_url).unwrap();
    ctx_identidad::run_migrations(&pool).unwrap();
    ctx_auditoria::run_migrations(&pool).unwrap();
    ctx_residencia::run_migrations(&pool).unwrap();
    ctx_poblacion::run_migrations(&pool).unwrap();
    ctx_cobertura::run_migrations(&pool).unwrap();
    ctx_cuidado::run_migrations(&pool).unwrap();
    ctx_historia::run_migrations(&pool).unwrap();
    ctx_politica::run_migrations(&pool).unwrap();
    ctx_vigilancia::run_migrations(&pool).unwrap();
    mana_observation::run_migrations(&pool).unwrap();
    ctx_streams::run_migrations(&pool).unwrap();
    ctx_evidence::run_migrations(&pool).unwrap();
    AppState::from_pool(pool, ctx_politica::AlarmCatalog::empty())
}

/// Helper: ingestar evento en Hub
fn hub_ingest(app: &AppState, event_id: &str, bed_id: &str, state: &str, zone: &str) {
    let input = EventInput {
        source_event_id: event_id.to_string(),
        monitor_key: format!("mon-{}", bed_id),
        resolution: mana_observation::Resolution::Resolved {
            bed_id: bed_id.to_string(),
            resident_id: Some("res-001".to_string()),
        },
        kind: "perception".to_string(),
        room_state: None,
        substate: None,
        zone: Some(zone.to_string()),
        state: Some(state.to_string()),
        sleeping: Some(state == "lying"),
        occurred_at: Instante::now(),
        payload_json: "{}".to_string(),
    };
    app.observation.ingest(input).unwrap();
}

/// Helper: crear PerceptionEvent para Engine con timestamp controlado
fn engine_perception_at(event_id: &str, bed_id: &str, state: &str, zone: &str, at: chrono::DateTime<chrono::Utc>) -> PerceptionEvent {
    PerceptionEvent {
        event_id: event_id.to_string(),
        trace_id: None,
        monitor_key: format!("mon-{}", bed_id),
        bed_id: Some(bed_id.to_string()),
        resident_id: Some("res-001".to_string()),
        state: Some(state.to_string()),
        sleeping: Some(state == "lying"),
        zone: Some(zone.to_string()),
        extremities_out_of_bed: None,
        body_parts_out: None,
        objects: None,
        room: None,
        confidence: 0.95,
        occurred_at: at,
    }
}

fn engine_perception(event_id: &str, bed_id: &str, state: &str, zone: &str) -> PerceptionEvent {
    engine_perception_at(event_id, bed_id, state, zone, chrono::Utc::now())
}

// ============================================================
// TEST 1: Flujo completo lying → standing → dwell fires
// ============================================================
#[test]
fn integration_lying_to_standing_dwell_fires() {
    let app = test_app();
    let mut engine = DigitalTwin::new();
    let evaluator = PresetEvaluator;
    let mut clip_manager = ClipWindowManager::new();

    let t0 = chrono::Utc::now();

    // === FASE 1: lying ===

    // 1a. Hub ingesta evento
    hub_ingest(&app, "evt-001", "118-A", "lying", "bed");

    // 1b. Engine procesa
    let events = engine.on_perception_event(engine_perception_at("evt-001", "118-A", "lying", "bed", t0));
    assert_eq!(events.len(), 2); // transition (Unknown→Lying) + perception

    // 1c. Sentinel evalúa (solo perception events, no transitions)
    for e in &events {
        if e.event_type == SceneEventType::Perception {
            let cat = evaluator.evaluate(e);
            assert_eq!(cat.category, EventCategory::Off); // in_bed → Off
        }
    }

    // Verificar: engine tiene timer activo
    let bed = engine.get_bed("118-A").unwrap();
    assert_eq!(bed.person.state.as_str(), "lying");
    assert_eq!(bed.timers.len(), 1); // lying dwell

    // === FASE 2: standing ===

    let t5 = t0 + chrono::Duration::minutes(5);

    // 2a. Hub ingesta evento
    hub_ingest(&app, "evt-002", "118-A", "standing", "bed");

    // 2b. Engine procesa
    let events = engine.on_perception_event(engine_perception_at("evt-002", "118-A", "standing", "bed", t5));
    assert_eq!(events.len(), 2); // transition (Lying→Standing) + perception

    // 2c. Sentinel evalúa transición
    let transition_event = events.iter().find(|e| e.event_type == SceneEventType::Transition).unwrap();
    let cat = evaluator.evaluate(transition_event);
    assert_eq!(cat.category, EventCategory::Alarm); // in→out → Alarm

    // 2d. Sentinel abre clip window
    let window = clip_manager.open_window("118-A", Some("res-001"), "2026-08-20T02:05:00Z");
    assert_eq!(window.state, mana_sentinel::clip_window::WindowState::Open);

    // Verificar: engine tiene timer standing
    let bed = engine.get_bed("118-A").unwrap();
    assert_eq!(bed.person.state.as_str(), "standing");
    assert_eq!(bed.timers.len(), 2); // lying(cancelled) + standing(active)

    // === FASE 3: tick después de 6 min desde standing (dwell se dispara) ===

    let tick_time = t5 + chrono::Duration::minutes(6);
    let tick_events = engine.tick(tick_time);
    assert_eq!(tick_events.len(), 1); // dwell_Standing se disparó

    // Sentinel evalúa dwell
    let dwell_event = &tick_events[0];
    let cat = evaluator.evaluate(dwell_event);
    assert_eq!(cat.category, EventCategory::Alarm); // dwell → Alarm

    // Sentinel crea evidence
    let evidence_input = ctx_evidence::EvidenceInput {
        bed_id: "118-A".to_string(),
        resident_id: Some("res-001".to_string()),
        evidence_type: ctx_evidence::EvidenceType::Dwell,
        category: ctx_evidence::EventCategory::Alarm,
        scene_event_id: "evt-002".to_string(),
        scene_event_json: serde_json::to_string(dwell_event).unwrap(),
        rule_id: Some("dwell_Standing".to_string()),
        shift: Some("night".to_string()),
        risk_level: Some("high".to_string()),
        timestamp: dwell_event.timestamp.to_rfc3339(),
    };
    let evidence = app.evidence_store.create_evidence(evidence_input).unwrap();
    assert_eq!(evidence.category, ctx_evidence::EventCategory::Alarm);

    // Sentinel cierra clip window
    let close_input = ctx_evidence::ClipWindowCloseInput {
        ended_at: chrono::Utc::now().to_rfc3339(),
        events: vec![ctx_evidence::CategorizedEvent {
            event_id: "evt-002".to_string(),
            event_type: "transition".to_string(),
            timestamp: "2026-08-20T02:05:00Z".to_string(),
            category: "alarm".to_string(),
            payload_json: "{}".to_string(),
        }],
        state: ctx_evidence::WindowState::Closed,
    };
    let hub_window = app.evidence_store.create_clip_window(ctx_evidence::ClipWindowInput {
        bed_id: "118-A".to_string(),
        resident_id: Some("res-001".to_string()),
        started_at: "2026-08-20T02:05:00Z".to_string(),
        timeout_minutes: 30,
        close_condition: ctx_evidence::CloseCondition::Timeout { minutes: 30 },
    }).unwrap();
    let closed = app.evidence_store.close_clip_window(&hub_window.window_id, close_input).unwrap();
    assert_eq!(closed.state, ctx_evidence::WindowState::Closed);

    // Verificar: evidence y clip window persistidos
    let evidence_list = app.evidence_store.list_evidence(ctx_evidence::EvidenceFilter {
        bed_id: Some("118-A".to_string()),
        ..Default::default()
    }).unwrap();
    assert_eq!(evidence_list.len(), 1);
}

// ============================================================
// TEST 2: Múltiples camas en paralelo
// ============================================================
#[test]
fn integration_multiple_beds_parallel() {
    let app = test_app();
    let mut engine = DigitalTwin::new();
    let evaluator = PresetEvaluator;

    let t0 = chrono::Utc::now();

    // 118-A: lying en t=0
    hub_ingest(&app, "evt-001", "118-A", "lying", "bed");
    engine.on_perception_event(engine_perception_at("evt-001", "118-A", "lying", "bed", t0));

    // 119-B: standing en t=0
    hub_ingest(&app, "evt-002", "119-B", "standing", "bed");
    let events_b = engine.on_perception_event(engine_perception_at("evt-002", "119-B", "standing", "bed", t0));

    // 119-B tiene transición Unknown→Standing (no es in→out, es Notify)
    let transition_b = events_b.iter().find(|e| e.event_type == SceneEventType::Transition).unwrap();
    let cat_b = evaluator.evaluate(transition_b);
    assert_eq!(cat_b.category, EventCategory::Notify);

    // Tick en t=6min: solo 119-B dispara dwell (6min > 5min threshold)
    let tick_time = t0 + chrono::Duration::minutes(6);
    let tick_events = engine.tick(tick_time);
    assert_eq!(tick_events.len(), 1);
    assert_eq!(tick_events[0].bed_id, "119-B");

    // 118-A sigue con timer activo (6min < 300min threshold)
    let bed_a = engine.get_bed("118-A").unwrap();
    assert_eq!(bed_a.timers.len(), 1);
    assert_eq!(bed_a.timers[0].state, mana_engine_v2::TimerState::Active);
}

// ============================================================
// TEST 3: Transición cancela dwell anterior
// ============================================================
#[test]
fn integration_transition_cancels_dwell() {
    let app = test_app();
    let mut engine = DigitalTwin::new();
    let _evaluator = PresetEvaluator;

    let t0 = chrono::Utc::now();

    // lying en t=0
    hub_ingest(&app, "evt-001", "118-A", "lying", "bed");
    engine.on_perception_event(engine_perception_at("evt-001", "118-A", "lying", "bed", t0));

    // standing en t=1min
    let t1 = t0 + chrono::Duration::minutes(1);
    hub_ingest(&app, "evt-002", "118-A", "standing", "bed");
    engine.on_perception_event(engine_perception_at("evt-002", "118-A", "standing", "bed", t1));

    // lying en t=3min (antes de 5 min de standing)
    let t3 = t0 + chrono::Duration::minutes(3);
    hub_ingest(&app, "evt-003", "118-A", "lying", "bed");
    engine.on_perception_event(engine_perception_at("evt-003", "118-A", "lying", "bed", t3));

    // Verificar: standing timer cancelado (ANTES del tick, que limpia)
    let bed = engine.get_bed("118-A").unwrap();
    let standing_timers: Vec<_> = bed.timers.iter()
        .filter(|t| t.rule_id.contains("Standing"))
        .collect();
    assert_eq!(standing_timers.len(), 1);
    assert_eq!(standing_timers[0].state, mana_engine_v2::TimerState::Cancelled);

    // Tick: limpia timers cancelled
    let tick_time = t3 + chrono::Duration::minutes(1);
    let tick_events = engine.tick(tick_time);
    assert_eq!(tick_events.len(), 0);

    // Después del tick: standing timer limpiado
    let bed = engine.get_bed("118-A").unwrap();
    let standing_timers: Vec<_> = bed.timers.iter()
        .filter(|t| t.rule_id.contains("Standing"))
        .collect();
    assert_eq!(standing_timers.len(), 0);
}
