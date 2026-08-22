use std::{collections::HashMap, sync::Arc};

use axum::{
    body::Body,
    http::{Request, StatusCode},
    response::Response,
};
use mana_app::{AppState, CreateStreamCommand, ReplaceRegionsCommand, RegionCommand, UpdateRegionCommand};
use mana_wire::{
    CreateStreamRequest, ReplaceRegionsRequest, StreamRegionResponse,
    StreamResponse, UpdateRegionRequest,
};

use crate::{
    path_segment,
    response::{failure_response, json_body, json_value},
    rust_handler, RustHandler,
};

pub fn streams_handlers(app: Arc<AppState>) -> HashMap<String, RustHandler> {
    let mut handlers = HashMap::new();
    register(
        &mut handlers,
        "rooms.streams.list.get",
        app.clone(),
        streams_list,
    );
    register(
        &mut handlers,
        "rooms.streams.create.post",
        app.clone(),
        stream_create,
    );
    register(
        &mut handlers,
        "streams.detail.get",
        app.clone(),
        stream_detail,
    );
    register(
        &mut handlers,
        "streams.regions.list.get",
        app.clone(),
        regions_list,
    );
    register(
        &mut handlers,
        "streams.regions.replace.put",
        app.clone(),
        regions_replace,
    );
    register(
        &mut handlers,
        "streams.regions.update.patch",
        app,
        region_update,
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
        .and_then(|value| value.to_str().ok())
        .unwrap_or("")
        .strip_prefix("Bearer ")
        .unwrap_or("")
        .to_owned()
}

fn streams_list(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let room_id = path_segment(&request, 3).to_owned();
    let token = authorization_token(request.headers());
    Box::pin(async move {
        match app.list_streams(&token, &room_id).await {
            Ok(streams) => json_value(
                StatusCode::OK,
                &serde_json::json!({
                    "streams": streams.into_iter().map(wire_stream).collect::<Vec<_>>()
                }),
            ),
            Err(failure) => failure_response(failure),
        }
    })
}

fn stream_create(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let room_id = path_segment(&request, 3).to_owned();
    let token = authorization_token(request.headers());
    Box::pin(async move {
        let body = match json_body::<CreateStreamRequest>(request.into_body()).await {
            Ok(body) => body,
            Err(response) => return response,
        };
        let command = CreateStreamCommand {
            room_id,
            stream_key: body.stream_key.unwrap_or_default(),
            name: body.name,
        };
        match app.create_stream(&token, command).await {
            Ok(stream) => json_value(StatusCode::CREATED, &wire_stream(stream)),
            Err(failure) => failure_response(failure),
        }
    })
}

fn stream_detail(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let stream_id = path_segment(&request, 3).to_owned();
    let token = authorization_token(request.headers());
    Box::pin(async move {
        match app.get_stream(&token, &stream_id).await {
            Ok(stream) => json_value(StatusCode::OK, &wire_stream(stream)),
            Err(failure) => failure_response(failure),
        }
    })
}

fn regions_list(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let stream_id = path_segment(&request, 3).to_owned();
    let token = authorization_token(request.headers());
    Box::pin(async move {
        match app.list_regions(&token, &stream_id).await {
            Ok(regions) => json_value(
                StatusCode::OK,
                &serde_json::json!({
                    "regions": regions.into_iter().map(wire_region).collect::<Vec<_>>()
                }),
            ),
            Err(failure) => failure_response(failure),
        }
    })
}

fn regions_replace(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let stream_id = path_segment(&request, 3).to_owned();
    let token = authorization_token(request.headers());
    Box::pin(async move {
        let body = match json_body::<ReplaceRegionsRequest>(request.into_body()).await {
            Ok(body) => body,
            Err(response) => return response,
        };
        let regions: Vec<RegionCommand> = body
            .regions
            .unwrap_or_default()
            .into_iter()
            .filter_map(|r| {
                Some(RegionCommand {
                    region_type: r.region_type?,
                    points: r.points?,
                    label: r.label,
                })
            })
            .collect();
        let command = ReplaceRegionsCommand { regions };
        match app.replace_regions(&token, &stream_id, command).await {
            Ok(regions) => json_value(
                StatusCode::OK,
                &serde_json::json!({
                    "regions": regions.into_iter().map(wire_region).collect::<Vec<_>>()
                }),
            ),
            Err(failure) => failure_response(failure),
        }
    })
}

fn region_update(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let stream_id = path_segment(&request, 3).to_owned();
    let region_id = path_segment(&request, 5).to_owned();
    let token = authorization_token(request.headers());
    Box::pin(async move {
        let body = match json_body::<UpdateRegionRequest>(request.into_body()).await {
            Ok(body) => body,
            Err(response) => return response,
        };
        let points = match body.points {
            Some(points) => points,
            None => {
                return failure_response(mana_app::AppFailure::validation(
                    "points is required",
                    None,
                ));
            }
        };
        let command = UpdateRegionCommand { points };
        match app.update_region(&token, &stream_id, &region_id, command).await {
            Ok(region) => json_value(StatusCode::OK, &wire_region(region)),
            Err(failure) => failure_response(failure),
        }
    })
}

fn wire_stream(stream: mana_app::StreamView) -> StreamResponse {
    StreamResponse {
        id: stream.id,
        room_id: stream.room_id,
        stream_key: stream.stream_key,
        name: stream.name,
    }
}

fn wire_region(region: mana_app::StreamRegionView) -> StreamRegionResponse {
    StreamRegionResponse {
        id: region.id,
        stream_id: region.stream_id,
        region_type: region.region_type,
        points: region.points,
        label: region.label,
        is_static: region.is_static,
        updated_by: region.updated_by,
    }
}
