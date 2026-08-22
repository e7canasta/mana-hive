use diesel::result::{DatabaseErrorKind, Error as DieselError};
use thiserror::Error;

use crate::{asignaciones, atributos, residentes};

#[derive(Debug, Error)]
pub enum PoblacionError {
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
    Residentes(#[from] residentes::ResidentesError),
    #[error(transparent)]
    Asignaciones(#[from] asignaciones::AsignacionesError),
    #[error(transparent)]
    Atributos(#[from] atributos::AtributosError),
}

impl PoblacionError {
    pub(crate) fn database(error: DieselError) -> Self {
        match error {
            DieselError::DatabaseError(DatabaseErrorKind::UniqueViolation, _) => Self::Conflict,
            other => Self::Database(other),
        }
    }
}
