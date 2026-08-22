use ctx_politica::{MobilityAid, Mode, Overrides, ProfileInput, RiskLevel};
use mana_kernel::Instante;
use serde_json::Value as JsonValue;

use crate::{
    error::AppFailure,
    identidad::required_token,
    perfil::PLANTILLA_POR_DEFECTO,
    state::AppState,
};

use super::{
    ApplyRecommendationCommand, ApplyRecommendationsCommand, UpdateProfileCommand,
};
use super::helpers::{
    alarm_presets_summary, autopilot_decision_view, default_mobility_aid, default_risk_level,
    load_alarm_residents, profile_settings_view, profile_view, recommendation_view,
};
use super::views::{
    AlarmPresetsView, AlarmProfileView, AlarmResidentView, ProfileVersionView,
    ProfileWithEffectiveView, ProfileSettingsInput,
};

use mana_motores::{decidir, AutopilotInput, AutopilotProfile};

impl AppState {
    /// Envelope publico de politica: una fila ya trae al residente, el perfil
    /// guardado o derivado, las reglas efectivas, la recomendacion y, cuando
    /// corresponde, la decision de autopilot. Asi cada motor se puede evaluar
    /// sin repetir consultas desde la UI.
    pub async fn list_alarm_profiles(
        &self,
        token: &str,
        query: &str,
    ) -> Result<AlarmPresetsView, AppFailure> {
        let token = required_token(token)?;
        let now = Instante::now();
        let (residents, updated_by_names) =
            load_alarm_residents(self, token, query.trim().to_owned(), now).await?;
        let mut profiles = Vec::with_capacity(residents.len());

        for resident in residents {
            let current = self.policy.get_current(&resident.id)?;
            let level = current
                .as_ref()
                .map(|profile| profile.risk_level)
                .unwrap_or_else(|| default_risk_level(&resident.traits));
            let aid = current
                .as_ref()
                .map(|profile| profile.mobility_aid)
                .unwrap_or_else(|| default_mobility_aid(&resident.traits));
            let autopilot = current.as_ref().is_some_and(|profile| profile.autopilot);
            let mode = current
                .as_ref()
                .map(|profile| profile.mode)
                .unwrap_or(Mode::Preset);
            let template_id = current
                .as_ref()
                .map(|profile| profile.template_id.clone())
                .unwrap_or_else(|| PLANTILLA_POR_DEFECTO.to_owned());
            let overrides = current
                .as_ref()
                .map(|profile| profile.overrides.as_value().clone())
                .unwrap_or_else(|| JsonValue::Object(Default::default()));

            let recommendation = self.recomendacion_para(&resident.id, aid, &now)?;
            let autopilot_decision = if autopilot {
                Some(decidir(
                    &AutopilotInput {
                        current_profile: AutopilotProfile {
                            risk_level: level,
                            autopilot,
                        },
                        recommendation: recommendation.clone(),
                        last_change_at: current.as_ref().map(|profile| profile.valid_from),
                        now,
                    },
                    &self.catalog.autopilot,
                ))
            } else {
                None
            };

            profiles.push(AlarmProfileView {
                resident: AlarmResidentView {
                    id: resident.id.clone(),
                    full_name: resident.full_name,
                    external_id: resident.external_id,
                    room_number: resident.room_number,
                    bed_label: resident.bed_label,
                    monitor_key: resident.monitor_key,
                    wing_id: resident.wing_id,
                    wing_name: resident.wing_name,
                    traits: resident.traits,
                },
                profile: profile_settings_view(ProfileSettingsInput {
                    profile: current.as_ref(),
                    level,
                    aid,
                    autopilot,
                    mode,
                    template_id: &template_id,
                    overrides: &overrides,
                    updated_by_name: current
                        .as_ref()
                        .and_then(|profile| profile.updated_by.as_ref())
                        .and_then(|actor| updated_by_names.get(actor))
                        .cloned(),
                }),
                effective: self.resolver_values(level, aid, mode, &template_id, &overrides),
                recommendation: recommendation_view(
                    &self.catalog,
                    &recommendation,
                    !autopilot && recommendation.level != level,
                    &now,
                ),
                autopilot_decision: autopilot_decision.as_ref().map(autopilot_decision_view),
            });
        }

        Ok(AlarmPresetsView {
            summary: alarm_presets_summary(&profiles),
            profiles,
        })
    }

    pub async fn get_alarm_profile(
        &self,
        token: &str,
        resident_id: &str,
    ) -> Result<Option<AlarmProfileView>, AppFailure> {
        let profiles = self.list_alarm_profiles(token, "").await?;
        Ok(profiles
            .profiles
            .into_iter()
            .find(|profile| profile.resident.id == resident_id))
    }

    /// Devuelve el perfil **con sus reglas ya resueltas**: quien acaba de
    /// cambiar la configuracion necesita ver que significa, no releer el JSON
    /// de ajustes que acaba de mandar.
    pub async fn update_profile(
        &self,
        token: &str,
        resident_id: &str,
        command: UpdateProfileCommand,
    ) -> Result<ProfileWithEffectiveView, AppFailure> {
        let actor = required_token(token)?;

        let current = self.policy.get_current(resident_id)?;

        let risk_level = command
            .risk_level
            .map(|l| RiskLevel::parse(&l))
            .transpose()
            .map_err(|e| AppFailure::validation(e.to_string(), Some("risk_level")))?
            .or_else(|| current.as_ref().map(|c| c.risk_level))
            .unwrap_or(RiskLevel::Medium);

        let mobility_aid = command
            .mobility_aid
            .map(|m| MobilityAid::parse(&m))
            .transpose()
            .map_err(|e| AppFailure::validation(e.to_string(), Some("mobility_aid")))?
            .or_else(|| current.as_ref().map(|c| c.mobility_aid))
            .unwrap_or(MobilityAid::None);

        let autopilot = command
            .autopilot
            .or_else(|| current.as_ref().map(|c| c.autopilot))
            .unwrap_or(false);

        let mode = command
            .mode
            .map(|m| Mode::parse(&m))
            .transpose()
            .map_err(|e| AppFailure::validation(e.to_string(), Some("mode")))?
            .or_else(|| current.as_ref().map(|c| c.mode))
            .unwrap_or(Mode::Preset);

        let template_id = command
            .template_id
            .or_else(|| current.as_ref().map(|c| c.template_id.clone()))
            .unwrap_or_else(|| PLANTILLA_POR_DEFECTO.to_owned());

        if self.catalog.find_template(&template_id).is_none() {
            return Err(AppFailure::validation(
                format!("unknown template: {template_id}"),
                Some("template_id"),
            ));
        }

        let overrides = command
            .overrides
            .or_else(|| current.as_ref().map(|c| c.overrides.clone()))
            .unwrap_or_default();

        // Los overrides se validaban... en ninguna parte. Un ajuste clinico que
        // el sistema guarda y despues ignora es una falla silenciosa.
        self.catalog
            .validate_overrides(overrides.as_value())
            .map_err(|e| AppFailure::validation(e.to_string(), Some("overrides")))?;

        let catalog_version = command
            .catalog_version
            .or_else(|| current.as_ref().map(|c| c.catalog_version.clone()))
            .unwrap_or_else(|| self.catalog.version.clone());

        let profile = self.policy.apply_profile(
            resident_id,
            ProfileInput {
                risk_level,
                mobility_aid,
                autopilot,
                mode,
                template_id,
                overrides,
                catalog_version,
            },
            mana_kernel::Id::new(&actor),
            mana_kernel::Instante::now(),
        )?;

        // Publish policy event to NATS (best-effort)
        if let Some(broker) = self.nats() {
            let view = profile_view(profile.clone());
            let event = mana_nats::publisher::PolicyEvent {
                event_type: "profile_updated".to_string(),
                resident_id: resident_id.to_string(),
                policy_type: "alarm_profile".to_string(),
                effective_at: mana_kernel::Instante::now().to_string(),
                payload: serde_json::to_value(&view).unwrap_or_default(),
            };
            if let Err(e) = broker.publish_policy(&event).await {
                tracing::warn!(error = %e, "Failed to publish policy event");
            }
        }

        let effective = self.resolver(Some(&profile));
        Ok(ProfileWithEffectiveView {
            profile: profile_view(profile),
            effective,
        })
    }

    pub async fn apply_recommendation(
        &self,
        token: &str,
        resident_id: &str,
        command: ApplyRecommendationCommand,
    ) -> Result<ProfileVersionView, AppFailure> {
        let actor = required_token(token)?;
        self.aplicar_recomendacion(
            &actor,
            resident_id,
            command.risk_level,
            command.template_id,
            command.overrides,
            command.catalog_version,
        )
    }

    pub async fn apply_recommendations(
        &self,
        token: &str,
        command: ApplyRecommendationsCommand,
    ) -> Result<Vec<ProfileVersionView>, AppFailure> {
        let actor = required_token(token)?;

        let mut results = Vec::with_capacity(command.recommendations.len());
        for rec in command.recommendations {
            results.push(self.aplicar_recomendacion(
                &actor,
                &rec.resident_id,
                rec.risk_level,
                rec.template_id,
                rec.overrides,
                rec.catalog_version,
            )?);
        }

        Ok(results)
    }

    fn aplicar_recomendacion(
        &self,
        actor: &str,
        resident_id: &str,
        risk_level: Option<String>,
        template_id: Option<String>,
        overrides: Option<Overrides>,
        catalog_version: Option<String>,
    ) -> Result<ProfileVersionView, AppFailure> {
        let current = self.policy.get_current(resident_id)?;

        let risk_level = risk_level
            .map(|l| RiskLevel::parse(&l))
            .transpose()
            .map_err(|e| AppFailure::validation(e.to_string(), Some("risk_level")))?
            .or_else(|| current.as_ref().map(|c| c.risk_level))
            .unwrap_or(RiskLevel::Medium);

        let template_id = template_id
            .or_else(|| current.as_ref().map(|c| c.template_id.clone()))
            .unwrap_or_else(|| PLANTILLA_POR_DEFECTO.to_owned());

        let overrides = overrides
            .or_else(|| current.as_ref().map(|c| c.overrides.clone()))
            .unwrap_or_default();

        let catalog_version = catalog_version.unwrap_or_else(|| self.catalog.version.clone());

        let mobility_aid = current
            .as_ref()
            .map(|c| c.mobility_aid)
            .unwrap_or(MobilityAid::None);
        let autopilot = current.as_ref().map(|c| c.autopilot).unwrap_or(false);
        let mode = current.as_ref().map(|c| c.mode).unwrap_or(Mode::Preset);

        let profile = self.policy.apply_profile(
            resident_id,
            ProfileInput {
                risk_level,
                mobility_aid,
                autopilot,
                mode,
                template_id,
                overrides,
                catalog_version,
            },
            mana_kernel::Id::new(actor),
            mana_kernel::Instante::now(),
        )?;

        Ok(profile_view(profile))
    }
}
