//! Traza append-only de mutaciones del Registro.

mod domain;
mod store;

pub mod schema;

pub use domain::{
    new_audit_id, AuditDomainError, AuditEntry, AuditEntryId, AuditFilter, AuditRecord,
};
pub use mana_storage::DbPool;
pub use store::{run_migrations, AuditStore};

use diesel::result::Error as DieselError;
use thiserror::Error;

/// Errores que cruzan el limite del contexto de auditoria.
#[derive(Debug, Error)]
pub enum AuditError {
    #[error(transparent)]
    Storage(#[from] mana_storage::StorageError),
    #[error("error de persistencia: {0}")]
    Database(#[from] DieselError),
    #[error("dato persistido invalido: {0}")]
    InvalidStoredData(String),
    #[error(transparent)]
    Domain(#[from] domain::AuditDomainError),
}
