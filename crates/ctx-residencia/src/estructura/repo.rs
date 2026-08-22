use diesel::SqliteConnection;
use mana_kernel::Instante;

use super::{
    Bed, BedId, BedInput, BedUpdate, Facility, FacilityId, FacilityInput, FacilityTree,
    FacilityUpdate, Room, RoomId, RoomInput, RoomUpdate, Wing, WingId, WingInput, WingUpdate,
};
use crate::ResidenceError;

/// Repositorio del subdominio de estructura. Los metodos operan sobre una
/// conexion ya abierta para que la capa de aplicacion pueda componer la
/// transaccion con auditoria u otros contextos.
pub trait EstructuraRepo {
    fn create_facility_in_transaction(
        connection: &mut SqliteConnection,
        id: FacilityId,
        input: FacilityInput,
        now: Instante,
    ) -> Result<Facility, ResidenceError>;

    fn list_facilities(connection: &mut SqliteConnection) -> Result<Vec<Facility>, ResidenceError>;

    fn get_facility(
        connection: &mut SqliteConnection,
        id: &FacilityId,
    ) -> Result<Facility, ResidenceError>;

    fn facility_tree(
        connection: &mut SqliteConnection,
        id: &FacilityId,
    ) -> Result<FacilityTree, ResidenceError>;

    fn update_facility_in_transaction(
        connection: &mut SqliteConnection,
        id: &FacilityId,
        input: FacilityUpdate,
        now: Instante,
    ) -> Result<Facility, ResidenceError>;

    fn create_wing_in_transaction(
        connection: &mut SqliteConnection,
        id: WingId,
        input: WingInput,
        now: Instante,
    ) -> Result<Wing, ResidenceError>;

    fn list_wings(
        connection: &mut SqliteConnection,
        facility_id: &FacilityId,
    ) -> Result<Vec<Wing>, ResidenceError>;

    fn list_wings_all(connection: &mut SqliteConnection) -> Result<Vec<Wing>, ResidenceError>;

    fn get_wing(connection: &mut SqliteConnection, id: &WingId) -> Result<Wing, ResidenceError>;

    fn update_wing_in_transaction(
        connection: &mut SqliteConnection,
        id: &WingId,
        input: WingUpdate,
        now: Instante,
    ) -> Result<Wing, ResidenceError>;

    fn create_room_in_transaction(
        connection: &mut SqliteConnection,
        id: RoomId,
        input: RoomInput,
        now: Instante,
    ) -> Result<Room, ResidenceError>;

    fn list_rooms(
        connection: &mut SqliteConnection,
        wing_id: &WingId,
    ) -> Result<Vec<Room>, ResidenceError>;

    fn get_room(connection: &mut SqliteConnection, id: &RoomId) -> Result<Room, ResidenceError>;

    fn update_room_in_transaction(
        connection: &mut SqliteConnection,
        id: &RoomId,
        input: RoomUpdate,
        now: Instante,
    ) -> Result<Room, ResidenceError>;

    fn create_bed_in_transaction(
        connection: &mut SqliteConnection,
        id: BedId,
        input: BedInput,
        now: Instante,
    ) -> Result<Bed, ResidenceError>;

    fn list_beds(
        connection: &mut SqliteConnection,
        room_id: &RoomId,
    ) -> Result<Vec<Bed>, ResidenceError>;

    fn get_bed(connection: &mut SqliteConnection, id: &BedId) -> Result<Bed, ResidenceError>;

    /// La cama activa vinculada a una `monitor_key`, si existe.
    ///
    /// Devuelve `Option` y no `Result<Bed>` a proposito: que el detector reporte
    /// una clave sin vincular es un estado esperado del sistema, no un error de
    /// la consulta. Quien llama decide que hacer con la evidencia huerfana.
    fn find_bed_by_monitor_key(
        connection: &mut SqliteConnection,
        monitor_key: &str,
    ) -> Result<Option<Bed>, ResidenceError>;

    fn update_bed_in_transaction(
        connection: &mut SqliteConnection,
        id: &BedId,
        input: BedUpdate,
        now: Instante,
    ) -> Result<Bed, ResidenceError>;
}
