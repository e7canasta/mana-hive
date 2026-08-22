use diesel::result::Error as DieselError;
use thiserror::Error;

#[derive(Debug, Error)]
pub enum ObservationError {
    #[error(transparent)]
    Storage(#[from] mana_storage::StorageError),
    #[error("error de persistencia: {0}")]
    Database(DieselError),
    #[error("dato persistido invalido: {0}")]
    InvalidStoredData(String),
    #[error("dato de entrada invalido: {0}")]
    Validation(String),
    #[error("observacion no encontrada")]
    NotFound,
}

impl From<DieselError> for ObservationError {
    fn from(error: DieselError) -> Self {
        match error {
            DieselError::NotFound => Self::NotFound,
            other => Self::Database(other),
        }
    }
}
