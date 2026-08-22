//! Puerta HTTP del hub.
//!
//! La tabla embebida es el inventario ejecutable: identidad ya tiene handlers
//! Rust y el resto conserva el fallback hacia Node mientras se migra.

mod audit;
mod cobertura;
mod config;
mod cuidado;
mod engine;
mod historia;
mod identity;
mod internal;
mod observation;
mod platform;
mod poblacion;
mod politica;
mod residencia;
mod response;
mod routes;
mod streams;
mod vigilancia;

#[cfg(test)]
mod tests;

use std::{
    collections::{BTreeSet, HashMap},
    sync::{Arc, Mutex},
};

use axum::{
    body::Body,
    extract::State,
    http::{Method, Request, StatusCode},
    response::Response,
    routing::{get, on, MethodFilter},
    Router,
};
use futures_util::future::BoxFuture;
use mana_kernel::Fallo;
use thiserror::Error;

pub use audit::audit_handlers;
pub use cobertura::cobertura_handlers;
pub use config::{ConfigError, HubConfig};
pub use cuidado::cuidado_handlers;
pub use engine::engine_handlers;
pub use historia::historia_handlers;
pub use identity::identity_handlers;
pub use internal::internal_handlers;
pub use observation::observation_handlers;
pub use poblacion::poblacion_handlers;
pub use politica::politica_handlers;
pub use residencia::residence_handlers;
pub use routes::{Route, RouteTable, RouteTableError, Ruta, Rutas, Serve};
pub use streams::streams_handlers;
pub use vigilancia::vigilancia_handlers;

/// Handler Rust registrado por id en la frontera de migracion del proxy.
pub type RustHandler = Arc<dyn Fn(Request<Body>) -> BoxFuture<'static, Response> + Send + Sync>;

pub fn rust_handler<F, Fut>(handler: F) -> RustHandler
where
    F: Fn(Request<Body>) -> Fut + Send + Sync + 'static,
    Fut: std::future::Future<Output = Response> + Send + 'static,
{
    Arc::new(move |request| Box::pin(handler(request)))
}

/// Tope de rutas distintas que se recuerdan. Evita que un escaneo de paths al
/// azar haga crecer el set sin limite; con la tabla completa deberia ser 0.
const NO_INVENTARIADAS_MAX: usize = 128;

/// Estado compartido construido a partir de `RouteTable`.
#[derive(Clone)]
pub struct HubState {
    pub(crate) config: HubConfig,
    pub(crate) routes: Arc<RouteTable>,
    pub(crate) handlers: Arc<HashMap<String, RustHandler>>,
    no_inventariadas: Arc<Mutex<BTreeSet<String>>>,
}

impl HubState {
    pub fn config(&self) -> &HubConfig {
        &self.config
    }

    pub fn routes(&self) -> &RouteTable {
        &self.routes
    }

    /// Rutas distintas —no requests— que llegaron sin estar en la tabla.
    pub fn no_inventariadas(&self) -> BTreeSet<String> {
        self.no_inventariadas
            .lock()
            .map(|set| set.clone())
            .unwrap_or_default()
    }

    fn registrar_no_inventariada(&self, method: &Method, path: &str) {
        if let Ok(mut set) = self.no_inventariadas.lock() {
            if set.len() < NO_INVENTARIADAS_MAX {
                set.insert(format!("{method} {path}"));
            }
        }
    }
}

#[derive(Debug, Error)]
pub enum HubError {
    #[error(transparent)]
    Config(#[from] ConfigError),
    #[error(transparent)]
    Routes(#[from] RouteTableError),
}

/// Construye el router de produccion con la tabla embebida.
pub fn build_router(config: HubConfig) -> Result<Router, HubError> {
    build_router_with_handlers(config, HashMap::new())
}

/// Variante para registrar handlers Rust y hacer que la validacion sea explicita.
pub fn build_router_with_handlers(
    config: HubConfig,
    handlers: HashMap<String, RustHandler>,
) -> Result<Router, HubError> {
    build_router_from_routes(config, RouteTable::embedded()?, handlers)
}

/// Construye un router desde una tabla ya validada; es util para probar el
/// contrato de arranque sin cambiar el archivo embebido.
pub fn build_router_from_routes(
    config: HubConfig,
    routes: RouteTable,
    handlers: HashMap<String, RustHandler>,
) -> Result<Router, HubError> {
    // El preflight no depende de ningun contexto, asi que lo registra el propio
    // transporte en vez de pedirselo a quien construye el router.
    let mut handlers = handlers;
    handlers.insert(
        "cors.preflight.options".to_owned(),
        rust_handler(platform::preflight),
    );

    let registered = handlers.keys().cloned().collect();
    routes.validate_handlers(&registered)?;

    let state = Arc::new(HubState {
        config,
        routes: Arc::new(routes),
        handlers: Arc::new(handlers),
        no_inventariadas: Arc::new(Mutex::new(BTreeSet::new())),
    });

    // `on(MethodFilter::GET, …)` y no `get(…)`: `get` tambien atiende HEAD, y la
    // API responde 404 a `HEAD /health`. El `.fallback(dispatch)` manda el resto
    // al despachador, que resuelve `OPTIONS` con el comodin y 404 lo demas — sin
    // esto axum cortaria con un 405 que esta API nunca emitio.
    Ok(Router::new()
        .route(
            "/health",
            on(MethodFilter::GET, platform::health)
                .on(MethodFilter::HEAD, dispatch)
                .fallback(dispatch),
        )
        .route("/__hub/ready", get(platform::ready))
        .route("/__hub/rutas", get(platform::routes_info))
        .fallback(dispatch)
        .with_state(state))
}

/// El segmento `index` del path, **sin contar la barra inicial**.
///
/// `/api/v1/residents/:residentId` da `api`=0, `v1`=1, `residents`=2 y el
/// identificador en 3.
///
/// Vive aca y no en cada modulo porque durante F1-F8 hubo **dos** versiones con
/// el mismo nombre —una recortaba la barra y la otra no—, asi que el mismo
/// indice significaba cosas distintas segun el archivo. De ahi salio que las
/// cinco rutas de alerta con identificador respondieran 404. El test
/// `every_handler_reads_the_right_path_segment` lo verifica contra
/// `rutas.toml`.
pub(crate) fn path_segment(request: &Request<Body>, index: usize) -> &str {
    request
        .uri()
        .path()
        .trim_matches('/')
        .split('/')
        .nth(index)
        .unwrap_or("")
}

async fn dispatch(State(state): State<Arc<HubState>>, request: Request<Body>) -> Response {
    let method = request.method().clone();
    let path = request.uri().path().to_owned();
    let route = state.routes.match_route(&method, &path);

    match route.map(|route| (route.sirve, route.id.clone())) {
        Some((Serve::Rust, id)) => match state.handlers.get(&id).cloned() {
            Some(handler) => handler(request).await,
            None => response::json_value(
                StatusCode::INTERNAL_SERVER_ERROR,
                &response::ErrorEnvelope::new(Fallo::InternalError, "handler Rust no registrado"),
            ),
        },
        // Inalcanzable: la tabla rechaza `sirve = "node"` al arrancar. Se
        // conserva el brazo para que quitar la variante sea una decision
        // explicita y no un descuido del compilador.
        Some((Serve::Node, id)) => response::json_value(
            StatusCode::INTERNAL_SERVER_ERROR,
            &response::ErrorEnvelope::new(
                Fallo::InternalError,
                format!("la ruta {id} quedo marcada para un fallback que ya no existe"),
            ),
        ),
        // Sin upstream, una ruta fuera de la tabla es un 404 del contrato, no un
        // 502. Se sigue contando: si `no_inventariada` sube, la tabla quedo
        // corta y `/__hub/rutas` dice exactamente donde.
        None => {
            state.registrar_no_inventariada(&method, &path);
            response::json_value(
                StatusCode::NOT_FOUND,
                &response::ErrorEnvelope::new(Fallo::NotFound, "recurso o ruta inexistente"),
            )
        }
    }
}
