use std::{
    collections::HashMap,
    net::SocketAddr,
    sync::{Arc, Mutex},
    time::{Duration, Instant},
};

use axum::{
    body::Body,
    extract::ConnectInfo,
    http::{header, HeaderMap, Request, StatusCode},
    response::Response,
};
use mana_app::{
    AdminUserView, AppFailure, AppState, AuthenticatedView, CreateUserCommand, LoginCommand,
    LoginResult, UpdateUserCommand,
};
use mana_kernel::Fallo;
use mana_wire::{
    AdminUser, AuthUser, CreateUserRequest, CurrentUserResponse, LoginRequest, LoginResponse,
    UpdateUserRequest, UserResponse, UsersResponse,
};

use crate::{
    response::{empty_response, failure_response, json_body, json_value},
    rust_handler, RustHandler,
};

/// Registra las rutas de identidad contra una instancia de aplicacion.
///
/// El registro usa ids, no paths, porque `RouteTable` es la autoridad que
/// decide si una entrada puede dejar de ir al Node.
pub fn identity_handlers(app: Arc<AppState>) -> HashMap<String, RustHandler> {
    let login_limiter = Arc::new(LoginRateLimiter::default());
    let mut handlers = HashMap::new();

    let app_for_login = app.clone();
    let limiter_for_login = login_limiter.clone();
    handlers.insert(
        "auth.login.post".to_owned(),
        rust_handler(move |request| {
            let app = app_for_login.clone();
            let limiter = limiter_for_login.clone();
            async move { identity_login(app, limiter, request).await }
        }),
    );

    let app_for_me = app.clone();
    handlers.insert(
        "auth.me.get".to_owned(),
        rust_handler(move |request| {
            let app = app_for_me.clone();
            async move { identity_me(app, request).await }
        }),
    );

    let app_for_logout = app.clone();
    handlers.insert(
        "auth.logout.post".to_owned(),
        rust_handler(move |request| {
            let app = app_for_logout.clone();
            async move { identity_logout(app, request).await }
        }),
    );

    let app_for_list = app.clone();
    handlers.insert(
        "users.list.get".to_owned(),
        rust_handler(move |request| {
            let app = app_for_list.clone();
            async move { users_list(app, request).await }
        }),
    );

    let app_for_create = app.clone();
    handlers.insert(
        "users.create.post".to_owned(),
        rust_handler(move |request| {
            let app = app_for_create.clone();
            async move { users_create(app, request).await }
        }),
    );

    handlers.insert(
        "users.update.patch".to_owned(),
        rust_handler(move |request| {
            let app = app.clone();
            async move { users_update(app, request).await }
        }),
    );

    handlers
}

#[derive(Default)]
struct LoginRateLimiter {
    attempts: Mutex<HashMap<String, LoginAttempt>>,
}

struct LoginAttempt {
    started_at: Instant,
    count: u8,
}

impl LoginRateLimiter {
    fn limited(&self, key: &str) -> bool {
        let Ok(mut attempts) = self.attempts.lock() else {
            return false;
        };
        let Some(attempt) = attempts.get(key) else {
            return false;
        };
        if attempt.started_at.elapsed() >= Duration::from_secs(60) {
            attempts.remove(key);
            return false;
        }
        attempt.count >= 5
    }

    fn failed(&self, key: &str) {
        if let Ok(mut attempts) = self.attempts.lock() {
            let entry = attempts.entry(key.to_owned()).or_insert(LoginAttempt {
                started_at: Instant::now(),
                count: 0,
            });
            if entry.started_at.elapsed() >= Duration::from_secs(60) {
                entry.started_at = Instant::now();
                entry.count = 0;
            }
            entry.count = entry.count.saturating_add(1);
        }
    }

    fn succeeded(&self, key: &str) {
        if let Ok(mut attempts) = self.attempts.lock() {
            attempts.remove(key);
        }
    }
}

async fn identity_login(
    app: Arc<AppState>,
    limiter: Arc<LoginRateLimiter>,
    request: Request<Body>,
) -> Response {
    let key = client_key(&request);
    if limiter.limited(&key) {
        return failure_response(AppFailure::new(
            Fallo::RateLimited,
            "Demasiados intentos de inicio de sesion",
        ));
    }
    let body = match json_body::<LoginRequest>(request.into_body()).await {
        Ok(body) => body,
        Err(response) => return response,
    };
    let command = match login_command(body) {
        Ok(command) => command,
        Err(failure) => return failure_response(failure),
    };
    match app.login(command).await {
        Ok(response) => {
            limiter.succeeded(&key);
            json_value(StatusCode::OK, &wire_login(response))
        }
        Err(failure) => {
            if failure.fallo == Fallo::InvalidCredentials {
                limiter.failed(&key);
            }
            failure_response(failure)
        }
    }
}

async fn identity_me(app: Arc<AppState>, request: Request<Body>) -> Response {
    match app
        .current_user(&authorization_token(request.headers()))
        .await
    {
        Ok(response) => json_value(
            StatusCode::OK,
            &CurrentUserResponse {
                user: wire_auth(response),
            },
        ),
        Err(failure) => failure_response(failure),
    }
}

async fn identity_logout(app: Arc<AppState>, request: Request<Body>) -> Response {
    match app.logout(&authorization_token(request.headers())).await {
        Ok(()) => empty_response(StatusCode::NO_CONTENT),
        Err(failure) => failure_response(failure),
    }
}

async fn users_list(app: Arc<AppState>, request: Request<Body>) -> Response {
    let include_inactive = query_value(request.uri().query(), "include_inactive") == Some("1");
    match app
        .list_users(&authorization_token(request.headers()), include_inactive)
        .await
    {
        Ok(response) => json_value(
            StatusCode::OK,
            &UsersResponse {
                users: response.into_iter().map(wire_admin).collect(),
            },
        ),
        Err(failure) => failure_response(failure),
    }
}

async fn users_create(app: Arc<AppState>, request: Request<Body>) -> Response {
    let token = authorization_token(request.headers());
    let body = match json_body(request.into_body()).await {
        Ok(body) => body,
        Err(response) => return response,
    };
    let command = match create_command(body) {
        Ok(command) => command,
        Err(failure) => return failure_response(failure),
    };
    match app.create_user(&token, command).await {
        Ok(response) => json_value(
            StatusCode::CREATED,
            &UserResponse {
                user: wire_admin(response),
            },
        ),
        Err(failure) => failure_response(failure),
    }
}

async fn users_update(app: Arc<AppState>, request: Request<Body>) -> Response {
    let token = authorization_token(request.headers());
    let user_id = request
        .uri()
        .path()
        .trim_end_matches('/')
        .rsplit('/')
        .next()
        .unwrap_or_default()
        .to_owned();
    let body = match json_body::<UpdateUserRequest>(request.into_body()).await {
        Ok(body) => body,
        Err(response) => return response,
    };
    let command = UpdateUserCommand {
        display_name: body.display_name,
        role: body.role,
        job_title: body.job_title,
        active: body.active,
        password: body.password,
    };
    match app.update_user(&token, &user_id, command).await {
        Ok(response) => json_value(
            StatusCode::OK,
            &UserResponse {
                user: wire_admin(response),
            },
        ),
        Err(failure) => failure_response(failure),
    }
}

fn authorization_token(headers: &HeaderMap) -> String {
    headers
        .get(header::AUTHORIZATION)
        .and_then(|value| value.to_str().ok())
        .and_then(|value| value.strip_prefix("Bearer "))
        .unwrap_or_default()
        .to_owned()
}

fn login_command(request: LoginRequest) -> Result<LoginCommand, AppFailure> {
    let username = request
        .username
        .filter(|value| !value.trim().is_empty())
        .ok_or_else(|| {
            AppFailure::validation("Usuario y clave son obligatorios", Some("username"))
        })?;
    let password = request
        .password
        .filter(|value| !value.is_empty())
        .ok_or_else(|| {
            AppFailure::validation("Usuario y clave son obligatorios", Some("password"))
        })?;
    Ok(LoginCommand { username, password })
}

fn create_command(request: CreateUserRequest) -> Result<CreateUserCommand, AppFailure> {
    let missing = [
        ("username", request.username.is_none()),
        ("display_name", request.display_name.is_none()),
        ("role", request.role.is_none()),
        ("password", request.password.is_none()),
    ]
    .into_iter()
    .filter(|(_, missing)| *missing)
    .map(|(field, _)| (field.to_owned(), "required".to_owned()))
    .collect::<Vec<_>>();
    if !missing.is_empty() {
        return Err(
            AppFailure::new(Fallo::ValidationError, "Faltan campos obligatorios")
                .with_fields(missing),
        );
    }
    let username = request
        .username
        .ok_or_else(|| AppFailure::validation("username es obligatorio", Some("username")))?;
    let display_name = request.display_name.ok_or_else(|| {
        AppFailure::validation("display_name es obligatorio", Some("display_name"))
    })?;
    let role = request
        .role
        .ok_or_else(|| AppFailure::validation("role es obligatorio", Some("role")))?;
    let password = request
        .password
        .ok_or_else(|| AppFailure::validation("password es obligatorio", Some("password")))?;
    Ok(CreateUserCommand {
        username,
        display_name,
        role,
        job_title: request.job_title,
        password,
    })
}

fn wire_login(result: LoginResult) -> LoginResponse {
    LoginResponse {
        token: result.token,
        expires_at: result.expires_at,
        user: wire_auth(result.user),
    }
}

fn wire_auth(view: AuthenticatedView) -> AuthUser {
    let permissions = view.features.clone();
    AuthUser {
        id: view.id,
        username: view.username,
        display_name: view.display_name,
        role: view.role,
        features: view.features,
        permissions,
        capabilities: view.capabilities,
    }
}

fn wire_admin(view: AdminUserView) -> AdminUser {
    AdminUser {
        id: view.id,
        username: view.username,
        display_name: view.display_name,
        role: view.role,
        job_title: view.job_title,
        // Node historicamente expone este flag como 0/1; mantenerlo evita una
        // diferencia observable mientras las rutas siguen compartiendo proxy.
        active: i32::from(view.active),
    }
}

fn client_key(request: &Request<Body>) -> String {
    request
        .extensions()
        .get::<ConnectInfo<SocketAddr>>()
        .map(|ConnectInfo(address)| address.ip().to_string())
        .unwrap_or_else(|| "unknown".to_owned())
}

fn query_value<'a>(query: Option<&'a str>, wanted: &str) -> Option<&'a str> {
    query?.split('&').find_map(|part| {
        let (key, value) = part.split_once('=')?;
        (key == wanted).then_some(value)
    })
}
