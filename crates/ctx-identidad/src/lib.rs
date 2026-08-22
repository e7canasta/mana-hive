//! Usuarios, sesiones y capabilities del Registro.

mod store;

pub mod domain;
pub mod schema;

pub use domain::{
    new_user_id, AuthenticatedUser, Capability, ClearSessionToken, CreateUserInput, DisplayName,
    Feature, JobTitle, LoginSession, PasswordHash, Role, TokenHash, UpdateUserInput, User, UserId,
    Username,
};
pub use mana_storage::DbPool;
pub use store::{run_migrations, IdentityStore};

use diesel::result::{DatabaseErrorKind, Error as DieselError};
use thiserror::Error;

/// Errores que cruzan el limite del contexto de identidad.
#[derive(Debug, Error)]
pub enum IdentityError {
    #[error(transparent)]
    Storage(#[from] mana_storage::StorageError),
    #[error("error de persistencia: {0}")]
    Database(#[from] DieselError),
    #[error("dato persistido invalido: {0}")]
    InvalidStoredData(String),
    #[error("el usuario ya existe")]
    Conflict,
    #[error("usuario no encontrado")]
    NotFound,
    #[error(transparent)]
    Domain(#[from] domain::DomainError),
}

impl IdentityError {
    pub(crate) fn database(error: DieselError) -> Self {
        match error {
            DieselError::DatabaseError(DatabaseErrorKind::UniqueViolation, _) => Self::Conflict,
            other => Self::Database(other),
        }
    }
}
