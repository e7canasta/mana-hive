use std::collections::BTreeMap;

use ctx_auditoria::{AuditEntry, AuditFilter, AuditStore};
use ctx_identidad::IdentityStore;
use mana_kernel::Fallo;
use serde_json::Value;

use crate::{
    error::AppFailure,
    identidad::{authenticated_actor, require_capability, required_token},
    state::AppState,
};

#[derive(Clone, Debug, Default)]
pub struct AuditQuery {
    pub limit: Option<usize>,
    pub entity_type: Option<String>,
    pub entity_id: Option<String>,
    pub action: Option<String>,
}

#[derive(Clone, Debug)]
pub struct AuditEntryView {
    pub id: String,
    pub actor_id: Option<String>,
    pub actor_name: Option<String>,
    pub action: String,
    pub entity_type: String,
    pub entity_id: String,
    pub metadata: Value,
    pub created_at: String,
}

impl AppState {
    pub async fn list_audit(
        &self,
        token: &str,
        query: AuditQuery,
    ) -> Result<Vec<AuditEntryView>, AppFailure> {
        let token = required_token(token)?;
        let enabled = self.enabled_capabilities.clone();
        run_audit_blocking(
            self.identity.clone(),
            self.audit.clone(),
            move |identity, audit| {
                let actor = authenticated_actor(&identity, &token, &enabled)?;
                require_capability(&actor, "audit.read")?;
                let filter = AuditFilter {
                    limit: query.limit,
                    entity_type: query.entity_type,
                    entity_id: query.entity_id,
                    action: query.action,
                };
                let entries = audit.list(&filter).map_err(AppFailure::from)?;
                let actors = identity
                    .list_users(true)
                    .map_err(AppFailure::from)?
                    .into_iter()
                    .map(|user| {
                        (
                            user.id.as_str().to_owned(),
                            user.display_name.as_str().to_owned(),
                        )
                    })
                    .collect::<BTreeMap<_, _>>();
                Ok(entries
                    .into_iter()
                    .map(|entry| audit_view(entry, &actors))
                    .collect())
            },
        )
        .await
    }
}

async fn run_audit_blocking<T, F>(
    identity: IdentityStore,
    audit: AuditStore,
    operation: F,
) -> Result<T, AppFailure>
where
    T: Send + 'static,
    F: FnOnce(IdentityStore, AuditStore) -> Result<T, AppFailure> + Send + 'static,
{
    tokio::task::spawn_blocking(move || operation(identity, audit))
        .await
        .map_err(|error| {
            tracing::error!(error = %error, "tarea SQLite abortada");
            AppFailure::new(Fallo::InternalError, "No se pudo completar la operacion")
        })?
}

fn audit_view(entry: AuditEntry, actors: &BTreeMap<String, String>) -> AuditEntryView {
    let actor_id = entry.actor_id.map(|actor| actor.into_string());
    let actor_name = actor_id
        .as_ref()
        .and_then(|actor_id| actors.get(actor_id).cloned());
    AuditEntryView {
        id: entry.id.into_string(),
        actor_id,
        actor_name,
        action: entry.action,
        entity_type: entry.entity_type,
        entity_id: entry.entity_id,
        metadata: entry.metadata,
        created_at: entry.created_at.to_string(),
    }
}
