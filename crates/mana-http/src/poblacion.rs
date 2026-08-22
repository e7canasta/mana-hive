use std::{collections::HashMap, sync::Arc};

use axum::{
    body::Body,
    http::{Request, StatusCode},
    response::Response,
};
use mana_app::{
    AppState, AssignBedCommand, CreateResidentCommand, DischargeCommand, UpdateResidentCommand,
};
use mana_wire::{
    AssignBedRequest, AssignmentResponse, AssignmentsResponse, BedAssignmentRecord,
    CreateResidentRequest, DischargeRequest, ResidentListItem, ResidentRecord, ResidentResponse,
    ResidentsResponse, RoomRef, UpdateResidentRequest,
};

use crate::{
    path_segment,
    response::{failure_response, json_body, json_value},
    rust_handler, RustHandler,
};

pub fn poblacion_handlers(app: Arc<AppState>) -> HashMap<String, RustHandler> {
    let mut handlers = HashMap::new();
    register(
        &mut handlers,
        "residents.list.get",
        app.clone(),
        residents_list,
    );
    register(
        &mut handlers,
        "residents.detail.get",
        app.clone(),
        resident_detail,
    );
    register(
        &mut handlers,
        "residents.create.post",
        app.clone(),
        resident_create,
    );
    register(
        &mut handlers,
        "residents.update.patch",
        app.clone(),
        resident_update,
    );
    register(
        &mut handlers,
        "residents.discharge.post",
        app.clone(),
        resident_discharge,
    );
    register(
        &mut handlers,
        "residents.assignments.get",
        app.clone(),
        assignments_list,
    );
    register(
        &mut handlers,
        "residents.assignments.create.post",
        app.clone(),
        assignment_create,
    );
    register(
        &mut handlers,
        "beds.assignment.delete",
        app,
        assignment_release,
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

fn residents_list(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let token = authorization_token(request.headers());
    let query = query_param(&request, "q");
    Box::pin(async move {
        match app.list_residents(&token, query).await {
            Ok(residents) => json_value(
                StatusCode::OK,
                &ResidentsResponse {
                    residents: residents.into_iter().map(wire_list_item).collect(),
                },
            ),
            Err(failure) => failure_response(failure),
        }
    })
}

fn resident_detail(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let token = authorization_token(request.headers());
    let resident_id = path_segment(&request, 3).to_owned();
    Box::pin(async move {
        match app.resident_detail(&token, &resident_id).await {
            Ok(resident) => json_value(
                StatusCode::OK,
                &ResidentResponse {
                    resident: wire_record(resident),
                },
            ),
            Err(failure) => failure_response(failure),
        }
    })
}

fn resident_create(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let token = authorization_token(request.headers());
    Box::pin(async move {
        let body = match json_body::<CreateResidentRequest>(request.into_body()).await {
            Ok(body) => body,
            Err(response) => return response,
        };
        let command = match create_resident_command(body) {
            Ok(command) => command,
            Err(failure) => return failure_response(failure),
        };
        match app.create_resident(&token, command).await {
            Ok(resident) => json_value(
                StatusCode::CREATED,
                &ResidentResponse {
                    resident: wire_record(resident),
                },
            ),
            Err(failure) => failure_response(failure),
        }
    })
}

fn resident_update(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let token = authorization_token(request.headers());
    let resident_id = path_segment(&request, 3).to_owned();
    Box::pin(async move {
        let body = match json_body::<UpdateResidentRequest>(request.into_body()).await {
            Ok(body) => body,
            Err(response) => return response,
        };
        match app
            .update_resident(
                &token,
                &resident_id,
                UpdateResidentCommand {
                    full_name: body.full_name,
                    external_id: body.external_id,
                    birth_date: body.birth_date,
                    admission_date: body.admission_date,
                },
            )
            .await
        {
            Ok(resident) => json_value(
                StatusCode::OK,
                &ResidentResponse {
                    resident: wire_record(resident),
                },
            ),
            Err(failure) => failure_response(failure),
        }
    })
}

fn resident_discharge(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let token = authorization_token(request.headers());
    let resident_id = path_segment(&request, 3).to_owned();
    Box::pin(async move {
        let body = match json_body::<DischargeRequest>(request.into_body()).await {
            Ok(body) => body,
            Err(response) => return response,
        };
        match app
            .discharge_resident(
                &token,
                &resident_id,
                DischargeCommand {
                    discharged_at: body.discharged_at,
                },
            )
            .await
        {
            Ok(result) => json_value(
                StatusCode::OK,
                &serde_json::json!({
                    "resident": wire_record(result.resident),
                    "assignments_closed": result.assignments_closed,
                }),
            ),
            Err(failure) => failure_response(failure),
        }
    })
}

fn assignments_list(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let token = authorization_token(request.headers());
    let resident_id = path_segment(&request, 3).to_owned();
    Box::pin(async move {
        match app.list_assignments(&token, &resident_id).await {
            Ok(assignments) => json_value(
                StatusCode::OK,
                &AssignmentsResponse {
                    assignments: assignments.into_iter().map(wire_assignment).collect(),
                },
            ),
            Err(failure) => failure_response(failure),
        }
    })
}

fn assignment_create(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let token = authorization_token(request.headers());
    let resident_id = path_segment(&request, 3).to_owned();
    Box::pin(async move {
        let body = match json_body::<AssignBedRequest>(request.into_body()).await {
            Ok(body) => body,
            Err(response) => return response,
        };
        let Some(bed_id) = body.bed_id else {
            return failure_response(missing_field("bed_id"));
        };
        match app
            .assign_bed(
                &token,
                &resident_id,
                AssignBedCommand {
                    bed_id,
                    starts_at: body.starts_at,
                },
            )
            .await
        {
            Ok(assignment) => json_value(
                StatusCode::CREATED,
                &AssignmentResponse {
                    assignment: wire_assignment(assignment),
                },
            ),
            Err(failure) => failure_response(failure),
        }
    })
}

fn assignment_release(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let token = authorization_token(request.headers());
    let bed_id = path_segment(&request, 3).to_owned();
    Box::pin(async move {
        match app.release_bed(&token, &bed_id).await {
            Ok(assignment) => json_value(
                StatusCode::OK,
                &AssignmentResponse {
                    assignment: wire_assignment(assignment),
                },
            ),
            Err(failure) => failure_response(failure),
        }
    })
}

fn create_resident_command(
    request: CreateResidentRequest,
) -> Result<CreateResidentCommand, mana_app::AppFailure> {
    let Some(full_name) = request.full_name else {
        return Err(missing_field("full_name"));
    };
    Ok(CreateResidentCommand {
        full_name,
        external_id: request.external_id,
        birth_date: request.birth_date,
        admission_date: request.admission_date,
    })
}

fn missing_field(field: &'static str) -> mana_app::AppFailure {
    mana_app::AppFailure::validation("Faltan campos obligatorios", Some(field))
}

fn query_param(request: &Request<Body>, name: &str) -> Option<String> {
    request
        .uri()
        .query()
        .and_then(|query| {
            url::form_urlencoded::parse(query.as_bytes())
                .find(|(key, _)| key == name)
                .map(|(_, value)| value.into_owned())
        })
        .filter(|value| !value.trim().is_empty())
}

fn authorization_token(headers: &axum::http::HeaderMap) -> String {
    headers
        .get(axum::http::header::AUTHORIZATION)
        .and_then(|value| value.to_str().ok())
        .and_then(|value| value.strip_prefix("Bearer "))
        .unwrap_or_default()
        .to_owned()
}

fn wire_record(resident: mana_app::ResidentRecordView) -> ResidentRecord {
    ResidentRecord {
        id: resident.id,
        external_id: resident.external_id,
        full_name: resident.full_name,
        birth_date: resident.birth_date,
        admission_date: resident.admission_date,
        status: resident.status,
        discharged_at: resident.discharged_at,
        discharged_by: resident.discharged_by,
        created_at: resident.created_at,
        updated_at: resident.updated_at,
    }
}

fn wire_list_item(resident: mana_app::ResidentListItemView) -> ResidentListItem {
    ResidentListItem {
        id: resident.id,
        external_id: resident.external_id,
        full_name: resident.full_name,
        birth_date: resident.birth_date,
        admission_date: resident.admission_date,
        status: resident.status,
        discharged_at: resident.discharged_at,
        room: resident.room.map(|room| RoomRef {
            id: room.id,
            number: room.number,
            wing_id: room.wing_id,
            wing_name: room.wing_name,
        }),
        bed_id: resident.bed_id,
    }
}

fn wire_assignment(assignment: mana_app::BedAssignmentView) -> BedAssignmentRecord {
    BedAssignmentRecord {
        id: assignment.id,
        resident_id: assignment.resident_id,
        bed_id: assignment.bed_id,
        starts_at: assignment.starts_at,
        ends_at: assignment.ends_at,
        created_at: assignment.created_at,
        created_by: assignment.created_by,
    }
}
