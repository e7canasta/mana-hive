use reqwest::Method;
use serde::{Deserialize, Serialize};

use crate::poblacion::path;
use crate::transport::{ApiResponse, ManaClient, ManaError};

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct Shift {
    pub id: String,
    pub key: String,
    pub label: String,
    pub start_minute: i32,
    pub sort_order: i32,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct ShiftGridResponse {
    pub facility_id: String,
    pub shifts: Vec<Shift>,
    pub coverages_cleared: i64,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct ReplaceShiftGridRequest {
    pub shifts: Vec<ShiftEntry>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct ShiftEntry {
    pub key: String,
    pub label: String,
    pub start_minute: i32,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct StaffGroup {
    pub id: String,
    pub facility_id: String,
    pub name: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub retired_at: Option<String>,
    pub created_at: String,
    pub updated_at: String,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct StaffGroupDetail {
    pub id: String,
    pub facility_id: String,
    pub name: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub retired_at: Option<String>,
    pub created_at: String,
    pub updated_at: String,
    #[serde(default)]
    pub members: Vec<Member>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct Member {
    pub id: String,
    pub staff_group_id: String,
    pub user_id: String,
    pub valid_from: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub valid_to: Option<String>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct GroupsResponse {
    pub groups: Vec<StaffGroup>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct GroupResponse {
    pub group: StaffGroupDetail,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct MembersResponse {
    pub members: Vec<Member>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct CreateGroupRequest {
    pub facility_id: String,
    pub name: String,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct UpdateGroupRequest {
    #[serde(skip_serializing_if = "Option::is_none")]
    pub name: Option<String>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct ReplaceMembersRequest {
    pub members: Vec<MemberEntry>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct MemberEntry {
    pub user_id: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub valid_from: Option<String>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct WingCoverage {
    pub id: String,
    pub wing_id: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub staff_group_id: Option<String>,
    pub shift_key: String,
    pub valid_from: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub valid_to: Option<String>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct CoverageResponse {
    pub coverages: Vec<WingCoverage>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct SingleCoverageResponse {
    pub coverage: WingCoverage,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct AssignCoverageRequest {
    #[serde(skip_serializing_if = "Option::is_none")]
    pub staff_group_id: Option<String>,
    pub shift_key: String,
}

impl ManaClient {
    // -- Shifts --

    pub async fn get_shift_grid(
        &self,
        facility_id: &str,
    ) -> Result<ApiResponse<ShiftGridResponse>, ManaError> {
        self.request(
            Method::GET,
            &(path("/api/v1/facilities", facility_id)? + "/shifts"),
        )
        .await
    }

    pub async fn replace_shift_grid(
        &self,
        facility_id: &str,
        request: ReplaceShiftGridRequest,
    ) -> Result<ApiResponse<ShiftGridResponse>, ManaError> {
        self.request_json(
            Method::PUT,
            &(path("/api/v1/facilities", facility_id)? + "/shifts"),
            request,
        )
        .await
    }

    // -- Groups --

    pub async fn list_staff_groups(
        &self,
        facility_id: &str,
    ) -> Result<ApiResponse<GroupsResponse>, ManaError> {
        self.request(
            Method::GET,
            &format!(
                "/api/v1/staff-groups?facility_id={}",
                url::form_urlencoded::byte_serialize(facility_id.as_bytes()).collect::<String>()
            ),
        )
        .await
    }

    pub async fn staff_group(
        &self,
        group_id: &str,
    ) -> Result<ApiResponse<GroupResponse>, ManaError> {
        self.request(Method::GET, &path("/api/v1/staff-groups", group_id)?)
            .await
    }

    pub async fn create_staff_group(
        &self,
        request: CreateGroupRequest,
    ) -> Result<ApiResponse<GroupResponse>, ManaError> {
        self.request_json(Method::POST, "/api/v1/staff-groups", request)
            .await
    }

    pub async fn update_staff_group(
        &self,
        group_id: &str,
        request: UpdateGroupRequest,
    ) -> Result<ApiResponse<GroupResponse>, ManaError> {
        self.request_json(
            Method::PATCH,
            &path("/api/v1/staff-groups", group_id)?,
            request,
        )
        .await
    }

    pub async fn replace_members(
        &self,
        group_id: &str,
        request: ReplaceMembersRequest,
    ) -> Result<ApiResponse<MembersResponse>, ManaError> {
        self.request_json(
            Method::PUT,
            &(path("/api/v1/staff-groups", group_id)? + "/members"),
            request,
        )
        .await
    }

    // -- Coverage --

    pub async fn get_wing_coverage(
        &self,
        wing_id: &str,
        at: Option<&str>,
    ) -> Result<ApiResponse<CoverageResponse>, ManaError> {
        let base = path("/api/v1/wings", wing_id)? + "/coverage";
        let url = match at {
            Some(at) => format!(
                "{}?at={}",
                base,
                url::form_urlencoded::byte_serialize(at.as_bytes()).collect::<String>()
            ),
            None => base,
        };
        self.request(Method::GET, &url).await
    }

    pub async fn assign_wing_coverage(
        &self,
        wing_id: &str,
        request: AssignCoverageRequest,
    ) -> Result<ApiResponse<SingleCoverageResponse>, ManaError> {
        self.request_json(
            Method::PUT,
            &(path("/api/v1/wings", wing_id)? + "/coverage"),
            request,
        )
        .await
    }

    pub async fn clear_wing_coverage(
        &self,
        wing_id: &str,
        shift_key: &str,
    ) -> Result<ApiResponse<SingleCoverageResponse>, ManaError> {
        // DELETE is not in the spec; use PUT with null staff_group_id
        self.request_json(
            Method::PUT,
            &(path("/api/v1/wings", wing_id)? + "/coverage"),
            AssignCoverageRequest {
                staff_group_id: None,
                shift_key: shift_key.to_owned(),
            },
        )
        .await
    }
}
