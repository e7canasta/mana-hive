use std::{collections::HashMap, sync::Arc};

use axum::{
    body::Body,
    http::{Request, StatusCode},
    response::Response,
};
use mana_app::{
    AppState, IngestBathroomCommand, IngestEventCommand, IngestMobilityCommand, IngestSleepCommand,
};

use crate::{
    path_segment,
    response::{failure_response, json_body, json_value},
    rust_handler, RustHandler,
};

pub fn observation_handlers(app: Arc<AppState>) -> HashMap<String, RustHandler> {
    let mut handlers = HashMap::new();
    register(
        &mut handlers,
        "events.internal.post",
        app.clone(),
        ingest_event,
    );
    register(
        &mut handlers,
        "clinical.sleep-summaries.ingest.post",
        app.clone(),
        ingest_sleep,
    );
    register(
        &mut handlers,
        "clinical.mobility-summaries.ingest.post",
        app.clone(),
        ingest_mobility,
    );
    register(
        &mut handlers,
        "clinical.bathroom-summaries.ingest.post",
        app.clone(),
        ingest_bathroom,
    );
    register(&mut handlers, "wings.board.get", app.clone(), board);
    register(
        &mut handlers,
        "companion.rooms.get",
        app.clone(),
        companion_rooms,
    );
    register(&mut handlers, "rooms.peek.post", app.clone(), peek);
    register(&mut handlers, "residents.sleep.get", app.clone(), sleep);
    register(
        &mut handlers,
        "residents.mobility.get",
        app.clone(),
        mobility,
    );
    register(
        &mut handlers,
        "residents.bathroom.get",
        app.clone(),
        bathroom,
    );
    register(
        &mut handlers,
        "residents.current-state.get",
        app.clone(),
        current_state,
    );
    register(
        &mut handlers,
        "residents.timeline.get",
        app.clone(),
        timeline,
    );
    register(&mut handlers, "residents.events.get", app.clone(), events);
    register(&mut handlers, "reports.summary.get", app, reports_summary);
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

fn ingest_secret(headers: &axum::http::HeaderMap) -> String {
    headers
        .get("x-clinical-secret")
        .and_then(|v| v.to_str().ok())
        .unwrap_or("")
        .to_owned()
}

/// Una fuente que no se declara queda nombrada, no vacia. Un `""` en
/// `provenance` es un dato clinico sin autor que despues nadie puede auditar.
fn unknown_source() -> String {
    "unknown".to_owned()
}

/// El rango de dias que cubre una lista de resumenes. Vacia, es hoy.
fn summary_period<T: serde::Serialize>(items: &[T]) -> (String, String) {
    let days: Vec<String> = items
        .iter()
        .filter_map(|item| {
            serde_json::to_value(item)
                .ok()
                .and_then(|value| value.get("observed_on")?.as_str().map(str::to_owned))
        })
        .collect();
    let today = mana_kernel::Instante::now().to_string()[..10].to_owned();
    match (days.iter().min(), days.iter().max()) {
        (Some(from), Some(to)) => (from.clone(), to.clone()),
        _ => (today.clone(), today),
    }
}

fn limit_param(request: &Request<Body>) -> Option<i64> {
    request.uri().query().and_then(|query| {
        query.split('&').find_map(|pair| {
            pair.strip_prefix("limit=")
                .and_then(|value| value.parse().ok())
        })
    })
}

// ------------------------------------------------------------------ bodies

#[derive(serde::Deserialize)]
struct EventRequest {
    source_event_id: String,
    monitor_key: String,
    kind: String,
    #[serde(default)]
    room_state: Option<String>,
    #[serde(default)]
    substate: Option<String>,
    #[serde(default)]
    zone: Option<String>,
    #[serde(default)]
    state: Option<String>,
    #[serde(default)]
    sleeping: Option<bool>,
    occurred_at: String,
    #[serde(default)]
    payload_json: Option<String>,
}

#[derive(serde::Deserialize)]
struct SleepRequest {
    source_record_id: String,
    resident_id: String,
    observed_on: String,
    // Los contadores ausentes son cero: un resumen los calcula todos, y que
    // falte uno significa que no hubo, no que se desconoce.
    #[serde(default)]
    calm_minutes: i32,
    #[serde(default)]
    restless_minutes: i32,
    #[serde(default)]
    awake_minutes: i32,
    #[serde(default)]
    out_of_bed_minutes: i32,
    #[serde(default)]
    bed_exit_count: i32,
    #[serde(default)]
    wake_count: i32,
    #[serde(default = "unknown_source")]
    source: String,
    model_version: String,
    #[serde(default)]
    confidence: Option<f64>,
    #[serde(default)]
    provenance_json: Option<String>,
}

#[derive(serde::Deserialize)]
struct MobilityRequest {
    source_record_id: String,
    resident_id: String,
    observed_on: String,
    #[serde(default)]
    in_bed_minutes: i32,
    #[serde(default)]
    out_of_bed_minutes: i32,
    #[serde(default)]
    out_of_sight_minutes: i32,
    #[serde(default)]
    walking_minutes: i32,
    #[serde(default)]
    distance_meters: Option<f64>,
    #[serde(default)]
    transfer_count: i32,
    #[serde(default = "unknown_source")]
    source: String,
    model_version: String,
    #[serde(default)]
    confidence: Option<f64>,
    #[serde(default)]
    provenance_json: Option<String>,
}

#[derive(serde::Deserialize)]
struct BathroomRequest {
    source_record_id: String,
    resident_id: String,
    observed_on: String,
    #[serde(default)]
    visit_count: i32,
    #[serde(default)]
    night_visit_count: i32,
    #[serde(default)]
    assisted_count: i32,
    #[serde(default)]
    total_minutes: i32,
    #[serde(default)]
    longest_visit_minutes: i32,
    #[serde(default = "unknown_source")]
    source: String,
    model_version: String,
    #[serde(default)]
    confidence: Option<f64>,
    #[serde(default)]
    provenance_json: Option<String>,
}

// ---------------------------------------------------------------- handlers

// POST /internal/v1/events
fn ingest_event(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let secret = ingest_secret(request.headers());
    Box::pin(async move {
        let body: EventRequest = match json_body(request.into_body()).await {
            Ok(b) => b,
            Err(r) => return r,
        };
        match app
            .ingest_event(
                &secret,
                IngestEventCommand {
                    source_event_id: body.source_event_id,
                    monitor_key: body.monitor_key,
                    kind: body.kind,
                    room_state: body.room_state,
                    substate: body.substate,
                    zone: body.zone,
                    state: body.state,
                    sleeping: body.sleeping,
                    occurred_at: body.occurred_at,
                    payload_json: body.payload_json,
                },
            )
            .await
        {
            // Un duplicado es 200; un evento nuevo es 201. El bridge distingue
            // "ya lo tenias" de "lo acabo de guardar" sin leer el cuerpo.
            Ok(result) => {
                let status = if result.duplicate {
                    StatusCode::OK
                } else {
                    StatusCode::CREATED
                };
                json_value(status, &result)
            }
            Err(failure) => failure_response(failure),
        }
    })
}

macro_rules! summary_ingest {
    ($name:ident, $body:ty, $call:ident, $command:expr) => {
        fn $name(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
            let secret = ingest_secret(request.headers());
            Box::pin(async move {
                let body: $body = match json_body(request.into_body()).await {
                    Ok(b) => b,
                    Err(r) => return r,
                };
                #[allow(clippy::redundant_closure_call)]
                match app.$call(&secret, ($command)(body)).await {
                    Ok(result) => {
                        let status = if result.replaced {
                            StatusCode::OK
                        } else {
                            StatusCode::CREATED
                        };
                        json_value(status, &result)
                    }
                    Err(failure) => failure_response(failure),
                }
            })
        }
    };
}

summary_ingest!(
    ingest_sleep,
    SleepRequest,
    ingest_sleep_summary,
    |body: SleepRequest| IngestSleepCommand {
        source_record_id: body.source_record_id,
        resident_id: body.resident_id,
        observed_on: body.observed_on,
        calm_minutes: body.calm_minutes,
        restless_minutes: body.restless_minutes,
        awake_minutes: body.awake_minutes,
        out_of_bed_minutes: body.out_of_bed_minutes,
        bed_exit_count: body.bed_exit_count,
        wake_count: body.wake_count,
        source: body.source,
        model_version: body.model_version,
        confidence: body.confidence,
        provenance_json: body.provenance_json,
    }
);

summary_ingest!(
    ingest_mobility,
    MobilityRequest,
    ingest_mobility_summary,
    |body: MobilityRequest| IngestMobilityCommand {
        source_record_id: body.source_record_id,
        resident_id: body.resident_id,
        observed_on: body.observed_on,
        in_bed_minutes: body.in_bed_minutes,
        out_of_bed_minutes: body.out_of_bed_minutes,
        out_of_sight_minutes: body.out_of_sight_minutes,
        walking_minutes: body.walking_minutes,
        distance_meters: body.distance_meters,
        transfer_count: body.transfer_count,
        source: body.source,
        model_version: body.model_version,
        confidence: body.confidence,
        provenance_json: body.provenance_json,
    }
);

summary_ingest!(
    ingest_bathroom,
    BathroomRequest,
    ingest_bathroom_summary,
    |body: BathroomRequest| IngestBathroomCommand {
        source_record_id: body.source_record_id,
        resident_id: body.resident_id,
        observed_on: body.observed_on,
        visit_count: body.visit_count,
        night_visit_count: body.night_visit_count,
        assisted_count: body.assisted_count,
        total_minutes: body.total_minutes,
        longest_visit_minutes: body.longest_visit_minutes,
        source: body.source,
        model_version: body.model_version,
        confidence: body.confidence,
        provenance_json: body.provenance_json,
    }
);

// GET /api/v1/wings/{wingId}/board
fn board(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let token = authorization_token(request.headers());
    let wing_id = path_segment(&request, 3).to_owned();
    Box::pin(async move {
        match app.wing_board(&token, &wing_id).await {
            Ok(view) => json_value(StatusCode::OK, &view),
            Err(failure) => failure_response(failure),
        }
    })
}

// GET /api/v1/companion/rooms
fn companion_rooms(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let token = authorization_token(request.headers());
    Box::pin(async move {
        match app.companion_rooms(&token).await {
            Ok(view) => json_value(StatusCode::OK, &view),
            Err(failure) => failure_response(failure),
        }
    })
}

// POST /api/v1/rooms/{roomId}/peek
fn peek(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let token = authorization_token(request.headers());
    let room_id = path_segment(&request, 3).to_owned();
    Box::pin(async move {
        match app.peek_room(&token, &room_id).await {
            Ok(view) => json_value(StatusCode::OK, &view),
            Err(failure) => failure_response(failure),
        }
    })
}

macro_rules! resident_summary_read {
    ($name:ident, $call:ident, $key:literal) => {
        fn $name(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
            let token = authorization_token(request.headers());
            let resident_id = path_segment(&request, 3).to_owned();
            let limit = limit_param(&request);
            Box::pin(async move {
                match app.$call(&token, &resident_id, limit).await {
                    Ok(items) => {
                        let (from, to) = summary_period(&items);
                        json_value(
                            StatusCode::OK,
                            &serde_json::json!({
                                $key: items,
                                "resident_id": resident_id,
                                // El periodo cubierto, para que el cliente sepa
                                // que ventana esta mirando sin inferirla.
                                "period": { "from": from, "to": to },
                            }),
                        )
                    }
                    Err(failure) => failure_response(failure),
                }
            })
        }
    };
}

resident_summary_read!(sleep, resident_sleep, "summaries");
resident_summary_read!(mobility, resident_mobility, "summaries");
resident_summary_read!(bathroom, resident_bathroom, "summaries");

// GET /api/v1/residents/{residentId}/current-state
fn current_state(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let token = authorization_token(request.headers());
    let resident_id = path_segment(&request, 3).to_owned();
    Box::pin(async move {
        match app.resident_current_state(&token, &resident_id).await {
            Ok(view) => json_value(StatusCode::OK, &view),
            Err(failure) => failure_response(failure),
        }
    })
}

// GET /api/v1/residents/{residentId}/events
fn events(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let token = authorization_token(request.headers());
    let resident_id = path_segment(&request, 3).to_owned();
    Box::pin(async move {
        match app.resident_events(&token, &resident_id).await {
            Ok(view) => json_value(StatusCode::OK, &view),
            Err(failure) => failure_response(failure),
        }
    })
}

// GET /api/v1/residents/{residentId}/timeline
fn timeline(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let token = authorization_token(request.headers());
    let resident_id = path_segment(&request, 3).to_owned();
    let limit = limit_param(&request);
    Box::pin(async move {
        match app.resident_timeline(&token, &resident_id, limit).await {
            Ok(view) => json_value(StatusCode::OK, &view),
            Err(failure) => failure_response(failure),
        }
    })
}

// GET /api/v1/reports/summary
fn reports_summary(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let token = authorization_token(request.headers());
    Box::pin(async move {
        match app.reports_summary(&token).await {
            Ok(view) => json_value(StatusCode::OK, &view),
            Err(failure) => failure_response(failure),
        }
    })
}
