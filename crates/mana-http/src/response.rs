use axum::{
    body::Body,
    http::{header, HeaderValue, StatusCode},
    response::Response,
};
use mana_app::AppFailure;
use mana_kernel::Fallo;
use mana_wire::ErrorEnvelope as WireErrorEnvelope;
use serde::{de::DeserializeOwned, Serialize};

const JSON_CONTENT_TYPE: &str = "application/json; charset=utf-8";

#[derive(Serialize)]
pub(crate) struct ErrorEnvelope {
    error: ErrorDetail,
}

#[derive(Serialize)]
struct ErrorDetail {
    code: &'static str,
    message: String,
}

impl ErrorEnvelope {
    /// El codigo sale de `mana-kernel`, no de un literal: el vocabulario de
    /// errores es uno solo y tiene que seguir siendo el que ya emite Node.
    pub(crate) fn new(fallo: Fallo, message: impl Into<String>) -> Self {
        Self {
            error: ErrorDetail {
                code: fallo.codigo(),
                message: message.into(),
            },
        }
    }
}

pub(crate) async fn json_body<T>(body: Body) -> Result<T, Response>
where
    T: DeserializeOwned,
{
    let bytes = axum::body::to_bytes(body, 1024 * 1024).await.map_err(|_| {
        failure_response(AppFailure::new(
            Fallo::PayloadTooLarge,
            "El payload excede el limite",
        ))
    })?;
    let source = if bytes.is_empty() {
        b"{}".as_slice()
    } else {
        &bytes
    };
    serde_json::from_slice(source).map_err(|_| {
        failure_response(AppFailure::new(
            Fallo::InvalidJson,
            "El body debe ser JSON valido",
        ))
    })
}

pub(crate) fn failure_response(failure: AppFailure) -> Response {
    let status = match failure.fallo {
        Fallo::ValidationError => StatusCode::UNPROCESSABLE_ENTITY,
        Fallo::NotFound => StatusCode::NOT_FOUND,
        Fallo::Conflict => StatusCode::CONFLICT,
        Fallo::Forbidden => StatusCode::FORBIDDEN,
        Fallo::Unauthenticated | Fallo::InvalidCredentials => StatusCode::UNAUTHORIZED,
        Fallo::RateLimited => StatusCode::TOO_MANY_REQUESTS,
        Fallo::InvalidJson | Fallo::InvalidBody => StatusCode::BAD_REQUEST,
        Fallo::PayloadTooLarge => StatusCode::PAYLOAD_TOO_LARGE,
        Fallo::InternalError => StatusCode::INTERNAL_SERVER_ERROR,
        Fallo::UpstreamUnavailable => StatusCode::BAD_GATEWAY,
    };
    json_value(
        status,
        &WireErrorEnvelope::new(failure.fallo, failure.message, failure.fields),
    )
}

pub(crate) fn json_value<T: Serialize>(status: StatusCode, value: &T) -> Response {
    match serde_json::to_vec(value) {
        Ok(body) => json_bytes(status, &body),
        Err(error) => json_bytes(
            StatusCode::INTERNAL_SERVER_ERROR,
            format!("{{\"error\":{{\"code\":\"INTERNAL_ERROR\",\"message\":\"{error}\"}}}}")
                .as_bytes(),
        ),
    }
}

pub(crate) fn json_bytes(status: StatusCode, body: &[u8]) -> Response {
    let mut response = Response::new(Body::from(body.to_vec()));
    *response.status_mut() = status;
    response.headers_mut().insert(
        header::CONTENT_TYPE,
        HeaderValue::from_static(JSON_CONTENT_TYPE),
    );
    response
        .headers_mut()
        .insert("access-control-allow-origin", HeaderValue::from_static("*"));
    response.headers_mut().insert(
        "access-control-allow-methods",
        HeaderValue::from_static("GET, POST, PATCH, PUT, DELETE, OPTIONS"),
    );
    response.headers_mut().insert(
        "access-control-allow-headers",
        HeaderValue::from_static("Content-Type, Authorization"),
    );
    response
}

pub(crate) fn empty_response(status: StatusCode) -> Response {
    let mut response = Response::new(Body::empty());
    *response.status_mut() = status;
    response
        .headers_mut()
        .insert("access-control-allow-origin", HeaderValue::from_static("*"));
    response.headers_mut().insert(
        "access-control-allow-methods",
        HeaderValue::from_static("GET, POST, PATCH, PUT, DELETE, OPTIONS"),
    );
    response.headers_mut().insert(
        "access-control-allow-headers",
        HeaderValue::from_static("Content-Type, Authorization"),
    );
    response
}
