//! DTOs manuales del contrato HTTP del hub.

use std::collections::BTreeMap;

use mana_kernel::Fallo;
use serde::{de::Deserializer, Deserialize, Serialize};
use serde_json::Value;

#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct LoginRequest {
    pub username: Option<String>,
    pub password: Option<String>,
}

#[derive(Debug, Serialize)]
pub struct LoginResponse {
    pub token: String,
    pub expires_at: String,
    pub user: AuthUser,
}

#[derive(Debug, Serialize)]
pub struct CurrentUserResponse {
    pub user: AuthUser,
}

#[derive(Debug, Serialize)]
pub struct AuthUser {
    pub id: String,
    pub username: String,
    pub display_name: String,
    pub role: String,
    pub features: Vec<String>,
    pub permissions: Vec<String>,
    pub capabilities: Vec<String>,
}

#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct CreateUserRequest {
    pub username: Option<String>,
    pub display_name: Option<String>,
    pub role: Option<String>,
    #[serde(default)]
    pub job_title: Option<String>,
    pub password: Option<String>,
}

#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct UpdateUserRequest {
    pub display_name: Option<String>,
    pub role: Option<String>,
    #[serde(default, deserialize_with = "deserialize_nullable_string")]
    pub job_title: Option<Option<String>>,
    pub active: Option<bool>,
    pub password: Option<String>,
}

fn deserialize_nullable_string<'de, D>(deserializer: D) -> Result<Option<Option<String>>, D::Error>
where
    D: Deserializer<'de>,
{
    Ok(Some(Option::<String>::deserialize(deserializer)?))
}

#[derive(Debug, Serialize)]
pub struct AdminUser {
    pub id: String,
    pub username: String,
    pub display_name: String,
    pub role: String,
    pub job_title: Option<String>,
    pub active: i32,
}

#[derive(Debug, Serialize)]
pub struct UsersResponse {
    pub users: Vec<AdminUser>,
}

#[derive(Debug, Serialize)]
pub struct UserResponse {
    pub user: AdminUser,
}

#[derive(Debug, Serialize)]
pub struct Facility {
    pub id: String,
    pub name: String,
    pub timezone: String,
}

#[derive(Debug, Serialize)]
pub struct FacilityDetail {
    pub id: String,
    pub name: String,
    pub timezone: String,
    pub wings: Vec<Wing>,
}

#[derive(Debug, Serialize)]
pub struct TreeResident {
    pub id: String,
    pub name: String,
}

#[derive(Debug, Serialize)]
pub struct TreeBed {
    pub id: String,
    pub label: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub monitor_key: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub resident: Option<TreeResident>,
}

#[derive(Debug, Serialize)]
pub struct TreeRegion {
    pub id: String,
    pub region_type: String,
    pub points: Vec<(f64, f64)>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub label: Option<String>,
    pub is_static: bool,
}

#[derive(Debug, Serialize)]
pub struct TreeStream {
    pub id: String,
    pub stream_key: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub name: Option<String>,
    #[serde(default)]
    pub regions: Vec<TreeRegion>,
}

#[derive(Debug, Serialize)]
pub struct TreeRoom {
    pub id: String,
    pub number: String,
    #[serde(rename = "type")]
    pub room_type: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub stream_key: Option<String>,
    pub beds: Vec<TreeBed>,
    #[serde(default)]
    pub streams: Vec<TreeStream>,
}

#[derive(Debug, Serialize)]
pub struct TreeWing {
    pub id: String,
    pub name: String,
    pub floor: String,
    pub sort_order: i32,
    pub rooms: Vec<TreeRoom>,
}

#[derive(Debug, Serialize)]
pub struct FacilityTree {
    pub id: String,
    pub name: String,
    pub timezone: String,
    pub wings: Vec<TreeWing>,
}

#[derive(Debug, Serialize)]
pub struct FacilityResponse {
    pub facility: Facility,
}

#[derive(Debug, Serialize)]
pub struct FacilitiesResponse {
    pub facilities: Vec<Facility>,
}

#[derive(Debug, Serialize)]
pub struct Wing {
    pub id: String,
    pub facility_id: String,
    pub name: String,
    pub floor: String,
    pub sort_order: i32,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub bed_count: Option<i32>,
}

#[derive(Debug, Serialize)]
pub struct WingResponse {
    pub wing: Wing,
}

#[derive(Debug, Serialize)]
pub struct WingsResponse {
    pub wings: Vec<Wing>,
}

#[derive(Debug, Serialize)]
pub struct Room {
    pub id: String,
    pub wing_id: String,
    pub number: String,
    #[serde(rename = "type")]
    pub room_type: String,
    pub stream_key: Option<String>,
}

#[derive(Debug, Serialize)]
pub struct RoomsResponse {
    pub rooms: Vec<Room>,
}

#[derive(Debug, Serialize)]
pub struct RoomResponse {
    pub room: Room,
}

#[derive(Debug, Serialize)]
pub struct Bed {
    pub id: String,
    pub room_id: String,
    pub label: String,
    pub monitor_key: Option<String>,
}

#[derive(Debug, Serialize)]
pub struct BedsResponse {
    pub beds: Vec<Bed>,
}

#[derive(Debug, Serialize)]
pub struct BedResponse {
    pub bed: Bed,
}

#[derive(Debug, Serialize)]
pub struct ResidenceBed {
    pub id: String,
    pub room_id: String,
    pub label: String,
    pub monitor_key: Option<String>,
    pub room_number: String,
    #[serde(rename = "room_type")]
    pub room_type: String,
    pub stream_key: Option<String>,
    pub wing_id: String,
    pub wing_name: String,
    pub wing_floor: String,
}

#[derive(Debug, Serialize)]
pub struct ResidenceBedsResponse {
    pub beds: Vec<ResidenceBed>,
}

#[derive(Debug, Serialize)]
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
    pub stream_key: Option<String>,
}

#[derive(Debug, Serialize)]
pub struct PlanogramResponse {
    pub wing_id: String,
    pub placements: Vec<PlanogramPlacement>,
}

#[derive(Debug, Serialize)]
pub struct PrivacyRegion {
    pub x: f64,
    pub y: f64,
    pub w: f64,
    pub h: f64,
}

#[derive(Debug, Serialize)]
pub struct PrivacyRegionsResponse {
    pub room_id: String,
    pub regions: Vec<PrivacyRegion>,
}

#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct PlanogramPlacementRequest {
    pub room_id: Option<String>,
    pub x: Option<f64>,
    pub y: Option<f64>,
    #[serde(default)]
    pub sort_order: Option<i32>,
}

#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct SavePlanogramRequest {
    pub placements: Option<Vec<PlanogramPlacementRequest>>,
}

#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct PrivacyRegionRequest {
    pub x: Option<f64>,
    pub y: Option<f64>,
    pub w: Option<f64>,
    pub h: Option<f64>,
}

#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct SavePrivacyRegionsRequest {
    pub regions: Option<Vec<PrivacyRegionRequest>>,
}

#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct CreateFacilityRequest {
    pub name: Option<String>,
    pub timezone: Option<String>,
}

#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct UpdateFacilityRequest {
    pub name: Option<String>,
    pub timezone: Option<String>,
}

#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct CreateWingRequest {
    pub name: Option<String>,
    pub floor: Option<String>,
    pub sort_order: Option<i32>,
}

#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct UpdateWingRequest {
    pub name: Option<String>,
    pub floor: Option<String>,
    pub sort_order: Option<i32>,
}

#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct CreateRoomRequest {
    pub number: Option<String>,
    #[serde(rename = "type")]
    pub room_type: Option<String>,
    #[serde(default)]
    pub stream_key: Option<String>,
}

#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct UpdateRoomRequest {
    pub number: Option<String>,
    #[serde(rename = "type")]
    pub room_type: Option<String>,
    #[serde(default, deserialize_with = "deserialize_nullable_residence_string")]
    pub stream_key: Option<Option<String>>,
}

#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct CreateBedRequest {
    pub label: Option<String>,
    #[serde(default)]
    pub monitor_key: Option<String>,
}

#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct UpdateBedRequest {
    pub label: Option<String>,
    #[serde(default, deserialize_with = "deserialize_nullable_residence_string")]
    pub monitor_key: Option<Option<String>>,
}

fn deserialize_nullable_residence_string<'de, D>(
    deserializer: D,
) -> Result<Option<Option<String>>, D::Error>
where
    D: Deserializer<'de>,
{
    Ok(Some(Option::<String>::deserialize(deserializer)?))
}

#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct CreateResidentRequest {
    pub full_name: Option<String>,
    #[serde(default)]
    pub external_id: Option<String>,
    #[serde(default)]
    pub birth_date: Option<String>,
    #[serde(default)]
    pub admission_date: Option<String>,
}

#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct UpdateResidentRequest {
    pub full_name: Option<String>,
    #[serde(default, deserialize_with = "deserialize_nullable_resident_string")]
    pub external_id: Option<Option<String>>,
    #[serde(default, deserialize_with = "deserialize_nullable_resident_string")]
    pub birth_date: Option<Option<String>>,
    #[serde(default, deserialize_with = "deserialize_nullable_resident_string")]
    pub admission_date: Option<Option<String>>,
}

fn deserialize_nullable_resident_string<'de, D>(
    deserializer: D,
) -> Result<Option<Option<String>>, D::Error>
where
    D: Deserializer<'de>,
{
    Ok(Some(Option::<String>::deserialize(deserializer)?))
}

#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct DischargeRequest {
    #[serde(default)]
    pub discharged_at: Option<String>,
}

#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct AssignBedRequest {
    pub bed_id: Option<String>,
    /// Cuando empieza la ocupacion. El panel lo manda desde siempre y la API lo
    /// rechazaba por `deny_unknown_fields`: una asignacion tiene inicio y el
    /// cliente es quien sabe cual.
    #[serde(default)]
    pub starts_at: Option<String>,
}

#[derive(Debug, Serialize)]
pub struct ResidentRecord {
    pub id: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub external_id: Option<String>,
    pub full_name: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub birth_date: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub admission_date: Option<String>,
    pub status: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub discharged_at: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub discharged_by: Option<String>,
    pub created_at: String,
    pub updated_at: String,
}

#[derive(Debug, Serialize)]
pub struct RoomRef {
    pub id: String,
    pub number: String,
    pub wing_id: String,
    pub wing_name: String,
}

#[derive(Debug, Serialize)]
pub struct ResidentListItem {
    pub id: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub external_id: Option<String>,
    pub full_name: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub birth_date: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub admission_date: Option<String>,
    pub status: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub discharged_at: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub room: Option<RoomRef>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub bed_id: Option<String>,
}

#[derive(Debug, Serialize)]
pub struct ResidentsResponse {
    pub residents: Vec<ResidentListItem>,
}

#[derive(Debug, Serialize)]
pub struct ResidentResponse {
    pub resident: ResidentRecord,
}

#[derive(Debug, Serialize)]
pub struct BedAssignmentRecord {
    pub id: String,
    pub resident_id: String,
    pub bed_id: String,
    pub starts_at: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub ends_at: Option<String>,
    pub created_at: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub created_by: Option<String>,
}

#[derive(Debug, Serialize)]
pub struct AssignmentsResponse {
    pub assignments: Vec<BedAssignmentRecord>,
}

#[derive(Debug, Serialize)]
pub struct AssignmentResponse {
    pub assignment: BedAssignmentRecord,
}

#[derive(Debug, Serialize)]
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

#[derive(Debug, Serialize)]
pub struct AuditResponse {
    pub audit: Vec<AuditEntry>,
}

#[derive(Debug, Serialize)]
pub struct ErrorEnvelope {
    pub error: ErrorDetail,
}

#[derive(Debug, Serialize)]
pub struct ErrorDetail {
    pub code: &'static str,
    pub message: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub fields: Option<BTreeMap<String, String>>,
}

impl ErrorEnvelope {
    pub fn new(
        fallo: Fallo,
        message: impl Into<String>,
        fields: Option<BTreeMap<String, String>>,
    ) -> Self {
        Self {
            error: ErrorDetail {
                code: fallo.codigo(),
                message: message.into(),
                fields,
            },
        }
    }
}

#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct CreateStreamRequest {
    pub stream_key: Option<String>,
    pub name: Option<String>,
}

#[derive(Debug, Serialize)]
pub struct StreamResponse {
    pub id: String,
    pub room_id: String,
    pub stream_key: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub name: Option<String>,
}

#[derive(Debug, Serialize)]
pub struct StreamRegionResponse {
    pub id: String,
    pub stream_id: String,
    pub region_type: String,
    pub points: Vec<(f64, f64)>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub label: Option<String>,
    pub is_static: bool,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub updated_by: Option<String>,
}

#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct RegionRequest {
    pub region_type: Option<String>,
    pub points: Option<Vec<(f64, f64)>>,
    pub label: Option<String>,
}

#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct ReplaceRegionsRequest {
    pub regions: Option<Vec<RegionRequest>>,
}

#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct UpdateRegionRequest {
    pub points: Option<Vec<(f64, f64)>>,
}

#[cfg(test)]
mod tests {
    use super::UpdateUserRequest;

    #[test]
    fn distinguishes_missing_nullable_fields_from_explicit_null() {
        let missing: UpdateUserRequest = serde_json::from_str("{}").unwrap();
        assert_eq!(missing.job_title, None);

        let cleared: UpdateUserRequest = serde_json::from_str(r#"{"job_title":null}"#).unwrap();
        assert_eq!(cleared.job_title, Some(None));
    }
}
