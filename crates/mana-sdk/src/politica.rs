use reqwest::Method;
use serde::{Deserialize, Serialize};

use crate::transport::{ApiResponse, ManaClient, ManaError};

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct CatalogResponse {
    pub version: String,
    #[serde(default)]
    pub levels: Vec<String>,
    #[serde(default)]
    pub mobility_aids: Vec<String>,
    #[serde(default)]
    pub actions: Vec<String>,
    #[serde(default)]
    pub shifts: Vec<String>,
    #[serde(default)]
    pub modes: Vec<String>,
    #[serde(default)]
    pub sensitivities: Vec<String>,
    #[serde(default)]
    pub groups: Vec<CatalogGroup>,
    /// Las reglas del catalogo. Se llaman `transitions` en la forma wire porque
    /// asi las nombra el contrato del cliente, aunque incluyan permanencias.
    #[serde(default)]
    pub transitions: Vec<CatalogRule>,
    /// La matriz: `nivel -> regla -> que hace`.
    #[serde(default)]
    pub presets: std::collections::BTreeMap<String, serde_json::Value>,
    #[serde(default)]
    pub templates: Vec<CatalogTemplate>,
    #[serde(default)]
    pub risk_factors: Vec<CatalogRiskFactor>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub autopilot: Option<AutopilotPolicy>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct PresetsSearchResponse {
    pub profiles: Vec<serde_json::Value>,
    pub summary: serde_json::Value,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct AutopilotPolicy {
    pub minimum_signals_for_raise: i32,
    pub minimum_days_between_changes: i64,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct CatalogGroup {
    pub id: String,
    pub label: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub detail: Option<String>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct CatalogRule {
    pub id: String,
    pub group: String,
    pub label: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub short_label: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub detail: Option<String>,
    /// `transition` la dispara el evento, `dwell` la dispara el reloj.
    #[serde(default)]
    pub class: String,
    #[serde(default)]
    pub locked: bool,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct CatalogTemplate {
    pub id: String,
    pub label: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub detail: Option<String>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct CatalogRiskFactor {
    pub id: String,
    pub label: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub icon: Option<String>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct ProfileVersion {
    pub id: String,
    pub resident_id: String,
    pub valid_from: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub valid_to: Option<String>,
    /// Elige el preset base del perfil. Sin el, el catalogo se puede servir
    /// pero no se puede evaluar.
    #[serde(default)]
    pub risk_level: String,
    pub mobility_aid: String,
    pub autopilot: bool,
    pub mode: String,
    pub template_id: String,
    pub overrides: String,
    pub catalog_version: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub updated_by: Option<String>,
    pub created_at: String,
    /// Las reglas ya resueltas: preset del nivel, plantilla y ajuste manual,
    /// con la capa que fijo cada valor.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub effective: Option<serde_json::Value>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct PresetResponse {
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub preset: Option<ProfileVersion>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct PresetsResponse {
    pub presets: Vec<ProfileVersion>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct ProfileHistoryResponse {
    pub versions: Vec<ProfileVersion>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct UpdatePresetRequest {
    #[serde(skip_serializing_if = "Option::is_none")]
    pub risk_level: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub mobility_aid: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub autopilot: Option<bool>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub mode: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub template_id: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub overrides: Option<serde_json::Value>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub catalog_version: Option<String>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct ApplyRecommendationRequest {
    pub resident_id: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub template_id: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub overrides: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub catalog_version: Option<String>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct ApplyBulkRequest {
    pub recommendations: Vec<ApplyRecommendationRequest>,
}

impl ManaClient {
    pub async fn get_catalog(&self) -> Result<ApiResponse<CatalogResponse>, ManaError> {
        self.request(Method::GET, "/api/v1/alarm-presets/catalog")
            .await
    }

    pub async fn search_presets(
        &self,
        query: &str,
    ) -> Result<ApiResponse<PresetsSearchResponse>, ManaError> {
        let url = format!("/api/v1/alarm-presets?q={query}");
        self.request(Method::GET, &url).await
    }

    pub async fn get_preset(
        &self,
        resident_id: &str,
    ) -> Result<ApiResponse<PresetResponse>, ManaError> {
        let url = format!("/api/v1/alarm-presets/{resident_id}");
        self.request(Method::GET, &url).await
    }

    pub async fn get_preset_at(
        &self,
        resident_id: &str,
        at: &str,
    ) -> Result<ApiResponse<PresetResponse>, ManaError> {
        let url = format!("/api/v1/alarm-presets/{resident_id}?at={at}");
        self.request(Method::GET, &url).await
    }

    pub async fn get_preset_history(
        &self,
        resident_id: &str,
    ) -> Result<ApiResponse<ProfileHistoryResponse>, ManaError> {
        let url = format!("/api/v1/alarm-presets/{resident_id}/history");
        self.request(Method::GET, &url).await
    }

    pub async fn update_preset(
        &self,
        resident_id: &str,
        request: UpdatePresetRequest,
    ) -> Result<ApiResponse<PresetResponse>, ManaError> {
        let url = format!("/api/v1/alarm-presets/{resident_id}");
        self.request_json(Method::PATCH, &url, request).await
    }

    pub async fn apply_recommendations(
        &self,
        request: ApplyBulkRequest,
    ) -> Result<ApiResponse<PresetsResponse>, ManaError> {
        self.request_json(
            Method::POST,
            "/api/v1/alarm-presets/apply-recommendations",
            request,
        )
        .await
    }

    pub async fn autopilot(&self) -> Result<ApiResponse<PresetsResponse>, ManaError> {
        self.request(Method::POST, "/api/v1/alarm-presets/autopilot")
            .await
    }

    pub async fn apply_recommendation(
        &self,
        resident_id: &str,
        request: ApplyRecommendationRequest,
    ) -> Result<ApiResponse<PresetResponse>, ManaError> {
        let url = format!("/api/v1/alarm-presets/{resident_id}/apply-recommendation");
        self.request_json(Method::POST, &url, request).await
    }
}
