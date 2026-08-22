use diesel::result::{DatabaseErrorKind, Error as DieselError};
use thiserror::Error;

use crate::{detecciones, revisiones};

#[derive(Debug, Error)]
pub enum HistoriaError {
    #[error(transparent)]
    Storage(#[from] mana_storage::StorageError),
    #[error("error de persistencia: {0}")]
    Database(#[from] DieselError),
    #[error("dato persistido invalido: {0}")]
    InvalidStoredData(String),
    #[error("el recurso ya existe")]
    Conflict,
    #[error("recurso no encontrado")]
    NotFound,
    #[error(transparent)]
    Detecciones(#[from] detecciones::DeteccionesError),
    #[error(transparent)]
    Revisiones(#[from] revisiones::RevisionesError),
}

impl HistoriaError {
    pub(crate) fn database(error: DieselError) -> Self {
        match error {
            DieselError::DatabaseError(DatabaseErrorKind::UniqueViolation, _) => Self::Conflict,
            other => Self::Database(other),
        }
    }
}
