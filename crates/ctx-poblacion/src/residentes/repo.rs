use chrono::NaiveDate;
use diesel::SqliteConnection;
use mana_kernel::{Actor, Id, Instante};

use super::{DischargeResult, Resident, ResidentId, ResidentInput, ResidentUpdate};
use crate::PoblacionError;

/// Repositorio del subdominio de padron. Los metodos operan sobre una conexion
/// ya abierta para que `mana-app` componga la transaccion con auditoria,
/// asignaciones y Residencia.
pub trait ResidentesRepo {
    fn create_resident_in_transaction(
        connection: &mut SqliteConnection,
        id: ResidentId,
        input: ResidentInput,
        now: Instante,
    ) -> Result<Resident, PoblacionError>;

    fn update_resident_in_transaction(
        connection: &mut SqliteConnection,
        id: &ResidentId,
        input: ResidentUpdate,
        now: Instante,
    ) -> Result<Resident, PoblacionError>;

    /// Egreso: cambia el ciclo clinico y cierra la asignacion abierta en la
    /// misma transaccion (invariante 6). Devuelve la asignacion cerrada para
    /// que la capa de aplicacion la audite.
    fn discharge_in_transaction(
        connection: &mut SqliteConnection,
        id: &ResidentId,
        date: NaiveDate,
        by: Id<Actor>,
        now: Instante,
    ) -> Result<DischargeResult, PoblacionError>;

    fn list_residents(
        connection: &mut SqliteConnection,
        query: Option<&str>,
    ) -> Result<Vec<Resident>, PoblacionError>;

    fn get_resident(
        connection: &mut SqliteConnection,
        id: &ResidentId,
    ) -> Result<Resident, PoblacionError>;

    fn ensure_resident_active(
        connection: &mut SqliteConnection,
        id: &ResidentId,
    ) -> Result<(), PoblacionError>;
}
