use mana_storage::StorageError;

#[derive(Debug, thiserror::Error)]
pub enum VigilanciaError {
    #[error("conflicto: {0}")]
    Conflict(String),
    #[error("no encontrado: {0}")]
    NotFound(String),
    #[error("error de validacion: {0}")]
    Validation(String),
    #[error(transparent)]
    Storage(#[from] StorageError),
    #[error(transparent)]
    Diesel(#[from] diesel::result::Error),
}

impl VigilanciaError {
    pub fn conflict(msg: impl Into<String>) -> Self {
        Self::Conflict(msg.into())
    }

    pub fn not_found(msg: impl Into<String>) -> Self {
        Self::NotFound(msg.into())
    }

    pub fn validation(msg: impl Into<String>) -> Self {
        Self::Validation(msg.into())
    }
}
