use diesel::SqliteConnection;
use mana_kernel::Instante;

use super::{PrivacyRegion, PrivacyRegionInput};
use crate::estructura::RoomId;
use crate::ResidenceError;

pub trait PrivacidadRepo {
    fn privacy_regions(
        connection: &mut SqliteConnection,
        room_id: &RoomId,
    ) -> Result<Vec<PrivacyRegion>, ResidenceError>;

    fn save_privacy_regions_in_transaction(
        connection: &mut SqliteConnection,
        room_id: &RoomId,
        inputs: Vec<PrivacyRegionInput>,
        now: Instante,
    ) -> Result<Vec<PrivacyRegion>, ResidenceError>;
}
