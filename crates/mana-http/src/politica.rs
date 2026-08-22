use std::{collections::HashMap, sync::Arc};

use axum::{
    body::Body,
    http::{Request, StatusCode},
    response::Response,
};
use mana_app::{
    AppState, ApplyRecommendationCommand, ApplyRecommendationsCommand, Overrides,
    UpdateProfileCommand,
};

use crate::{
    path_segment,
    response::{failure_response, json_body, json_value},
    rust_handler, RustHandler,
};

pub fn politica_handlers(app: Arc<AppState>) -> HashMap<String, RustHandler> {
    let mut handlers = HashMap::new();
    register(
        &mut handlers,
        "alarm-presets.catalog.get",
        app.clone(),
        catalog,
    );
    register(
        &mut handlers,
        "alarm-presets.list.get",
        app.clone(),
        list_presets,
    );
    register(
        &mut handlers,
        "alarm-presets.resident.get",
        app.clone(),
        get_profile,
    );
    register(
        &mut handlers,
        "alarm-presets.resident.patch",
        app.clone(),
        update_profile,
    );
    register(
        &mut handlers,
        "alarm-presets.recommendations.post",
        app.clone(),
        apply_recommendations,
    );
    register(
        &mut handlers,
        "alarm-presets.autopilot.post",
        app.clone(),
        autopilot,
    );
    register(
        &mut handlers,
        "alarm-presets.history.get",
        app.clone(),
        profile_history,
    );
    register(
        &mut handlers,
        "alarm-presets.recommendation.post",
        app.clone(),
        apply_recommendation,
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

fn query_param<'a>(request: &'a Request<Body>, name: &str) -> Option<&'a str> {
    request.uri().query().and_then(|q| {
        q.split('&').find_map(|param| {
            let (key, value) = param.split_once('=')?;
            if key == name {
                Some(value)
            } else {
                None
            }
        })
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

// GET /api/v1/alarm-presets/catalog
fn catalog(app: Arc<AppState>, _request: Request<Body>) -> HandlerFuture {
    Box::pin(async move {
        match app.get_catalog().await {
            Ok(catalog) => json_value(StatusCode::OK, &serde_json::json!(catalog)),
            Err(failure) => failure_response(failure),
        }
    })
}

// GET /api/v1/alarm-presets?q={query}
fn list_presets(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let token = authorization_token(request.headers());
    let query = query_param(&request, "q").unwrap_or("").to_owned();
    Box::pin(async move {
        if query.trim().is_empty() {
            match app.list_alarm_profiles(&token, "").await {
                Ok(profiles) => json_value(StatusCode::OK, &profiles),
                Err(failure) => failure_response(failure),
            }
        } else {
            match app.search_presets(&token, &query).await {
                Ok(catalog) => json_value(StatusCode::OK, &serde_json::json!(catalog)),
                Err(failure) => failure_response(failure),
            }
        }
    })
}

// GET /api/v1/alarm-presets/:residentId
fn get_profile(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let token = authorization_token(request.headers());
    let resident_id = path_segment(&request, 3).to_owned();
    let at = query_param(&request, "at").map(str::to_owned);
    Box::pin(async move {
        let result = if let Some(at) = at {
            app.get_profile_at(&token, &resident_id, &at).await
        } else {
            app.get_current_profile(&token, &resident_id).await
        };
        match result {
            Ok(legacy) => match app.get_alarm_profile(&token, &resident_id).await {
                Ok(profile) => json_value(
                    StatusCode::OK,
                    &serde_json::json!({ "preset": legacy, "profile": profile }),
                ),
                Err(failure) => failure_response(failure),
            },
            Err(failure) => failure_response(failure),
        }
    })
}

// PATCH /api/v1/alarm-presets/:residentId
fn update_profile(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let token = authorization_token(request.headers());
    let resident_id = path_segment(&request, 3).to_owned();
    Box::pin(async move {
        let body: UpdatePresetRequest = match json_body(request.into_body()).await {
            Ok(b) => b,
            Err(r) => return r,
        };
        match app
            .update_profile(
                &token,
                &resident_id,
                UpdateProfileCommand {
                    risk_level: body.risk_level,
                    mobility_aid: body.mobility_aid,
                    autopilot: body.autopilot,
                    mode: body.mode,
                    template_id: body.template_id,
                    overrides: body
                        .overrides_json
                        .and_then(|s| s.parse::<Overrides>().ok())
                        .or(body.overrides.and_then(|v| serde_json::from_value(v).ok())),
                    catalog_version: body.catalog_version,
                },
            )
            .await
        {
            Ok(profile) => match app.get_alarm_profile(&token, &resident_id).await {
                Ok(full_profile) => json_value(
                    StatusCode::OK,
                    &serde_json::json!({ "preset": profile, "profile": full_profile }),
                ),
                Err(failure) => failure_response(failure),
            },
            Err(failure) => failure_response(failure),
        }
    })
}

// POST /api/v1/alarm-presets/apply-recommendations
fn apply_recommendations(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let token = authorization_token(request.headers());
    Box::pin(async move {
        let body: ApplyBulkRequest = match json_body(request.into_body()).await {
            Ok(b) => b,
            Err(r) => return r,
        };
        let recommendations = body
            .recommendations
            .into_iter()
            .map(|r| ApplyRecommendationCommand {
                resident_id: r.resident_id,
                risk_level: r.risk_level,
                template_id: r.template_id,
                overrides: r.overrides_json
                    .and_then(|s| s.parse::<Overrides>().ok())
                    .or(r.overrides.and_then(|v| serde_json::from_value(v).ok())),
                catalog_version: r.catalog_version,
            })
            .collect();
        match app
            .apply_recommendations(&token, ApplyRecommendationsCommand { recommendations })
            .await
        {
            Ok(results) => match app.list_alarm_profiles(&token, "").await {
                Ok(profiles) => json_value(
                    StatusCode::OK,
                    &serde_json::json!({
                        "presets": results,
                        "applied": results.iter().map(|profile| profile.resident_id.clone()).collect::<Vec<_>>(),
                        "profiles": profiles.profiles,
                        "summary": profiles.summary,
                    }),
                ),
                Err(failure) => failure_response(failure),
            },
            Err(failure) => failure_response(failure),
        }
    })
}

// POST /api/v1/alarm-presets/autopilot
fn autopilot(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let token = authorization_token(request.headers());
    Box::pin(async move {
        let body: AutopilotRequest = match json_body(request.into_body()).await {
            Ok(body) => body,
            Err(response) => return response,
        };
        let result = match body.enabled {
            Some(enabled) => app
                .set_autopilot_for_all(&token, enabled)
                .await
                .map(|results| (results, "changed")),
            None => app
                .autopilot(&token)
                .await
                .map(|results| (results, "applied")),
        };
        match result {
            Ok((results, action)) => match app.list_alarm_profiles(&token, "").await {
                Ok(profiles) => json_value(
                    StatusCode::OK,
                    &serde_json::json!({
                        "presets": results,
                        action: results.iter().map(|profile| profile.resident_id.clone()).collect::<Vec<_>>(),
                        "profiles": profiles.profiles,
                        "summary": profiles.summary,
                    }),
                ),
                Err(failure) => failure_response(failure),
            },
            Err(failure) => failure_response(failure),
        }
    })
}

// POST /api/v1/alarm-presets/:residentId/apply-recommendation
fn apply_recommendation(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let token = authorization_token(request.headers());
    let resident_id = path_segment(&request, 3).to_owned();
    Box::pin(async move {
        let body: ApplySingleRequest = match json_body(request.into_body()).await {
            Ok(b) => b,
            Err(r) => return r,
        };
        match app
            .apply_recommendation(
                &token,
                &resident_id,
                ApplyRecommendationCommand {
                    resident_id: resident_id.clone(),
                    risk_level: body.risk_level,
                    template_id: body.template_id,
                    overrides: body.overrides_json
                        .and_then(|s| s.parse::<Overrides>().ok())
                        .or(body.overrides.and_then(|v| serde_json::from_value(v).ok())),
                    catalog_version: body.catalog_version,
                },
            )
            .await
        {
            Ok(profile) => match app.get_alarm_profile(&token, &resident_id).await {
                Ok(full_profile) => json_value(
                    StatusCode::OK,
                    &serde_json::json!({ "preset": profile, "profile": full_profile }),
                ),
                Err(failure) => failure_response(failure),
            },
            Err(failure) => failure_response(failure),
        }
    })
}

#[derive(serde::Deserialize)]
struct UpdatePresetRequest {
    #[serde(default)]
    risk_level: Option<String>,
    #[serde(default)]
    mobility_aid: Option<String>,
    #[serde(default)]
    autopilot: Option<bool>,
    #[serde(default)]
    mode: Option<String>,
    #[serde(default)]
    template_id: Option<String>,
    #[serde(default)]
    overrides_json: Option<String>,
    /// El cliente manda el documento de ajustes como objeto, no como texto.
    /// Aceptar solo `overrides_json` daba un 400 contra un cuerpo que el panel
    /// manda desde siempre.
    #[serde(default)]
    overrides: Option<serde_json::Value>,
    #[serde(default)]
    catalog_version: Option<String>,
}

#[derive(serde::Deserialize)]
struct ApplyBulkRequest {
    recommendations: Vec<ApplyBulkRecommendation>,
}

#[derive(serde::Deserialize)]
struct ApplyBulkRecommendation {
    resident_id: String,
    #[serde(default)]
    risk_level: Option<String>,
    #[serde(default)]
    template_id: Option<String>,
    #[serde(default)]
    overrides_json: Option<String>,
    #[serde(default)]
    overrides: Option<serde_json::Value>,
    #[serde(default)]
    catalog_version: Option<String>,
}

#[derive(serde::Deserialize)]
struct ApplySingleRequest {
    #[serde(default)]
    risk_level: Option<String>,
    #[serde(default)]
    template_id: Option<String>,
    #[serde(default)]
    overrides_json: Option<String>,
    #[serde(default)]
    overrides: Option<serde_json::Value>,
    #[serde(default)]
    catalog_version: Option<String>,
}

#[derive(serde::Deserialize, Default)]
struct AutopilotRequest {
    #[serde(default)]
    enabled: Option<bool>,
}

// GET /api/v1/alarm-presets/:residentId/history
//
// El caso de uso existia en `mana-app` desde F6 pero nunca tuvo ruta ni
// handler: la escena lo pedia y caia al proxy con 502.
fn profile_history(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let token = authorization_token(request.headers());
    let resident_id = path_segment(&request, 3).to_owned();
    Box::pin(async move {
        match app.get_profile_history(&token, &resident_id).await {
            Ok(view) => json_value(StatusCode::OK, &view),
            Err(failure) => failure_response(failure),
        }
    })
}
