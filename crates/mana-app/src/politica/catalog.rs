use ctx_politica::{AlarmCatalog, MobilityAid, Mode, RiskLevel};
use serde_json::Value as JsonValue;

use crate::{
    error::AppFailure,
    identidad::required_token,
    motor::evaluacion::PerfilEfectivo,
    perfil::{perfil_efectivo, PLANTILLA_POR_DEFECTO},
    state::AppState,
};

use super::helpers::effective_rule_view;
use super::views::{
    AutopilotPolicyView, CatalogGroupView, CatalogParamOptionView, CatalogParamView,
    CatalogPresetRuleView, CatalogRiskFactorView, CatalogRuleView, CatalogTemplateView,
    CatalogView, EffectiveView, ProfileHistoryView, ProfileWithEffectiveView,
    ShiftHoursView,
};
use super::helpers::profile_view;

pub(super) fn catalog_view_inner(catalog: &AlarmCatalog) -> CatalogView {
    CatalogView {
        version: catalog.version.clone(),
        levels: catalog.levels.clone(),
        mobility_aids: catalog.mobility_aids.clone(),
        actions: catalog.actions.clone(),
        shifts: catalog.shifts.clone(),
        shift_hours: ShiftHoursView {
            day_start: catalog.shift_hours.day_start,
            night_start: catalog.shift_hours.night_start,
        },
        modes: catalog.modes.clone(),
        sensitivities: catalog.sensitivities.clone(),
        groups: catalog
            .groups
            .iter()
            .map(|g| CatalogGroupView {
                id: g.id.clone(),
                label: g.label.clone(),
                detail: g.detail.clone(),
            })
            .collect(),
        transitions: catalog.rules.iter().map(rule_view_inner).collect(),
        presets: catalog
            .presets
            .iter()
            .map(|(level, rules)| {
                (
                    level.clone(),
                    rules
                        .iter()
                        .map(|(id, rule)| {
                            (
                                id.clone(),
                                CatalogPresetRuleView {
                                    day: rule.day.as_str().to_owned(),
                                    night: rule.night.as_str().to_owned(),
                                    params: rule.params.clone().into_iter().collect(),
                                },
                            )
                        })
                        .collect(),
                )
            })
            .collect(),
        templates: catalog
            .templates
            .iter()
            .map(|t| CatalogTemplateView {
                id: t.id.clone(),
                label: t.label.clone(),
                detail: t.detail.clone(),
                recommended_for: t.recommended_for.clone(),
                rules: t
                    .rules
                    .iter()
                    .map(|(id, rule)| {
                        let mut entry = serde_json::Map::new();
                        if let Some(day) = rule.day {
                            entry.insert("day".to_owned(), JsonValue::from(day.as_str()));
                        }
                        if let Some(night) = rule.night {
                            entry.insert("night".to_owned(), JsonValue::from(night.as_str()));
                        }
                        if !rule.params.is_empty() {
                            entry.insert(
                                "params".to_owned(),
                                JsonValue::Object(rule.params.clone().into_iter().collect()),
                            );
                        }
                        (id.clone(), JsonValue::Object(entry))
                    })
                    .collect(),
            })
            .collect(),
        risk_factors: catalog
            .risk_factors
            .iter()
            .map(|f| CatalogRiskFactorView {
                id: f.id.clone(),
                label: f.label.clone(),
                icon: f.icon.clone(),
            })
            .collect(),
        autopilot: AutopilotPolicyView {
            minimum_signals_for_raise: catalog.autopilot.minimum_signals_for_raise,
            minimum_days_between_changes: catalog.autopilot.minimum_days_between_changes,
        },
    }
}

fn rule_view_inner(rule: &ctx_politica::AlarmRule) -> CatalogRuleView {
    CatalogRuleView {
        id: rule.id.clone(),
        group: rule.group.clone(),
        label: rule.label.clone(),
        short_label: rule.short_label.clone(),
        detail: rule.detail.clone(),
        pictogram: rule.pictogram.clone(),
        art: rule.art.clone(),
        locked: rule.locked,
        class: rule.class.as_str().to_owned(),
        requires_aid: rule
            .requires_aid
            .as_ref()
            .map(|aids| aids.iter().map(|a| a.as_str().to_owned()).collect()),
        params: rule
            .params
            .iter()
            .map(|p| CatalogParamView {
                key: p.key.clone(),
                kind: p.kind.clone(),
                param_type: match p.param_type {
                    ctx_politica::ParamType::Number => "number",
                    ctx_politica::ParamType::Enum => "enum",
                    ctx_politica::ParamType::Multi => "multi",
                }
                .to_owned(),
                label: p.label.clone(),
                detail: p.detail.clone(),
                unit: p.unit.clone(),
                min: p.min,
                max: p.max,
                step: p.step,
                options: p
                    .options
                    .iter()
                    .map(|o| CatalogParamOptionView {
                        value: o.value.clone(),
                        label: o.label.clone(),
                        detail: o.detail.clone(),
                    })
                    .collect(),
            })
            .collect(),
    }
}

impl AppState {
    pub async fn get_catalog(&self) -> Result<CatalogView, AppFailure> {
        Ok(catalog_view_inner(&self.catalog))
    }

    pub async fn search_presets(
        &self,
        token: &str,
        query: &str,
    ) -> Result<CatalogView, AppFailure> {
        required_token(token)?;
        let encontradas: Vec<String> = self
            .catalog
            .search_rules(query)
            .into_iter()
            .map(|r| r.id.clone())
            .collect();
        let mut result = catalog_view_inner(&self.catalog);
        result.transitions.retain(|r| encontradas.contains(&r.id));
        Ok(result)
    }

    /// Las reglas efectivas de un residente, resueltas contra el perfil vigente
    /// en un instante.
    ///
    /// Un residente sin perfil guardado **no queda sin vigilancia**: se resuelve
    /// con el nivel por defecto y la plantilla base. Dejar de vigilar por falta
    /// de configuracion seria la peor forma de fallar.
    pub(crate) fn resolver_values(
        &self,
        level: RiskLevel,
        aid: MobilityAid,
        mode: Mode,
        template_id: &str,
        overrides: &JsonValue,
    ) -> EffectiveView {
        let reglas =
            self.catalog
                .resolve_rules(level, aid, mode.is_custom(), Some(template_id), overrides);

        EffectiveView {
            level: level.as_str().to_owned(),
            mobility_aid: aid.as_str().to_owned(),
            mode: mode.as_str().to_owned(),
            template_id: template_id.to_owned(),
            rules: reglas
                .iter()
                .map(|(id, rule)| (id.clone(), effective_rule_view(rule)))
                .collect(),
        }
    }

    pub(crate) fn resolver(&self, profile: Option<&ctx_politica::AlarmProfileVersion>) -> EffectiveView {
        let level = profile.map(|p| p.risk_level).unwrap_or(RiskLevel::Medium);
        let aid = profile.map(|p| p.mobility_aid).unwrap_or(MobilityAid::None);
        let mode = profile.map(|p| p.mode).unwrap_or(Mode::Preset);
        let template_id = profile
            .map(|p| p.template_id.as_str())
            .unwrap_or(PLANTILLA_POR_DEFECTO);
        let overrides = profile
            .map(|p| p.overrides.as_value().clone())
            .unwrap_or_else(|| JsonValue::Object(Default::default()));

        self.resolver_values(level, aid, mode, template_id, &overrides)
    }

    /// El perfil efectivo tal como lo consume el motor de alarmas.
    ///
    /// Toma el perfil **vigente en `at`**, no el de ahora: evaluar una caida de
    /// hace tres semanas con la politica de hoy es reescribir la historia.
    pub fn perfil_efectivo_en(
        &self,
        resident_id: Option<&str>,
        at: &mana_kernel::Instante,
    ) -> Result<PerfilEfectivo, AppFailure> {
        let profile = match resident_id {
            Some(id) => self.policy.get_at(id, at)?,
            None => None,
        };
        Ok(perfil_efectivo(&self.catalog, profile.as_ref()))
    }

    pub async fn get_current_profile(
        &self,
        token: &str,
        resident_id: &str,
    ) -> Result<Option<ProfileWithEffectiveView>, AppFailure> {
        required_token(token)?;
        let profile = self.policy.get_current(resident_id)?;
        let effective = self.resolver(profile.as_ref());
        Ok(profile.map(|p| ProfileWithEffectiveView {
            profile: profile_view(p),
            effective,
        }))
    }

    pub async fn get_profile_at(
        &self,
        token: &str,
        resident_id: &str,
        at: &str,
    ) -> Result<Option<ProfileWithEffectiveView>, AppFailure> {
        required_token(token)?;
        let at_instant = at
            .parse()
            .map_err(|_| AppFailure::validation("invalid at parameter", Some("at")))?;
        let profile = self.policy.get_at(resident_id, &at_instant)?;
        let effective = self.resolver(profile.as_ref());
        Ok(profile.map(|p| ProfileWithEffectiveView {
            profile: profile_view(p),
            effective,
        }))
    }

    pub async fn get_profile_history(
        &self,
        token: &str,
        resident_id: &str,
    ) -> Result<ProfileHistoryView, AppFailure> {
        required_token(token)?;
        let versions = self.policy.list_history(resident_id)?;
        Ok(ProfileHistoryView {
            versions: versions.into_iter().map(profile_view).collect(),
        })
    }
}
