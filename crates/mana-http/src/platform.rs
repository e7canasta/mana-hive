use std::sync::Arc;

use axum::{
    extract::State,
    http::{HeaderValue, StatusCode},
    response::Response,
};
use serde::Serialize;

use crate::{
    response::{empty_response, json_bytes, json_value},
    HubState,
};

pub(crate) async fn health() -> Response {
    let mut response = json_bytes(
        StatusCode::OK,
        br#"{"ok":true,"service":"virtual-rounds-api","database":"sqlite"}"#,
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

/// Readiness del hub.
///
/// Hasta F8 sondeaba el `/health` del upstream Node. Con el fallback retirado no
/// hay upstream que sondear: el hub esta listo cuando arranco, porque arrancar
/// ya exige que toda ruta `rust` tenga handler registrado.
pub(crate) async fn ready(State(state): State<Arc<HubState>>) -> Response {
    json_value(
        StatusCode::OK,
        &Readiness {
            ok: true,
            rutas: state.routes.total(),
            fallback: None,
        },
    )
}

#[derive(Serialize)]
struct Readiness {
    ok: bool,
    rutas: usize,
    /// Siempre `null`. Se conserva en el cuerpo para que quien lo monitoreaba
    /// vea que el fallback se retiro y no que el campo se perdio.
    fallback: Option<String>,
}

/// `OPTIONS` sobre cualquier path.
///
/// Node atendia el preflight antes del router y contestaba `204` para todo
/// path, existiera o no; por eso la tabla tiene una entrada comodin y no un
/// patron por ruta. Este handler conserva ese comportamiento exacto.
pub(crate) async fn preflight(_request: axum::http::Request<axum::body::Body>) -> Response {
    empty_response(StatusCode::NO_CONTENT)
}

#[derive(Serialize)]
struct RoutesInfo {
    total: usize,
    node: usize,
    rust: usize,
    /// Rutas distintas fuera de la tabla. Con el inventario completo es 0; si
    /// sube, la tabla quedo corta y `rutas` dice exactamente dónde.
    no_inventariada: usize,
    rutas_no_inventariadas: Vec<String>,
}

pub(crate) async fn routes_info(State(state): State<Arc<HubState>>) -> Response {
    let no_inventariadas = state.no_inventariadas();
    let counts = RoutesInfo {
        total: state.routes.total(),
        node: state.routes.node(),
        rust: state.routes.rust(),
        no_inventariada: no_inventariadas.len(),
        rutas_no_inventariadas: no_inventariadas.into_iter().collect(),
    };
    json_value(StatusCode::OK, &counts)
}
