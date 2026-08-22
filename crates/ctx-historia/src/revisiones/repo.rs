use diesel::SqliteConnection;
use mana_kernel::{Actor, Id, Instante};

use super::{IncidentReview, ReviewInput};
use crate::HistoriaError;

pub trait RevisionesRepo {
    fn create_review_in_transaction(
        connection: &mut SqliteConnection,
        incident_id: &str,
        input: ReviewInput,
        actor_id: Id<Actor>,
        now: Instante,
    ) -> Result<IncidentReview, HistoriaError>;

    fn list_by_incident(
        connection: &mut SqliteConnection,
        incident_id: &str,
    ) -> Result<Vec<IncidentReview>, HistoriaError>;

    fn get_current_review(
        connection: &mut SqliteConnection,
        incident_id: &str,
    ) -> Result<Option<IncidentReview>, HistoriaError>;
}
