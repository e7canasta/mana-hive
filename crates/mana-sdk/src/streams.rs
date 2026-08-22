use reqwest::Method;
use serde::{Deserialize, Serialize};

use crate::transport::{ApiResponse, ManaClient, ManaError};

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct Stream {
    pub id: String,
    pub room_id: String,
    pub stream_key: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub name: Option<String>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct StreamsResponse {
    #[serde(default)]
    pub streams: Vec<Stream>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct StreamRegion {
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

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct RegionsResponse {
    #[serde(default)]
    pub regions: Vec<StreamRegion>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct CreateStreamRequest {
    #[serde(skip_serializing_if = "Option::is_none")]
    pub stream_key: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub name: Option<String>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct RegionRequest {
    pub region_type: String,
    pub points: Vec<(f64, f64)>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub label: Option<String>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct ReplaceRegionsRequest {
    pub regions: Vec<RegionRequest>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct UpdateRegionRequest {
    pub points: Vec<(f64, f64)>,
}

fn path(base: &str, id: &str) -> Result<String, ManaError> {
    if id.is_empty() {
        return Err(ManaError::InvalidPath("id vacio".to_owned()));
    }
    Ok(format!("{base}/{id}"))
}

impl ManaClient {
    pub async fn list_streams(
        &self,
        room_id: &str,
    ) -> Result<ApiResponse<StreamsResponse>, ManaError> {
        self.request(Method::GET, &(path("/api/v1/rooms", room_id)? + "/streams"))
            .await
    }

    pub async fn create_stream(
        &self,
        room_id: &str,
        request: CreateStreamRequest,
    ) -> Result<ApiResponse<Stream>, ManaError> {
        self.request_json(
            Method::POST,
            &(path("/api/v1/rooms", room_id)? + "/streams"),
            request,
        )
        .await
    }

    pub async fn get_stream(
        &self,
        stream_id: &str,
    ) -> Result<ApiResponse<Stream>, ManaError> {
        self.request(Method::GET, &path("/api/v1/streams", stream_id)?)
            .await
    }

    pub async fn list_regions(
        &self,
        stream_id: &str,
    ) -> Result<ApiResponse<RegionsResponse>, ManaError> {
        self.request(
            Method::GET,
            &(path("/api/v1/streams", stream_id)? + "/regions"),
        )
        .await
    }

    pub async fn replace_regions(
        &self,
        stream_id: &str,
        request: ReplaceRegionsRequest,
    ) -> Result<ApiResponse<RegionsResponse>, ManaError> {
        self.request_json(
            Method::PUT,
            &(path("/api/v1/streams", stream_id)? + "/regions"),
            request,
        )
        .await
    }

    pub async fn update_region(
        &self,
        stream_id: &str,
        region_id: &str,
        request: UpdateRegionRequest,
    ) -> Result<ApiResponse<StreamRegion>, ManaError> {
        self.request_json(
            Method::PATCH,
            &(path("/api/v1/streams", stream_id)? + "/regions/" + region_id),
            request,
        )
        .await
    }
}
