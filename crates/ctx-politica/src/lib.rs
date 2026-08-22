pub mod schema;

mod catalogo;
mod error;
mod perfiles;

pub use catalogo::{
    Action, AlarmCatalog, AlarmRule, CatalogError, Class, MobilityAid, ParamDef, ParamOption,
    ParamType, PresetRule, ResolvedRule, RiskFactor, RiskLevel, RuleGroup, RuleSource,
    SensitivityCalibration, Shift, ShiftHours, Template, TemplateRule,
};
pub use error::PoliticaError;
pub use mana_motores::{
    decidir, plantilla_sugerida, recomendar, AutopilotAction, AutopilotDecision, AutopilotInput,
    AutopilotPolicy, AutopilotProfile, AutopilotReason, Banda, Direccion, FactorDeRiesgo,
    PoliticaDeRecomendacion, Recomendacion, ReglaDeRiesgo, Senales,
};
pub use mana_storage::DbPool;
pub use perfiles::{
    new_profile_id, AlarmProfileVersion, Mode, Overrides, PerfilesError, PerfilEfectivoInput,
    ProfileId, ProfileInput,
};

use diesel::prelude::*;
use diesel_migrations::{embed_migrations, EmbeddedMigrations};
use mana_kernel::{Actor, Id, Instante};
use mana_storage::{connection as get_connection, DbConnection};

use crate::perfiles::repo::PerfilesRepo;

pub const MIGRATIONS: EmbeddedMigrations = embed_migrations!();

#[derive(Clone)]
pub struct PolicyStore {
    pub(crate) pool: DbPool,
}

pub fn run_migrations(pool: &DbPool) -> Result<(), PoliticaError> {
    mana_storage::run_migrations(pool, MIGRATIONS).map_err(PoliticaError::from)
}

impl PolicyStore {
    pub fn new(pool: DbPool) -> Self {
        Self { pool }
    }

    pub fn pool(&self) -> &DbPool {
        &self.pool
    }

    fn connection(&self) -> Result<DbConnection, PoliticaError> {
        get_connection(&self.pool).map_err(PoliticaError::from)
    }

    pub fn get_current(
        &self,
        resident_id: &str,
    ) -> Result<Option<AlarmProfileVersion>, PoliticaError> {
        let mut connection = self.connection()?;
        <SqliteConnection as PerfilesRepo>::get_current(&mut connection, resident_id)
    }

    pub fn get_at(
        &self,
        resident_id: &str,
        at: &Instante,
    ) -> Result<Option<AlarmProfileVersion>, PoliticaError> {
        let mut connection = self.connection()?;
        <SqliteConnection as PerfilesRepo>::get_at(&mut connection, resident_id, at)
    }

    /// El perfil vigente en un instante, dentro de una transaccion en curso.
    ///
    /// El motor evalua contra la politica que regia **cuando ocurrio** la
    /// observacion, y tiene que leerla en la misma transaccion que la ingesta.
    pub fn get_at_in_transaction(
        &self,
        connection: &mut SqliteConnection,
        resident_id: &str,
        at: &Instante,
    ) -> Result<Option<AlarmProfileVersion>, PoliticaError> {
        <SqliteConnection as PerfilesRepo>::get_at(connection, resident_id, at)
    }

    pub fn list_history(
        &self,
        resident_id: &str,
    ) -> Result<Vec<AlarmProfileVersion>, PoliticaError> {
        let mut connection = self.connection()?;
        <SqliteConnection as PerfilesRepo>::list_history(&mut connection, resident_id)
    }

    pub fn apply_profile(
        &self,
        resident_id: &str,
        input: ProfileInput,
        actor_id: Id<Actor>,
        now: Instante,
    ) -> Result<AlarmProfileVersion, PoliticaError> {
        let mut connection = self.connection()?;
        <SqliteConnection as PerfilesRepo>::apply_in_transaction(
            &mut connection,
            resident_id,
            input,
            actor_id,
            now,
        )
    }
}

#[cfg(test)]
pub(crate) mod testsupport {
    use mana_storage::build_pool;

    use super::{run_migrations, PolicyStore};

    pub(crate) fn store() -> PolicyStore {
        let pool = build_pool(":memory:").unwrap();
        run_migrations(&pool).unwrap();
        PolicyStore::new(pool)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::testsupport::store;

    fn actor() -> Id<Actor> {
        Id::new("user-1")
    }

    fn t(minute: u32) -> Instante {
        format!("2026-08-18T03:{minute:02}:00.000Z")
            .parse()
            .unwrap()
    }

    fn default_input() -> ProfileInput {
        ProfileInput {
            risk_level: RiskLevel::Medium,
            mobility_aid: MobilityAid::None,
            autopilot: false,
            mode: Mode::Preset,
            template_id: "default".to_owned(),
            overrides: Overrides::empty(),
            catalog_version: "2025.1".to_owned(),
        }
    }

    #[test]
    fn apply_creates_first_version() {
        let store = store();
        let version = store
            .apply_profile("resident-1", default_input(), actor(), t(1))
            .unwrap();
        assert_eq!(version.resident_id, "resident-1");
        assert!(version.valid_to.is_none());
        assert_eq!(version.mobility_aid, MobilityAid::None);
        assert!(!version.autopilot);
    }

    #[test]
    fn apply_closes_previous_and_creates_new() {
        let store = store();
        store
            .apply_profile("resident-1", default_input(), actor(), t(1))
            .unwrap();

        let updated = store
            .apply_profile(
                "resident-1",
                ProfileInput {
                    mobility_aid: MobilityAid::Walker,
                    autopilot: true,
                    ..default_input()
                },
                actor(),
                t(2),
            )
            .unwrap();
        assert!(updated.valid_to.is_none());
        assert_eq!(updated.mobility_aid, MobilityAid::Walker);
        assert!(updated.autopilot);

        let history = store.list_history("resident-1").unwrap();
        assert_eq!(history.len(), 2);
        assert!(history[0].valid_to.is_some());
        assert!(history[1].valid_to.is_none());
    }

    #[test]
    fn get_current_returns_latest() {
        let store = store();
        store
            .apply_profile("resident-1", default_input(), actor(), t(1))
            .unwrap();
        store
            .apply_profile(
                "resident-1",
                ProfileInput {
                    mode: Mode::Custom,
                    ..default_input()
                },
                actor(),
                t(2),
            )
            .unwrap();

        let current = store.get_current("resident-1").unwrap().unwrap();
        assert_eq!(current.mode, Mode::Custom);
    }

    #[test]
    fn get_at_returns_version_valid_at_instant() {
        let store = store();
        store
            .apply_profile("resident-1", default_input(), actor(), t(1))
            .unwrap();
        store
            .apply_profile(
                "resident-1",
                ProfileInput {
                    mobility_aid: MobilityAid::Walker,
                    ..default_input()
                },
                actor(),
                t(5),
            )
            .unwrap();

        let at_v1 = store.get_at("resident-1", &t(2)).unwrap().unwrap();
        assert_eq!(at_v1.mobility_aid, MobilityAid::None);

        let at_v2 = store.get_at("resident-1", &t(6)).unwrap().unwrap();
        assert_eq!(at_v2.mobility_aid, MobilityAid::Walker);
    }

    #[test]
    fn different_residents_independent() {
        let store = store();
        store
            .apply_profile("resident-1", default_input(), actor(), t(1))
            .unwrap();
        store
            .apply_profile(
                "resident-2",
                ProfileInput {
                    mode: Mode::Custom,
                    ..default_input()
                },
                actor(),
                t(2),
            )
            .unwrap();

        let r1 = store.get_current("resident-1").unwrap().unwrap();
        let r2 = store.get_current("resident-2").unwrap().unwrap();
        assert_eq!(r1.mode, Mode::Preset);
        assert_eq!(r2.mode, Mode::Custom);
    }

    #[test]
    fn no_current_returns_none() {
        let store = store();
        let current = store.get_current("resident-no-existe").unwrap();
        assert!(current.is_none());
    }

    #[test]
    fn template_and_overrides_preserved() {
        let store = store();
        let version = store
            .apply_profile(
                "resident-1",
                ProfileInput {
                    template_id: "high-risk".to_owned(),
                    overrides: r#"{"fall":{"enabled":true,"threshold":3}}"#.parse().unwrap(),
                    ..default_input()
                },
                actor(),
                t(1),
            )
            .unwrap();
        assert_eq!(version.template_id, "high-risk");
        let overrides_str = version.overrides.to_string();
        assert!(overrides_str.contains("fall"));
    }

    #[test]
    fn updated_by_records_actor() {
        let store = store();
        let version = store
            .apply_profile("resident-1", default_input(), actor(), t(1))
            .unwrap();
        assert_eq!(version.updated_by.as_deref(), Some("user-1"));
    }
}
