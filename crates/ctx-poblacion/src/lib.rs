//! Padron de residentes, atributos con provenance y asignaciones residente-cama.
//!
//! El crate se divide en subdominios con reglas propias:
//!
//! - [`residentes`]: el residente, su ciclo de vida de admision y sus datos.
//! - [`asignaciones`]: la asociacion residente-cama con su frontera de
//!   consistencia propia (unicidad abierta por lado, intervalos ordenados).
//! - [`atributos`]: afirmaciones fechadas con provenance.
//!
//! `ctx-poblacion` nunca importa Residencia u Observacion: `bed_id` viaja como
//! referencia opaca (`BedRef`) y los cruces los compone `mana-app`.

mod common;
mod error;

pub mod asignaciones;
pub mod atributos;
pub mod residentes;
pub mod schema;

pub use asignaciones::{
    new_assignment_id, AsignacionesError, AssignResult, AssignmentId, BedAssignment, BedRef,
};
pub use atributos::{
    new_attribute_id, AtributosError, AttributeCode, AttributeId, AttributeInput, ResidentAttribute,
};
pub use error::PoblacionError;
pub use mana_storage::DbPool;
pub use residentes::{
    new_resident_id, DischargeResult, Resident, ResidentId, ResidentInput, ResidentStatus,
    ResidentUpdate, ResidentesError,
};

use chrono::NaiveDate;
use diesel::prelude::*;
use diesel_migrations::{embed_migrations, EmbeddedMigrations};
use mana_kernel::{Actor, Id, Instante};
use mana_storage::{connection as get_connection, DbConnection};

use crate::asignaciones::repo::AsignacionesRepo;
use crate::atributos::repo::AtributosRepo;
use crate::residentes::repo::ResidentesRepo;

pub const MIGRATIONS: EmbeddedMigrations = embed_migrations!();

#[derive(Clone)]
pub struct PopulationStore {
    pub(crate) pool: DbPool,
}

pub fn run_migrations(pool: &DbPool) -> Result<(), PoblacionError> {
    mana_storage::run_migrations(pool, MIGRATIONS).map_err(PoblacionError::from)
}

impl PopulationStore {
    pub fn new(pool: DbPool) -> Self {
        Self { pool }
    }

    pub fn pool(&self) -> &DbPool {
        &self.pool
    }

    fn connection(&self) -> Result<DbConnection, PoblacionError> {
        get_connection(&self.pool).map_err(PoblacionError::from)
    }

    pub fn create_resident(
        &self,
        input: ResidentInput,
        now: Instante,
    ) -> Result<Resident, PoblacionError> {
        let id = new_resident_id();
        let mut connection = self.connection()?;
        connection.transaction(|connection| {
            <SqliteConnection as ResidentesRepo>::create_resident_in_transaction(
                connection, id, input, now,
            )
        })
    }

    pub fn create_resident_in_transaction(
        &self,
        connection: &mut SqliteConnection,
        id: ResidentId,
        input: ResidentInput,
        now: Instante,
    ) -> Result<Resident, PoblacionError> {
        <SqliteConnection as ResidentesRepo>::create_resident_in_transaction(
            connection, id, input, now,
        )
    }

    pub fn update_resident(
        &self,
        id: &ResidentId,
        input: ResidentUpdate,
        now: Instante,
    ) -> Result<Resident, PoblacionError> {
        let mut connection = self.connection()?;
        connection.transaction(|connection| {
            <SqliteConnection as ResidentesRepo>::update_resident_in_transaction(
                connection, id, input, now,
            )
        })
    }

    pub fn update_resident_in_transaction(
        &self,
        connection: &mut SqliteConnection,
        id: &ResidentId,
        input: ResidentUpdate,
        now: Instante,
    ) -> Result<Resident, PoblacionError> {
        <SqliteConnection as ResidentesRepo>::update_resident_in_transaction(
            connection, id, input, now,
        )
    }

    pub fn discharge(
        &self,
        id: &ResidentId,
        date: &str,
        by: &str,
        now: Instante,
    ) -> Result<DischargeResult, PoblacionError> {
        let date = parse_discharge_date(date)?;
        let by = Id::<Actor>::new(by);
        let mut connection = self.connection()?;
        connection.transaction(|connection| {
            <SqliteConnection as ResidentesRepo>::discharge_in_transaction(
                connection, id, date, by, now,
            )
        })
    }

    pub fn discharge_in_transaction(
        &self,
        connection: &mut SqliteConnection,
        id: &ResidentId,
        date: NaiveDate,
        by: Id<Actor>,
        now: Instante,
    ) -> Result<DischargeResult, PoblacionError> {
        <SqliteConnection as ResidentesRepo>::discharge_in_transaction(
            connection, id, date, by, now,
        )
    }

    pub fn list_residents(&self, query: Option<&str>) -> Result<Vec<Resident>, PoblacionError> {
        let mut connection = self.connection()?;
        <SqliteConnection as ResidentesRepo>::list_residents(&mut connection, query)
    }

    pub fn get_resident(&self, id: &ResidentId) -> Result<Resident, PoblacionError> {
        let mut connection = self.connection()?;
        <SqliteConnection as ResidentesRepo>::get_resident(&mut connection, id)
    }

    pub fn assign(
        &self,
        resident_id: &ResidentId,
        bed_id: &BedRef,
        starts_at: Instante,
        created_by: Option<Id<Actor>>,
    ) -> Result<AssignResult, PoblacionError> {
        let id = new_assignment_id();
        let mut connection = self.connection()?;
        connection.transaction(|connection| {
            <SqliteConnection as AsignacionesRepo>::assign_in_transaction(
                connection,
                id,
                resident_id.clone(),
                bed_id,
                starts_at,
                created_by,
            )
        })
    }

    pub fn assign_in_transaction(
        &self,
        connection: &mut SqliteConnection,
        id: AssignmentId,
        resident_id: ResidentId,
        bed_id: &BedRef,
        starts_at: Instante,
        created_by: Option<Id<Actor>>,
    ) -> Result<AssignResult, PoblacionError> {
        <SqliteConnection as AsignacionesRepo>::assign_in_transaction(
            connection,
            id,
            resident_id,
            bed_id,
            starts_at,
            created_by,
        )
    }

    pub fn release(
        &self,
        bed_id: &BedRef,
        ends_at: Instante,
    ) -> Result<BedAssignment, PoblacionError> {
        let mut connection = self.connection()?;
        connection.transaction(|connection| {
            <SqliteConnection as AsignacionesRepo>::release_in_transaction(
                connection, bed_id, ends_at,
            )
        })
    }

    pub fn release_in_transaction(
        &self,
        connection: &mut SqliteConnection,
        bed_id: &BedRef,
        ends_at: Instante,
    ) -> Result<BedAssignment, PoblacionError> {
        <SqliteConnection as AsignacionesRepo>::release_in_transaction(connection, bed_id, ends_at)
    }

    pub fn close_open_for_resident_in_transaction(
        &self,
        connection: &mut SqliteConnection,
        resident_id: &ResidentId,
        ends_at: Instante,
    ) -> Result<Option<BedAssignment>, PoblacionError> {
        <SqliteConnection as AsignacionesRepo>::close_open_for_resident_in_transaction(
            connection,
            resident_id,
            ends_at,
        )
    }

    pub fn list_assignments(
        &self,
        resident_id: &ResidentId,
    ) -> Result<Vec<BedAssignment>, PoblacionError> {
        let mut connection = self.connection()?;
        <SqliteConnection as AsignacionesRepo>::list_assignments(&mut connection, resident_id)
    }

    pub fn list_open_assignments(&self) -> Result<Vec<BedAssignment>, PoblacionError> {
        let mut connection = self.connection()?;
        <SqliteConnection as AsignacionesRepo>::list_open_assignments(&mut connection)
    }

    pub fn list_open_assignments_in_transaction(
        &self,
        connection: &mut SqliteConnection,
    ) -> Result<Vec<BedAssignment>, PoblacionError> {
        <SqliteConnection as AsignacionesRepo>::list_open_assignments(connection)
    }

    pub fn open_assignment_for_resident_in_transaction(
        &self,
        connection: &mut SqliteConnection,
        resident_id: &ResidentId,
    ) -> Result<Option<BedAssignment>, PoblacionError> {
        <SqliteConnection as AsignacionesRepo>::open_assignment_for_resident(
            connection,
            resident_id,
        )
    }

    pub fn create_attribute(
        &self,
        id: AttributeId,
        input: AttributeInput,
        now: Instante,
    ) -> Result<ResidentAttribute, PoblacionError> {
        let mut connection = self.connection()?;
        connection.transaction(|connection| {
            <SqliteConnection as AtributosRepo>::create_attribute_in_transaction(
                connection, id, input, now,
            )
        })
    }

    pub fn list_attributes(
        &self,
        resident_id: &ResidentId,
    ) -> Result<Vec<ResidentAttribute>, PoblacionError> {
        let mut connection = self.connection()?;
        <SqliteConnection as AtributosRepo>::list_attributes(&mut connection, resident_id)
    }
}

fn parse_discharge_date(value: &str) -> Result<NaiveDate, PoblacionError> {
    crate::residentes::parse_date(value, "discharged_at").map_err(PoblacionError::from)
}

#[cfg(test)]
pub(crate) mod testsupport {
    use mana_kernel::Instante;
    use mana_storage::build_pool;

    use super::{run_migrations, PopulationStore};

    pub(crate) fn instant() -> Instante {
        "2026-08-18T12:00:00.000Z".parse().unwrap()
    }

    pub(crate) fn store() -> PopulationStore {
        let pool = build_pool(":memory:").unwrap();
        run_migrations(&pool).unwrap();
        PopulationStore::new(pool)
    }
}

#[cfg(test)]
mod egreso_cierra_la_asignacion {
    use super::*;
    use crate::testsupport::{instant, store};

    /// Dos asignaciones creadas en el **mismo** instante empatan en
    /// `starts_at`. El egreso preguntaba "¿la ultima esta abierta?" y con el
    /// empate a veces le tocaba la ya cerrada: no cerraba nada, la cama quedaba
    /// ocupada y liberarla devolvia 200 en vez del 409 que corresponde.
    #[test]
    fn discharging_closes_the_open_assignment_even_when_starts_at_ties() {
        let store = store();
        let now = instant();
        let resident = store
            .create_resident(
                ResidentInput {
                    full_name: "Ana Test".to_owned(),
                    external_id: None,
                    birth_date: None,
                    admission_date: Some("2025-01-01".to_owned()),
                },
                now,
            )
            .unwrap();

        let bed_one = BedRef::new("bed-1").unwrap();
        let bed_two = BedRef::new("bed-2").unwrap();
        store.assign(&resident.id, &bed_one, now, None).unwrap();
        // Mudanza en el mismo instante: cierra la de bed-1 y abre la de bed-2.
        store.assign(&resident.id, &bed_two, now, None).unwrap();

        let discharged = store
            .discharge(&resident.id, "2025-02-01", "user-1", now)
            .unwrap();

        let closed = discharged
            .closed_assignment
            .expect("el egreso tiene que cerrar la asignacion abierta");
        assert_eq!(closed.bed_id.as_str(), "bed-2");

        // Y la cama queda libre: liberarla de nuevo es el 409 deliberado.
        assert!(store.release(&bed_two, now).is_err());
    }
}
