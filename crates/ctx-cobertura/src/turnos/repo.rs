use diesel::SqliteConnection;

use super::{FacilityShift, ReplaceGridResult, ShiftGrid, ShiftInput};
use crate::CoberturaError;

pub trait TurnosRepo {
    fn get_grid(
        connection: &mut SqliteConnection,
        facility_id: &str,
    ) -> Result<ShiftGrid, CoberturaError>;

    fn replace_grid_in_transaction(
        connection: &mut SqliteConnection,
        facility_id: &str,
        shifts: Vec<ShiftInput>,
        now: Instante,
    ) -> Result<ReplaceGridResult, CoberturaError>;

    fn list_shifts(
        connection: &mut SqliteConnection,
        facility_id: &str,
    ) -> Result<Vec<FacilityShift>, CoberturaError>;

    fn ensure_shift_exists(
        connection: &mut SqliteConnection,
        facility_id: &str,
        shift_key: &str,
    ) -> Result<(), CoberturaError>;
}

use mana_kernel::Instante;
