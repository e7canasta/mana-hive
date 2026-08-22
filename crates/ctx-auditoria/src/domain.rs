use base64::{engine::general_purpose::URL_SAFE_NO_PAD, Engine as _};
use mana_kernel::{define_kinds, Actor, Id, Instante};
use rand::RngExt;
use serde_json::Value;
use thiserror::Error;

define_kinds!(AuditEntryKind);

pub type AuditEntryId = Id<AuditEntryKind>;

pub const DEFAULT_LIMIT: usize = 100;
pub const MAX_LIMIT: usize = 500;
const MAX_METADATA_BYTES: usize = 16 * 1024;
const MAX_LABEL_LENGTH: usize = 160;

#[derive(Clone, Debug, Eq, PartialEq, Error)]
pub enum AuditDomainError {
    #[error("action no puede estar vacio")]
    EmptyAction,
    #[error("entity_type no puede estar vacio")]
    EmptyEntityType,
    #[error("entity_id no puede estar vacio")]
    EmptyEntityId,
    #[error("el valor de auditoria excede la longitud maxima")]
    LabelTooLong,
    #[error("metadata de auditoria invalida")]
    InvalidMetadata,
    #[error("metadata de auditoria excede el limite de 16 KiB")]
    MetadataTooLarge,
    #[error("no se pudo generar el ID de auditoria")]
    Randomness,
}

/// Comando de escritura. El timestamp no pertenece al input: lo asigna el
/// store al cruzar la frontera de persistencia.
#[derive(Clone, Debug)]
pub struct AuditRecord {
    pub(crate) actor_id: Option<Id<Actor>>,
    pub(crate) action: String,
    pub(crate) entity_type: String,
    pub(crate) entity_id: String,
    pub(crate) metadata: Value,
}

impl AuditRecord {
    pub fn new(
        actor_id: Option<Id<Actor>>,
        action: impl Into<String>,
        entity_type: impl Into<String>,
        entity_id: impl Into<String>,
        metadata: Value,
    ) -> Result<Self, AuditDomainError> {
        let action = label(action.into(), AuditDomainError::EmptyAction)?;
        let entity_type = label(entity_type.into(), AuditDomainError::EmptyEntityType)?;
        let entity_id = label(entity_id.into(), AuditDomainError::EmptyEntityId)?;
        if !metadata.is_object() {
            return Err(AuditDomainError::InvalidMetadata);
        }
        let metadata_size = serde_json::to_vec(&metadata)
            .map_err(|_| AuditDomainError::InvalidMetadata)?
            .len();
        if metadata_size > MAX_METADATA_BYTES {
            return Err(AuditDomainError::MetadataTooLarge);
        }
        Ok(Self {
            actor_id,
            action,
            entity_type,
            entity_id,
            metadata,
        })
    }
}

#[derive(Clone, Debug)]
pub struct AuditEntry {
    pub id: AuditEntryId,
    pub actor_id: Option<Id<Actor>>,
    pub action: String,
    pub entity_type: String,
    pub entity_id: String,
    pub metadata: Value,
    pub created_at: Instante,
}

#[derive(Clone, Debug, Default)]
pub struct AuditFilter {
    pub limit: Option<usize>,
    pub entity_type: Option<String>,
    pub entity_id: Option<String>,
    pub action: Option<String>,
}

impl AuditFilter {
    pub fn effective_limit(&self) -> usize {
        self.limit
            .filter(|limit| *limit > 0)
            .unwrap_or(DEFAULT_LIMIT)
            .min(MAX_LIMIT)
    }
}

pub fn new_audit_id() -> Result<AuditEntryId, AuditDomainError> {
    let mut bytes = [0_u8; 16];
    rand::rng().fill(&mut bytes);
    Ok(Id::new(format!("audit-{}", URL_SAFE_NO_PAD.encode(bytes))))
}

fn label(value: String, empty: AuditDomainError) -> Result<String, AuditDomainError> {
    let value = value.trim().to_owned();
    if value.is_empty() {
        return Err(empty);
    }
    if value.chars().count() > MAX_LABEL_LENGTH {
        return Err(AuditDomainError::LabelTooLong);
    }
    Ok(value)
}
