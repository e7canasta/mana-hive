use reqwest::Method;
use serde::{Deserialize, Serialize};

use crate::transport::{ApiResponse, ManaClient, ManaError};

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct ResidentRecord {
    pub id: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub external_id: Option<String>,
    pub full_name: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub birth_date: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub admission_date: Option<String>,
    pub status: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub discharged_at: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub discharged_by: Option<String>,
    pub created_at: String,
    pub updated_at: String,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct RoomRef {
    pub id: String,
    pub number: String,
    pub wing_id: String,
    pub wing_name: String,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct ResidentListItem {
    pub id: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub external_id: Option<String>,
    pub full_name: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub birth_date: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub admission_date: Option<String>,
    pub status: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub discharged_at: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub room: Option<RoomRef>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub bed_id: Option<String>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct ResidentsResponse {
    #[serde(default)]
    pub residents: Vec<ResidentListItem>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct ResidentResponse {
    pub resident: ResidentRecord,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct BedAssignment {
    pub id: String,
    pub resident_id: String,
    pub bed_id: String,
    pub starts_at: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub ends_at: Option<String>,
    pub created_at: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub created_by: Option<String>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct AssignmentsResponse {
    #[serde(default)]
    pub assignments: Vec<BedAssignment>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct AssignmentResponse {
    pub assignment: BedAssignment,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct CreateResidentRequest {
    #[serde(skip_serializing_if = "Option::is_none")]
    pub full_name: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub external_id: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub birth_date: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub admission_date: Option<String>,
}

#[derive(Clone, Debug, Deserialize, Serialize, Default)]
pub struct UpdateResidentRequest {
    #[serde(skip_serializing_if = "Option::is_none")]
    pub full_name: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub external_id: Option<Option<String>>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub birth_date: Option<Option<String>>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub admission_date: Option<Option<String>>,
}

#[derive(Clone, Debug, Deserialize, Serialize, Default)]
pub struct DischargeRequest {
    #[serde(skip_serializing_if = "Option::is_none")]
    pub discharged_at: Option<String>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct AssignBedRequest {
    pub bed_id: String,
}

impl ManaClient {
    pub async fn list_residents(
        &self,
        query: Option<&str>,
    ) -> Result<ApiResponse<ResidentsResponse>, ManaError> {
        let path = match query {
            Some(query) => format!(
                "/api/v1/residents?q={}",
                url::form_urlencoded::byte_serialize(query.as_bytes()).collect::<String>()
            ),
            None => "/api/v1/residents".to_owned(),
        };
        self.request(Method::GET, &path).await
    }

    pub async fn resident(
        &self,
        resident_id: &str,
    ) -> Result<ApiResponse<ResidentResponse>, ManaError> {
        self.request(Method::GET, &path("/api/v1/residents", resident_id)?)
            .await
    }

    pub async fn create_resident(
        &self,
        request: CreateResidentRequest,
    ) -> Result<ApiResponse<ResidentResponse>, ManaError> {
        self.request_json(Method::POST, "/api/v1/residents", request)
            .await
    }

    pub async fn update_resident(
        &self,
        resident_id: &str,
        request: UpdateResidentRequest,
    ) -> Result<ApiResponse<ResidentResponse>, ManaError> {
        self.request_json(
            Method::PATCH,
            &path("/api/v1/residents", resident_id)?,
            request,
        )
        .await
    }

    pub async fn discharge_resident(
        &self,
        resident_id: &str,
        request: DischargeRequest,
    ) -> Result<ApiResponse<ResidentResponse>, ManaError> {
        self.request_json(
            Method::POST,
            &(path("/api/v1/residents", resident_id)? + "/discharge"),
            request,
        )
        .await
    }

    pub async fn list_assignments(
        &self,
        resident_id: &str,
    ) -> Result<ApiResponse<AssignmentsResponse>, ManaError> {
        self.request(
            Method::GET,
            &(path("/api/v1/residents", resident_id)? + "/assignments"),
        )
        .await
    }

    pub async fn assign_bed(
        &self,
        resident_id: &str,
        request: AssignBedRequest,
    ) -> Result<ApiResponse<AssignmentResponse>, ManaError> {
        self.request_json(
            Method::POST,
            &(path("/api/v1/residents", resident_id)? + "/assignments"),
            request,
        )
        .await
    }

    pub async fn release_bed(
        &self,
        bed_id: &str,
    ) -> Result<ApiResponse<AssignmentResponse>, ManaError> {
        self.request(
            Method::DELETE,
            &(path("/api/v1/beds", bed_id)? + "/assignment"),
        )
        .await
    }
}

pub(crate) fn path(prefix: &str, id: &str) -> Result<String, ManaError> {
    if id.trim().is_empty() || id.contains(['/', '?', '#']) {
        return Err(ManaError::InvalidPath(id.to_owned()));
    }
    Ok(format!(
        "{prefix}/{}",
        url::form_urlencoded::byte_serialize(id.as_bytes()).collect::<String>()
    ))
}
