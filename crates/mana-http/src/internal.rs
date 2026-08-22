use std::{collections::HashMap, sync::Arc};

use axum::{
    body::Body,
    http::{Request, StatusCode},
    response::Response,
};
use mana_app::{AppFailure, AppState};
use mana_kernel::Fallo;

use crate::{
    response::{failure_response, json_body, json_value},
    rust_handler, RustHandler,
};

pub fn internal_handlers(app: Arc<AppState>) -> HashMap<String, RustHandler> {
    let mut handlers = HashMap::new();
    register(
        &mut handlers,
        "internal.evidence.post",
        app.clone(),
        create_evidence,
    );
    register(
        &mut handlers,
        "internal.evidence.get",
        app.clone(),
        get_evidence,
    );
    register(
        &mut handlers,
        "internal.evidence.list",
        app.clone(),
        list_evidence,
    );
    register(
        &mut handlers,
        "internal.timelines.post",
        app.clone(),
        create_timeline,
    );
    register(
        &mut handlers,
        "internal.timelines.get",
        app.clone(),
        get_timeline,
    );
    register(
        &mut handlers,
        "internal.timelines.close",
        app.clone(),
        close_timeline,
    );
    register(
        &mut handlers,
        "internal.clip-windows.post",
        app.clone(),
        create_clip_window,
    );
    register(
        &mut handlers,
        "internal.clip-windows.get",
        app.clone(),
        get_clip_window,
    );
    register(
        &mut handlers,
        "internal.clip-windows.close",
        app.clone(),
        close_clip_window,
    );
    register(
        &mut handlers,
        "internal.clip-windows.list-open",
        app.clone(),
        list_open_clip_windows,
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

fn app_failure(fallo: Fallo, message: impl Into<String>) -> AppFailure {
    AppFailure::new(fallo, message)
}

// ============================================================
// EVIDENCE
// ============================================================

fn create_evidence(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    Box::pin(async move {
        let body: serde_json::Value = match json_body(request.into_body()).await {
            Ok(b) => b,
            Err(r) => return r,
        };

        let input: ctx_evidence::EvidenceInput = match serde_json::from_value(body) {
            Ok(input) => input,
            Err(e) => {
                return failure_response(app_failure(Fallo::InvalidJson, e.to_string()))
            }
        };

        match app.evidence_store.create_evidence(input) {
            Ok(evidence) => json_value(StatusCode::CREATED, &evidence),
            Err(e) => failure_response(app_failure(Fallo::InternalError, e.to_string())),
        }
    })
}

fn get_evidence(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    Box::pin(async move {
        let evidence_id = path_segment(&request, 4);

        match app.evidence_store.get_evidence(evidence_id) {
            Ok(evidence) => json_value(StatusCode::OK, &evidence),
            Err(e) => failure_response(app_failure(Fallo::NotFound, e.to_string())),
        }
    })
}

fn list_evidence(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    Box::pin(async move {
        let query = request.uri().query().unwrap_or("");
        let params: HashMap<String, String> = url::form_urlencoded::parse(query.as_bytes())
            .into_owned()
            .collect();

        let filter = ctx_evidence::EvidenceFilter {
            bed_id: params.get("bed_id").cloned(),
            resident_id: params.get("resident_id").cloned(),
            since: params.get("since").cloned(),
            until: params.get("until").cloned(),
            limit: params.get("limit").and_then(|l| l.parse().ok()),
            ..Default::default()
        };

        match app.evidence_store.list_evidence(filter) {
            Ok(evidence) => json_value(StatusCode::OK, &serde_json::json!({ "evidence": evidence })),
            Err(e) => failure_response(app_failure(Fallo::InternalError, e.to_string())),
        }
    })
}

// ============================================================
// TIMELINES
// ============================================================

fn create_timeline(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    Box::pin(async move {
        let body: serde_json::Value = match json_body(request.into_body()).await {
            Ok(b) => b,
            Err(r) => return r,
        };

        let input: ctx_evidence::TimelineInput = match serde_json::from_value(body) {
            Ok(input) => input,
            Err(e) => {
                return failure_response(app_failure(Fallo::InvalidJson, e.to_string()))
            }
        };

        match app.evidence_store.create_timeline(input) {
            Ok(timeline) => json_value(StatusCode::CREATED, &timeline),
            Err(e) => failure_response(app_failure(Fallo::InternalError, e.to_string())),
        }
    })
}

fn get_timeline(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    Box::pin(async move {
        let timeline_id = path_segment(&request, 4);

        match app.evidence_store.get_timeline(timeline_id) {
            Ok(timeline) => json_value(StatusCode::OK, &timeline),
            Err(e) => failure_response(app_failure(Fallo::NotFound, e.to_string())),
        }
    })
}

fn close_timeline(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    Box::pin(async move {
        let timeline_id = path_segment(&request, 4).to_string();
        let body: serde_json::Value = match json_body(request.into_body()).await {
            Ok(b) => b,
            Err(r) => return r,
        };

        let input: ctx_evidence::TimelineCloseInput = match serde_json::from_value(body) {
            Ok(input) => input,
            Err(e) => {
                return failure_response(app_failure(Fallo::InvalidJson, e.to_string()))
            }
        };

        match app.evidence_store.close_timeline(&timeline_id, input) {
            Ok(timeline) => json_value(StatusCode::OK, &timeline),
            Err(e) => failure_response(app_failure(Fallo::InternalError, e.to_string())),
        }
    })
}

// ============================================================
// CLIP WINDOWS
// ============================================================

fn create_clip_window(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    Box::pin(async move {
        let body: serde_json::Value = match json_body(request.into_body()).await {
            Ok(b) => b,
            Err(r) => return r,
        };

        let input: ctx_evidence::ClipWindowInput = match serde_json::from_value(body) {
            Ok(input) => input,
            Err(e) => {
                return failure_response(app_failure(Fallo::InvalidJson, e.to_string()))
            }
        };

        match app.evidence_store.create_clip_window(input) {
            Ok(window) => json_value(StatusCode::CREATED, &window),
            Err(e) => failure_response(app_failure(Fallo::InternalError, e.to_string())),
        }
    })
}

fn get_clip_window(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    Box::pin(async move {
        let window_id = path_segment(&request, 4);

        match app.evidence_store.get_clip_window(window_id) {
            Ok(window) => json_value(StatusCode::OK, &window),
            Err(e) => failure_response(app_failure(Fallo::NotFound, e.to_string())),
        }
    })
}

fn close_clip_window(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    Box::pin(async move {
        let window_id = path_segment(&request, 4).to_string();
        let body: serde_json::Value = match json_body(request.into_body()).await {
            Ok(b) => b,
            Err(r) => return r,
        };

        let input: ctx_evidence::ClipWindowCloseInput = match serde_json::from_value(body) {
            Ok(input) => input,
            Err(e) => {
                return failure_response(app_failure(Fallo::InvalidJson, e.to_string()))
            }
        };

        match app.evidence_store.close_clip_window(&window_id, input) {
            Ok(window) => json_value(StatusCode::OK, &window),
            Err(e) => failure_response(app_failure(Fallo::InternalError, e.to_string())),
        }
    })
}

fn list_open_clip_windows(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    Box::pin(async move {
        let bed_id = path_segment(&request, 4);

        match app.evidence_store.list_open_clip_windows(bed_id) {
            Ok(windows) => json_value(StatusCode::OK, &serde_json::json!({ "windows": windows })),
            Err(e) => failure_response(app_failure(Fallo::InternalError, e.to_string())),
        }
    })
}

// ============================================================
// HELPERS
// ============================================================

fn path_segment(request: &Request<Body>, index: usize) -> &str {
    request
        .uri()
        .path()
        .trim_matches('/')
        .split('/')
        .nth(index)
        .unwrap_or("")
}
