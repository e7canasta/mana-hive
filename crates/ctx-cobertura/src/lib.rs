//! Contexto de cobertura: grupos de staff, grilla laboral y cobertura temporal.
//!
//! El crate se divide en subdominios:
//!
//! - [`grupos`]: grupos de staff y membresias temporales.
//! - [`turnos`]: grilla de turnos laborales por facility.
//! - [`cobertura`]: asignacion temporal de un grupo a un ala y turno.
//!
//! `ctx-cobertura` nunca importa Residencia, Identidad ni Observacion:
//! los IDs viajan como referencias opacas y los cruces los compone `mana-app`.

mod common;
mod error;

pub mod cobertura;
pub mod grupos;
pub mod schema;
pub mod turnos;

pub use cobertura::{
    new_coverage_id, CoberturaDomainError, CoverageId, CoverageInput, CoverageResult, WingCoverage,
};
pub use error::CoberturaError;
pub use grupos::{
    new_group_id, new_membership_id, GruposError, MembershipId, MembershipInput, StaffGroup,
    StaffGroupId, StaffGroupInput, StaffGroupMembership, StaffGroupUpdate,
};
pub use mana_storage::DbPool;
pub use turnos::{
    new_shift_id, FacilityShift, ReplaceGridResult, ShiftGrid, ShiftId, ShiftInput, TurnosError,
};

use diesel::prelude::*;
use diesel_migrations::{embed_migrations, EmbeddedMigrations};
use mana_kernel::{Actor, Id, Instante};
use mana_storage::{connection as get_connection, DbConnection};

use crate::cobertura::repo::CoberturaRepo;
use crate::grupos::repo::GruposRepo;
use crate::turnos::repo::TurnosRepo;

pub const MIGRATIONS: EmbeddedMigrations = embed_migrations!();

#[derive(Clone)]
pub struct CoverageStore {
    pub(crate) pool: DbPool,
}

pub fn run_migrations(pool: &DbPool) -> Result<(), CoberturaError> {
    mana_storage::run_migrations(pool, MIGRATIONS).map_err(CoberturaError::from)
}

impl CoverageStore {
    pub fn new(pool: DbPool) -> Self {
        Self { pool }
    }

    pub fn pool(&self) -> &DbPool {
        &self.pool
    }

    fn connection(&self) -> Result<DbConnection, CoberturaError> {
        get_connection(&self.pool).map_err(CoberturaError::from)
    }

    // -- Groups --

    pub fn create_group(
        &self,
        input: StaffGroupInput,
        now: Instante,
    ) -> Result<StaffGroup, CoberturaError> {
        let id = new_group_id();
        let mut connection = self.connection()?;
        connection.transaction(|c| {
            <SqliteConnection as GruposRepo>::create_group_in_transaction(c, id, input, now)
        })
    }

    pub fn update_group(
        &self,
        id: &StaffGroupId,
        input: StaffGroupUpdate,
        now: Instante,
    ) -> Result<StaffGroup, CoberturaError> {
        let mut connection = self.connection()?;
        connection.transaction(|c| {
            <SqliteConnection as GruposRepo>::update_group_in_transaction(c, id, input, now)
        })
    }

    pub fn retire_group(
        &self,
        id: &StaffGroupId,
        by: Id<Actor>,
        now: Instante,
    ) -> Result<StaffGroup, CoberturaError> {
        let mut connection = self.connection()?;
        connection.transaction(|c| {
            <SqliteConnection as GruposRepo>::retire_group_in_transaction(c, id, by, now)
        })
    }

    pub fn get_group(&self, id: &StaffGroupId) -> Result<StaffGroup, CoberturaError> {
        let mut connection = self.connection()?;
        <SqliteConnection as GruposRepo>::get_group(&mut connection, id)
    }

    pub fn list_groups(&self, facility_id: &str) -> Result<Vec<StaffGroup>, CoberturaError> {
        let mut connection = self.connection()?;
        <SqliteConnection as GruposRepo>::list_groups(&mut connection, facility_id)
    }

    pub fn replace_members(
        &self,
        group_id: &StaffGroupId,
        members: Vec<MembershipInput>,
        now: Instante,
    ) -> Result<Vec<StaffGroupMembership>, CoberturaError> {
        let mut connection = self.connection()?;
        connection.transaction(|c| {
            <SqliteConnection as GruposRepo>::replace_members_in_transaction(
                c, group_id, members, now,
            )
        })
    }

    pub fn list_members(
        &self,
        group_id: &StaffGroupId,
        at: Option<&Instante>,
    ) -> Result<Vec<StaffGroupMembership>, CoberturaError> {
        let mut connection = self.connection()?;
        <SqliteConnection as GruposRepo>::list_members(&mut connection, group_id, at)
    }

    // -- Shifts --

    pub fn get_grid(&self, facility_id: &str) -> Result<ShiftGrid, CoberturaError> {
        let mut connection = self.connection()?;
        <SqliteConnection as TurnosRepo>::get_grid(&mut connection, facility_id)
    }

    pub fn replace_grid(
        &self,
        facility_id: &str,
        shifts: Vec<ShiftInput>,
        now: Instante,
    ) -> Result<ReplaceGridResult, CoberturaError> {
        let mut connection = self.connection()?;
        connection.transaction(|c| {
            <SqliteConnection as TurnosRepo>::replace_grid_in_transaction(
                c,
                facility_id,
                shifts,
                now,
            )
        })
    }

    pub fn list_shifts(&self, facility_id: &str) -> Result<Vec<FacilityShift>, CoberturaError> {
        let mut connection = self.connection()?;
        <SqliteConnection as TurnosRepo>::list_shifts(&mut connection, facility_id)
    }

    // -- Coverage --

    pub fn assign_coverage(
        &self,
        input: CoverageInput,
        now: Instante,
        by: Option<Id<Actor>>,
    ) -> Result<CoverageResult, CoberturaError> {
        let mut connection = self.connection()?;
        connection.transaction(|c| {
            <SqliteConnection as CoberturaRepo>::assign_coverage_in_transaction(c, input, now, by)
        })
    }

    pub fn clear_coverage(
        &self,
        wing_id: &str,
        shift_key: &str,
        now: Instante,
    ) -> Result<WingCoverage, CoberturaError> {
        let mut connection = self.connection()?;
        connection.transaction(|c| {
            <SqliteConnection as CoberturaRepo>::clear_coverage_in_transaction(
                c, wing_id, shift_key, now,
            )
        })
    }

    pub fn get_coverage(
        &self,
        wing_id: &str,
        at: &Instante,
    ) -> Result<Vec<WingCoverage>, CoberturaError> {
        let mut connection = self.connection()?;
        <SqliteConnection as CoberturaRepo>::get_coverage(&mut connection, wing_id, at)
    }

    pub fn list_coverages(&self, wing_id: &str) -> Result<Vec<WingCoverage>, CoberturaError> {
        let mut connection = self.connection()?;
        <SqliteConnection as CoberturaRepo>::list_coverages(&mut connection, wing_id)
    }
}

#[cfg(test)]
pub(crate) mod testsupport {
    use mana_kernel::Instante;
    use mana_storage::build_pool;

    use super::{run_migrations, CoverageStore};

    pub(crate) fn instant() -> Instante {
        "2026-08-18T12:00:00.000Z".parse().unwrap()
    }

    pub(crate) fn store() -> CoverageStore {
        let pool = build_pool(":memory:").unwrap();
        run_migrations(&pool).unwrap();
        CoverageStore::new(pool)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::testsupport::{instant, store};

    #[test]
    fn create_list_and_get_groups() {
        let store = store();
        let group = store
            .create_group(
                StaffGroupInput {
                    facility_id: "facility-1".to_owned(),
                    name: "Enfermeria A".to_owned(),
                },
                instant(),
            )
            .unwrap();
        assert_eq!(group.name, "Enfermeria A");

        let groups = store.list_groups("facility-1").unwrap();
        assert_eq!(groups.len(), 1);

        let fetched = store.get_group(&group.id).unwrap();
        assert_eq!(fetched.id, group.id);
    }

    #[test]
    fn duplicate_group_name_conflicts() {
        let store = store();
        store
            .create_group(
                StaffGroupInput {
                    facility_id: "facility-1".to_owned(),
                    name: "Enfermeria".to_owned(),
                },
                instant(),
            )
            .unwrap();
        assert!(matches!(
            store.create_group(
                StaffGroupInput {
                    facility_id: "facility-1".to_owned(),
                    name: "Enfermeria".to_owned(),
                },
                instant(),
            ),
            Err(CoberturaError::Conflict)
        ));
    }

    #[test]
    fn replace_members_preserves_history() {
        let store = store();
        let group = store
            .create_group(
                StaffGroupInput {
                    facility_id: "facility-1".to_owned(),
                    name: "Team A".to_owned(),
                },
                instant(),
            )
            .unwrap();
        let members = store
            .replace_members(
                &group.id,
                vec![MembershipInput {
                    user_id: "user-1".to_owned(),
                    valid_from: instant(),
                }],
                instant(),
            )
            .unwrap();
        assert_eq!(members.len(), 1);
        assert_eq!(members[0].user_id, "user-1");
        assert!(members[0].valid_to.is_none());

        // Replace with different member
        let members2 = store
            .replace_members(
                &group.id,
                vec![MembershipInput {
                    user_id: "user-2".to_owned(),
                    valid_from: instant(),
                }],
                instant(),
            )
            .unwrap();
        assert_eq!(members2.len(), 1);
        assert_eq!(members2[0].user_id, "user-2");

        // Only current members returned
        let current = store.list_members(&group.id, None).unwrap();
        assert_eq!(current.len(), 1);
        assert_eq!(current[0].user_id, "user-2");
    }

    #[test]
    fn replace_grid_and_clear_dependent_coverages() {
        let store = store();
        // Create initial grid with 3 shifts
        let result = store
            .replace_grid(
                "facility-1",
                vec![
                    ShiftInput {
                        key: "morning".to_owned(),
                        label: "Morning".to_owned(),
                        start_minute: 480,
                    },
                    ShiftInput {
                        key: "afternoon".to_owned(),
                        label: "Afternoon".to_owned(),
                        start_minute: 840,
                    },
                    ShiftInput {
                        key: "night".to_owned(),
                        label: "Night".to_owned(),
                        start_minute: 0,
                    },
                ],
                instant(),
            )
            .unwrap();
        assert_eq!(result.grid.shifts.len(), 3);
        assert_eq!(result.coverages_cleared, 0);

        // Assign coverage for night (not morning, which stays)
        store
            .assign_coverage(
                CoverageInput {
                    wing_id: "wing-1".to_owned(),
                    staff_group_id: Some("group-1".to_owned()),
                    shift_key: "night".to_owned(),
                },
                instant(),
                None,
            )
            .unwrap();

        // Replace grid with only 2 shifts (removes night)
        let result2 = store
            .replace_grid(
                "facility-1",
                vec![
                    ShiftInput {
                        key: "morning".to_owned(),
                        label: "Morning".to_owned(),
                        start_minute: 480,
                    },
                    ShiftInput {
                        key: "afternoon".to_owned(),
                        label: "Afternoon".to_owned(),
                        start_minute: 840,
                    },
                ],
                instant(),
            )
            .unwrap();
        assert_eq!(result2.grid.shifts.len(), 2);
        // morning coverage was open, gets closed because shift was removed
        assert_eq!(result2.coverages_cleared, 1);
    }

    #[test]
    fn assign_and_clear_coverage() {
        let store = store();
        store
            .replace_grid(
                "facility-1",
                vec![ShiftInput {
                    key: "morning".to_owned(),
                    label: "Morning".to_owned(),
                    start_minute: 480,
                }],
                instant(),
            )
            .unwrap();

        let result = store
            .assign_coverage(
                CoverageInput {
                    wing_id: "wing-1".to_owned(),
                    staff_group_id: Some("group-1".to_owned()),
                    shift_key: "morning".to_owned(),
                },
                instant(),
                None,
            )
            .unwrap();
        assert!(result.closed_previous.is_none());
        assert_eq!(result.coverage.wing_id, "wing-1");

        // Assign again closes previous
        let result2 = store
            .assign_coverage(
                CoverageInput {
                    wing_id: "wing-1".to_owned(),
                    staff_group_id: Some("group-2".to_owned()),
                    shift_key: "morning".to_owned(),
                },
                instant(),
                None,
            )
            .unwrap();
        assert!(result2.closed_previous.is_some());

        // Clear
        let cleared = store
            .clear_coverage("wing-1", "morning", instant())
            .unwrap();
        assert!(cleared.valid_to.is_some());
    }

    #[test]
    fn empty_grid_is_rejected() {
        let store = store();
        assert!(matches!(
            store.replace_grid("facility-1", vec![], instant()),
            Err(CoberturaError::Turnos(TurnosError::EmptyGrid))
        ));
    }
}
