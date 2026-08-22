mod common;
mod error;

pub mod detecciones;
pub mod revisiones;
pub mod schema;

pub use detecciones::{
    new_detection_id, DeteccionesError, DetectionId, DetectionInput, IncidentDetection,
    IncidentKind, Severity,
};
pub use error::HistoriaError;
pub use mana_storage::DbPool;
pub use revisiones::{
    new_review_id, DetectionVerdict, IncidentReview, ReviewId, ReviewInput, ReviewStatus,
    RevisionesError,
};

use diesel::prelude::*;
use diesel_migrations::{embed_migrations, EmbeddedMigrations};
use mana_kernel::{Actor, Id, Instante};
use mana_storage::{connection as get_connection, DbConnection};

use crate::detecciones::repo::DeteccionesRepo;
use crate::revisiones::repo::RevisionesRepo;

pub const MIGRATIONS: EmbeddedMigrations = embed_migrations!();

#[derive(Clone)]
pub struct HistoryStore {
    pub(crate) pool: DbPool,
}

pub fn run_migrations(pool: &DbPool) -> Result<(), HistoriaError> {
    mana_storage::run_migrations(pool, MIGRATIONS).map_err(HistoriaError::from)
}

impl HistoryStore {
    pub fn new(pool: DbPool) -> Self {
        Self { pool }
    }

    pub fn pool(&self) -> &DbPool {
        &self.pool
    }

    fn connection(&self) -> Result<DbConnection, HistoriaError> {
        get_connection(&self.pool).map_err(HistoriaError::from)
    }

    pub fn ingest_detection(
        &self,
        input: DetectionInput,
    ) -> Result<(IncidentDetection, bool), HistoriaError> {
        let mut connection = self.connection()?;
        <SqliteConnection as DeteccionesRepo>::ingest_in_transaction(&mut connection, input)
    }

    pub fn get_detection(&self, id: &DetectionId) -> Result<IncidentDetection, HistoriaError> {
        let mut connection = self.connection()?;
        <SqliteConnection as DeteccionesRepo>::get_detection(&mut connection, id)
    }

    pub fn list_by_resident(
        &self,
        resident_id: &str,
        limit: i64,
    ) -> Result<Vec<IncidentDetection>, HistoriaError> {
        let mut connection = self.connection()?;
        <SqliteConnection as DeteccionesRepo>::list_by_resident(&mut connection, resident_id, limit)
    }

    pub fn create_review(
        &self,
        incident_id: &str,
        input: ReviewInput,
        actor_id: Id<Actor>,
        now: Instante,
    ) -> Result<IncidentReview, HistoriaError> {
        let mut connection = self.connection()?;
        <SqliteConnection as RevisionesRepo>::create_review_in_transaction(
            &mut connection,
            incident_id,
            input,
            actor_id,
            now,
        )
    }

    pub fn list_reviews(&self, incident_id: &str) -> Result<Vec<IncidentReview>, HistoriaError> {
        let mut connection = self.connection()?;
        <SqliteConnection as RevisionesRepo>::list_by_incident(&mut connection, incident_id)
    }

    pub fn get_current_review(
        &self,
        incident_id: &str,
    ) -> Result<Option<IncidentReview>, HistoriaError> {
        let mut connection = self.connection()?;
        <SqliteConnection as RevisionesRepo>::get_current_review(&mut connection, incident_id)
    }
}

#[cfg(test)]
pub(crate) mod testsupport {
    use mana_storage::build_pool;

    use super::{run_migrations, HistoryStore};

    pub(crate) fn store() -> HistoryStore {
        let pool = build_pool(":memory:").unwrap();
        run_migrations(&pool).unwrap();
        HistoryStore::new(pool)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::testsupport::store;

    fn input(source_record_id: &str) -> DetectionInput {
        DetectionInput {
            source_record_id: source_record_id.to_owned(),
            resident_id: "resident-1".to_owned(),
            bed_id: Some("bed-1".to_owned()),
            source_alert_id: None,
            kind: IncidentKind::Fall,
            severity: Severity::High,
            occurred_at: "2026-08-18T03:12:44.000Z".parse().unwrap(),
            location: None,
            activity: None,
            injury_status: "unknown".to_owned(),
            self_recovery: None,
            response_seconds: None,
            narrative: None,
            interventions_json: None,
            source: "ai".to_owned(),
            model_version: "v1".to_owned(),
            confidence: None,
            provenance_json: None,
        }
    }

    fn review_input(status: ReviewStatus) -> ReviewInput {
        ReviewInput {
            status,
            detection_verdict: None,
            review_note: None,
            resolved_at: None,
        }
    }

    fn actor() -> Id<Actor> {
        Id::new("user-1")
    }

    fn t(minute: u32) -> Instante {
        format!("2026-08-18T03:{minute:02}:00.000Z")
            .parse()
            .unwrap()
    }

    #[test]
    fn ingest_and_get_detection() {
        let store = store();
        let (det, duplicate) = store.ingest_detection(input("sr-001")).unwrap();
        assert!(!duplicate);
        assert_eq!(det.kind, IncidentKind::Fall);
        assert_eq!(det.severity, Severity::High);

        let fetched = store.get_detection(&det.id).unwrap();
        assert_eq!(fetched.id, det.id);
    }

    #[test]
    fn duplicate_source_record_returns_existing() {
        let store = store();
        let (det1, dup1) = store.ingest_detection(input("sr-002")).unwrap();
        assert!(!dup1);

        let (det2, dup2) = store.ingest_detection(input("sr-002")).unwrap();
        assert!(dup2);
        assert_eq!(det1.id, det2.id);
    }

    #[test]
    fn create_review_and_list() {
        let store = store();
        let (det, _) = store.ingest_detection(input("sr-003")).unwrap();

        let review = store
            .create_review(
                det.id.as_str(),
                review_input(ReviewStatus::UnderReview),
                actor(),
                t(1),
            )
            .unwrap();
        assert_eq!(review.status, ReviewStatus::UnderReview);
        assert_eq!(review.actor_id.as_str(), "user-1");

        let reviews = store.list_reviews(det.id.as_str()).unwrap();
        assert_eq!(reviews.len(), 1);
    }

    #[test]
    fn review_reopen_and_close_preserves_all() {
        let store = store();
        let (det, _) = store.ingest_detection(input("sr-004")).unwrap();

        store
            .create_review(
                det.id.as_str(),
                review_input(ReviewStatus::UnderReview),
                actor(),
                t(1),
            )
            .unwrap();
        store
            .create_review(
                det.id.as_str(),
                ReviewInput {
                    status: ReviewStatus::Open,
                    detection_verdict: None,
                    review_note: Some("reopened".to_owned()),
                    resolved_at: None,
                },
                actor(),
                t(2),
            )
            .unwrap();
        store
            .create_review(
                det.id.as_str(),
                ReviewInput {
                    status: ReviewStatus::Closed,
                    detection_verdict: Some(DetectionVerdict::NotAFall),
                    review_note: None,
                    resolved_at: Some("2026-08-18T04:00:00.000Z".parse().unwrap()),
                },
                actor(),
                t(3),
            )
            .unwrap();

        let reviews = store.list_reviews(det.id.as_str()).unwrap();
        assert_eq!(reviews.len(), 3);
        assert_eq!(reviews[0].status, ReviewStatus::UnderReview);
        assert_eq!(reviews[1].status, ReviewStatus::Open);
        assert_eq!(reviews[2].status, ReviewStatus::Closed);
        assert_eq!(
            reviews[2].detection_verdict,
            Some(DetectionVerdict::NotAFall)
        );
    }

    #[test]
    fn current_review_returns_latest() {
        let store = store();
        let (det, _) = store.ingest_detection(input("sr-005")).unwrap();

        store
            .create_review(
                det.id.as_str(),
                review_input(ReviewStatus::Open),
                actor(),
                t(1),
            )
            .unwrap();
        store
            .create_review(
                det.id.as_str(),
                review_input(ReviewStatus::Closed),
                actor(),
                t(2),
            )
            .unwrap();

        let current = store.get_current_review(det.id.as_str()).unwrap().unwrap();
        assert_eq!(current.status, ReviewStatus::Closed);
    }

    #[test]
    fn list_by_resident() {
        let store = store();
        store.ingest_detection(input("sr-006")).unwrap();
        store
            .ingest_detection(DetectionInput {
                source_record_id: "sr-007".to_owned(),
                resident_id: "resident-2".to_owned(),
                ..input("sr-007")
            })
            .unwrap();

        let r1 = store.list_by_resident("resident-1", 10).unwrap();
        assert_eq!(r1.len(), 1);
        let r2 = store.list_by_resident("resident-2", 10).unwrap();
        assert_eq!(r2.len(), 1);
    }
}

#[cfg(test)]
mod orden_de_revisiones {
    use super::*;
    use crate::testsupport::store;

    /// Tres revisiones con el **mismo** `now` empatan en `created_at`. Antes el
    /// desempate era el id aleatorio, y la revision vigente salia al azar: la
    /// escena de historia fallaba dando "open" o "under_review" segun la
    /// corrida. El orden de un log append-only es el de insercion.
    #[test]
    fn the_current_review_is_the_last_inserted_even_within_the_same_millisecond() {
        let store = store();
        let now: Instante = "2026-08-18T03:12:44.000Z".parse().unwrap();
        let actor = Id::<Actor>::new("user-1");

        let detection = store
            .ingest_detection(DetectionInput {
                source_record_id: "sr-orden".to_owned(),
                resident_id: "resident-1".to_owned(),
                bed_id: None,
                source_alert_id: None,
                kind: IncidentKind::Fall,
                severity: Severity::High,
                occurred_at: now,
                location: None,
                activity: None,
                injury_status: "unknown".to_owned(),
                self_recovery: None,
                response_seconds: None,
                narrative: None,
                interventions_json: None,
                source: "sensor".to_owned(),
                model_version: "v1".to_owned(),
                confidence: None,
                provenance_json: None,
            })
            .unwrap()
            .0;
        let incident_id = detection.id.as_str().to_owned();

        for status in [
            ReviewStatus::UnderReview,
            ReviewStatus::Open,
            ReviewStatus::Closed,
        ] {
            store
                .create_review(
                    &incident_id,
                    ReviewInput {
                        status,
                        detection_verdict: None,
                        review_note: None,
                        resolved_at: None,
                    },
                    actor.clone(),
                    now,
                )
                .unwrap();
        }

        let current = store.get_current_review(&incident_id).unwrap().unwrap();
        assert_eq!(current.status, ReviewStatus::Closed);

        let reviews = store.list_reviews(&incident_id).unwrap();
        assert_eq!(reviews.len(), 3);
        assert_eq!(reviews[0].status, ReviewStatus::UnderReview);
        assert_eq!(reviews[1].status, ReviewStatus::Open);
        assert_eq!(reviews[2].status, ReviewStatus::Closed);
    }
}
