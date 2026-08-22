use diesel::SqliteConnection;

use super::{DetectionId, DetectionInput, IncidentDetection};
use crate::HistoriaError;

pub trait DeteccionesRepo {
    fn ingest_in_transaction(
        connection: &mut SqliteConnection,
        input: DetectionInput,
    ) -> Result<(IncidentDetection, bool), HistoriaError>;

    fn get_detection(
        connection: &mut SqliteConnection,
        id: &DetectionId,
    ) -> Result<IncidentDetection, HistoriaError>;

    fn list_by_resident(
        connection: &mut SqliteConnection,
        resident_id: &str,
        limit: i64,
    ) -> Result<Vec<IncidentDetection>, HistoriaError>;
}
