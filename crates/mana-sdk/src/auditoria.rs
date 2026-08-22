use serde::{Deserialize, Serialize};
use serde_json::Value;
use url::form_urlencoded;

use crate::transport::{ApiResponse, ManaClient, ManaError};

#[derive(Clone, Debug, Default, Deserialize, Serialize)]
pub struct AuditQuery {
    pub limit: Option<usize>,
    pub entity_type: Option<String>,
    pub entity_id: Option<String>,
    pub action: Option<String>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct AuditEntry {
    pub id: String,
    pub actor_id: Option<String>,
    pub actor_name: Option<String>,
    pub action: String,
    pub entity_type: String,
    pub entity_id: String,
    pub metadata: Value,
    pub created_at: String,
}

#[derive(Clone, Debug, Default, Deserialize, Serialize)]
pub struct AuditResponse {
    #[serde(default)]
    pub audit: Vec<AuditEntry>,
}

impl ManaClient {
    pub async fn list_audit(
        &self,
        query: AuditQuery,
    ) -> Result<ApiResponse<AuditResponse>, ManaError> {
        let mut serializer = form_urlencoded::Serializer::new(String::new());
        if let Some(limit) = query.limit {
            serializer.append_pair("limit", &limit.to_string());
        }
        if let Some(entity_type) = query.entity_type {
            serializer.append_pair("entity_type", &entity_type);
        }
        if let Some(entity_id) = query.entity_id {
            serializer.append_pair("entity_id", &entity_id);
        }
        if let Some(action) = query.action {
            serializer.append_pair("action", &action);
        }
        let query = serializer.finish();
        let path = if query.is_empty() {
            "/api/v1/audit-log".to_owned()
        } else {
            format!("/api/v1/audit-log?{query}")
        };
        self.request(reqwest::Method::GET, &path).await
    }

    pub async fn audit_log(
        &self,
        entity_type: impl Into<String>,
        entity_id: impl Into<String>,
    ) -> Result<ApiResponse<AuditResponse>, ManaError> {
        self.list_audit(AuditQuery {
            entity_type: Some(entity_type.into()),
            entity_id: Some(entity_id.into()),
            ..AuditQuery::default()
        })
        .await
    }
}
