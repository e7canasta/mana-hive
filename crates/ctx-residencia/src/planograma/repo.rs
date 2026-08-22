use diesel::SqliteConnection;
use mana_kernel::Instante;

use super::{PlanogramEntry, PlanogramPlacementInput};
use crate::estructura::WingId;
use crate::ResidenceError;

pub trait PlanogramaRepo {
    fn planogram(
        connection: &mut SqliteConnection,
        wing_id: &WingId,
    ) -> Result<Vec<PlanogramEntry>, ResidenceError>;

    fn save_planogram_in_transaction(
        connection: &mut SqliteConnection,
        wing_id: &WingId,
        inputs: Vec<PlanogramPlacementInput>,
        now: Instante,
    ) -> Result<Vec<PlanogramEntry>, ResidenceError>;
}
