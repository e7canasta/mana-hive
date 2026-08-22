use mana_kernel::{Actor, Id, Instante};

use super::{AlarmProfileVersion, ProfileInput};
use crate::error::PoliticaError;

pub trait PerfilesRepo {
    fn get_current(
        &mut self,
        resident_id: &str,
    ) -> Result<Option<AlarmProfileVersion>, PoliticaError>;

    fn get_at(
        &mut self,
        resident_id: &str,
        at: &Instante,
    ) -> Result<Option<AlarmProfileVersion>, PoliticaError>;

    fn list_history(
        &mut self,
        resident_id: &str,
    ) -> Result<Vec<AlarmProfileVersion>, PoliticaError>;

    fn apply_in_transaction(
        &mut self,
        resident_id: &str,
        input: ProfileInput,
        actor_id: Id<Actor>,
        now: Instante,
    ) -> Result<AlarmProfileVersion, PoliticaError>;
}
