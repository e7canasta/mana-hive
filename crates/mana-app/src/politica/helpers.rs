use std::collections::HashMap;

use ctx_politica::{
    AlarmCatalog, AlarmProfileVersion, MobilityAid, Mode, RiskLevel,
};
use mana_kernel::Instante;
use crate::{
    error::AppFailure,
    identidad::{authenticated_actor, require_capability},
    perfil::PLANTILLA_POR_DEFECTO,
    state::AppState,
};

use super::views::{
    AlarmPresetsSummaryView, AlarmProfileView, AlarmResidentView, AlarmProfileSettingsView,
    AutopilotDecisionView, EffectiveRuleView, ProfileSettingsInput, ProfileVersionView,
    RecommendationFactorView, RecommendationView,
};

pub(crate) fn profile_view(profile: AlarmProfileVersion) -> ProfileVersionView {
    ProfileVersionView {
        id: profile.id.into_string(),
        resident_id: profile.resident_id,
        valid_from: profile.valid_from.to_string(),
        valid_to: profile.valid_to.map(|t| t.to_string()),
        risk_level: profile.risk_level.as_str().to_owned(),
        mobility_aid: profile.mobility_aid.as_str().to_owned(),
        autopilot: profile.autopilot,
        mode: profile.mode.as_str().to_owned(),
        template_id: profile.template_id,
        overrides: profile.overrides.to_string(),
        catalog_version: profile.catalog_version,
        updated_by: profile.updated_by,
        created_at: profile.created_at.to_string(),
    }
}

pub(crate) fn effective_rule_view(rule: &ctx_politica::ResolvedRule) -> EffectiveRuleView {
    EffectiveRuleView {
        day: rule.day.as_str().to_owned(),
        night: rule.night.as_str().to_owned(),
        group: rule.group.clone(),
        class: rule.class.as_str().to_owned(),
        locked: rule.locked,
        source: rule.source.as_str().to_owned(),
        customized: rule.customized,
        params: rule.params.clone(),
    }
}

pub(crate) async fn load_alarm_residents(
    state: &AppState,
    token: String,
    query: String,
    now: Instante,
) -> Result<(Vec<AlarmResidentView>, HashMap<String, String>), AppFailure> {
    let identity = state.identity.clone();
    let poblacion = state.poblacion.clone();
    let residence = state.residence.clone();
    let enabled = state.enabled_capabilities.clone();

    tokio::task::spawn_blocking(move || {
        let actor = authenticated_actor(&identity, &token, &enabled)?;
        require_capability(&actor, "config.alarms.read")?;

        let residents = poblacion
            .list_residents(if query.is_empty() { None } else { Some(&query) })
            .map_err(AppFailure::from)?;
        let assignments = poblacion
            .list_open_assignments()
            .map_err(AppFailure::from)?
            .into_iter()
            .map(|assignment| (assignment.resident_id.as_str().to_owned(), assignment))
            .collect::<HashMap<_, _>>();
        let beds = residence
            .list_beds_all()
            .map_err(AppFailure::from)?
            .into_iter()
            .map(|bed| (bed.bed.id.as_str().to_owned(), bed))
            .collect::<HashMap<_, _>>();
        let updated_by_names = identity
            .list_users(false)
            .map_err(AppFailure::from)?
            .into_iter()
            .map(|user| (user.id.into_string(), user.display_name.as_str().to_owned()))
            .collect();
        let today = now.as_datetime().date_naive();

        let residents = residents
            .into_iter()
            .filter(|resident| resident.status == ctx_poblacion::ResidentStatus::Active)
            .map(|resident| {
                let traits = poblacion
                    .list_attributes(&resident.id)
                    .map_err(AppFailure::from)?
                    .into_iter()
                    .filter(|attribute| attribute.valid_to.is_none_or(|until| until >= today))
                    .filter(|attribute| {
                        !matches!(attribute.value.as_str(), "false" | "no" | "0" | "")
                    })
                    .map(|attribute| attribute.code.as_str().to_owned())
                    .collect();
                let bed = assignments
                    .get(resident.id.as_str())
                    .and_then(|assignment| beds.get(assignment.bed_id.as_str()));

                Ok(AlarmResidentView {
                    id: resident.id.into_string(),
                    full_name: resident.full_name,
                    external_id: resident.external_id,
                    room_number: bed.map(|bed| bed.room_number.clone()),
                    bed_label: bed.map(|bed| bed.bed.label.clone()),
                    monitor_key: bed
                        .and_then(|bed| bed.bed.monitor_key.as_ref())
                        .map(|key| key.as_str().to_owned()),
                    wing_id: bed.map(|bed| bed.wing_id.as_str().to_owned()),
                    wing_name: bed.map(|bed| bed.wing_name.clone()),
                    traits,
                })
            })
            .collect::<Result<Vec<_>, AppFailure>>()?;

        Ok((residents, updated_by_names))
    })
    .await
    .map_err(|error| {
        tracing::error!(error = %error, "carga de perfiles de alarmas abortada");
        AppFailure::new(
            mana_kernel::Fallo::InternalError,
            "No se pudo completar la operacion",
        )
    })?
}

pub(crate) fn default_mobility_aid(traits: &[String]) -> MobilityAid {
    if traits.iter().any(|trait_code| trait_code == "wheelchair") {
        MobilityAid::Wheelchair
    } else if traits.iter().any(|trait_code| trait_code == "walker") {
        MobilityAid::Walker
    } else {
        MobilityAid::None
    }
}

pub(crate) fn default_risk_level(traits: &[String]) -> RiskLevel {
    if traits
        .iter()
        .any(|trait_code| trait_code == "fall_risk" || trait_code == "wandering")
    {
        RiskLevel::Medium
    } else {
        RiskLevel::Low
    }
}

pub(crate) fn profile_settings_view(input: ProfileSettingsInput<'_>) -> AlarmProfileSettingsView {
    AlarmProfileSettingsView {
        risk_level: input.level.as_str().to_owned(),
        mobility_aid: input.aid.as_str().to_owned(),
        autopilot: input.autopilot,
        mode: input.mode.as_str().to_owned(),
        template_id: input.template_id.to_owned(),
        overrides: input.overrides.clone(),
        updated_at: input.profile.map(|profile| profile.valid_from.to_string()),
        updated_by: input.profile.and_then(|profile| profile.updated_by.clone()),
        updated_by_name: input.updated_by_name,
        source: if input.profile.is_some() {
            "stored".to_owned()
        } else {
            "default".to_owned()
        },
    }
}

pub(crate) fn recommendation_view(
    catalog: &AlarmCatalog,
    recommendation: &mana_motores::Recomendacion,
    changed: bool,
    now: &Instante,
) -> RecommendationView {
    RecommendationView {
        level: recommendation.level.as_str().to_owned(),
        changed,
        factors: recommendation
            .factors
            .iter()
            .map(|factor| {
                let definition = catalog
                    .risk_factors
                    .iter()
                    .find(|item| item.id == factor.id);
                RecommendationFactorView {
                    id: factor.id.clone(),
                    label: definition
                        .map(|item| item.label.clone())
                        .unwrap_or_else(|| factor.id.clone()),
                    icon: definition.and_then(|item| item.icon.clone()),
                    detail: factor.detail.clone(),
                    weight: factor.weight,
                }
            })
            .collect(),
        score: recommendation.score,
        signals_evaluated: recommendation.signals_evaluated,
        suggested_template: recommendation.suggested_template.clone(),
        computed_at: now.to_string(),
    }
}

pub(crate) fn autopilot_decision_view(
    decision: &mana_motores::AutopilotDecision,
) -> AutopilotDecisionView {
    AutopilotDecisionView {
        action: decision.action.as_str().to_owned(),
        reason: decision.reason.as_str().to_owned(),
        current_level: decision.current_level.as_str().to_owned(),
        recommended_level: decision.recommended_level.as_str().to_owned(),
        score: decision.score,
        signals_evaluated: decision.signals_evaluated,
    }
}

pub(crate) fn alarm_presets_summary(profiles: &[AlarmProfileView]) -> AlarmPresetsSummaryView {
    AlarmPresetsSummaryView {
        residents: profiles.len(),
        autopilot: profiles
            .iter()
            .filter(|profile| profile.profile.autopilot)
            .count(),
        action_needed: profiles
            .iter()
            .filter(|profile| profile.recommendation.changed)
            .count(),
        custom: profiles
            .iter()
            .filter(|profile| profile.profile.mode == Mode::Custom.as_str())
            .count(),
        templated: profiles
            .iter()
            .filter(|profile| profile.profile.template_id != PLANTILLA_POR_DEFECTO)
            .count(),
    }
}
