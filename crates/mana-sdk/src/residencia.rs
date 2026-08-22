use reqwest::Method;
use serde::{Deserialize, Serialize};

use crate::transport::{ApiResponse, ManaClient, ManaError};

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct Facility {
    pub id: String,
    pub name: String,
    pub timezone: String,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct FacilityDetail {
    pub id: String,
    pub name: String,
    pub timezone: String,
    #[serde(default)]
    pub wings: Vec<Wing>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct TreeResident {
    pub id: String,
    pub name: String,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct TreeBed {
    pub id: String,
    pub label: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub monitor_key: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub resident: Option<TreeResident>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct TreeRegion {
    pub id: String,
    pub region_type: String,
    pub points: Vec<(f64, f64)>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub label: Option<String>,
    pub is_static: bool,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct TreeStream {
    pub id: String,
    pub stream_key: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub name: Option<String>,
    #[serde(default)]
    pub regions: Vec<TreeRegion>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct TreeRoom {
    pub id: String,
    pub number: String,
    #[serde(rename = "type")]
    pub room_type: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub stream_key: Option<String>,
    #[serde(default)]
    pub beds: Vec<TreeBed>,
    #[serde(default)]
    pub streams: Vec<TreeStream>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct TreeWing {
    pub id: String,
    pub name: String,
    pub floor: String,
    pub sort_order: i32,
    #[serde(default)]
    pub rooms: Vec<TreeRoom>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct FacilityTree {
    pub id: String,
    pub name: String,
    pub timezone: String,
    #[serde(default)]
    pub wings: Vec<TreeWing>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct FacilitiesResponse {
    #[serde(default)]
    pub facilities: Vec<Facility>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct FacilityResponse {
    pub facility: Facility,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct Wing {
    pub id: String,
    #[serde(default)]
    pub facility_id: String,
    pub name: String,
    pub floor: String,
    #[serde(default)]
    pub sort_order: i32,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub bed_count: Option<i32>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct WingsResponse {
    #[serde(default)]
    pub wings: Vec<Wing>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct WingResponse {
    pub wing: Wing,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct Room {
    pub id: String,
    pub wing_id: String,
    pub number: String,
    #[serde(rename = "type")]
    pub room_type: String,
    #[serde(default)]
    pub stream_key: Option<String>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct RoomsResponse {
    #[serde(default)]
    pub rooms: Vec<Room>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct RoomResponse {
    pub room: Room,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct Bed {
    pub id: String,
    pub room_id: String,
    pub label: String,
    #[serde(default)]
    pub monitor_key: Option<String>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct BedsResponse {
    #[serde(default)]
    pub beds: Vec<Bed>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct BedResponse {
    pub bed: Bed,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct ResidenceBed {
    pub id: String,
    pub room_id: String,
    pub label: String,
    #[serde(default)]
    pub monitor_key: Option<String>,
    pub room_number: String,
    #[serde(rename = "room_type")]
    pub room_type: String,
    #[serde(default)]
    pub stream_key: Option<String>,
    pub wing_id: String,
    pub wing_name: String,
    pub wing_floor: String,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct ResidenceBedsResponse {
    #[serde(default)]
    pub beds: Vec<ResidenceBed>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct PlanogramPlacement {
    pub id: String,
    pub wing_id: String,
    pub room_id: String,
    pub x: f64,
    pub y: f64,
    pub sort_order: i32,
    pub room_number: String,
    #[serde(rename = "room_type")]
    pub room_type: String,
    #[serde(default)]
    pub stream_key: Option<String>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct PlanogramResponse {
    pub wing_id: String,
    #[serde(default)]
    pub placements: Vec<PlanogramPlacement>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct PrivacyRegion {
    pub x: f64,
    pub y: f64,
    pub w: f64,
    pub h: f64,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct PrivacyRegionsResponse {
    pub room_id: String,
    #[serde(default)]
    pub regions: Vec<PrivacyRegion>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct PlanogramPlacementRequest {
    pub room_id: String,
    pub x: f64,
    pub y: f64,
    pub sort_order: i32,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct SavePlanogramRequest {
    pub placements: Vec<PlanogramPlacementRequest>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct PrivacyRegionRequest {
    pub x: f64,
    pub y: f64,
    pub w: f64,
    pub h: f64,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct SavePrivacyRegionsRequest {
    pub regions: Vec<PrivacyRegionRequest>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct CreateFacilityRequest {
    pub name: String,
    pub timezone: String,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct UpdateFacilityRequest {
    #[serde(skip_serializing_if = "Option::is_none")]
    pub name: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub timezone: Option<String>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct CreateWingRequest {
    pub name: String,
    pub floor: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub sort_order: Option<i32>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct UpdateWingRequest {
    #[serde(skip_serializing_if = "Option::is_none")]
    pub name: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub floor: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub sort_order: Option<i32>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct CreateRoomRequest {
    pub number: String,
    #[serde(rename = "type", skip_serializing_if = "Option::is_none")]
    pub room_type: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub stream_key: Option<String>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct UpdateRoomRequest {
    #[serde(skip_serializing_if = "Option::is_none")]
    pub number: Option<String>,
    #[serde(rename = "type", skip_serializing_if = "Option::is_none")]
    pub room_type: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub stream_key: Option<Option<String>>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct CreateBedRequest {
    pub label: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub monitor_key: Option<String>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct UpdateBedRequest {
    #[serde(skip_serializing_if = "Option::is_none")]
    pub label: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub monitor_key: Option<Option<String>>,
}

impl ManaClient {
    pub async fn list_facilities(&self) -> Result<ApiResponse<FacilitiesResponse>, ManaError> {
        self.request(Method::GET, "/api/v1/facilities").await
    }

    pub async fn facility(
        &self,
        facility_id: &str,
    ) -> Result<ApiResponse<FacilityDetail>, ManaError> {
        self.request(Method::GET, &path("/api/v1/facilities", facility_id)?)
            .await
    }

    pub async fn facility_tree(
        &self,
        facility_id: &str,
    ) -> Result<ApiResponse<FacilityTree>, ManaError> {
        self.request(
            Method::GET,
            &(path("/api/v1/facilities", facility_id)? + "/tree"),
        )
        .await
    }

    pub async fn create_facility(
        &self,
        request: CreateFacilityRequest,
    ) -> Result<ApiResponse<Facility>, ManaError> {
        self.request_json(Method::POST, "/api/v1/facilities", request)
            .await
    }

    pub async fn update_facility(
        &self,
        facility_id: &str,
        request: UpdateFacilityRequest,
    ) -> Result<ApiResponse<FacilityResponse>, ManaError> {
        self.request_json(
            Method::PATCH,
            &path("/api/v1/facilities", facility_id)?,
            request,
        )
        .await
    }

    pub async fn list_wings(&self) -> Result<ApiResponse<WingsResponse>, ManaError> {
        self.request(Method::GET, "/api/v1/wings").await
    }

    pub async fn create_wing(
        &self,
        facility_id: &str,
        request: CreateWingRequest,
    ) -> Result<ApiResponse<Wing>, ManaError> {
        self.request_json(
            Method::POST,
            &(path("/api/v1/facilities", facility_id)? + "/wings"),
            request,
        )
        .await
    }

    pub async fn update_wing(
        &self,
        wing_id: &str,
        request: UpdateWingRequest,
    ) -> Result<ApiResponse<WingResponse>, ManaError> {
        self.request_json(Method::PATCH, &path("/api/v1/wings", wing_id)?, request)
            .await
    }

    pub async fn list_rooms(&self, wing_id: &str) -> Result<ApiResponse<RoomsResponse>, ManaError> {
        self.request(Method::GET, &(path("/api/v1/wings", wing_id)? + "/rooms"))
            .await
    }

    pub async fn create_room(
        &self,
        wing_id: &str,
        request: CreateRoomRequest,
    ) -> Result<ApiResponse<Room>, ManaError> {
        self.request_json(
            Method::POST,
            &(path("/api/v1/wings", wing_id)? + "/rooms"),
            request,
        )
        .await
    }

    pub async fn update_room(
        &self,
        room_id: &str,
        request: UpdateRoomRequest,
    ) -> Result<ApiResponse<RoomResponse>, ManaError> {
        self.request_json(Method::PATCH, &path("/api/v1/rooms", room_id)?, request)
            .await
    }

    pub async fn list_beds(&self, room_id: &str) -> Result<ApiResponse<BedsResponse>, ManaError> {
        self.request(Method::GET, &(path("/api/v1/rooms", room_id)? + "/beds"))
            .await
    }

    pub async fn create_bed(
        &self,
        room_id: &str,
        request: CreateBedRequest,
    ) -> Result<ApiResponse<Bed>, ManaError> {
        self.request_json(
            Method::POST,
            &(path("/api/v1/rooms", room_id)? + "/beds"),
            request,
        )
        .await
    }

    pub async fn update_bed(
        &self,
        bed_id: &str,
        request: UpdateBedRequest,
    ) -> Result<ApiResponse<BedResponse>, ManaError> {
        self.request_json(Method::PATCH, &path("/api/v1/beds", bed_id)?, request)
            .await
    }

    pub async fn list_residence_beds(
        &self,
    ) -> Result<ApiResponse<ResidenceBedsResponse>, ManaError> {
        self.request(Method::GET, "/api/v1/beds").await
    }

    pub async fn planogram(
        &self,
        wing_id: &str,
    ) -> Result<ApiResponse<PlanogramResponse>, ManaError> {
        self.request(
            Method::GET,
            &(path("/api/v1/wings", wing_id)? + "/planogram"),
        )
        .await
    }

    pub async fn save_planogram(
        &self,
        wing_id: &str,
        request: SavePlanogramRequest,
    ) -> Result<ApiResponse<PlanogramResponse>, ManaError> {
        self.request_json(
            Method::PUT,
            &(path("/api/v1/wings", wing_id)? + "/planogram"),
            request,
        )
        .await
    }

    pub async fn privacy_regions(
        &self,
        room_id: &str,
    ) -> Result<ApiResponse<PrivacyRegionsResponse>, ManaError> {
        self.request(
            Method::GET,
            &(path("/api/v1/rooms", room_id)? + "/privacy-regions"),
        )
        .await
    }

    pub async fn save_privacy_regions(
        &self,
        room_id: &str,
        request: SavePrivacyRegionsRequest,
    ) -> Result<ApiResponse<PrivacyRegionsResponse>, ManaError> {
        self.request_json(
            Method::PUT,
            &(path("/api/v1/rooms", room_id)? + "/privacy-regions"),
            request,
        )
        .await
    }
}

fn path(prefix: &str, id: &str) -> Result<String, ManaError> {
    if id.trim().is_empty() || id.contains(['/', '?', '#']) {
        return Err(ManaError::InvalidPath(id.to_owned()));
    }
    Ok(format!(
        "{prefix}/{}",
        url::form_urlencoded::byte_serialize(id.as_bytes()).collect::<String>()
    ))
}
