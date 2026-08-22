use std::{collections::HashMap, sync::Arc};

use axum::{
    body::Body,
    http::{Request, StatusCode},
    response::Response,
};
use mana_app::{AppState, CreateReviewCommand, IngestIncidentCommand};

use crate::{
    path_segment,
    response::{failure_response, json_body, json_value},
    rust_handler, RustHandler,
};

pub fn historia_handlers(app: Arc<AppState>) -> HashMap<String, RustHandler> {
    let mut handlers = HashMap::new();
    register(
        &mut handlers,
        "clinical.incidents.ingest.post",
        app.clone(),
        ingest,
    );
    register(
        &mut handlers,
        "residents.incidents.get",
        app.clone(),
        list_incidents,
    );
    register(
        &mut handlers,
        "incidents.sequence.get",
        app.clone(),
        incident_detail,
    );
    register(
        &mut handlers,
        "incidents.update.patch",
        app.clone(),
        create_review,
    );
    // El cliente revisa el incidente con un PATCH sobre el recurso; la tabla
    // solo tenia el POST a `/reviews` y el PATCH caia en 404.
    register(
        &mut handlers,
        "incidents.review.patch",
        app.clone(),
        review_incident,
    );
    handlers
}

fn register(
    handlers: &mut HashMap<String, RustHandler>,
    id: &str,
    app: Arc<AppState>,
    handler: fn(Arc<AppState>, Request<Body>) -> HandlerFuture,
) {
    handlers.insert(
        id.to_owned(),
        rust_handler(move |request| handler(app.clone(), request)),
    );
}

type HandlerFuture = std::pin::Pin<Box<dyn std::future::Future<Output = Response> + Send>>;

fn authorization_token(headers: &axum::http::HeaderMap) -> String {
    headers
        .get("authorization")
        .and_then(|v| v.to_str().ok())
        .and_then(|v| v.strip_prefix("Bearer "))
        .unwrap_or("")
        .to_owned()
}

fn clinical_secret(headers: &axum::http::HeaderMap) -> String {
    headers
        .get("x-clinical-secret")
        .and_then(|v| v.to_str().ok())
        .unwrap_or("")
        .to_owned()
}

// POST /internal/v1/clinical/incidents
fn ingest(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let secret = clinical_secret(request.headers());
    Box::pin(async move {
        let body: IngestRequest = match json_body(request.into_body()).await {
            Ok(b) => b,
            Err(r) => return r,
        };
        match app
            .ingest_incident(
                &secret,
                IngestIncidentCommand {
                    source_record_id: body.source_record_id,
                    resident_id: body.resident_id,
                    bed_id: body.bed_id,
                    source_alert_id: body.source_alert_id,
                    kind: body.kind,
                    severity: body.severity,
                    occurred_at: body.occurred_at,
                    location: body.location,
                    activity: body.activity,
                    injury_status: body.injury_status,
                    self_recovery: body.self_recovery,
                    response_seconds: body.response_seconds,
                    narrative: body.narrative,
                    interventions_json: body
                        .interventions
                        .as_ref()
                        .and_then(|values| serde_json::to_string(values).ok())
                        .or(body.interventions_json),
                    source: body.source,
                    model_version: body.model_version,
                    confidence: body.confidence,
                    provenance_json: body.provenance_json,
                },
            )
            .await
        {
            Ok(result) => {
                let status = if result.duplicate {
                    StatusCode::OK
                } else {
                    StatusCode::CREATED
                };
                json_value(
                    status,
                    &serde_json::json!({
                        "incident": result.incident,
                        "duplicate": result.duplicate,
                    }),
                )
            }
            Err(failure) => failure_response(failure),
        }
    })
}

// GET /api/v1/residents/:residentId/incidents
fn list_incidents(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let token = authorization_token(request.headers());
    let resident_id = path_segment(&request, 3).to_owned();
    Box::pin(async move {
        match app.list_incidents(&token, &resident_id, 50).await {
            Ok(incidents) => json_value(
                StatusCode::OK,
                &serde_json::json!({
                    "incidents": incidents,
                }),
            ),
            Err(failure) => failure_response(failure),
        }
    })
}

// GET /api/v1/incidents/:incidentId/sequence
fn incident_detail(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let token = authorization_token(request.headers());
    let incident_id = path_segment(&request, 3).to_owned();
    Box::pin(async move {
        match app.get_incident_sequence(&token, &incident_id).await {
            Ok(view) => json_value(StatusCode::OK, &view),
            Err(failure) => failure_response(failure),
        }
    })
}

// POST /api/v1/incidents/:incidentId/reviews
fn create_review(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let token = authorization_token(request.headers());
    let incident_id = path_segment(&request, 3).to_owned();
    Box::pin(async move {
        let body: CreateReviewRequest = match json_body(request.into_body()).await {
            Ok(b) => b,
            Err(r) => return r,
        };
        match app
            .create_review(
                &token,
                &incident_id,
                CreateReviewCommand {
                    status: body.status,
                    detection_verdict: body.detection_verdict,
                    review_note: body.review_note,
                    resolved_at: body.resolved_at,
                },
            )
            .await
        {
            Ok(incident) => json_value(
                StatusCode::OK,
                &serde_json::json!({
                    "incident": incident,
                }),
            ),
            Err(failure) => failure_response(failure),
        }
    })
}

/// Un `""` en un campo clinico es un dato sin autor que despues nadie puede
/// auditar. Si la fuente no lo declara, queda nombrado.
fn unknown_source() -> String {
    "unknown".to_owned()
}

fn unknown_injury_status() -> String {
    "unknown".to_owned()
}

#[derive(serde::Deserialize)]
struct IngestRequest {
    source_record_id: String,
    resident_id: String,
    #[serde(default)]
    bed_id: Option<String>,
    #[serde(default)]
    source_alert_id: Option<String>,
    kind: String,
    severity: String,
    occurred_at: String,
    #[serde(default)]
    location: Option<String>,
    #[serde(default)]
    activity: Option<String>,
    #[serde(default = "unknown_injury_status")]
    injury_status: String,
    #[serde(default)]
    self_recovery: Option<bool>,
    #[serde(default)]
    response_seconds: Option<i32>,
    #[serde(default)]
    narrative: Option<String>,
    #[serde(default)]
    interventions_json: Option<String>,
    /// El cliente manda el array; `interventions_json` es la forma
    /// persistida. Se acepta cualquiera de las dos y gana la del cliente.
    #[serde(default)]
    interventions: Option<Vec<String>>,
    #[serde(default = "unknown_source")]
    source: String,
    #[serde(default)]
    model_version: String,
    #[serde(default)]
    confidence: Option<f64>,
    #[serde(default)]
    provenance_json: Option<String>,
}

#[derive(serde::Deserialize)]
struct CreateReviewRequest {
    status: String,
    #[serde(default)]
    detection_verdict: Option<String>,
    #[serde(default)]
    review_note: Option<String>,
    #[serde(default)]
    resolved_at: Option<String>,
}

// PATCH /api/v1/incidents/:incidentId
//
// Mismo caso de uso que `POST /reviews`: una revision es append-only y el
// PATCH no muta la deteccion, agrega una revision.
fn review_incident(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    create_review(app, request)
}
