use diesel::result::{DatabaseErrorKind, Error as DieselError};
use thiserror::Error;

use crate::{cobertura, grupos, turnos};

#[derive(Debug, Error)]
pub enum CoberturaError {
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
    #[error("el grupo pertenece a otra facility que el ala")]
    CrossFacility,
    #[error("la facility no tiene turnos definidos")]
    NoShifts,
    #[error(transparent)]
    Grupos(#[from] grupos::GruposError),
    #[error(transparent)]
    Turnos(#[from] turnos::TurnosError),
    #[error(transparent)]
    Cobertura(#[from] cobertura::CoberturaDomainError),
}

impl CoberturaError {
    pub(crate) fn database(error: DieselError) -> Self {
        match error {
            DieselError::DatabaseError(DatabaseErrorKind::UniqueViolation, _) => Self::Conflict,
            other => Self::Database(other),
        }
    }
}
