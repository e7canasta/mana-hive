use std::{collections::HashMap, sync::Arc};

use axum::{
    body::Body,
    http::{Request, StatusCode},
    response::Response,
};
use mana_app::{AppState, CreateNoteCommand, CreateRoundCommand, UpdateTaskCommand};

use crate::{
    path_segment,
    response::{failure_response, json_body, json_value},
    rust_handler, RustHandler,
};

pub fn cuidado_handlers(app: Arc<AppState>) -> HashMap<String, RustHandler> {
    let mut handlers = HashMap::new();
    register(
        &mut handlers,
        "rounds.current.get",
        app.clone(),
        round_current,
    );
    register(&mut handlers, "rounds.list.get", app.clone(), rounds_list);
    register(
        &mut handlers,
        "rounds.create.post",
        app.clone(),
        round_create,
    );
    register(
        &mut handlers,
        "rounds.detail.get",
        app.clone(),
        round_detail,
    );
    register(
        &mut handlers,
        "rounds.update.patch",
        app.clone(),
        round_update,
    );
    register(
        &mut handlers,
        "round-tasks.update.patch",
        app.clone(),
        task_update,
    );
    register(
        &mut handlers,
        "residents.care.get",
        app.clone(),
        care_summary,
    );
    register(
        &mut handlers,
        "residents.notes.get",
        app.clone(),
        notes_list,
    );
    register(
        &mut handlers,
        "residents.notes.create.post",
        app,
        note_create,
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

// GET /api/v1/rounds/current?wing_id={wingId}
fn round_current(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let token = authorization_token(request.headers());
    let wing_id = query_param(&request, "wing_id").unwrap_or_default();
    Box::pin(async move {
        match app.get_current_round(&token, &wing_id).await {
            Ok(round) => json_value(
                StatusCode::OK,
                &serde_json::json!({
                    "round": round,
                }),
            ),
            Err(failure) => failure_response(failure),
        }
    })
}

// GET /api/v1/rounds?wing_id={wingId}&limit={n}
fn rounds_list(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let token = authorization_token(request.headers());
    let wing_id = query_param(&request, "wing_id").unwrap_or_default();
    let limit = query_param(&request, "limit")
        .and_then(|v| v.parse::<i64>().ok())
        .unwrap_or(20);
    Box::pin(async move {
        match app.list_rounds(&token, &wing_id, limit).await {
            Ok(rounds) => json_value(
                StatusCode::OK,
                &serde_json::json!({
                    "rounds": rounds,
                }),
            ),
            Err(failure) => failure_response(failure),
        }
    })
}

// POST /api/v1/rounds
fn round_create(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let token = authorization_token(request.headers());
    Box::pin(async move {
        let body: CreateRoundRequest = match json_body(request.into_body()).await {
            Ok(b) => b,
            Err(r) => return r,
        };
        match app
            .create_round(
                &token,
                CreateRoundCommand {
                    wing_id: body.wing_id,
                },
            )
            .await
        {
            Ok(round) => json_value(
                StatusCode::CREATED,
                &serde_json::json!({
                    "round": round,
                }),
            ),
            Err(failure) => failure_response(failure),
        }
    })
}

// GET /api/v1/rounds/:roundId
fn round_detail(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let token = authorization_token(request.headers());
    let round_id = path_segment(&request, 3).to_owned();
    Box::pin(async move {
        match app.get_round(&token, &round_id).await {
            Ok((round, tasks)) => json_value(
                StatusCode::OK,
                &serde_json::json!({
                    "round": round,
                    "tasks": tasks,
                }),
            ),
            Err(failure) => failure_response(failure),
        }
    })
}

// PATCH /api/v1/rounds/:roundId
fn round_update(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let token = authorization_token(request.headers());
    let round_id = path_segment(&request, 3).to_owned();
    Box::pin(async move {
        let body: UpdateRoundRequest = match json_body(request.into_body()).await {
            Ok(b) => b,
            Err(r) => return r,
        };
        match body.status.as_deref() {
            Some("completed") => match app.complete_round(&token, &round_id).await {
                Ok(round) => json_value(
                    StatusCode::OK,
                    &serde_json::json!({
                        "round": round,
                    }),
                ),
                Err(failure) => failure_response(failure),
            },
            Some("cancelled") => match app.cancel_round(&token, &round_id).await {
                Ok(round) => json_value(
                    StatusCode::OK,
                    &serde_json::json!({
                        "round": round,
                    }),
                ),
                Err(failure) => failure_response(failure),
            },
            _ => failure_response(mana_app::AppFailure::validation(
                "status must be 'completed' or 'cancelled'",
                Some("status"),
            )),
        }
    })
}

// PATCH /api/v1/round-tasks/:taskId
fn task_update(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let token = authorization_token(request.headers());
    let task_id = path_segment(&request, 3).to_owned();
    Box::pin(async move {
        let body: UpdateTaskRequest = match json_body(request.into_body()).await {
            Ok(b) => b,
            Err(r) => return r,
        };
        match app
            .update_task(
                &token,
                &task_id,
                UpdateTaskCommand {
                    status: body.status,
                    note: body.note,
                },
            )
            .await
        {
            Ok(task) => json_value(
                StatusCode::OK,
                &serde_json::json!({
                    "task": task,
                }),
            ),
            Err(failure) => failure_response(failure),
        }
    })
}

// GET /api/v1/residents/:residentId/care?days={n}
fn care_summary(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let token = authorization_token(request.headers());
    let resident_id = path_segment(&request, 3).to_owned();
    let days = query_param(&request, "days")
        .and_then(|v| v.parse::<i64>().ok())
        .unwrap_or(7)
        .clamp(1, 365);
    Box::pin(async move {
        match app.care_activity(&token, &resident_id, days).await {
            Ok(view) => json_value(StatusCode::OK, &view),
            Err(failure) => failure_response(failure),
        }
    })
}

// GET /api/v1/residents/:residentId/notes?limit={n}
fn notes_list(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let token = authorization_token(request.headers());
    let resident_id = path_segment(&request, 3).to_owned();
    let limit = query_param(&request, "limit")
        .and_then(|v| v.parse::<i64>().ok())
        .unwrap_or(20);
    Box::pin(async move {
        match app.list_notes(&token, &resident_id, limit).await {
            Ok(notes) => json_value(
                StatusCode::OK,
                &serde_json::json!({
                    "notes": notes,
                }),
            ),
            Err(failure) => failure_response(failure),
        }
    })
}

// POST /api/v1/residents/:residentId/notes
fn note_create(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let token = authorization_token(request.headers());
    let resident_id = path_segment(&request, 3).to_owned();
    Box::pin(async move {
        let body: CreateNoteRequest = match json_body(request.into_body()).await {
            Ok(b) => b,
            Err(r) => return r,
        };
        match app
            .create_note(
                &token,
                &resident_id,
                CreateNoteCommand {
                    body: body.body,
                    kind: body.kind,
                    duration_min: body.duration_min,
                },
            )
            .await
        {
            Ok(note) => json_value(
                StatusCode::CREATED,
                &serde_json::json!({
                    "note": note,
                }),
            ),
            Err(failure) => failure_response(failure),
        }
    })
}

// Request types
#[derive(serde::Deserialize)]
struct CreateRoundRequest {
    wing_id: String,
}

#[derive(serde::Deserialize)]
struct UpdateRoundRequest {
    status: Option<String>,
}

#[derive(serde::Deserialize)]
struct UpdateTaskRequest {
    status: Option<String>,
    note: Option<Option<String>>,
}

#[derive(serde::Deserialize)]
struct CreateNoteRequest {
    body: String,
    kind: Option<String>,
    duration_min: Option<i32>,
}

// Helpers

fn query_param(request: &Request<Body>, name: &str) -> Option<String> {
    request.uri().query().and_then(|q| {
        url::form_urlencoded::parse(q.as_bytes())
            .find(|(k, _)| k == name)
            .map(|(_, v)| v.into_owned())
    })
}

fn authorization_token(headers: &axum::http::HeaderMap) -> String {
    headers
        .get("authorization")
        .and_then(|v| v.to_str().ok())
        .and_then(|v| v.strip_prefix("Bearer "))
        .unwrap_or("")
        .to_owned()
}
