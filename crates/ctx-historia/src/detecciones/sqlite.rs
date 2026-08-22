use diesel::prelude::*;
use diesel::{OptionalExtension, SqliteConnection};

use crate::common::parse_instant;
use crate::schema::incident_detections;
use crate::HistoriaError;

use super::repo::DeteccionesRepo;
use super::{DetectionId, DetectionInput, IncidentDetection, IncidentKind, Severity};

#[derive(Queryable, Selectable)]
#[diesel(table_name = incident_detections)]
#[diesel(check_for_backend(diesel::sqlite::Sqlite))]
struct DetectionRow {
    id: String,
    source_record_id: String,
    resident_id: String,
    bed_id: Option<String>,
    source_alert_id: Option<String>,
    kind: String,
    severity: String,
    occurred_at: String,
    location: Option<String>,
    activity: Option<String>,
    injury_status: String,
    self_recovery: Option<i32>,
    response_seconds: Option<i32>,
    narrative: Option<String>,
    interventions_json: String,
    source: String,
    model_version: String,
    confidence: Option<f64>,
    provenance_json: String,
    created_at: String,
}

#[derive(Insertable)]
#[diesel(table_name = incident_detections)]
struct NewDetectionRow<'a> {
    id: &'a str,
    source_record_id: &'a str,
    resident_id: &'a str,
    bed_id: Option<&'a str>,
    source_alert_id: Option<&'a str>,
    kind: &'a str,
    severity: &'a str,
    occurred_at: &'a str,
    location: Option<&'a str>,
    activity: Option<&'a str>,
    injury_status: &'a str,
    self_recovery: Option<i32>,
    response_seconds: Option<i32>,
    narrative: Option<&'a str>,
    interventions_json: &'a str,
    source: &'a str,
    model_version: &'a str,
    confidence: Option<f64>,
    provenance_json: &'a str,
    created_at: &'a str,
}

impl DeteccionesRepo for SqliteConnection {
    fn ingest_in_transaction(
        connection: &mut SqliteConnection,
        input: DetectionInput,
    ) -> Result<(IncidentDetection, bool), HistoriaError> {
        let existing = incident_detections::table
            .filter(incident_detections::source_record_id.eq(&input.source_record_id))
            .select(DetectionRow::as_select())
            .first(connection)
            .optional()
            .map_err(HistoriaError::database)?;

        if let Some(row) = existing {
            let detection = IncidentDetection::try_from(row)?;
            return Ok((detection, true));
        }

        let id = super::new_detection_id();
        let now = mana_kernel::Instante::now();
        let occurred_at = input.occurred_at.to_string();
        let created_at = now.to_string();
        diesel::insert_into(incident_detections::table)
            .values(NewDetectionRow {
                id: id.as_str(),
                source_record_id: &input.source_record_id,
                resident_id: &input.resident_id,
                bed_id: input.bed_id.as_deref(),
                source_alert_id: input.source_alert_id.as_deref(),
                kind: input.kind.as_str(),
                severity: input.severity.as_str(),
                occurred_at: &occurred_at,
                location: input.location.as_deref(),
                activity: input.activity.as_deref(),
                injury_status: &input.injury_status,
                self_recovery: input.self_recovery.map(i32::from),
                response_seconds: input.response_seconds,
                narrative: input.narrative.as_deref(),
                interventions_json: input.interventions_json.as_deref().unwrap_or("[]"),
                source: &input.source,
                model_version: &input.model_version,
                confidence: input.confidence,
                provenance_json: input.provenance_json.as_deref().unwrap_or("{}"),
                created_at: &created_at,
            })
            .execute(connection)
            .map_err(HistoriaError::database)?;

        let detection = IncidentDetection {
            id,
            source_record_id: input.source_record_id,
            resident_id: input.resident_id,
            bed_id: input.bed_id,
            source_alert_id: input.source_alert_id,
            kind: input.kind,
            severity: input.severity,
            occurred_at: input.occurred_at,
            location: input.location,
            activity: input.activity,
            injury_status: input.injury_status,
            self_recovery: input.self_recovery,
            response_seconds: input.response_seconds,
            narrative: input.narrative,
            interventions_json: input.interventions_json.unwrap_or_else(|| "[]".to_owned()),
            source: input.source,
            model_version: input.model_version,
            confidence: input.confidence,
            provenance_json: input.provenance_json.unwrap_or_else(|| "{}".to_owned()),
            created_at: now,
        };
        Ok((detection, false))
    }

    fn get_detection(
        connection: &mut SqliteConnection,
        id: &DetectionId,
    ) -> Result<IncidentDetection, HistoriaError> {
        incident_detections::table
            .filter(incident_detections::id.eq(id.as_str()))
            .select(DetectionRow::as_select())
            .first(connection)
            .optional()
            .map_err(HistoriaError::database)?
            .map(IncidentDetection::try_from)
            .transpose()?
            .ok_or(HistoriaError::NotFound)
    }

    fn list_by_resident(
        connection: &mut SqliteConnection,
        resident_id: &str,
        limit: i64,
    ) -> Result<Vec<IncidentDetection>, HistoriaError> {
        incident_detections::table
            .filter(incident_detections::resident_id.eq(resident_id))
            .select(DetectionRow::as_select())
            .order(incident_detections::occurred_at.desc())
            .limit(limit)
            .load::<DetectionRow>(connection)
            .map_err(HistoriaError::database)?
            .into_iter()
            .map(IncidentDetection::try_from)
            .collect()
    }
}

impl TryFrom<DetectionRow> for IncidentDetection {
    type Error = HistoriaError;

    fn try_from(row: DetectionRow) -> Result<Self, HistoriaError> {
        let occurred_at = parse_instant("occurred_at", row.occurred_at)?;
        let created_at = parse_instant("created_at", row.created_at)?;
        Ok(Self {
            id: DetectionId::new(row.id),
            source_record_id: row.source_record_id,
            resident_id: row.resident_id,
            bed_id: row.bed_id,
            source_alert_id: row.source_alert_id,
            kind: IncidentKind::parse(&row.kind)?,
            severity: Severity::parse(&row.severity)?,
            occurred_at,
            location: row.location,
            activity: row.activity,
            injury_status: row.injury_status,
            self_recovery: row.self_recovery.map(|v| v != 0),
            response_seconds: row.response_seconds,
            narrative: row.narrative,
            interventions_json: row.interventions_json,
            source: row.source,
            model_version: row.model_version,
            confidence: row.confidence,
            provenance_json: row.provenance_json,
            created_at,
        })
    }
}
