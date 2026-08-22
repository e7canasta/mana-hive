use diesel::result::{DatabaseErrorKind, Error as DieselError};
use thiserror::Error;

use crate::{estructura, planograma, privacidad};

#[derive(Debug, Error)]
pub enum ResidenceError {
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
    #[error("la habitacion {room_id} no existe o esta retirada")]
    RoomNotFound { room_id: String },
    #[error("la habitacion {room_id} ya esta en el planograma")]
    DuplicatePlanogramRoom { room_id: String },
    #[error(transparent)]
    Estructura(#[from] estructura::EstructuraError),
    #[error(transparent)]
    Planograma(#[from] planograma::PlanogramaError),
    #[error(transparent)]
    Privacidad(#[from] privacidad::PrivacidadError),
}

impl ResidenceError {
    pub(crate) fn database(error: DieselError) -> Self {
        match error {
            DieselError::DatabaseError(DatabaseErrorKind::UniqueViolation, _) => Self::Conflict,
            other => Self::Database(other),
        }
    }
}
