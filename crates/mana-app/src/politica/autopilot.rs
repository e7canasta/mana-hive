use ctx_politica::{MobilityAid, Mode, Overrides, ProfileInput, RiskLevel};
use mana_kernel::Instante;
use mana_motores::{decidir, AutopilotAction, AutopilotInput, AutopilotProfile};

use crate::{
    error::AppFailure,
    identidad::required_token,
    state::AppState,
};

use super::helpers::profile_view;
use super::views::ProfileVersionView;

impl AppState {
    /// Activa o desactiva autopilot para todos los residentes activos. Esta es
    /// la operacion de configuracion masiva; una llamada sin `enabled` ejecuta
    /// el motor y queda separada en `autopilot`.
    pub async fn set_autopilot_for_all(
        &self,
        token: &str,
        enabled: bool,
    ) -> Result<Vec<ProfileVersionView>, AppFailure> {
        let actor = required_token(token)?;
        let current = self.list_alarm_profiles(token, "").await?;
        let now = Instante::now();
        let mut results = Vec::new();

        for profile in current.profiles {
            if profile.profile.autopilot == enabled {
                continue;
            }
            let risk_level = RiskLevel::parse(&profile.profile.risk_level)
                .map_err(|error| AppFailure::validation(error.to_string(), Some("risk_level")))?;
            let mobility_aid = MobilityAid::parse(&profile.profile.mobility_aid)
                .map_err(|error| AppFailure::validation(error.to_string(), Some("mobility_aid")))?;
            let mode = Mode::parse(&profile.profile.mode)
                .map_err(|error| AppFailure::validation(error.to_string(), Some("mode")))?;
            let template_id = profile.profile.template_id;
            let overrides: Overrides =
                serde_json::from_value(profile.profile.overrides.clone()).map_err(|error| {
                    AppFailure::new(mana_kernel::Fallo::InternalError, error.to_string())
                })?;

            let updated = self.policy.apply_profile(
                &profile.resident.id,
                ProfileInput {
                    risk_level,
                    mobility_aid,
                    autopilot: enabled,
                    mode,
                    template_id,
                    overrides,
                    catalog_version: self.catalog.version.clone(),
                },
                mana_kernel::Id::new(&actor),
                now,
            )?;
            results.push(profile_view(updated));
        }

        Ok(results)
    }

    /// Ejecuta autopilot sobre los residentes activos que lo tienen habilitado.
    ///
    /// La hidratacion de senales y la persistencia viven aca; la politica de
    /// aplicacion vive en `mana-motores`. Una propuesta de bajada o una ventana
    /// sin evidencia no se persiste como si hubiera sido una decision aplicada.
    pub async fn autopilot(&self, token: &str) -> Result<Vec<ProfileVersionView>, AppFailure> {
        required_token(token)?;
        let now = mana_kernel::Instante::now();

        let residents = self.poblacion.list_residents(None)?;
        let mut results = Vec::new();
        for resident in residents
            .into_iter()
            .filter(|r| r.status == ctx_poblacion::ResidentStatus::Active)
        {
            let Some(profile) = self.policy.get_current(resident.id.as_str())? else {
                continue;
            };
            if !profile.autopilot {
                continue;
            }

            let recommendation =
                self.recomendacion_para(resident.id.as_str(), profile.mobility_aid, &now)?;
            let decision = decidir(
                &AutopilotInput {
                    current_profile: AutopilotProfile {
                        risk_level: profile.risk_level,
                        autopilot: profile.autopilot,
                    },
                    recommendation,
                    last_change_at: Some(profile.valid_from),
                    now,
                },
                &self.catalog.autopilot,
            );
            if decision.action != AutopilotAction::Apply {
                continue;
            }

            let new_profile = self.policy.apply_profile(
                resident.id.as_str(),
                ProfileInput {
                    risk_level: decision.recommended_level,
                    mobility_aid: profile.mobility_aid,
                    autopilot: true,
                    mode: profile.mode,
                    template_id: profile.template_id,
                    overrides: profile.overrides.clone(),
                    catalog_version: profile.catalog_version,
                },
                mana_kernel::Id::new("autopilot"),
                now,
            )?;
            results.push(profile_view(new_profile));
        }

        Ok(results)
    }
}
