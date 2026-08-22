use diesel::prelude::*;
use diesel::{OptionalExtension, SqliteConnection};
use mana_kernel::{Actor, Id, Instante};

use crate::common::parse_instant;
use crate::schema::incident_reviews;
use crate::HistoriaError;

use super::repo::RevisionesRepo;
use super::{DetectionVerdict, IncidentReview, ReviewId, ReviewInput, ReviewStatus};

#[derive(Queryable, Selectable)]
#[diesel(table_name = incident_reviews)]
#[diesel(check_for_backend(diesel::sqlite::Sqlite))]
struct ReviewRow {
    id: String,
    incident_id: String,
    status: String,
    detection_verdict: Option<String>,
    review_note: Option<String>,
    resolved_at: Option<String>,
    actor_id: String,
    created_at: String,
}

#[derive(Insertable)]
#[diesel(table_name = incident_reviews)]
struct NewReviewRow<'a> {
    id: &'a str,
    incident_id: &'a str,
    status: &'a str,
    detection_verdict: Option<&'a str>,
    review_note: Option<&'a str>,
    resolved_at: Option<&'a str>,
    actor_id: &'a str,
    created_at: &'a str,
}

impl RevisionesRepo for SqliteConnection {
    fn create_review_in_transaction(
        connection: &mut SqliteConnection,
        incident_id: &str,
        input: ReviewInput,
        actor_id: Id<Actor>,
        now: Instante,
    ) -> Result<IncidentReview, HistoriaError> {
        let id = super::new_review_id();
        let created_at = now.to_string();
        let resolved_at = input.resolved_at.map(|t| t.to_string());

        diesel::insert_into(incident_reviews::table)
            .values(NewReviewRow {
                id: id.as_str(),
                incident_id,
                status: input.status.as_str(),
                detection_verdict: input.detection_verdict.map(|v| v.as_str()),
                review_note: input.review_note.as_deref(),
                resolved_at: resolved_at.as_deref(),
                actor_id: actor_id.as_str(),
                created_at: &created_at,
            })
            .execute(connection)
            .map_err(HistoriaError::database)?;

        Ok(IncidentReview {
            id,
            incident_id: incident_id.to_owned(),
            status: input.status,
            detection_verdict: input.detection_verdict,
            review_note: input.review_note,
            resolved_at: input.resolved_at,
            actor_id,
            created_at: now,
        })
    }

    fn list_by_incident(
        connection: &mut SqliteConnection,
        incident_id: &str,
    ) -> Result<Vec<IncidentReview>, HistoriaError> {
        incident_reviews::table
            .filter(incident_reviews::incident_id.eq(incident_id))
            .select(ReviewRow::as_select())
            // Por orden de insercion, no por reloj: `created_at` tiene
            // milisegundos y tres revisiones seguidas empatan, dejando que
            // desempate un id aleatorio. El resultado era no determinista.
            .order(incident_reviews::rowid.asc())
            .load::<ReviewRow>(connection)
            .map_err(HistoriaError::database)?
            .into_iter()
            .map(IncidentReview::try_from)
            .collect()
    }

    fn get_current_review(
        connection: &mut SqliteConnection,
        incident_id: &str,
    ) -> Result<Option<IncidentReview>, HistoriaError> {
        incident_reviews::table
            .filter(incident_reviews::incident_id.eq(incident_id))
            .select(ReviewRow::as_select())
            // La revision vigente es la ultima insertada, no la de mayor
            // timestamp: ver el comentario en `list_reviews`.
            .order(incident_reviews::rowid.desc())
            .first(connection)
            .optional()
            .map_err(HistoriaError::database)?
            .map(IncidentReview::try_from)
            .transpose()
    }
}

impl TryFrom<ReviewRow> for IncidentReview {
    type Error = HistoriaError;

    fn try_from(row: ReviewRow) -> Result<Self, HistoriaError> {
        let created_at = parse_instant("created_at", row.created_at)?;
        let resolved_at = row
            .resolved_at
            .map(|v| parse_instant("resolved_at", v))
            .transpose()?;
        let detection_verdict = row
            .detection_verdict
            .map(|v| DetectionVerdict::parse(&v))
            .transpose()?;
        Ok(Self {
            id: ReviewId::new(row.id),
            incident_id: row.incident_id,
            status: ReviewStatus::parse(&row.status)?,
            detection_verdict,
            review_note: row.review_note,
            resolved_at,
            actor_id: Id::<Actor>::new(row.actor_id),
            created_at,
        })
    }
}
