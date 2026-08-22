use std::{collections::HashMap, sync::Arc};

use axum::{
    body::Body,
    http::{Request, StatusCode},
    response::Response,
};
use mana_app::{AppFailure, AppState};
use mana_engine_v2::PerceptionEvent;

use crate::{
    response::{failure_response, json_body, json_value},
    rust_handler, RustHandler,
};

pub fn engine_handlers(app: Arc<AppState>) -> HashMap<String, RustHandler> {
    let mut handlers = HashMap::new();
    register(
        &mut handlers,
        "engine.perception.post",
        app.clone(),
        handle_perception,
    );
    register(
        &mut handlers,
        "engine.tick.post",
        app.clone(),
        handle_tick,
    );
    register(
        &mut handlers,
        "engine.state.get",
        app.clone(),
        get_bed_state,
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

fn app_failure(fallo: mana_kernel::Fallo, message: impl Into<String>) -> AppFailure {
    AppFailure::new(fallo, message)
}

// POST /internal/v1/engine/perception
fn handle_perception(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    Box::pin(async move {
        let body: PerceptionEvent = match json_body(request.into_body()).await {
            Ok(b) => b,
            Err(r) => return r,
        };

        // Procesar el perception event a través del engine v2
        let scene_events = {
            let engine_v2 = app.engine_v2();
            let mut engine = engine_v2.write().await;
            engine.on_perception_event(body)
        };
        
        // Por ahora solo retornamos los scene events generados
        // TODO: persistir scene events y notificar a sentinel
        json_value(
            StatusCode::OK,
            &serde_json::json!({
                "processed": true,
                "scene_events_count": scene_events.len(),
                "scene_events": scene_events,
            }),
        )
    })
}

// POST /internal/v1/engine/tick
fn handle_tick(app: Arc<AppState>, _request: Request<Body>) -> HandlerFuture {
    Box::pin(async move {
        let now = chrono::Utc::now();
        let scene_events = {
            let engine_v2 = app.engine_v2();
            let mut engine = engine_v2.write().await;
            engine.tick(now)
        };

        json_value(
            StatusCode::OK,
            &serde_json::json!({
                "tick_at": now,
                "scene_events_count": scene_events.len(),
                "scene_events": scene_events,
            }),
        )
    })
}

// GET /internal/v1/engine/state/{bed_id}
fn get_bed_state(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    Box::pin(async move {
        let bed_id = path_segment(&request, 5);
        
        let bed = {
            let engine_v2 = app.engine_v2();
            let engine = engine_v2.read().await;
            engine.get_bed(bed_id).cloned()
        };
        
        match bed {
            Some(bed) => json_value(StatusCode::OK, &serde_json::json!({
                "bed_id": bed.bed_id,
                "resident_id": bed.resident_id,
                "person_state": bed.person.state.as_str(),
                "person_state_since": bed.person.state_since,
                "location": format!("{:?}", bed.person.location),
                "sleeping": bed.person.sleeping,
                "confidence": bed.person.confidence,
                "bed_occupancy": format!("{:?}", bed.objects.bed),
                "chair_occupancy": format!("{:?}", bed.objects.chair),
                "wheelchair_occupancy": format!("{:?}", bed.objects.wheelchair),
                "walker_presence": format!("{:?}", bed.objects.walker),
                "extremities_out_of_bed": bed.extremities.out_of_bed,
                "body_parts_out": bed.extremities.body_parts_out,
                "transitions_count": bed.transitions.len(),
            })),
            None => failure_response(app_failure(
                mana_kernel::Fallo::NotFound,
                format!("Bed {} not found", bed_id),
            )),
        }
    })
}

fn path_segment(request: &Request<Body>, index: usize) -> &str {
    request
        .uri()
        .path()
        .trim_matches('/')
        .split('/')
        .nth(index)
        .unwrap_or("")
}
