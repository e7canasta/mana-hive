use mana_kernel::Instante;
use mana_storage::StorageError;

use crate::catalogo::CatalogError;

#[derive(Debug, thiserror::Error)]
pub enum PoliticaError {
    #[error("conflicto: {0}")]
    Conflict(String),
    #[error("no encontrado: {0}")]
    NotFound(String),
    #[error("error de validacion: {0}")]
    Validation(String),
    #[error("error de catalogo: {0}")]
    Catalogo(#[from] CatalogError),
    #[error(transparent)]
    Storage(#[from] StorageError),
    #[error(transparent)]
    Diesel(#[from] diesel::result::Error),
    #[error(transparent)]
    Parse(#[from] chrono::format::ParseError),
}

impl PoliticaError {
    pub fn conflict(msg: impl Into<String>) -> Self {
        Self::Conflict(msg.into())
    }

    pub fn not_found(msg: impl Into<String>) -> Self {
        Self::NotFound(msg.into())
    }

    pub fn validation(msg: impl Into<String>) -> Self {
        Self::Validation(msg.into())
    }

    pub fn database(&self) -> Option<&StorageError> {
        match self {
            Self::Storage(e) => Some(e),
            _ => None,
        }
    }
}

#[allow(dead_code)]
pub fn random_id() -> String {
    use base64::Engine;
    let bytes: [u8; 16] = rand::random();
    base64::engine::general_purpose::URL_SAFE_NO_PAD.encode(bytes)
}

#[allow(dead_code)]
pub fn parse_instant(value: &str) -> Result<Instante, PoliticaError> {
    value
        .parse::<Instante>()
        .map_err(|_| PoliticaError::validation(format!("invalid instant: {value}")))
}
