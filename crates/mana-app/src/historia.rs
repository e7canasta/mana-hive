use ctx_historia::{
    DetectionInput, DetectionVerdict, IncidentKind, ReviewInput, ReviewStatus, Severity,
};

use crate::{error::AppFailure, identidad::required_token, state::AppState};

#[derive(Clone, Debug)]
pub struct IngestIncidentCommand {
    pub source_record_id: String,
    pub resident_id: String,
    pub bed_id: Option<String>,
    pub source_alert_id: Option<String>,
    pub kind: String,
    pub severity: String,
    pub occurred_at: String,
    pub location: Option<String>,
    pub activity: Option<String>,
    pub injury_status: String,
    pub self_recovery: Option<bool>,
    pub response_seconds: Option<i32>,
    pub narrative: Option<String>,
    pub interventions_json: Option<String>,
    pub source: String,
    pub model_version: String,
    pub confidence: Option<f64>,
    pub provenance_json: Option<String>,
}

#[derive(Clone, Debug)]
pub struct CreateReviewCommand {
    pub status: String,
    pub detection_verdict: Option<String>,
    pub review_note: Option<String>,
    pub resolved_at: Option<String>,
}

#[derive(Clone, Debug, serde::Serialize)]
pub struct IncidentView {
    pub id: String,
    pub resident_id: String,
    /// El estado vigente, plano. Duplica `current.status` a proposito: el
    /// contrato del cliente lo pide arriba y `current` lleva el detalle.
    pub status: String,
    pub occurred_at: String,
    pub detection: DetectionView,
    pub reviews: Vec<ReviewView>,
    pub current: CurrentView,
}

#[derive(Clone, Debug, serde::Serialize)]
pub struct DetectionView {
    pub kind: String,
    pub severity: String,
    pub injury_status: String,
    pub source: String,
    pub model_version: String,
}

#[derive(Clone, Debug, serde::Serialize)]
pub struct ReviewView {
    pub id: String,
    pub status: String,
    pub detection_verdict: Option<String>,
    pub review_note: Option<String>,
    pub resolved_at: Option<String>,
    pub actor_id: String,
    pub created_at: String,
}

#[derive(Clone, Debug, serde::Serialize)]
pub struct CurrentView {
    pub status: String,
    pub detection_verdict: Option<String>,
    pub resolved_at: Option<String>,
}

/// La reconstruccion de un incidente: que informo el detector alrededor del
/// momento, y que se deriva de eso.
///
/// Compone Historia (la deteccion) con Observacion (la evidencia de la cama) y
/// Poblacion (que cama ocupaba). Es lo que permite revisar una caida sin
/// mirar la base a mano.
#[derive(Clone, Debug, serde::Serialize)]
pub struct SequenceWindowView {
    pub from: String,
    pub to: String,
    pub before_minutes: i64,
    pub after_minutes: i64,
}

#[derive(Clone, Debug, serde::Serialize)]
pub struct SequenceEventView {
    pub id: String,
    pub occurred_at: String,
    pub state: Option<String>,
    /// Etiqueta legible; hoy es el estado crudo. Cuando el vocabulario del
    /// detector se cierre en un enum, sale de ahi.
    pub state_label: String,
    pub room_state: Option<String>,
    pub substate: Option<String>,
    /// Nunca se persiste: es politica, no observacion. Viaja en `null` para no
    /// romper al cliente que ya lee el campo.
    pub alert_level: Option<String>,
    pub on_floor: bool,
    /// **La IA cell si detecta presencia de staff**; lo que falta es que el
    /// vocabulario de `sensor_events` la transporte. Hasta entonces queda en
    /// `false`, y `staff_arrival_seconds` en `null` — que es el numero que
    /// convierte una revision de caida en una medida de respuesta. Es F11.
    pub staff_present: bool,
    pub offset_seconds: i64,
}

#[derive(Clone, Debug, serde::Serialize)]
pub struct SequenceDerivedView {
    pub time_on_floor_seconds: Option<i64>,
    pub reached_floor: bool,
    pub staff_arrival_at: Option<String>,
    pub staff_arrival_seconds: Option<i64>,
    pub response_seconds: Option<i64>,
}

#[derive(Clone, Debug, serde::Serialize)]
pub struct IncidentSequenceView {
    pub incident_id: String,
    pub occurred_at: String,
    pub window: SequenceWindowView,
    pub events: Vec<SequenceEventView>,
    pub derived: SequenceDerivedView,
    pub incident: IncidentView,
}

#[derive(Clone, Debug, serde::Serialize)]
pub struct IngestResultView {
    pub incident: IncidentView,
    pub duplicate: bool,
}

impl AppState {
    pub async fn ingest_incident(
        &self,
        clinical_secret: &str,
        command: IngestIncidentCommand,
    ) -> Result<IngestResultView, AppFailure> {
        validate_clinical_secret(clinical_secret)?;

        let kind = IncidentKind::parse(&command.kind)
            .map_err(|e| AppFailure::validation(e.to_string(), Some("kind")))?;
        let severity = Severity::parse(&command.severity)
            .map_err(|e| AppFailure::validation(e.to_string(), Some("severity")))?;
        let occurred_at = command
            .occurred_at
            .parse()
            .map_err(|_| AppFailure::validation("invalid occurred_at", Some("occurred_at")))?;

        let (detection, duplicate) = self.history.ingest_detection(DetectionInput {
            source_record_id: command.source_record_id,
            resident_id: command.resident_id,
            bed_id: command.bed_id,
            source_alert_id: command.source_alert_id,
            kind,
            severity,
            occurred_at,
            location: command.location,
            activity: command.activity,
            injury_status: command.injury_status,
            self_recovery: command.self_recovery,
            response_seconds: command.response_seconds,
            narrative: command.narrative,
            interventions_json: command.interventions_json,
            source: command.source,
            model_version: command.model_version,
            confidence: command.confidence,
            provenance_json: command.provenance_json,
        })?;

        let reviews = self.history.list_reviews(detection.id.as_str())?;
        let incident = incident_view(detection, &reviews);

        Ok(IngestResultView {
            incident,
            duplicate,
        })
    }

    pub async fn list_incidents(
        &self,
        token: &str,
        resident_id: &str,
        limit: i64,
    ) -> Result<Vec<IncidentView>, AppFailure> {
        required_token(token)?;
        let detections = self.history.list_by_resident(resident_id, limit)?;
        let mut incidents = Vec::with_capacity(detections.len());
        for det in detections {
            let reviews = self.history.list_reviews(det.id.as_str())?;
            incidents.push(incident_view(det, &reviews));
        }
        Ok(incidents)
    }

    pub async fn get_incident(
        &self,
        token: &str,
        incident_id: &str,
    ) -> Result<IncidentView, AppFailure> {
        required_token(token)?;
        let id = ctx_historia::DetectionId::new(incident_id);
        let detection = self.history.get_detection(&id)?;
        let reviews = self.history.list_reviews(incident_id)?;
        Ok(incident_view(detection, &reviews))
    }

    /// La secuencia alrededor de un incidente: diez minutos antes y despues.
    pub async fn get_incident_sequence(
        &self,
        token: &str,
        incident_id: &str,
    ) -> Result<IncidentSequenceView, AppFailure> {
        const BEFORE_MINUTES: i64 = 10;
        const AFTER_MINUTES: i64 = 10;

        required_token(token)?;
        let id = ctx_historia::DetectionId::new(incident_id);
        let detection = self.history.get_detection(&id)?;
        let reviews = self.history.list_reviews(incident_id)?;

        let center = *detection.occurred_at.as_datetime();
        let from = center - chrono::Duration::minutes(BEFORE_MINUTES);
        let to = center + chrono::Duration::minutes(AFTER_MINUTES);

        // La cama la sabe la deteccion; si no la trae, no hay evidencia que
        // cruzar y la secuencia sale vacia en vez de fallar.
        let events = match detection.bed_id.clone() {
            Some(bed_id) => self.observation.events_for_bed(&bed_id, 500)?,
            None => Vec::new(),
        };

        let mut sequence: Vec<SequenceEventView> = events
            .into_iter()
            .filter(|event| {
                let at = *event.occurred_at.as_datetime();
                at >= from && at <= to
            })
            .map(|event| {
                let at = *event.occurred_at.as_datetime();
                let state = event.state.clone();
                let on_floor = state
                    .as_deref()
                    .map(|value| value.contains("floor") || value.contains("fallen"))
                    .unwrap_or(false);
                SequenceEventView {
                    id: event.id.as_str().to_owned(),
                    occurred_at: event.occurred_at.to_string(),
                    state_label: state.clone().unwrap_or_else(|| event.kind.clone()),
                    state,
                    room_state: event.room_state.clone(),
                    substate: event.substate.clone(),
                    alert_level: None,
                    on_floor,
                    staff_present: false,
                    offset_seconds: at.signed_duration_since(center).num_seconds(),
                }
            })
            .collect();
        sequence.sort_by_key(|event| event.offset_seconds);

        let on_floor: Vec<i64> = sequence
            .iter()
            .filter(|event| event.on_floor)
            .map(|event| event.offset_seconds)
            .collect();
        let time_on_floor_seconds = match (on_floor.first(), on_floor.last()) {
            (Some(first), Some(last)) if on_floor.len() > 1 => Some(last - first),
            _ => None,
        };

        Ok(IncidentSequenceView {
            incident_id: incident_id.to_owned(),
            occurred_at: detection.occurred_at.to_string(),
            window: SequenceWindowView {
                from: mana_kernel::Instante::new(from).to_string(),
                to: mana_kernel::Instante::new(to).to_string(),
                before_minutes: BEFORE_MINUTES,
                after_minutes: AFTER_MINUTES,
            },
            events: sequence,
            derived: SequenceDerivedView {
                time_on_floor_seconds,
                reached_floor: !on_floor.is_empty(),
                // La llegada del staff se deriva del primer evento con
                // presencia. El detector la ve; el evento todavia no la trae,
                // asi que por ahora es `null`. Cero seria una mentira comoda.
                staff_arrival_at: None,
                staff_arrival_seconds: None,
                response_seconds: detection.response_seconds.map(i64::from),
            },
            incident: incident_view(detection, &reviews),
        })
    }

    pub async fn create_review(
        &self,
        token: &str,
        incident_id: &str,
        command: CreateReviewCommand,
    ) -> Result<IncidentView, AppFailure> {
        let actor = required_token(token)?;

        let status = ReviewStatus::parse(&command.status)
            .map_err(|e| AppFailure::validation(e.to_string(), Some("status")))?;
        let verdict = command
            .detection_verdict
            .map(|v| {
                DetectionVerdict::parse(&v)
                    .map_err(|e| AppFailure::validation(e.to_string(), Some("detection_verdict")))
            })
            .transpose()?;
        let resolved_at = command
            .resolved_at
            .map(|r| r.parse())
            .transpose()
            .map_err(|_| AppFailure::validation("invalid resolved_at", Some("resolved_at")))?;

        self.history.create_review(
            incident_id,
            ReviewInput {
                status,
                detection_verdict: verdict,
                review_note: command.review_note,
                resolved_at,
            },
            mana_kernel::Id::new(&actor),
            mana_kernel::Instante::now(),
        )?;

        let id = ctx_historia::DetectionId::new(incident_id);
        let detection = self.history.get_detection(&id)?;
        let reviews = self.history.list_reviews(incident_id)?;
        Ok(incident_view(detection, &reviews))
    }
}

fn validate_clinical_secret(secret: &str) -> Result<(), AppFailure> {
    let expected =
        std::env::var("MANA_CLINICAL_SECRET").unwrap_or_else(|_| "clinical-dev-secret".to_owned());
    if secret != expected {
        return Err(AppFailure::new(
            mana_kernel::Fallo::Forbidden,
            "invalid clinical secret",
        ));
    }
    Ok(())
}

fn incident_view(
    detection: ctx_historia::IncidentDetection,
    reviews: &[ctx_historia::IncidentReview],
) -> IncidentView {
    let current = reviews.last().map_or(
        CurrentView {
            status: "open".to_owned(),
            detection_verdict: None,
            resolved_at: None,
        },
        |r| CurrentView {
            status: r.status.as_str().to_owned(),
            detection_verdict: r.detection_verdict.map(|v| v.as_str().to_owned()),
            resolved_at: r.resolved_at.map(|t| t.to_string()),
        },
    );

    IncidentView {
        id: detection.id.into_string(),
        resident_id: detection.resident_id,
        status: current.status.clone(),
        occurred_at: detection.occurred_at.to_string(),
        detection: DetectionView {
            kind: detection.kind.as_str().to_owned(),
            severity: detection.severity.as_str().to_owned(),
            injury_status: detection.injury_status,
            source: detection.source,
            model_version: detection.model_version,
        },
        reviews: reviews.iter().map(review_view).collect(),
        current,
    }
}

fn review_view(review: &ctx_historia::IncidentReview) -> ReviewView {
    ReviewView {
        id: review.id.as_str().to_owned(),
        status: review.status.as_str().to_owned(),
        detection_verdict: review.detection_verdict.map(|v| v.as_str().to_owned()),
        review_note: review.review_note.clone(),
        resolved_at: review.resolved_at.map(|t| t.to_string()),
        actor_id: review.actor_id.as_str().to_owned(),
        created_at: review.created_at.to_string(),
    }
}
