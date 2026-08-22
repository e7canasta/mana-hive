use std::{collections::HashMap, sync::Arc};

use axum::{
    body::Body,
    http::{Request, StatusCode},
    response::Response,
};
use mana_app::{
    AppState, AssignCoverageCommand, CreateGroupCommand, ReplaceGridCommand, ReplaceMembersCommand,
    UpdateGroupCommand,
};

use crate::{
    path_segment,
    response::{failure_response, json_body, json_value},
    rust_handler, RustHandler,
};

pub fn cobertura_handlers(app: Arc<AppState>) -> HashMap<String, RustHandler> {
    let mut handlers = HashMap::new();
    register(
        &mut handlers,
        "facilities.shifts.get",
        app.clone(),
        shifts_get,
    );
    register(
        &mut handlers,
        "facilities.shifts.put",
        app.clone(),
        shifts_put,
    );
    register(
        &mut handlers,
        "staff-groups.list.get",
        app.clone(),
        groups_list,
    );
    register(
        &mut handlers,
        "staff-groups.detail.get",
        app.clone(),
        group_detail,
    );
    register(
        &mut handlers,
        "staff-groups.create.post",
        app.clone(),
        group_create,
    );
    register(
        &mut handlers,
        "staff-groups.update.patch",
        app.clone(),
        group_update,
    );
    register(
        &mut handlers,
        "staff-groups.members.put",
        app.clone(),
        members_replace,
    );
    register(
        &mut handlers,
        "wings.coverage.get",
        app.clone(),
        coverage_get,
    );
    register(&mut handlers, "wings.coverage.put", app, coverage_put);
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

// GET /api/v1/facilities/:facilityId/shifts
fn shifts_get(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let token = authorization_token(request.headers());
    let facility_id = path_segment(&request, 3).to_owned();
    Box::pin(async move {
        match app.get_shift_grid(&token, &facility_id).await {
            Ok(grid) => json_value(
                StatusCode::OK,
                &serde_json::json!({
                    "facility_id": grid.facility_id,
                    "shifts": grid.shifts,
                    "coverages_cleared": grid.coverages_cleared,
                }),
            ),
            Err(failure) => failure_response(failure),
        }
    })
}

// PUT /api/v1/facilities/:facilityId/shifts
fn shifts_put(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let token = authorization_token(request.headers());
    let facility_id = path_segment(&request, 3).to_owned();
    Box::pin(async move {
        let body: ReplaceShiftGridRequest = match json_body(request.into_body()).await {
            Ok(b) => b,
            Err(r) => return r,
        };
        let command = ReplaceGridCommand {
            shifts: body
                .shifts
                .into_iter()
                .map(|s| {
                    let start_minute = s.minute();
                    mana_app::ShiftEntry {
                        key: s.key,
                        label: s.label,
                        start_minute,
                    }
                })
                .collect(),
        };
        match app.replace_shift_grid(&token, &facility_id, command).await {
            Ok(grid) => json_value(
                StatusCode::OK,
                &serde_json::json!({
                    "facility_id": grid.facility_id,
                    "shifts": grid.shifts,
                    "coverages_cleared": grid.coverages_cleared,
                }),
            ),
            Err(failure) => failure_response(failure),
        }
    })
}

// GET /api/v1/staff-groups?facility_id={facilityId}
fn groups_list(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let token = authorization_token(request.headers());
    let facility_id = query_param(&request, "facility_id").unwrap_or_default();
    Box::pin(async move {
        match app.list_staff_groups(&token, &facility_id).await {
            Ok(groups) => json_value(
                StatusCode::OK,
                &serde_json::json!({
                    "staff_groups": groups,
                }),
            ),
            Err(failure) => failure_response(failure),
        }
    })
}

// GET /api/v1/staff-groups/:groupId
fn group_detail(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let token = authorization_token(request.headers());
    let group_id = path_segment(&request, 3).to_owned();
    Box::pin(async move {
        match app.get_staff_group(&token, &group_id).await {
            Ok((group, members)) => json_value(
                StatusCode::OK,
                &serde_json::json!({
                    "staff_group": {
                        "id": group.id,
                        "facility_id": group.facility_id,
                        "name": group.name,
                        "retired_at": group.retired_at,
                        "created_at": group.created_at,
                        "updated_at": group.updated_at,
                        "members": members,
                    }
                }),
            ),
            Err(failure) => failure_response(failure),
        }
    })
}

// POST /api/v1/staff-groups
fn group_create(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let token = authorization_token(request.headers());
    Box::pin(async move {
        let body: CreateGroupRequest = match json_body(request.into_body()).await {
            Ok(b) => b,
            Err(r) => return r,
        };
        match app
            .create_staff_group(
                &token,
                CreateGroupCommand {
                    facility_id: body.facility_id,
                    name: body.name,
                },
            )
            .await
        {
            Ok(group) => json_value(
                StatusCode::CREATED,
                &serde_json::json!({
                    "staff_group": group,
                }),
            ),
            Err(failure) => failure_response(failure),
        }
    })
}

// PATCH /api/v1/staff-groups/:groupId
fn group_update(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let token = authorization_token(request.headers());
    let group_id = path_segment(&request, 3).to_owned();
    Box::pin(async move {
        let body: UpdateGroupRequest = match json_body(request.into_body()).await {
            Ok(b) => b,
            Err(r) => return r,
        };
        match app
            .update_staff_group(&token, &group_id, UpdateGroupCommand { name: body.name })
            .await
        {
            Ok(group) => json_value(
                StatusCode::OK,
                &serde_json::json!({
                    "staff_group": group,
                }),
            ),
            Err(failure) => failure_response(failure),
        }
    })
}

// PUT /api/v1/staff-groups/:groupId/members
fn members_replace(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let token = authorization_token(request.headers());
    let group_id = path_segment(&request, 3).to_owned();
    Box::pin(async move {
        let body: ReplaceMembersRequest = match json_body(request.into_body()).await {
            Ok(b) => b,
            Err(r) => return r,
        };
        let command = ReplaceMembersCommand {
            members: body
                .entries()
                .into_iter()
                .map(|m| mana_app::MemberEntry {
                    user_id: m.user_id,
                    valid_from: m.valid_from,
                })
                .collect(),
        };
        match app.replace_members(&token, &group_id, command).await {
            // Devuelve el grupo entero, igual que el detalle: el cliente pinta
            // la misma vista despues de editar y no tiene que pedirla de nuevo.
            Ok(_) => match app.get_staff_group(&token, &group_id).await {
                Ok((group, members)) => json_value(
                    StatusCode::OK,
                    &serde_json::json!({
                        "staff_group": {
                            "id": group.id,
                            "facility_id": group.facility_id,
                            "name": group.name,
                            "retired_at": group.retired_at,
                            "created_at": group.created_at,
                            "updated_at": group.updated_at,
                            "members": members,
                        }
                    }),
                ),
                Err(failure) => failure_response(failure),
            },
            Err(failure) => failure_response(failure),
        }
    })
}

// GET /api/v1/wings/:wingId/coverage
fn coverage_get(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let token = authorization_token(request.headers());
    let wing_id = path_segment(&request, 3).to_owned();
    let at = query_param(&request, "at");
    Box::pin(async move {
        match app.get_wing_coverage(&token, &wing_id, at.as_deref()).await {
            Ok(view) => json_value(StatusCode::OK, &view),
            Err(failure) => failure_response(failure),
        }
    })
}

// PUT /api/v1/wings/:wingId/coverage
fn coverage_put(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let token = authorization_token(request.headers());
    let wing_id = path_segment(&request, 3).to_owned();
    Box::pin(async move {
        let body: AssignCoverageRequest = match json_body(request.into_body()).await {
            Ok(b) => b,
            Err(r) => return r,
        };
        match app
            .assign_wing_coverage(
                &token,
                &wing_id,
                AssignCoverageCommand {
                    shift_key: body.key(),
                    staff_group_id: body.staff_group_id,
                },
            )
            .await
        {
            // Responde la cobertura del momento, igual que el GET: el panel
            // pinta la misma vista despues de asignar y no tiene que pedirla
            // de nuevo.
            Ok(_) => match app.get_wing_coverage(&token, &wing_id, None).await {
                Ok(view) => json_value(StatusCode::OK, &view),
                Err(failure) => failure_response(failure),
            },
            Err(failure) => failure_response(failure),
        }
    })
}

// Request types
#[derive(serde::Deserialize)]
struct ReplaceShiftGridRequest {
    shifts: Vec<ShiftEntry>,
}

#[derive(serde::Deserialize)]
/// El cliente manda `start_hour`; la precision interna es el minuto. Se acepta
/// cualquiera de los dos y `start_hour` gana si vienen ambos, porque es el que
/// esta en el contrato.
#[allow(dead_code)]
struct ShiftEntry {
    key: String,
    label: String,
    #[serde(default)]
    start_minute: Option<i32>,
    #[serde(default)]
    start_hour: Option<i32>,
}

impl ShiftEntry {
    fn minute(&self) -> i32 {
        self.start_hour
            .map(|hour| hour * 60)
            .or(self.start_minute)
            .unwrap_or(0)
    }
}

#[derive(serde::Deserialize)]
struct CreateGroupRequest {
    facility_id: String,
    name: String,
}

#[derive(serde::Deserialize)]
struct UpdateGroupRequest {
    name: Option<String>,
}

#[derive(serde::Deserialize)]
struct ReplaceMembersRequest {
    /// El cliente manda `user_ids`; `members` es la forma rica con
    /// `valid_from`. Se acepta cualquiera de las dos.
    #[serde(default)]
    members: Vec<MemberEntry>,
    #[serde(default)]
    user_ids: Vec<String>,
}

impl ReplaceMembersRequest {
    fn entries(self) -> Vec<MemberEntry> {
        if !self.members.is_empty() {
            return self.members;
        }
        self.user_ids
            .into_iter()
            .map(|user_id| MemberEntry {
                user_id,
                valid_from: None,
            })
            .collect()
    }
}

#[derive(serde::Deserialize)]
struct MemberEntry {
    user_id: String,
    valid_from: Option<String>,
}

#[derive(serde::Deserialize)]
struct AssignCoverageRequest {
    staff_group_id: Option<String>,
    /// El cliente manda `shift`; `shift_key` es el nombre interno. Se acepta
    /// cualquiera de los dos.
    #[serde(default)]
    shift_key: Option<String>,
    #[serde(default)]
    shift: Option<String>,
}

impl AssignCoverageRequest {
    fn key(&self) -> String {
        self.shift
            .clone()
            .or_else(|| self.shift_key.clone())
            .unwrap_or_default()
    }
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
