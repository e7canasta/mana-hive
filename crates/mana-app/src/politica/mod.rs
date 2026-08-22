mod autopilot;
mod catalog;
mod helpers;
mod profiles;
mod views;

pub use views::{
    AlarmPresetsSummaryView, AlarmPresetsView, AlarmProfileSettingsView, AlarmProfileView,
    AlarmResidentView, AutopilotDecisionView, AutopilotPolicyView, CatalogGroupView,
    CatalogParamView, CatalogPresetRuleView, CatalogRiskFactorView, CatalogRuleView,
    CatalogTemplateView, CatalogView, EffectiveRuleView, EffectiveView, ProfileHistoryView,
    ProfileVersionView, ProfileWithEffectiveView, RecommendationFactorView, RecommendationView,
};

#[derive(Clone, Debug)]
pub struct UpdateProfileCommand {
    pub risk_level: Option<String>,
    pub mobility_aid: Option<String>,
    pub autopilot: Option<bool>,
    pub mode: Option<String>,
    pub template_id: Option<String>,
    pub overrides: Option<ctx_politica::Overrides>,
    pub catalog_version: Option<String>,
}

#[derive(Clone, Debug)]
pub struct ApplyRecommendationCommand {
    pub resident_id: String,
    pub risk_level: Option<String>,
    pub template_id: Option<String>,
    pub overrides: Option<ctx_politica::Overrides>,
    pub catalog_version: Option<String>,
}

#[derive(Clone, Debug)]
pub struct ApplyRecommendationsCommand {
    pub recommendations: Vec<ApplyRecommendationCommand>,
}
