use std::collections::BTreeMap;

use serde_json::Value as JsonValue;

#[derive(Clone, Debug, serde::Serialize)]
pub struct ProfileVersionView {
    pub id: String,
    pub resident_id: String,
    pub valid_from: String,
    pub valid_to: Option<String>,
    pub risk_level: String,
    pub mobility_aid: String,
    pub autopilot: bool,
    pub mode: String,
    pub template_id: String,
    pub overrides: String,
    pub catalog_version: String,
    pub updated_by: Option<String>,
    pub created_at: String,
}

/// Una regla ya resuelta, con la capa que fijo lo que quedo. Es la forma que el
/// panel pinta y la que el motor evalua: **la misma**, para que no puedan
/// discrepar.
#[derive(Clone, Debug, serde::Serialize)]
pub struct EffectiveRuleView {
    pub day: String,
    pub night: String,
    pub group: String,
    pub class: String,
    pub locked: bool,
    pub source: String,
    pub customized: bool,
    pub params: BTreeMap<String, JsonValue>,
}

#[derive(Clone, Debug, serde::Serialize)]
pub struct EffectiveView {
    pub level: String,
    pub mobility_aid: String,
    pub mode: String,
    pub template_id: String,
    pub rules: BTreeMap<String, EffectiveRuleView>,
}

/// El perfil de un residente con sus reglas ya resueltas.
///
/// Servir el perfil sin las reglas efectivas era el hueco: el panel recibia
/// `mode`, `template_id` y un JSON de overrides, y tenia que adivinar que
/// significaban juntos.
#[derive(Clone, Debug, serde::Serialize)]
pub struct ProfileWithEffectiveView {
    #[serde(flatten)]
    pub profile: ProfileVersionView,
    pub effective: EffectiveView,
}

#[derive(Clone, Debug, serde::Serialize)]
pub struct AlarmResidentView {
    pub id: String,
    pub full_name: String,
    pub external_id: Option<String>,
    pub room_number: Option<String>,
    pub bed_label: Option<String>,
    pub monitor_key: Option<String>,
    pub wing_id: Option<String>,
    pub wing_name: Option<String>,
    pub traits: Vec<String>,
}

#[derive(Clone, Debug, serde::Serialize)]
pub struct AlarmProfileSettingsView {
    pub risk_level: String,
    pub mobility_aid: String,
    pub autopilot: bool,
    pub mode: String,
    pub template_id: String,
    pub overrides: JsonValue,
    pub updated_at: Option<String>,
    pub updated_by: Option<String>,
    pub updated_by_name: Option<String>,
    pub source: String,
}

#[derive(Clone, Debug, serde::Serialize)]
pub struct RecommendationFactorView {
    pub id: String,
    pub label: String,
    pub icon: Option<String>,
    pub detail: String,
    pub weight: i32,
}

#[derive(Clone, Debug, serde::Serialize)]
pub struct RecommendationView {
    pub level: String,
    pub changed: bool,
    pub factors: Vec<RecommendationFactorView>,
    pub score: i32,
    pub signals_evaluated: i32,
    pub suggested_template: String,
    pub computed_at: String,
}

#[derive(Clone, Debug, serde::Serialize)]
pub struct AutopilotDecisionView {
    pub action: String,
    pub reason: String,
    pub current_level: String,
    pub recommended_level: String,
    pub score: i32,
    pub signals_evaluated: i32,
}

#[derive(Clone, Debug, serde::Serialize)]
pub struct AlarmProfileView {
    pub resident: AlarmResidentView,
    pub profile: AlarmProfileSettingsView,
    pub effective: EffectiveView,
    pub recommendation: RecommendationView,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub autopilot_decision: Option<AutopilotDecisionView>,
}

#[derive(Clone, Debug, serde::Serialize)]
pub struct AlarmPresetsSummaryView {
    pub residents: usize,
    pub autopilot: usize,
    pub action_needed: usize,
    pub custom: usize,
    pub templated: usize,
}

#[derive(Clone, Debug, serde::Serialize)]
pub struct AlarmPresetsView {
    pub profiles: Vec<AlarmProfileView>,
    pub summary: AlarmPresetsSummaryView,
}

pub(crate) struct ProfileSettingsInput<'a> {
    pub profile: Option<&'a ctx_politica::AlarmProfileVersion>,
    pub level: ctx_politica::RiskLevel,
    pub aid: ctx_politica::MobilityAid,
    pub autopilot: bool,
    pub mode: ctx_politica::Mode,
    pub template_id: &'a str,
    pub overrides: &'a JsonValue,
    pub updated_by_name: Option<String>,
}

#[derive(Clone, Debug, serde::Serialize)]
pub struct CatalogView {
    pub version: String,
    pub levels: Vec<String>,
    pub mobility_aids: Vec<String>,
    pub actions: Vec<String>,
    pub shifts: Vec<String>,
    pub shift_hours: ShiftHoursView,
    pub modes: Vec<String>,
    pub sensitivities: Vec<String>,
    pub groups: Vec<CatalogGroupView>,
    pub transitions: Vec<CatalogRuleView>,
    pub presets: BTreeMap<String, BTreeMap<String, CatalogPresetRuleView>>,
    pub templates: Vec<CatalogTemplateView>,
    pub risk_factors: Vec<CatalogRiskFactorView>,
    pub autopilot: AutopilotPolicyView,
}

#[derive(Clone, Debug, serde::Serialize)]
pub struct AutopilotPolicyView {
    pub minimum_signals_for_raise: i32,
    pub minimum_days_between_changes: i64,
}

#[derive(Clone, Debug, serde::Serialize)]
pub struct ShiftHoursView {
    pub day_start: u32,
    pub night_start: u32,
}

#[derive(Clone, Debug, serde::Serialize)]
pub struct CatalogGroupView {
    pub id: String,
    pub label: String,
    pub detail: Option<String>,
}

#[derive(Clone, Debug, serde::Serialize)]
pub struct CatalogParamOptionView {
    pub value: String,
    pub label: String,
    pub detail: Option<String>,
}

#[derive(Clone, Debug, serde::Serialize)]
pub struct CatalogParamView {
    pub key: String,
    pub kind: String,
    #[serde(rename = "type")]
    pub param_type: String,
    pub label: String,
    pub detail: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub unit: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub min: Option<f64>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub max: Option<f64>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub step: Option<f64>,
    #[serde(skip_serializing_if = "Vec::is_empty")]
    pub options: Vec<CatalogParamOptionView>,
}

#[derive(Clone, Debug, serde::Serialize)]
pub struct CatalogRuleView {
    pub id: String,
    pub group: String,
    pub label: String,
    pub short_label: Option<String>,
    pub detail: Option<String>,
    pub pictogram: Option<String>,
    pub art: Option<String>,
    pub locked: bool,
    pub class: String,
    pub requires_aid: Option<Vec<String>>,
    pub params: Vec<CatalogParamView>,
}

#[derive(Clone, Debug, serde::Serialize)]
pub struct CatalogPresetRuleView {
    pub day: String,
    pub night: String,
    pub params: BTreeMap<String, JsonValue>,
}

#[derive(Clone, Debug, serde::Serialize)]
pub struct CatalogTemplateView {
    pub id: String,
    pub label: String,
    pub detail: Option<String>,
    pub recommended_for: Vec<String>,
    pub rules: BTreeMap<String, JsonValue>,
}

#[derive(Clone, Debug, serde::Serialize)]
pub struct CatalogRiskFactorView {
    pub id: String,
    pub label: String,
    pub icon: Option<String>,
}

#[derive(Clone, Debug, serde::Serialize)]
pub struct ProfileHistoryView {
    pub versions: Vec<ProfileVersionView>,
}
