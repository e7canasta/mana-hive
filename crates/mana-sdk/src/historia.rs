use reqwest::Method;
use serde::{Deserialize, Serialize};

use crate::poblacion::path;
use crate::transport::{ApiResponse, ManaClient, ManaError};

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct Incident {
    pub id: String,
    pub resident_id: String,
    pub occurred_at: String,
    pub detection: DetectionInfo,
    #[serde(default)]
    pub reviews: Vec<ReviewInfo>,
    pub current: CurrentInfo,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct DetectionInfo {
    pub kind: String,
    pub severity: String,
    pub injury_status: String,
    pub source: String,
    pub model_version: String,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct ReviewInfo {
    pub id: String,
    pub status: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub detection_verdict: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub review_note: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub resolved_at: Option<String>,
    pub actor_id: String,
    pub created_at: String,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct CurrentInfo {
    pub status: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub detection_verdict: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub resolved_at: Option<String>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct IngestResponse {
    pub incident: Incident,
    pub duplicate: bool,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct IncidentResponse {
    pub incident: Incident,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct IncidentsResponse {
    pub incidents: Vec<Incident>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct IngestRequest {
    pub source_record_id: String,
    pub resident_id: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub bed_id: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub source_alert_id: Option<String>,
    pub kind: String,
    pub severity: String,
    pub occurred_at: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub location: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub activity: Option<String>,
    #[serde(default)]
    pub injury_status: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub self_recovery: Option<bool>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub response_seconds: Option<i32>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub narrative: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub interventions_json: Option<String>,
    #[serde(default)]
    pub source: String,
    #[serde(default)]
    pub model_version: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub confidence: Option<f64>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub provenance_json: Option<String>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct CreateReviewRequest {
    pub status: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub detection_verdict: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub review_note: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub resolved_at: Option<String>,
}

impl ManaClient {
    pub async fn ingest_incident(
        &self,
        request: IngestRequest,
    ) -> Result<ApiResponse<IngestResponse>, ManaError> {
        self.request_json(Method::POST, "/internal/v1/clinical/incidents", request)
            .await
    }

    pub async fn list_incidents(
        &self,
        resident_id: &str,
    ) -> Result<ApiResponse<IncidentsResponse>, ManaError> {
        let url = path("/api/v1/residents", resident_id)? + "/incidents";
        self.request(Method::GET, &url).await
    }

    pub async fn get_incident(
        &self,
        incident_id: &str,
    ) -> Result<ApiResponse<IncidentResponse>, ManaError> {
        self.request(
            Method::GET,
            &(path("/api/v1/incidents", incident_id)? + "/sequence"),
        )
        .await
    }

    pub async fn create_review(
        &self,
        incident_id: &str,
        request: CreateReviewRequest,
    ) -> Result<ApiResponse<IncidentResponse>, ManaError> {
        self.request_json(
            Method::POST,
            &(path("/api/v1/incidents", incident_id)? + "/reviews"),
            request,
        )
        .await
    }
}
