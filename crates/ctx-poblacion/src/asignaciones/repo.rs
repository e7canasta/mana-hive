use diesel::SqliteConnection;
use mana_kernel::{Actor, Id, Instante};

use super::{AssignResult, BedAssignment, BedRef};
use crate::residentes::ResidentId;
use crate::PoblacionError;

/// Repositorio del subdominio de asignaciones.
///
/// La unicidad de la asignacion abierta (una por residente, una por cama) la
/// imponen indices parciales; el rechazo de solapamiento y el cierre de la
/// asignacion activa de ambos lados viven dentro de la misma transaccion de
/// escritura (invariantes 3 y 4).
pub trait AsignacionesRepo {
    fn assign_in_transaction(
        connection: &mut SqliteConnection,
        id: crate::asignaciones::AssignmentId,
        resident_id: ResidentId,
        bed_id: &BedRef,
        starts_at: Instante,
        created_by: Option<Id<Actor>>,
    ) -> Result<AssignResult, PoblacionError>;

    /// Libera la cama cerrando su asignacion abierta. Una cama libre es un
    /// error `FreeBed` (409), no un exito idempotente.
    fn release_in_transaction(
        connection: &mut SqliteConnection,
        bed_id: &BedRef,
        ends_at: Instante,
    ) -> Result<BedAssignment, PoblacionError>;

    fn close_open_for_resident_in_transaction(
        connection: &mut SqliteConnection,
        resident_id: &ResidentId,
        ends_at: Instante,
    ) -> Result<Option<BedAssignment>, PoblacionError>;

    fn list_assignments(
        connection: &mut SqliteConnection,
        resident_id: &ResidentId,
    ) -> Result<Vec<BedAssignment>, PoblacionError>;

    fn list_open_assignments(
        connection: &mut SqliteConnection,
    ) -> Result<Vec<BedAssignment>, PoblacionError>;

    fn open_assignment_for_resident(
        connection: &mut SqliteConnection,
        resident_id: &ResidentId,
    ) -> Result<Option<BedAssignment>, PoblacionError>;
}
