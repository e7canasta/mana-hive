use std::{collections::HashMap, sync::Arc};

use axum::{
    body::Body,
    http::{Request, StatusCode},
    response::Response,
};
use mana_app::{AppState, AuditQuery};
use mana_wire::{AuditEntry, AuditResponse};
use url::form_urlencoded;

use crate::{
    response::{failure_response, json_value},
    rust_handler, RustHandler,
};

pub fn audit_handlers(app: Arc<AppState>) -> HashMap<String, RustHandler> {
    let mut handlers = HashMap::new();
    handlers.insert(
        "audit-log.list.get".to_owned(),
        rust_handler(move |request| {
            let app = app.clone();
            async move { audit_list(app, request).await }
        }),
    );
    handlers
}

async fn audit_list(app: Arc<AppState>, request: Request<Body>) -> Response {
    match app
        .list_audit(
            &authorization_token(request.headers()),
            audit_query(request.uri().query()),
        )
        .await
    {
        Ok(entries) => json_value(
            StatusCode::OK,
            &AuditResponse {
                audit: entries.into_iter().map(wire_entry).collect(),
            },
        ),
        Err(failure) => failure_response(failure),
    }
}

fn authorization_token(headers: &axum::http::HeaderMap) -> String {
    headers
        .get(axum::http::header::AUTHORIZATION)
        .and_then(|value| value.to_str().ok())
        .and_then(|value| value.strip_prefix("Bearer "))
        .unwrap_or_default()
        .to_owned()
}

fn audit_query(query: Option<&str>) -> AuditQuery {
    let mut result = AuditQuery::default();
    for (key, value) in form_urlencoded::parse(query.unwrap_or_default().as_bytes()) {
        match key.as_ref() {
            "limit" => result.limit = value.parse::<usize>().ok(),
            "entity_type" => result.entity_type = non_empty(value.into_owned()),
            "entity_id" => result.entity_id = non_empty(value.into_owned()),
            "action" => result.action = non_empty(value.into_owned()),
            _ => {}
        }
    }
    result
}

fn non_empty(value: String) -> Option<String> {
    let value = value.trim().to_owned();
    (!value.is_empty()).then_some(value)
}

fn wire_entry(entry: mana_app::AuditEntryView) -> AuditEntry {
    AuditEntry {
        id: entry.id,
        actor_id: entry.actor_id,
        actor_name: entry.actor_name,
        action: entry.action,
        entity_type: entry.entity_type,
        entity_id: entry.entity_id,
        metadata: entry.metadata,
        created_at: entry.created_at,
    }
}
