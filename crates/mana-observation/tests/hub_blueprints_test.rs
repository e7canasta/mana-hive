use std::sync::atomic::{AtomicUsize, Ordering};

use mana_app::AppState;
use mana_observation::EventInput;
use mana_kernel::Instante;

static COUNTER: AtomicUsize = AtomicUsize::new(0);

/// Helper: crear AppState con DB en memoria (pool size = 1)
fn test_app() -> AppState {
    let id = COUNTER.fetch_add(1, Ordering::SeqCst);
    let database_url = format!("file:/tmp/hub_test_{}_{}.sqlite?mode=memory&cache=shared", std::process::id(), id);
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

/// Helper: ingestar un evento de percepción en el hub
fn ingest_event(
    app: &AppState,
    source_event_id: &str,
    monitor_key: &str,
    bed_id: &str,
    resident_id: &str,
    state: &str,
    zone: &str,
    sleeping: bool,
) {
    let input = EventInput {
        source_event_id: source_event_id.to_string(),
        monitor_key: monitor_key.to_string(),
        resolution: mana_observation::Resolution::Resolved {
            bed_id: bed_id.to_string(),
            resident_id: Some(resident_id.to_string()),
        },
        kind: "perception".to_string(),
        room_state: None,
        substate: None,
        zone: Some(zone.to_string()),
        state: Some(state.to_string()),
        sleeping: Some(sleeping),
        occurred_at: Instante::now(),
        payload_json: "{}".to_string(),
    };

    let result = app.observation.ingest(input);
    assert!(result.is_ok(), "ingest failed: {:?}", result.err());
}

// ============================================================
// TEST 1: Hub guarda eventos de percepción
// ============================================================
#[test]
fn hub_stores_perception_events() {
    let app = test_app();

    // Simular 3 eventos del IA server
    ingest_event(&app, "evt-001", "mon-118-A", "118-A", "res-001", "lying", "bed", true);
    ingest_event(&app, "evt-002", "mon-118-A", "118-A", "res-001", "sitting", "bed", false);
    ingest_event(&app, "evt-003", "mon-118-A", "118-A", "res-001", "standing", "bed", false);

    // Verificar: hay 3 eventos para la cama 118-A
    let events = app.observation.events_for_bed("118-A", 10).unwrap();
    assert_eq!(events.len(), 3);
}

// ============================================================
// TEST 2: Hub actualiza bed state proyectado
// ============================================================
#[test]
fn hub_updates_projected_bed_state() {
    let app = test_app();

    // Primer evento: lying
    ingest_event(&app, "evt-001", "mon-118-A", "118-A", "res-001", "lying", "bed", true);

    // Verificar: bed state proyectado es lying
    let state = app.observation.current_state("118-A").unwrap();
    assert!(state.is_some());
    let state = state.unwrap();
    assert_eq!(state.state, "lying");

    // Segundo evento: standing
    ingest_event(&app, "evt-002", "mon-118-A", "118-A", "res-001", "standing", "bed", false);

    // Verificar: bed state actualizado a standing
    let state = app.observation.current_state("118-A").unwrap().unwrap();
    assert_eq!(state.state, "standing");
}

// ============================================================
// TEST 3: Resolución de monitor_key a bed_id
// ============================================================
#[test]
fn hub_resolves_monitor_key_to_bed() {
    let app = test_app();

    // El evento viene con monitor_key, el hub resuelve a bed_id
    ingest_event(&app, "evt-001", "mon-118-A", "118-A", "res-001", "lying", "bed", true);

    // Verificar que el evento se guardó con el bed_id correcto
    let events = app.observation.events_for_bed("118-A", 10).unwrap();
    assert_eq!(events.len(), 1);
    assert_eq!(events[0].monitor_key, "mon-118-A");
}

// ============================================================
// TEST 4: Múltiples camas independientes
// ============================================================
#[test]
fn hub_handles_multiple_beds() {
    let app = test_app();

    // Eventos para dos camas diferentes
    ingest_event(&app, "evt-001", "mon-118-A", "118-A", "res-001", "lying", "bed", true);
    ingest_event(&app, "evt-002", "mon-119-B", "119-B", "res-002", "standing", "bed", false);

    // Verificar: cada cama tiene su evento
    let events_a = app.observation.events_for_bed("118-A", 10).unwrap();
    let events_b = app.observation.events_for_bed("119-B", 10).unwrap();
    assert_eq!(events_a.len(), 1);
    assert_eq!(events_b.len(), 1);

    // Verificar: cada cama tiene su estado proyectado
    let state_a = app.observation.current_state("118-A").unwrap().unwrap();
    let state_b = app.observation.current_state("119-B").unwrap().unwrap();
    assert_eq!(state_a.state, "lying");
    assert_eq!(state_b.state, "standing");
}

// ============================================================
// TEST 5: API de evidence funciona
// ============================================================
#[test]
fn hub_evidence_api_works() {
    let app = test_app();

    // Crear evidence
    let input = ctx_evidence::EvidenceInput {
        bed_id: "118-A".to_string(),
        resident_id: Some("res-001".to_string()),
        evidence_type: ctx_evidence::EvidenceType::Transition,
        category: ctx_evidence::EventCategory::Notify,
        scene_event_id: "evt-001".to_string(),
        scene_event_json: "{}".to_string(),
        rule_id: Some("out_of_bed".to_string()),
        shift: Some("night".to_string()),
        risk_level: Some("medium".to_string()),
        timestamp: "2026-08-20T02:00:00Z".to_string(),
    };

    let evidence = app.evidence_store.create_evidence(input).unwrap();
    assert_eq!(evidence.bed_id, "118-A");
    assert_eq!(evidence.category, ctx_evidence::EventCategory::Notify);

    // Listar evidence
    let filter = ctx_evidence::EvidenceFilter {
        bed_id: Some("118-A".to_string()),
        ..Default::default()
    };
    let evidence_list = app.evidence_store.list_evidence(filter).unwrap();
    assert_eq!(evidence_list.len(), 1);
}

// ============================================================
// TEST 6: API de clip windows funciona
// ============================================================
#[test]
fn hub_clip_window_api_works() {
    let app = test_app();

    // Crear clip window
    let input = ctx_evidence::ClipWindowInput {
        bed_id: "118-A".to_string(),
        resident_id: Some("res-001".to_string()),
        started_at: "2026-08-20T02:00:00Z".to_string(),
        timeout_minutes: 30,
        close_condition: ctx_evidence::CloseCondition::Timeout { minutes: 30 },
    };

    let window = app.evidence_store.create_clip_window(input).unwrap();
    assert_eq!(window.bed_id, "118-A");
    assert_eq!(window.state, ctx_evidence::WindowState::Open);

    // Listar ventanas abiertas
    let open = app.evidence_store.list_open_clip_windows("118-A").unwrap();
    assert_eq!(open.len(), 1);

    // Cerrar ventana
    let close_input = ctx_evidence::ClipWindowCloseInput {
        ended_at: "2026-08-20T02:30:00Z".to_string(),
        events: vec![],
        state: ctx_evidence::WindowState::Closed,
    };
    let closed = app.evidence_store.close_clip_window(&window.window_id, close_input).unwrap();
    assert_eq!(closed.state, ctx_evidence::WindowState::Closed);

    // No hay ventanas abiertas
    let open = app.evidence_store.list_open_clip_windows("118-A").unwrap();
    assert_eq!(open.len(), 0);
}

// ============================================================
// TEST 7: API de timelines funciona
// ============================================================
#[test]
fn hub_timeline_api_works() {
    let app = test_app();

    // Crear timeline
    let input = ctx_evidence::TimelineInput {
        bed_id: "118-A".to_string(),
        resident_id: Some("res-001".to_string()),
        anchor_event_id: "evt-002".to_string(),
        anchor_event_json: "{}".to_string(),
        before_events: vec![ctx_evidence::TimelineEvent {
            event_id: "evt-001".to_string(),
            event_type: "perception".to_string(),
            timestamp: "2026-08-20T02:00:00Z".to_string(),
            event_json: "{}".to_string(),
        }],
        window_start: "2026-08-20T01:55:00Z".to_string(),
        window_end: "2026-08-20T02:10:00Z".to_string(),
    };

    let timeline = app.evidence_store.create_timeline(input).unwrap();
    assert_eq!(timeline.bed_id, "118-A");
    assert_eq!(timeline.before_events.len(), 1);
    assert!(timeline.closed_at.is_none());

    // Cerrar timeline
    let close_input = ctx_evidence::TimelineCloseInput {
        after_events: vec![ctx_evidence::TimelineEvent {
            event_id: "evt-003".to_string(),
            event_type: "perception".to_string(),
            timestamp: "2026-08-20T02:05:00Z".to_string(),
            event_json: "{}".to_string(),
        }],
    };
    let closed = app.evidence_store.close_timeline(&timeline.id, close_input).unwrap();
    assert!(closed.closed_at.is_some());
    assert_eq!(closed.after_events.len(), 1);
}
