//! mana-hub-client: HTTP client shared by workers to communicate with Hub.
//!
//! Workers are stateless and persist via Hub API. This crate provides
//! the shared client used by sentinel and vigilancia workers.

use std::time::Duration;

use serde::{Deserialize, Serialize};
use thiserror::Error;
use tracing::warn;

const MAX_RETRIES: u32 = 3;
const BASE_DELAY_MS: u64 = 100;

#[derive(Debug, Error)]
pub enum HubClientError {
    #[error("HTTP request failed: {0}")]
    Http(#[from] reqwest::Error),
    #[error("Serialization failed: {0}")]
    Serialization(String),
}

/// Client for Hub HTTP API with automatic retry.
#[derive(Clone)]
pub struct HubClient {
    base_url: String,
    client: reqwest::Client,
}

impl HubClient {
    pub fn new(base_url: &str) -> Self {
        Self {
            base_url: base_url.to_string(),
            client: reqwest::Client::new(),
        }
    }

    async fn retry<F, Fut, T>(&self, name: &str, mut f: F) -> Result<T, HubClientError>
    where
        F: FnMut() -> Fut,
        Fut: std::future::Future<Output = Result<T, reqwest::Error>>,
    {
        let mut last_err = None;
        for attempt in 0..MAX_RETRIES {
            match f().await {
                Ok(val) => return Ok(val),
                Err(e) => {
                    warn!(
                        error = %e,
                        operation = name,
                        attempt = attempt + 1,
                        max_retries = MAX_RETRIES,
                        "Hub API call failed, retrying"
                    );
                    last_err = Some(e);
                    let delay = Duration::from_millis(BASE_DELAY_MS * 2u64.pow(attempt));
                    tokio::time::sleep(delay).await;
                }
            }
        }
        Err(HubClientError::Http(last_err.unwrap()))
    }

    // === Incidents ===

    pub async fn create_incident(
        &self,
        input: &CreateIncidentRequest,
    ) -> Result<IncidentResponse, HubClientError> {
        let url = format!("{}/api/v1/incidents", self.base_url);
        let input = input.clone();
        self.retry("create_incident", || {
            let url = url.clone();
            let input = input.clone();
            let client = self.client.clone();
            async move { client.post(&url).json(&input).send().await?.json().await }
        }).await
    }

    pub async fn patch_incident(
        &self,
        id: &str,
        input: &PatchIncidentRequest,
    ) -> Result<IncidentResponse, HubClientError> {
        let url = format!("{}/api/v1/incidents/{}", self.base_url, id);
        let input = input.clone();
        self.retry("patch_incident", || {
            let url = url.clone();
            let input = input.clone();
            let client = self.client.clone();
            async move { client.patch(&url).json(&input).send().await?.json().await }
        }).await
    }

    // === Alerts ===

    pub async fn create_alert(
        &self,
        input: &CreateAlertRequest,
    ) -> Result<AlertResponse, HubClientError> {
        let url = format!("{}/api/v1/alerts", self.base_url);
        let input = input.clone();
        self.retry("create_alert", || {
            let url = url.clone();
            let input = input.clone();
            let client = self.client.clone();
            async move { client.post(&url).json(&input).send().await?.json().await }
        }).await
    }

    pub async fn patch_alert(
        &self,
        id: &str,
        input: &PatchAlertRequest,
    ) -> Result<AlertResponse, HubClientError> {
        let url = format!("{}/api/v1/alerts/{}", self.base_url, id);
        let input = input.clone();
        self.retry("patch_alert", || {
            let url = url.clone();
            let input = input.clone();
            let client = self.client.clone();
            async move { client.patch(&url).json(&input).send().await?.json().await }
        }).await
    }
}

// === Request/Response types ===

#[derive(Debug, Clone, Serialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub enum IncidentStatus {
    CollectingEvidence,
    WaitingForStaff,
    StaffOnSite,
    ClosingIncident,
    Closed,
}

impl IncidentStatus {
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::CollectingEvidence => "collecting_evidence",
            Self::WaitingForStaff => "waiting_for_staff",
            Self::StaffOnSite => "staff_on_site",
            Self::ClosingIncident => "closing_incident",
            Self::Closed => "closed",
        }
    }
}

#[derive(Debug, Clone, Serialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub enum AlertLevel {
    Low,
    Medium,
    High,
    Critical,
}

impl AlertLevel {
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::Low => "low",
            Self::Medium => "medium",
            Self::High => "high",
            Self::Critical => "critical",
        }
    }
}

#[derive(Debug, Clone, Serialize)]
pub struct CreateIncidentRequest {
    pub bed_id: String,
    pub resident_id: Option<String>,
    pub preset: String,
    pub status: IncidentStatus,
}

#[derive(Debug, Clone, Serialize)]
pub struct PatchIncidentRequest {
    pub status: IncidentStatus,
    pub staff_id: Option<String>,
}

#[derive(Debug, Clone, Serialize)]
pub struct CreateAlertRequest {
    pub bed_id: String,
    pub resident_id: Option<String>,
    pub rule_id: String,
    pub level: AlertLevel,
    pub title: String,
    pub detail: Option<String>,
}

#[derive(Debug, Clone, Serialize)]
pub struct PatchAlertRequest {
    pub to_status: String,
}

#[derive(Debug, Clone, Deserialize)]
pub struct IncidentResponse {
    pub incident: serde_json::Value,
}

#[derive(Debug, Clone, Deserialize)]
pub struct AlertResponse {
    pub alert: serde_json::Value,
}
