use chrono::{Duration, Utc};
use mana_kernel::Instante;

use crate::{
    clear_projection_in_transaction,
    evidence::{EventInput, Resolution},
    run_migrations,
    state::{Freshness, FreshnessThresholds},
    ObservationStore,
};

fn store() -> ObservationStore {
    let pool = mana_storage::build_pool(":memory:").unwrap();
    run_migrations(&pool).unwrap();
    ObservationStore::new(pool)
}

fn event(source_event_id: &str, state: &str, resolution: Resolution) -> EventInput {
    EventInput {
        source_event_id: source_event_id.to_owned(),
        monitor_key: "mana-camera-118".to_owned(),
        resolution,
        kind: "room_state".to_owned(),
        room_state: Some("occupied".to_owned()),
        substate: None,
        zone: None,
        state: Some(state.to_owned()),
        sleeping: None,
        occurred_at: Instante::now(),
        payload_json: "{}".to_owned(),
    }
}

fn resolved() -> Resolution {
    Resolution::Resolved {
        bed_id: "bed-118-0".to_owned(),
        resident_id: Some("res-1".to_owned()),
    }
}

#[test]
fn ingest_is_idempotent_by_source_event_id() {
    let store = store();
    let first = store
        .ingest(event("evt-1", "laying_in_bed", resolved()))
        .unwrap();
    assert!(!first.duplicate);

    let retry = store
        .ingest(event("evt-1", "standing", resolved()))
        .unwrap();
    assert!(retry.duplicate, "el reintento del bridge no es un error");
    assert_eq!(retry.event.id.as_str(), first.event.id.as_str());

    // Un duplicado no vuelve a proyectar: el estado sigue siendo el del primero.
    let projected = store.current_state("bed-118-0").unwrap().unwrap();
    assert_eq!(projected.state, "laying_in_bed");
}

#[test]
fn an_unresolved_monitor_key_is_still_accepted_and_counted() {
    let store = store();
    let ingestion = store
        .ingest(event("evt-huerfano", "standing", Resolution::Unresolved))
        .unwrap();

    assert!(!ingestion.duplicate);
    assert!(!ingestion.event.resolution.is_resolved());
    // La evidencia no se tira: la camara si estaba viendo algo.
    assert_eq!(store.unresolved_count().unwrap(), 1);
    // Y no proyecta sobre ninguna cama.
    assert!(store.current_state("bed-118-0").unwrap().is_none());
}

#[test]
fn unknown_sleeping_stays_unknown() {
    let store = store();
    store
        .ingest(event("evt-1", "laying_in_bed", resolved()))
        .unwrap();
    let projected = store.current_state("bed-118-0").unwrap().unwrap();

    // Invariante 4: "no se" no es "no". Un `DEFAULT 0` romperia esto.
    assert_eq!(projected.sleeping, None);

    let mut awake = event("evt-2", "laying_in_bed", resolved());
    awake.sleeping = Some(false);
    store.ingest(awake).unwrap();
    assert_eq!(
        store.current_state("bed-118-0").unwrap().unwrap().sleeping,
        Some(false),
        "false informado es distinto de no informado"
    );
}

#[test]
fn state_since_only_moves_when_the_state_changes() {
    let store = store();
    store
        .ingest(event("evt-1", "standing", resolved()))
        .unwrap();
    let first = store.current_state("bed-118-0").unwrap().unwrap();

    // Mismo estado repetido: el reloj de la permanencia no se reinicia, o una
    // alarma de "cuarenta minutos fuera de la cama" no venceria nunca.
    store
        .ingest(event("evt-2", "standing", resolved()))
        .unwrap();
    let repeated = store.current_state("bed-118-0").unwrap().unwrap();
    assert_eq!(repeated.state_since, first.state_since);

    store
        .ingest(event("evt-3", "laying_in_bed", resolved()))
        .unwrap();
    let changed = store.current_state("bed-118-0").unwrap().unwrap();
    assert_ne!(changed.state_since, first.state_since);
}

#[test]
fn clearing_the_projection_leaves_the_evidence_intact() {
    let store = store();
    store
        .ingest(event("evt-1", "standing", resolved()))
        .unwrap();

    {
        // El pool en memoria tiene una sola conexion: hay que devolverla antes
        // de volver a hablarle al store.
        let mut connection = mana_storage::connection(store.pool()).unwrap();
        clear_projection_in_transaction(&mut connection, "bed-118-0").unwrap();
    }

    // Invariante 6: cambiar el ocupante descarta la proyeccion...
    assert!(store.current_state("bed-118-0").unwrap().is_none());

    // ...pero la evidencia es inmutable y sigue ahi: reingerir el mismo
    // `source_event_id` sigue siendo un duplicado.
    let retry = store
        .ingest(event("evt-1", "standing", resolved()))
        .unwrap();
    assert!(retry.duplicate);
}

#[test]
fn a_malformed_payload_is_rejected_before_touching_the_database() {
    let store = store();
    let mut broken = event("evt-1", "standing", resolved());
    broken.payload_json = "{no es json".to_owned();

    assert!(store.ingest(broken).is_err());
    assert!(store.current_state("bed-118-0").unwrap().is_none());
}

#[test]
fn freshness_is_derived_and_distinguishes_never_seen_from_offline() {
    let now = Instante::now();
    let thresholds = FreshnessThresholds::default();
    let ago = |seconds: i64| Instante::new(now.into_datetime() - Duration::seconds(seconds));

    // Nunca observada no es lo mismo que caida.
    assert_eq!(
        Freshness::derive(None, now, thresholds),
        Freshness::NotObserved
    );
    assert_eq!(
        Freshness::derive(Some(ago(10)), now, thresholds),
        Freshness::Live
    );
    assert_eq!(
        Freshness::derive(Some(ago(300)), now, thresholds),
        Freshness::Stale
    );
    assert_eq!(
        Freshness::derive(Some(ago(3600)), now, thresholds),
        Freshness::Offline
    );
    let _ = Utc::now();
}

mod summaries {
    use super::store;
    use crate::summaries::{
        BathroomSummaryInput, MobilitySummaryInput, Provenance, SleepSummaryInput,
    };

    fn provenance() -> Provenance {
        Provenance {
            source: "percepcion".to_owned(),
            model_version: "v1".to_owned(),
            confidence: Some(0.9),
            provenance_json: "{}".to_owned(),
        }
    }

    fn sleep(source_record_id: &str, wakes: i32, exits: i32) -> SleepSummaryInput {
        SleepSummaryInput {
            source_record_id: source_record_id.to_owned(),
            resident_id: "res-1".to_owned(),
            observed_on: "2026-08-18".to_owned(),
            calm_minutes: 300,
            restless_minutes: 60,
            awake_minutes: 30,
            out_of_bed_minutes: 20,
            bed_exit_count: exits,
            wake_count: wakes,
            provenance: provenance(),
        }
    }

    #[test]
    fn a_bed_exit_without_a_wake_is_rejected() {
        let store = store();
        // Salir de la cama implica haberse despertado.
        assert!(store.ingest_sleep_summary(sleep("rec-1", 1, 3)).is_err());
        assert!(store.ingest_sleep_summary(sleep("rec-1", 3, 1)).is_ok());
    }

    #[test]
    fn reingesting_a_day_replaces_it_and_keeps_created_at() {
        let store = store();
        let first = store.ingest_sleep_summary(sleep("rec-1", 3, 1)).unwrap();
        assert!(!first.replaced);

        let mut recalculated = sleep("rec-2", 5, 2);
        recalculated.calm_minutes = 400;
        let second = store.ingest_sleep_summary(recalculated).unwrap();

        // La fuente recalculo el mismo dia: se reemplaza, no se duplica.
        assert!(second.replaced);
        assert_eq!(second.summary.calm_minutes, 400);
        assert_eq!(second.summary.created_at, first.summary.created_at);
        assert_eq!(store.resident_sleep("res-1", 10).unwrap().len(), 1);
    }

    #[test]
    fn sleep_efficiency_is_derived_and_undefined_without_time_in_bed() {
        let store = store();
        let summary = store.ingest_sleep_summary(sleep("rec-1", 3, 1)).unwrap();
        // 300 tranquilo sobre 390 en cama.
        let efficiency = summary.summary.efficiency().unwrap();
        assert!((efficiency - 300.0 / 390.0).abs() < 1e-9);

        let mut none_in_bed = sleep("rec-2", 0, 0);
        none_in_bed.calm_minutes = 0;
        none_in_bed.restless_minutes = 0;
        none_in_bed.awake_minutes = 0;
        let empty = store.ingest_sleep_summary(none_in_bed).unwrap();
        // Dividir por cero no es cero: es "no se puede decir".
        assert_eq!(empty.summary.efficiency(), None);
    }

    #[test]
    fn bathroom_counts_cannot_exceed_their_total() {
        let store = store();
        let base = |night: i32, assisted: i32, longest: i32| BathroomSummaryInput {
            source_record_id: "rec-1".to_owned(),
            resident_id: "res-1".to_owned(),
            observed_on: "2026-08-18".to_owned(),
            visit_count: 4,
            night_visit_count: night,
            assisted_count: assisted,
            total_minutes: 40,
            longest_visit_minutes: longest,
            provenance: provenance(),
        };
        assert!(store.ingest_bathroom_summary(base(9, 1, 10)).is_err());
        assert!(store.ingest_bathroom_summary(base(1, 9, 10)).is_err());
        assert!(store.ingest_bathroom_summary(base(1, 1, 99)).is_err());
        assert!(store.ingest_bathroom_summary(base(1, 1, 10)).is_ok());
    }

    #[test]
    fn mobility_minutes_cannot_exceed_a_day_and_walking_is_a_subset() {
        let store = store();
        let base = |out_of_bed: i32, walking: i32, out_of_sight: i32| MobilitySummaryInput {
            source_record_id: "rec-1".to_owned(),
            resident_id: "res-1".to_owned(),
            observed_on: "2026-08-18".to_owned(),
            in_bed_minutes: 600,
            out_of_bed_minutes: out_of_bed,
            out_of_sight_minutes: out_of_sight,
            walking_minutes: walking,
            distance_meters: Some(120.0),
            transfer_count: 3,
            provenance: provenance(),
        };
        // Caminar es parte de estar fuera de la cama, no un sumando aparte.
        assert!(store.ingest_mobility_summary(base(60, 90, 10)).is_err());
        assert!(store.ingest_mobility_summary(base(900, 100, 900)).is_err());
        assert!(store.ingest_mobility_summary(base(300, 100, 60)).is_ok());
    }

    #[test]
    fn confidence_outside_zero_to_one_is_rejected() {
        let store = store();
        let mut invalid = sleep("rec-1", 3, 1);
        invalid.provenance.confidence = Some(1.4);
        assert!(store.ingest_sleep_summary(invalid).is_err());
    }
}
