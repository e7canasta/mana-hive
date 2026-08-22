use std::collections::HashSet;

use axum::http::Method;
use serde::{Deserialize, Serialize};
use thiserror::Error;

const ROUTES_SOURCE: &str = include_str!("../../../rutas.toml");

/// Destino declarado para una entrada de la tabla.
#[derive(Clone, Copy, Debug, Deserialize, Eq, PartialEq, Serialize)]
#[serde(rename_all = "lowercase")]
pub enum Serve {
    Node,
    Rust,
}

/// Una entrada de `hub/rutas.toml`.
#[derive(Clone, Debug, Deserialize, Eq, PartialEq, Serialize)]
pub struct Route {
    pub id: String,
    pub metodo: String,
    pub patron: String,
    #[serde(default)]
    pub contexto: String,
    pub sirve: Serve,
}

impl Route {
    pub fn method(&self) -> &str {
        &self.metodo
    }

    pub fn pattern(&self) -> &str {
        &self.patron
    }

    pub fn matches(&self, method: &str, path: &str) -> bool {
        self.metodo.eq_ignore_ascii_case(method) && pattern_score(&self.patron, path).is_some()
    }
}

#[derive(Debug, Deserialize)]
struct RoutesDocument {
    #[serde(default)]
    ruta: Vec<Route>,
}

/// Inventario validado de rutas de negocio.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct RouteTable {
    routes: Vec<Route>,
}

/// Nombre alternativo en castellano para consumidores del contrato.
pub type Rutas = RouteTable;
/// Nombre singular del registro de rutas.
pub type Ruta = Route;

#[derive(Debug, Error)]
pub enum RouteTableError {
    #[error("rutas.toml no se pudo parsear: {0}")]
    Parse(#[from] toml::de::Error),
    #[error("la ruta {index} no tiene id")]
    EmptyId { index: usize },
    #[error("la ruta {id} no tiene metodo HTTP valido: {method}")]
    InvalidMethod { id: String, method: String },
    #[error("la ruta {id} no tiene patron absoluto: {pattern}")]
    InvalidPattern { id: String, pattern: String },
    #[error(
        "la ruta {id} declara sirve = \"node\": el fallback a Node se retiro en F9 y ya no hay upstream"
    )]
    NodeFallbackRetired { id: String },
    #[error("la ruta {id} repite metodo y patron de otra entrada: {method} {pattern}")]
    Duplicate {
        id: String,
        method: String,
        pattern: String,
    },
    #[error("la ruta Rust {id} ({method} {pattern}) no tiene handler registrado")]
    MissingRustHandler {
        id: String,
        method: String,
        pattern: String,
    },
}

impl RouteTable {
    pub fn parse(source: &str) -> Result<Self, RouteTableError> {
        let document: RoutesDocument = toml::from_str(source)?;
        let mut ids = HashSet::new();
        let mut method_patterns = HashSet::new();
        let mut routes = Vec::with_capacity(document.ruta.len());

        for (index, mut route) in document.ruta.into_iter().enumerate() {
            if route.id.trim().is_empty() {
                return Err(RouteTableError::EmptyId { index });
            }
            route.id = route.id.trim().to_owned();
            route.metodo = route.metodo.trim().to_ascii_uppercase();
            route.patron = route.patron.trim().to_owned();
            route.contexto = route.contexto.trim().to_owned();

            if Method::from_bytes(route.metodo.as_bytes()).is_err() {
                return Err(RouteTableError::InvalidMethod {
                    id: route.id,
                    method: route.metodo,
                });
            }
            // `*` es el comodin de path: Node atiende OPTIONS de cualquier ruta
            // antes que el router (api/server.js:409), y eso no se puede
            // expresar enumerando patrones.
            if (!route.patron.starts_with('/') && route.patron != "*") || route.patron.contains('?')
            {
                return Err(RouteTableError::InvalidPattern {
                    id: route.id,
                    pattern: route.patron,
                });
            }
            if route.sirve == Serve::Node {
                return Err(RouteTableError::NodeFallbackRetired { id: route.id });
            }
            if !ids.insert(route.id.clone()) {
                return Err(RouteTableError::Duplicate {
                    id: route.id,
                    method: route.metodo,
                    pattern: route.patron,
                });
            }
            let key = (route.metodo.clone(), route.patron.clone());
            if !method_patterns.insert(key) {
                return Err(RouteTableError::Duplicate {
                    id: route.id,
                    method: route.metodo,
                    pattern: route.patron,
                });
            }
            routes.push(route);
        }

        Ok(Self { routes })
    }

    pub fn embedded() -> Result<Self, RouteTableError> {
        Self::parse(ROUTES_SOURCE)
    }

    pub fn source() -> &'static str {
        ROUTES_SOURCE
    }

    pub fn routes(&self) -> &[Route] {
        &self.routes
    }

    pub fn total(&self) -> usize {
        self.routes.len()
    }

    pub fn node(&self) -> usize {
        self.routes
            .iter()
            .filter(|route| route.sirve == Serve::Node)
            .count()
    }

    pub fn rust(&self) -> usize {
        self.routes
            .iter()
            .filter(|route| route.sirve == Serve::Rust)
            .count()
    }

    /// Selecciona la ruta mas especifica que coincide con metodo y path.
    pub fn find(&self, method: &str, path: &str) -> Option<&Route> {
        self.routes
            .iter()
            .filter_map(|route| {
                if !route.metodo.eq_ignore_ascii_case(method) {
                    return None;
                }
                pattern_score(&route.patron, path).map(|score| (score, route))
            })
            .max_by_key(|(score, _)| *score)
            .map(|(_, route)| route)
    }

    pub fn match_route(&self, method: &Method, path: &str) -> Option<&Route> {
        self.find(method.as_str(), path)
    }

    pub fn validate_handlers(
        &self,
        registered_handler_ids: &HashSet<String>,
    ) -> Result<(), RouteTableError> {
        for route in self
            .routes
            .iter()
            .filter(|route| route.sirve == Serve::Rust)
        {
            if !registered_handler_ids.contains(&route.id) {
                return Err(RouteTableError::MissingRustHandler {
                    id: route.id.clone(),
                    method: route.metodo.clone(),
                    pattern: route.patron.clone(),
                });
            }
        }
        Ok(())
    }
}

impl std::str::FromStr for RouteTable {
    type Err = RouteTableError;

    fn from_str(source: &str) -> Result<Self, Self::Err> {
        Self::parse(source)
    }
}

fn pattern_score(pattern: &str, path: &str) -> Option<usize> {
    // El comodin puntua 0: cualquier patron con un segmento literal le gana.
    if pattern == "*" {
        return Some(0);
    }
    let pattern_parts: Vec<_> = pattern.split('/').skip(1).collect();
    let path_parts: Vec<_> = path.split('/').skip(1).collect();
    if pattern_parts.len() != path_parts.len() {
        return None;
    }

    let mut literal_parts = 0;
    for (pattern_part, path_part) in pattern_parts.iter().zip(path_parts) {
        if pattern_part.starts_with(':') {
            if pattern_part.len() == 1 || path_part.is_empty() {
                return None;
            }
        } else if pattern_part != &path_part {
            return None;
        } else {
            literal_parts += 1;
        }
    }
    Some(literal_parts)
}
