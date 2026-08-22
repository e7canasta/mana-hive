use std::{collections::HashMap, sync::Arc};

use axum::{
    body::Body,
    http::{Request, StatusCode},
    response::Response,
};
use mana_app::{
    AppState, BedCommand, CreateFacilityCommand, CreateRoomCommand, CreateWingCommand,
    PlanogramPlacementCommand, PrivacyRegionCommand, SavePlanogramCommand,
    SavePrivacyRegionsCommand, UpdateBedCommand, UpdateFacilityCommand, UpdateRoomCommand,
    UpdateWingCommand,
};
use mana_wire::{
    Bed, BedsResponse, CreateBedRequest, CreateFacilityRequest, CreateRoomRequest,
    CreateWingRequest, FacilitiesResponse, Facility, FacilityDetail, PlanogramPlacement,
    PlanogramResponse, PrivacyRegion, PrivacyRegionsResponse, ResidenceBed, ResidenceBedsResponse,
    Room, RoomResponse, RoomsResponse, SavePlanogramRequest, SavePrivacyRegionsRequest,
    UpdateBedRequest, UpdateFacilityRequest, UpdateRoomRequest, UpdateWingRequest, Wing,
    WingResponse, WingsResponse,
};

use crate::{
    path_segment,
    response::{failure_response, json_body, json_value},
    rust_handler, RustHandler,
};

pub fn residence_handlers(app: Arc<AppState>) -> HashMap<String, RustHandler> {
    let mut handlers = HashMap::new();
    register(
        &mut handlers,
        "facilities.list.get",
        app.clone(),
        facilities_list,
    );
    register(
        &mut handlers,
        "facilities.detail.get",
        app.clone(),
        facility_detail,
    );
    register(
        &mut handlers,
        "facilities.tree.get",
        app.clone(),
        facility_tree,
    );
    register(
        &mut handlers,
        "facilities.create.post",
        app.clone(),
        facility_create,
    );
    register(
        &mut handlers,
        "facilities.update.patch",
        app.clone(),
        facility_update,
    );
    register(&mut handlers, "wings.list.get", app.clone(), wings_list);
    register(
        &mut handlers,
        "facilities.wings.create.post",
        app.clone(),
        wing_create,
    );
    register(
        &mut handlers,
        "wings.update.patch",
        app.clone(),
        wing_update,
    );
    register(&mut handlers, "wings.rooms.get", app.clone(), rooms_list);
    register(
        &mut handlers,
        "wings.rooms.create.post",
        app.clone(),
        room_create,
    );
    register(
        &mut handlers,
        "rooms.update.patch",
        app.clone(),
        room_update,
    );
    register(&mut handlers, "rooms.beds.get", app.clone(), beds_list);
    register(
        &mut handlers,
        "rooms.beds.create.post",
        app.clone(),
        bed_create,
    );
    register(&mut handlers, "beds.update.patch", app.clone(), bed_update);
    register(&mut handlers, "beds.list.get", app.clone(), beds_all);
    register(
        &mut handlers,
        "wings.planogram.get",
        app.clone(),
        planogram_get,
    );
    register(
        &mut handlers,
        "wings.planogram.put",
        app.clone(),
        planogram_put,
    );
    register(
        &mut handlers,
        "rooms.privacy-regions.get",
        app.clone(),
        privacy_regions_get,
    );
    register(
        &mut handlers,
        "rooms.privacy-regions.put",
        app,
        privacy_regions_put,
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

fn facilities_list(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    Box::pin(async move {
        match app
            .list_facilities(&authorization_token(request.headers()))
            .await
        {
            Ok(facilities) => json_value(
                StatusCode::OK,
                &FacilitiesResponse {
                    facilities: facilities.into_iter().map(wire_facility).collect(),
                },
            ),
            Err(failure) => failure_response(failure),
        }
    })
}

fn facility_detail(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let facility_id = path_segment(&request, 3).to_owned();
    let token = authorization_token(request.headers());
    Box::pin(async move {
        match app.facility_detail(&token, &facility_id).await {
            Ok(facility) => json_value(StatusCode::OK, &wire_facility_detail(facility)),
            Err(failure) => failure_response(failure),
        }
    })
}

fn facility_tree(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let facility_id = path_segment(&request, 3).to_owned();
    let token = authorization_token(request.headers());
    Box::pin(async move {
        match app.facility_tree(&token, &facility_id).await {
            Ok(tree) => json_value(StatusCode::OK, &wire_facility_tree(tree)),
            Err(failure) => failure_response(failure),
        }
    })
}

fn facility_create(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let token = authorization_token(request.headers());
    Box::pin(async move {
        let body = match json_body::<CreateFacilityRequest>(request.into_body()).await {
            Ok(body) => body,
            Err(response) => return response,
        };
        let command = match create_facility_command(body) {
            Ok(command) => command,
            Err(failure) => return failure_response(failure),
        };
        match app.create_facility(&token, command).await {
            Ok(facility) => json_value(StatusCode::CREATED, &wire_facility(facility)),
            Err(failure) => failure_response(failure),
        }
    })
}

fn facility_update(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let facility_id = path_segment(&request, 3).to_owned();
    let token = authorization_token(request.headers());
    Box::pin(async move {
        let body = match json_body::<UpdateFacilityRequest>(request.into_body()).await {
            Ok(body) => body,
            Err(response) => return response,
        };
        match app
            .update_facility(
                &token,
                &facility_id,
                UpdateFacilityCommand {
                    name: body.name,
                    timezone: body.timezone,
                },
            )
            .await
        {
            Ok(facility) => json_value(
                StatusCode::OK,
                &mana_wire::FacilityResponse {
                    facility: wire_facility(facility),
                },
            ),
            Err(failure) => failure_response(failure),
        }
    })
}

fn wings_list(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let token = authorization_token(request.headers());
    Box::pin(async move {
        match app.list_wings(&token).await {
            Ok(wings) => json_value(
                StatusCode::OK,
                &WingsResponse {
                    wings: wings.into_iter().map(wire_wing).collect(),
                },
            ),
            Err(failure) => failure_response(failure),
        }
    })
}

fn wing_create(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let token = authorization_token(request.headers());
    let facility_id = path_segment(&request, 3).to_owned();
    Box::pin(async move {
        let body = match json_body::<CreateWingRequest>(request.into_body()).await {
            Ok(body) => body,
            Err(response) => return response,
        };
        let command = match create_wing_command(body) {
            Ok(command) => command,
            Err(failure) => return failure_response(failure),
        };
        match app.create_wing(&token, &facility_id, command).await {
            Ok(wing) => json_value(StatusCode::CREATED, &wire_wing(wing)),
            Err(failure) => failure_response(failure),
        }
    })
}

fn wing_update(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let token = authorization_token(request.headers());
    let wing_id = path_segment(&request, 3).to_owned();
    Box::pin(async move {
        let body = match json_body::<UpdateWingRequest>(request.into_body()).await {
            Ok(body) => body,
            Err(response) => return response,
        };
        match app
            .update_wing(
                &token,
                &wing_id,
                UpdateWingCommand {
                    name: body.name,
                    floor: body.floor,
                    sort_order: body.sort_order,
                },
            )
            .await
        {
            Ok(wing) => json_value(
                StatusCode::OK,
                &WingResponse {
                    wing: wire_wing(wing),
                },
            ),
            Err(failure) => failure_response(failure),
        }
    })
}

fn rooms_list(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let token = authorization_token(request.headers());
    let wing_id = path_segment(&request, 3).to_owned();
    Box::pin(async move {
        match app.list_rooms(&token, &wing_id).await {
            Ok(rooms) => json_value(
                StatusCode::OK,
                &RoomsResponse {
                    rooms: rooms.into_iter().map(wire_room).collect(),
                },
            ),
            Err(failure) => failure_response(failure),
        }
    })
}

fn room_create(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let token = authorization_token(request.headers());
    let wing_id = path_segment(&request, 3).to_owned();
    Box::pin(async move {
        let body = match json_body::<CreateRoomRequest>(request.into_body()).await {
            Ok(body) => body,
            Err(response) => return response,
        };
        let command = match create_room_command(body) {
            Ok(command) => command,
            Err(failure) => return failure_response(failure),
        };
        match app.create_room(&token, &wing_id, command).await {
            Ok(room) => json_value(StatusCode::CREATED, &wire_room(room)),
            Err(failure) => failure_response(failure),
        }
    })
}

fn room_update(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let token = authorization_token(request.headers());
    let room_id = path_segment(&request, 3).to_owned();
    Box::pin(async move {
        let body = match json_body::<UpdateRoomRequest>(request.into_body()).await {
            Ok(body) => body,
            Err(response) => return response,
        };
        match app
            .update_room(
                &token,
                &room_id,
                UpdateRoomCommand {
                    number: body.number,
                    room_type: body.room_type,
                    stream_key: body.stream_key,
                },
            )
            .await
        {
            Ok(room) => json_value(
                StatusCode::OK,
                &RoomResponse {
                    room: wire_room(room),
                },
            ),
            Err(failure) => failure_response(failure),
        }
    })
}

fn beds_list(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let token = authorization_token(request.headers());
    let room_id = path_segment(&request, 3).to_owned();
    Box::pin(async move {
        match app.list_beds(&token, &room_id).await {
            Ok(beds) => json_value(
                StatusCode::OK,
                &BedsResponse {
                    beds: beds.into_iter().map(wire_bed).collect(),
                },
            ),
            Err(failure) => failure_response(failure),
        }
    })
}

fn bed_create(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let token = authorization_token(request.headers());
    let room_id = path_segment(&request, 3).to_owned();
    Box::pin(async move {
        let body = match json_body::<CreateBedRequest>(request.into_body()).await {
            Ok(body) => body,
            Err(response) => return response,
        };
        let Some(label) = body.label else {
            return failure_response(missing_field("label"));
        };
        match app
            .create_bed(
                &token,
                &room_id,
                BedCommand {
                    label,
                    monitor_key: body.monitor_key,
                },
            )
            .await
        {
            Ok(bed) => json_value(StatusCode::CREATED, &wire_bed(bed)),
            Err(failure) => failure_response(failure),
        }
    })
}

fn bed_update(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let token = authorization_token(request.headers());
    let bed_id = path_segment(&request, 3).to_owned();
    Box::pin(async move {
        let body = match json_body::<UpdateBedRequest>(request.into_body()).await {
            Ok(body) => body,
            Err(response) => return response,
        };
        match app
            .update_bed(
                &token,
                &bed_id,
                UpdateBedCommand {
                    label: body.label,
                    monitor_key: body.monitor_key,
                },
            )
            .await
        {
            Ok(bed) => json_value(
                StatusCode::OK,
                &mana_wire::BedResponse { bed: wire_bed(bed) },
            ),
            Err(failure) => failure_response(failure),
        }
    })
}

fn beds_all(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let token = authorization_token(request.headers());
    Box::pin(async move {
        match app.list_residence_beds(&token).await {
            Ok(beds) => json_value(
                StatusCode::OK,
                &ResidenceBedsResponse {
                    beds: beds.into_iter().map(wire_residence_bed).collect(),
                },
            ),
            Err(failure) => failure_response(failure),
        }
    })
}

fn planogram_get(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let token = authorization_token(request.headers());
    let wing_id = path_segment(&request, 3).to_owned();
    Box::pin(async move {
        match app.planogram(&token, &wing_id).await {
            Ok(placements) => json_value(
                StatusCode::OK,
                &PlanogramResponse {
                    wing_id: wing_id.clone(),
                    placements: placements
                        .into_iter()
                        .map(wire_planogram_placement)
                        .collect(),
                },
            ),
            Err(failure) => failure_response(failure),
        }
    })
}

fn planogram_put(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let token = authorization_token(request.headers());
    let wing_id = path_segment(&request, 3).to_owned();
    Box::pin(async move {
        let body = match json_body::<SavePlanogramRequest>(request.into_body()).await {
            Ok(body) => body,
            Err(response) => return response,
        };
        let command = match planogram_command(body) {
            Ok(command) => command,
            Err(failure) => return failure_response(failure),
        };
        match app.save_planogram(&token, &wing_id, command).await {
            Ok(placements) => json_value(
                StatusCode::OK,
                &PlanogramResponse {
                    wing_id: wing_id.clone(),
                    placements: placements
                        .into_iter()
                        .map(wire_planogram_placement)
                        .collect(),
                },
            ),
            Err(failure) => failure_response(failure),
        }
    })
}

fn privacy_regions_get(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let token = authorization_token(request.headers());
    let room_id = path_segment(&request, 3).to_owned();
    Box::pin(async move {
        match app.privacy_regions(&token, &room_id).await {
            Ok(regions) => json_value(
                StatusCode::OK,
                &PrivacyRegionsResponse {
                    room_id: room_id.clone(),
                    regions: regions.into_iter().map(wire_privacy_region).collect(),
                },
            ),
            Err(failure) => failure_response(failure),
        }
    })
}

fn privacy_regions_put(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let token = authorization_token(request.headers());
    let room_id = path_segment(&request, 3).to_owned();
    Box::pin(async move {
        let body = match json_body::<SavePrivacyRegionsRequest>(request.into_body()).await {
            Ok(body) => body,
            Err(response) => return response,
        };
        let command = match privacy_regions_command(body) {
            Ok(command) => command,
            Err(failure) => return failure_response(failure),
        };
        match app.save_privacy_regions(&token, &room_id, command).await {
            Ok(regions) => json_value(
                StatusCode::OK,
                &PrivacyRegionsResponse {
                    room_id: room_id.clone(),
                    regions: regions.into_iter().map(wire_privacy_region).collect(),
                },
            ),
            Err(failure) => failure_response(failure),
        }
    })
}

fn planogram_command(
    request: SavePlanogramRequest,
) -> Result<SavePlanogramCommand, mana_app::AppFailure> {
    let Some(placements) = request.placements else {
        return Err(missing_field("placements"));
    };
    let mut commands = Vec::with_capacity(placements.len());
    for placement in placements {
        let Some(room_id) = placement.room_id else {
            return Err(missing_field("room_id"));
        };
        let Some(x) = placement.x else {
            return Err(missing_field("x"));
        };
        let Some(y) = placement.y else {
            return Err(missing_field("y"));
        };
        commands.push(PlanogramPlacementCommand {
            room_id,
            x,
            y,
            sort_order: placement.sort_order.unwrap_or_default(),
        });
    }
    Ok(SavePlanogramCommand {
        placements: commands,
    })
}

fn privacy_regions_command(
    request: SavePrivacyRegionsRequest,
) -> Result<SavePrivacyRegionsCommand, mana_app::AppFailure> {
    let Some(regions) = request.regions else {
        return Err(missing_field("regions"));
    };
    let mut commands = Vec::with_capacity(regions.len());
    for region in regions {
        let Some(x) = region.x else {
            return Err(missing_field("x"));
        };
        let Some(y) = region.y else {
            return Err(missing_field("y"));
        };
        let Some(w) = region.w else {
            return Err(missing_field("w"));
        };
        let Some(h) = region.h else {
            return Err(missing_field("h"));
        };
        commands.push(PrivacyRegionCommand { x, y, w, h });
    }
    Ok(SavePrivacyRegionsCommand { regions: commands })
}

fn create_facility_command(
    request: CreateFacilityRequest,
) -> Result<CreateFacilityCommand, mana_app::AppFailure> {
    let Some(name) = request.name else {
        return Err(missing_field("name"));
    };
    Ok(CreateFacilityCommand {
        name,
        timezone: request.timezone.unwrap_or_else(|| "UTC".to_owned()),
    })
}

fn create_wing_command(
    request: CreateWingRequest,
) -> Result<CreateWingCommand, mana_app::AppFailure> {
    let Some(name) = request.name else {
        return Err(missing_field("name"));
    };
    let Some(floor) = request.floor else {
        return Err(missing_field("floor"));
    };
    Ok(CreateWingCommand {
        name,
        floor,
        sort_order: request.sort_order,
    })
}

fn create_room_command(
    request: CreateRoomRequest,
) -> Result<CreateRoomCommand, mana_app::AppFailure> {
    let Some(number) = request.number else {
        return Err(missing_field("number"));
    };
    Ok(CreateRoomCommand {
        number,
        room_type: request.room_type,
        stream_key: request.stream_key,
    })
}

fn missing_field(field: &'static str) -> mana_app::AppFailure {
    mana_app::AppFailure::validation("Faltan campos obligatorios", Some(field))
}

fn authorization_token(headers: &axum::http::HeaderMap) -> String {
    headers
        .get(axum::http::header::AUTHORIZATION)
        .and_then(|value| value.to_str().ok())
        .and_then(|value| value.strip_prefix("Bearer "))
        .unwrap_or_default()
        .to_owned()
}

fn wire_facility(facility: mana_app::FacilityView) -> Facility {
    Facility {
        id: facility.id,
        name: facility.name,
        timezone: facility.timezone,
    }
}

fn wire_facility_detail(facility: mana_app::FacilityDetailView) -> FacilityDetail {
    FacilityDetail {
        id: facility.id,
        name: facility.name,
        timezone: facility.timezone,
        wings: facility.wings.into_iter().map(wire_wing).collect(),
    }
}

fn wire_facility_tree(tree: mana_app::FacilityTreeView) -> mana_wire::FacilityTree {
    mana_wire::FacilityTree {
        id: tree.id,
        name: tree.name,
        timezone: tree.timezone,
        wings: tree
            .wings
            .into_iter()
            .map(|w| mana_wire::TreeWing {
                id: w.id,
                name: w.name,
                floor: w.floor,
                sort_order: w.sort_order,
                rooms: w
                    .rooms
                    .into_iter()
                    .map(|r| mana_wire::TreeRoom {
                        id: r.id,
                        number: r.number,
                        room_type: r.room_type,
                        stream_key: r.stream_key,
                        beds: r
                            .beds
                            .into_iter()
                            .map(|b| mana_wire::TreeBed {
                                id: b.id,
                                label: b.label,
                                monitor_key: b.monitor_key,
                                resident: b.resident.map(|r| mana_wire::TreeResident {
                                    id: r.id,
                                    name: r.name,
                                }),
                            })
                            .collect(),
                        streams: r
                            .streams
                            .into_iter()
                            .map(|s| mana_wire::TreeStream {
                                id: s.id,
                                stream_key: s.stream_key,
                                name: s.name,
                                regions: s
                                    .regions
                                    .into_iter()
                                    .map(|reg| mana_wire::TreeRegion {
                                        id: reg.id,
                                        region_type: reg.region_type,
                                        points: reg.points,
                                        label: reg.label,
                                        is_static: reg.is_static,
                                    })
                                    .collect(),
                            })
                            .collect(),
                    })
                    .collect(),
            })
            .collect(),
    }
}

fn wire_wing(wing: mana_app::WingView) -> Wing {
    Wing {
        id: wing.id,
        facility_id: wing.facility_id,
        name: wing.name,
        floor: wing.floor,
        sort_order: wing.sort_order,
        bed_count: wing.bed_count,
    }
}

fn wire_room(room: mana_app::RoomView) -> Room {
    Room {
        id: room.id,
        wing_id: room.wing_id,
        number: room.number,
        room_type: room.room_type,
        stream_key: room.stream_key,
    }
}

fn wire_bed(bed: mana_app::BedView) -> Bed {
    Bed {
        id: bed.id,
        room_id: bed.room_id,
        label: bed.label,
        monitor_key: bed.monitor_key,
    }
}

fn wire_residence_bed(bed: mana_app::ResidenceBedView) -> ResidenceBed {
    ResidenceBed {
        id: bed.id,
        room_id: bed.room_id,
        label: bed.label,
        monitor_key: bed.monitor_key,
        room_number: bed.room_number,
        room_type: bed.room_type,
        stream_key: bed.stream_key,
        wing_id: bed.wing_id,
        wing_name: bed.wing_name,
        wing_floor: bed.wing_floor,
    }
}

fn wire_planogram_placement(placement: mana_app::PlanogramPlacementView) -> PlanogramPlacement {
    PlanogramPlacement {
        id: placement.id,
        wing_id: placement.wing_id,
        room_id: placement.room_id,
        x: placement.x,
        y: placement.y,
        sort_order: placement.sort_order,
        room_number: placement.room_number,
        room_type: placement.room_type,
        stream_key: placement.stream_key,
    }
}

fn wire_privacy_region(region: mana_app::PrivacyRegionView) -> PrivacyRegion {
    PrivacyRegion {
        x: region.x,
        y: region.y,
        w: region.w,
        h: region.h,
    }
}
