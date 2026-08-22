use serde::{Deserialize, Serialize};

use crate::transport::{ApiResponse, ManaClient, ManaError};

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct HealthResponse {
    pub ok: bool,
    #[serde(default)]
    pub service: Option<String>,
    #[serde(default)]
    pub database: Option<String>,
}

pub async fn health(client: &ManaClient) -> Result<ApiResponse<HealthResponse>, ManaError> {
    client.request(reqwest::Method::GET, "/health").await
}

impl ManaClient {
    pub async fn health(&self) -> Result<ApiResponse<HealthResponse>, ManaError> {
        health(self).await
    }
}
