//! Estructura fisica del hogar: residencias, alas, habitaciones y camas.
//!
//! El crate se divide en subdominios con reglas propias:
//!
//! - [`estructura`]: facilities, wings, rooms, beds y su retiro logico.
//! - [`planograma`]: disposicion espacial de las habitaciones por ala.
//! - [`privacidad`]: regiones de enmascaramiento por habitacion.
//! - [`proyecciones`]: read models que cruzan los agregados para la UI.

mod common;
mod error;

pub mod estructura;
pub mod planograma;
pub mod privacidad;
pub mod proyecciones;
pub mod schema;

pub use error::ResidenceError;
pub use estructura::{
    new_bed_id, new_facility_id, new_room_id, new_wing_id, Bed, BedId, BedInput, BedUpdate,
    Facility, FacilityId, FacilityInput, FacilityTree, FacilityUpdate, MonitorKey, Room, RoomId,
    RoomInput, RoomUpdate, StreamKey, Wing, WingId, WingInput, WingUpdate,
};
pub use mana_storage::DbPool;
pub use planograma::{new_planogram_id, PlanogramEntry, PlanogramPlacementInput};
pub use privacidad::{
    new_privacy_region_id, PrivacyRegion, PrivacyRegionInput, MAX_PRIVACY_REGIONS,
};
pub use proyecciones::ResidenceBed;

use diesel::prelude::*;
use diesel_migrations::{embed_migrations, EmbeddedMigrations};
use mana_kernel::Instante;
use mana_storage::{connection as get_connection, DbConnection};

use crate::estructura::repo::EstructuraRepo;
use crate::planograma::repo::PlanogramaRepo;
use crate::privacidad::repo::PrivacidadRepo;

pub const MIGRATIONS: EmbeddedMigrations = embed_migrations!();

#[derive(Clone)]
pub struct ResidenceStore {
    pub(crate) pool: DbPool,
}

pub fn run_migrations(pool: &DbPool) -> Result<(), ResidenceError> {
    mana_storage::run_migrations(pool, MIGRATIONS).map_err(ResidenceError::from)
}

impl ResidenceStore {
    pub fn new(pool: DbPool) -> Self {
        Self { pool }
    }

    fn connection(&self) -> Result<DbConnection, ResidenceError> {
        get_connection(&self.pool).map_err(ResidenceError::from)
    }

    pub fn create_facility(
        &self,
        input: FacilityInput,
        now: Instante,
    ) -> Result<Facility, ResidenceError> {
        let id = new_facility_id();
        let mut connection = self.connection()?;
        connection.transaction(|connection| {
            <SqliteConnection as EstructuraRepo>::create_facility_in_transaction(
                connection, id, input, now,
            )
        })
    }

    pub fn create_facility_in_transaction(
        &self,
        connection: &mut SqliteConnection,
        id: FacilityId,
        input: FacilityInput,
        now: Instante,
    ) -> Result<Facility, ResidenceError> {
        <SqliteConnection as EstructuraRepo>::create_facility_in_transaction(
            connection, id, input, now,
        )
    }

    pub fn list_facilities(&self) -> Result<Vec<Facility>, ResidenceError> {
        let mut connection = self.connection()?;
        <SqliteConnection as EstructuraRepo>::list_facilities(&mut connection)
    }

    pub fn get_facility(&self, id: &FacilityId) -> Result<Facility, ResidenceError> {
        let mut connection = self.connection()?;
        <SqliteConnection as EstructuraRepo>::get_facility(&mut connection, id)
    }

    pub fn facility_tree(&self, id: &FacilityId) -> Result<FacilityTree, ResidenceError> {
        let mut connection = self.connection()?;
        <SqliteConnection as EstructuraRepo>::facility_tree(&mut connection, id)
    }

    pub fn update_facility(
        &self,
        id: &FacilityId,
        input: FacilityUpdate,
        now: Instante,
    ) -> Result<Facility, ResidenceError> {
        let mut connection = self.connection()?;
        connection.transaction(|connection| {
            <SqliteConnection as EstructuraRepo>::update_facility_in_transaction(
                connection, id, input, now,
            )
        })
    }

    pub fn update_facility_in_transaction(
        &self,
        connection: &mut SqliteConnection,
        id: &FacilityId,
        input: FacilityUpdate,
        now: Instante,
    ) -> Result<Facility, ResidenceError> {
        <SqliteConnection as EstructuraRepo>::update_facility_in_transaction(
            connection, id, input, now,
        )
    }

    pub fn create_wing(&self, input: WingInput, now: Instante) -> Result<Wing, ResidenceError> {
        let id = new_wing_id();
        let mut connection = self.connection()?;
        connection.transaction(|connection| {
            <SqliteConnection as EstructuraRepo>::create_wing_in_transaction(
                connection, id, input, now,
            )
        })
    }

    pub fn create_wing_in_transaction(
        &self,
        connection: &mut SqliteConnection,
        id: WingId,
        input: WingInput,
        now: Instante,
    ) -> Result<Wing, ResidenceError> {
        <SqliteConnection as EstructuraRepo>::create_wing_in_transaction(connection, id, input, now)
    }

    pub fn list_wings(&self, facility_id: &FacilityId) -> Result<Vec<Wing>, ResidenceError> {
        let mut connection = self.connection()?;
        <SqliteConnection as EstructuraRepo>::list_wings(&mut connection, facility_id)
    }

    pub fn list_wings_all(&self) -> Result<Vec<Wing>, ResidenceError> {
        let mut connection = self.connection()?;
        <SqliteConnection as EstructuraRepo>::list_wings_all(&mut connection)
    }

    pub fn get_wing(&self, id: &WingId) -> Result<Wing, ResidenceError> {
        let mut connection = self.connection()?;
        <SqliteConnection as EstructuraRepo>::get_wing(&mut connection, id)
    }

    pub fn update_wing(
        &self,
        id: &WingId,
        input: WingUpdate,
        now: Instante,
    ) -> Result<Wing, ResidenceError> {
        let mut connection = self.connection()?;
        connection.transaction(|connection| {
            <SqliteConnection as EstructuraRepo>::update_wing_in_transaction(
                connection, id, input, now,
            )
        })
    }

    pub fn update_wing_in_transaction(
        &self,
        connection: &mut SqliteConnection,
        id: &WingId,
        input: WingUpdate,
        now: Instante,
    ) -> Result<Wing, ResidenceError> {
        <SqliteConnection as EstructuraRepo>::update_wing_in_transaction(connection, id, input, now)
    }

    pub fn create_room(&self, input: RoomInput, now: Instante) -> Result<Room, ResidenceError> {
        let id = new_room_id();
        let mut connection = self.connection()?;
        connection.transaction(|connection| {
            <SqliteConnection as EstructuraRepo>::create_room_in_transaction(
                connection, id, input, now,
            )
        })
    }

    pub fn create_room_in_transaction(
        &self,
        connection: &mut SqliteConnection,
        id: RoomId,
        input: RoomInput,
        now: Instante,
    ) -> Result<Room, ResidenceError> {
        <SqliteConnection as EstructuraRepo>::create_room_in_transaction(connection, id, input, now)
    }

    pub fn list_rooms(&self, wing_id: &WingId) -> Result<Vec<Room>, ResidenceError> {
        let mut connection = self.connection()?;
        <SqliteConnection as EstructuraRepo>::list_rooms(&mut connection, wing_id)
    }

    pub fn get_room(&self, id: &RoomId) -> Result<Room, ResidenceError> {
        let mut connection = self.connection()?;
        <SqliteConnection as EstructuraRepo>::get_room(&mut connection, id)
    }

    pub fn update_room(
        &self,
        id: &RoomId,
        input: RoomUpdate,
        now: Instante,
    ) -> Result<Room, ResidenceError> {
        let mut connection = self.connection()?;
        connection.transaction(|connection| {
            <SqliteConnection as EstructuraRepo>::update_room_in_transaction(
                connection, id, input, now,
            )
        })
    }

    pub fn update_room_in_transaction(
        &self,
        connection: &mut SqliteConnection,
        id: &RoomId,
        input: RoomUpdate,
        now: Instante,
    ) -> Result<Room, ResidenceError> {
        <SqliteConnection as EstructuraRepo>::update_room_in_transaction(connection, id, input, now)
    }

    pub fn create_bed(&self, input: BedInput, now: Instante) -> Result<Bed, ResidenceError> {
        let id = new_bed_id();
        let mut connection = self.connection()?;
        connection.transaction(|connection| {
            <SqliteConnection as EstructuraRepo>::create_bed_in_transaction(
                connection, id, input, now,
            )
        })
    }

    pub fn create_bed_in_transaction(
        &self,
        connection: &mut SqliteConnection,
        id: BedId,
        input: BedInput,
        now: Instante,
    ) -> Result<Bed, ResidenceError> {
        <SqliteConnection as EstructuraRepo>::create_bed_in_transaction(connection, id, input, now)
    }

    pub fn list_beds(&self, room_id: &RoomId) -> Result<Vec<Bed>, ResidenceError> {
        let mut connection = self.connection()?;
        <SqliteConnection as EstructuraRepo>::list_beds(&mut connection, room_id)
    }

    pub fn get_bed(&self, id: &BedId) -> Result<Bed, ResidenceError> {
        let mut connection = self.connection()?;
        <SqliteConnection as EstructuraRepo>::get_bed(&mut connection, id)
    }

    /// Valida que la cama exista y este activa, reusando la conexion de la
    /// transaccion compuesta. Lo usa `mana-app` para validar el lado
    /// Residencia de una asignacion de Poblacion.
    pub fn find_bed_by_monitor_key_in_transaction(
        &self,
        connection: &mut SqliteConnection,
        monitor_key: &str,
    ) -> Result<Option<Bed>, ResidenceError> {
        <SqliteConnection as EstructuraRepo>::find_bed_by_monitor_key(connection, monitor_key)
    }

    /// La zona horaria de la residencia a la que pertenece una cama.
    ///
    /// El turno de una alarma se decide en **hora local de la residencia**, no
    /// en la del server: una alarma nocturna configurada en Buenos Aires no
    /// puede depender de UTC.
    pub fn facility_timezone_for_bed_in_transaction(
        &self,
        connection: &mut SqliteConnection,
        bed_id: &str,
    ) -> Result<Option<String>, ResidenceError> {
        use crate::schema::{beds, facilities, rooms, wings};
        let timezone: Option<String> = beds::table
            .inner_join(rooms::table.on(rooms::id.eq(beds::room_id)))
            .inner_join(wings::table.on(wings::id.eq(rooms::wing_id)))
            .inner_join(facilities::table.on(facilities::id.eq(wings::facility_id)))
            .filter(beds::id.eq(bed_id))
            .select(facilities::timezone)
            .first(connection)
            .optional()?;
        Ok(timezone)
    }

    pub fn ensure_bed_active_in_transaction(
        &self,
        connection: &mut SqliteConnection,
        id: &BedId,
    ) -> Result<(), ResidenceError> {
        crate::estructura::sqlite::ensure_bed_active(connection, id)
    }

    pub fn update_bed(
        &self,
        id: &BedId,
        input: BedUpdate,
        now: Instante,
    ) -> Result<Bed, ResidenceError> {
        let mut connection = self.connection()?;
        connection.transaction(|connection| {
            <SqliteConnection as EstructuraRepo>::update_bed_in_transaction(
                connection, id, input, now,
            )
        })
    }

    pub fn update_bed_in_transaction(
        &self,
        connection: &mut SqliteConnection,
        id: &BedId,
        input: BedUpdate,
        now: Instante,
    ) -> Result<Bed, ResidenceError> {
        <SqliteConnection as EstructuraRepo>::update_bed_in_transaction(connection, id, input, now)
    }

    pub fn list_wings_overview(
        &self,
    ) -> Result<Vec<crate::proyecciones::WingOverview>, ResidenceError> {
        let mut connection = self.connection()?;
        crate::proyecciones::sqlite::list_wings_overview(&mut connection)
    }

    pub fn list_beds_all(&self) -> Result<Vec<ResidenceBed>, ResidenceError> {
        let mut connection = self.connection()?;
        crate::proyecciones::sqlite::list_beds_all(&mut connection)
    }

    pub fn planogram(&self, wing_id: &WingId) -> Result<Vec<PlanogramEntry>, ResidenceError> {
        let mut connection = self.connection()?;
        <SqliteConnection as PlanogramaRepo>::planogram(&mut connection, wing_id)
    }

    pub fn save_planogram(
        &self,
        wing_id: &WingId,
        inputs: Vec<PlanogramPlacementInput>,
        now: Instante,
    ) -> Result<Vec<PlanogramEntry>, ResidenceError> {
        let mut connection = self.connection()?;
        connection.transaction(|connection| {
            <SqliteConnection as PlanogramaRepo>::save_planogram_in_transaction(
                connection, wing_id, inputs, now,
            )
        })
    }

    pub fn save_planogram_in_transaction(
        &self,
        connection: &mut SqliteConnection,
        wing_id: &WingId,
        inputs: Vec<PlanogramPlacementInput>,
        now: Instante,
    ) -> Result<Vec<PlanogramEntry>, ResidenceError> {
        <SqliteConnection as PlanogramaRepo>::save_planogram_in_transaction(
            connection, wing_id, inputs, now,
        )
    }

    pub fn privacy_regions(&self, room_id: &RoomId) -> Result<Vec<PrivacyRegion>, ResidenceError> {
        let mut connection = self.connection()?;
        <SqliteConnection as PrivacidadRepo>::privacy_regions(&mut connection, room_id)
    }

    pub fn save_privacy_regions(
        &self,
        room_id: &RoomId,
        inputs: Vec<PrivacyRegionInput>,
        now: Instante,
    ) -> Result<Vec<PrivacyRegion>, ResidenceError> {
        let mut connection = self.connection()?;
        connection.transaction(|connection| {
            <SqliteConnection as PrivacidadRepo>::save_privacy_regions_in_transaction(
                connection, room_id, inputs, now,
            )
        })
    }

    pub fn save_privacy_regions_in_transaction(
        &self,
        connection: &mut SqliteConnection,
        room_id: &RoomId,
        inputs: Vec<PrivacyRegionInput>,
        now: Instante,
    ) -> Result<Vec<PrivacyRegion>, ResidenceError> {
        <SqliteConnection as PrivacidadRepo>::save_privacy_regions_in_transaction(
            connection, room_id, inputs, now,
        )
    }
}

#[cfg(test)]
pub(crate) mod testsupport {
    use mana_kernel::Instante;
    use mana_storage::build_pool;

    use super::{run_migrations, ResidenceStore};

    pub(crate) fn instant() -> Instante {
        "2026-08-18T12:00:00.000Z".parse().unwrap()
    }

    pub(crate) fn store() -> ResidenceStore {
        let pool = build_pool(":memory:").unwrap();
        run_migrations(&pool).unwrap();
        ResidenceStore::new(pool)
    }
}
