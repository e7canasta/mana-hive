use diesel::result::{DatabaseErrorKind, Error as DieselError};
use thiserror::Error;

use crate::{notas, rondas};

#[derive(Debug, Error)]
pub enum CuidadoError {
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
    Rondas(#[from] rondas::RondasError),
    #[error(transparent)]
    Notas(#[from] notas::NotasError),
}

impl CuidadoError {
    pub(crate) fn database(error: DieselError) -> Self {
        match error {
            DieselError::DatabaseError(DatabaseErrorKind::UniqueViolation, _) => Self::Conflict,
            other => Self::Database(other),
        }
    }
}
