use std::{collections::HashMap, sync::Arc};

use axum::{
    body::Body,
    http::{Request, StatusCode},
    response::Response,
};
use mana_app::{
    AddDeliveryEventCommand, AppState, CreateAlertCommand, CreateDeliveryCommand,
    TransitionAlertCommand,
};

use crate::{
    path_segment,
    response::{failure_response, json_body, json_value},
    rust_handler, RustHandler,
};

pub fn vigilancia_handlers(app: Arc<AppState>) -> HashMap<String, RustHandler> {
    let mut handlers = HashMap::new();
    register(&mut handlers, "alerts.list.get", app.clone(), list_alerts);
    register(
        &mut handlers,
        "alerts.create.post",
        app.clone(),
        create_alert,
    );
    register(
        &mut handlers,
        "alerts.update.patch",
        app.clone(),
        transition_alert,
    );
    register(&mut handlers, "alerts.view.post", app.clone(), view_alert);
    register(
        &mut handlers,
        "alerts.deliveries.get",
        app.clone(),
        list_deliveries,
    );
    register(
        &mut handlers,
        "alerts.deliveries.create.post",
        app.clone(),
        create_delivery,
    );
    register(
        &mut handlers,
        "alerts.delivery-events.create.post",
        app.clone(),
        add_delivery_event,
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

// GET /api/v1/alerts
fn list_alerts(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let token = authorization_token(request.headers());
    Box::pin(async move {
        match app.list_alerts(&token, None, None, None).await {
            Ok(data) => json_value(StatusCode::OK, &serde_json::json!(data)),
            Err(failure) => failure_response(failure),
        }
    })
}

// POST /api/v1/alerts
fn create_alert(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let token = authorization_token(request.headers());
    Box::pin(async move {
        let body: CreateAlertRequest = match json_body(request.into_body()).await {
            Ok(b) => b,
            Err(r) => return r,
        };
        match app
            .create_alert(
                &token,
                CreateAlertCommand {
                    resident_id: body.resident_id,
                    bed_id: body.bed_id,
                    evidence_kind: body.evidence_kind,
                    evidence_ref: body.evidence_ref,
                    rule_id: body.rule_id,
                    level: body.level,
                    title: body.title,
                    detail: body.detail,
                    occurred_at: body.occurred_at,
                },
            )
            .await
        {
            Ok(alert) => json_value(StatusCode::CREATED, &serde_json::json!({ "alert": alert })),
            Err(failure) => failure_response(failure),
        }
    })
}

// PATCH /api/v1/alerts/:alertId
fn transition_alert(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let token = authorization_token(request.headers());
    let alert_id = path_segment(&request, 3).to_owned();
    Box::pin(async move {
        let body: TransitionAlertRequest = match json_body(request.into_body()).await {
            Ok(b) => b,
            Err(r) => return r,
        };
        match app
            .transition_alert(
                &token,
                &alert_id,
                TransitionAlertCommand {
                    to_status: body.to_status,
                    actor_id: body.actor_id,
                },
            )
            .await
        {
            Ok(alert) => json_value(StatusCode::OK, &serde_json::json!({ "alert": alert })),
            Err(failure) => failure_response(failure),
        }
    })
}

// POST /api/v1/alerts/:alertId/view
fn view_alert(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let token = authorization_token(request.headers());
    let alert_id = path_segment(&request, 3).to_owned();
    Box::pin(async move {
        match app.view_alert(&token, &alert_id).await {
            Ok(alert) => json_value(StatusCode::OK, &serde_json::json!({ "alert": alert })),
            Err(failure) => failure_response(failure),
        }
    })
}

// GET /api/v1/alerts/:alertId/deliveries
fn list_deliveries(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let token = authorization_token(request.headers());
    let alert_id = path_segment(&request, 3).to_owned();
    Box::pin(async move {
        match app.list_deliveries(&token, &alert_id).await {
            Ok(data) => json_value(StatusCode::OK, &serde_json::json!(data)),
            Err(failure) => failure_response(failure),
        }
    })
}

// POST /api/v1/alerts/:alertId/deliveries
fn create_delivery(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let token = authorization_token(request.headers());
    let alert_id = path_segment(&request, 3).to_owned();
    Box::pin(async move {
        let body: CreateDeliveryRequest = match json_body(request.into_body()).await {
            Ok(b) => b,
            Err(r) => return r,
        };
        match app
            .create_delivery(
                &token,
                &alert_id,
                CreateDeliveryCommand {
                    recipient_kind: body.recipient_kind,
                    recipient_id: body.recipient_id,
                    channel: body.channel,
                    escalation_level: body.escalation_level,
                },
            )
            .await
        {
            Ok(delivery) => json_value(
                StatusCode::CREATED,
                &serde_json::json!({ "delivery": delivery }),
            ),
            Err(failure) => failure_response(failure),
        }
    })
}

// POST /api/v1/deliveries/:deliveryId/events
fn add_delivery_event(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let token = authorization_token(request.headers());
    let delivery_id = path_segment(&request, 3).to_owned();
    Box::pin(async move {
        let body: AddDeliveryEventRequest = match json_body(request.into_body()).await {
            Ok(b) => b,
            Err(r) => return r,
        };
        match app
            .add_delivery_event(
                &token,
                &delivery_id,
                AddDeliveryEventCommand {
                    kind: body.kind,
                    reason: body.reason,
                },
            )
            .await
        {
            Ok(delivery) => {
                json_value(StatusCode::OK, &serde_json::json!({ "delivery": delivery }))
            }
            Err(failure) => failure_response(failure),
        }
    })
}

#[derive(serde::Deserialize)]
struct CreateAlertRequest {
    #[serde(default)]
    resident_id: Option<String>,
    bed_id: String,
    evidence_kind: String,
    #[serde(default)]
    evidence_ref: Option<String>,
    rule_id: String,
    level: String,
    title: String,
    #[serde(default)]
    detail: Option<String>,
    occurred_at: String,
}

#[derive(serde::Deserialize)]
struct TransitionAlertRequest {
    to_status: String,
    #[serde(default)]
    actor_id: Option<String>,
}

#[derive(serde::Deserialize)]
struct CreateDeliveryRequest {
    recipient_kind: String,
    recipient_id: String,
    channel: String,
    #[serde(default)]
    escalation_level: i32,
}

#[derive(serde::Deserialize)]
struct AddDeliveryEventRequest {
    kind: String,
    #[serde(default)]
    reason: Option<String>,
}

#[cfg(test)]
mod tests {
    use super::*;

    /// Estuvo en el indice 3, que es el literal `"alerts"`, y todas las rutas
    /// con identificador respondian 404 buscando la alerta `"alerts"`. La
    /// escena de vigilancia lo detectaba y el bug sobrevivio igual porque no
    /// habia test.
    #[test]
    fn the_identifier_lives_in_the_fifth_segment() {
        let cases = [
            ("/api/v1/alerts/AX-123", "AX-123"),
            ("/api/v1/alerts/AX-123/view", "AX-123"),
            ("/api/v1/alerts/AX-123/deliveries", "AX-123"),
            ("/api/v1/deliveries/DL-9/events", "DL-9"),
        ];
        for (path, expected) in cases {
            let request = Request::builder().uri(path).body(Body::empty()).unwrap();
            assert_eq!(path_segment(&request, 3), expected, "path {path}");
        }
    }
}
