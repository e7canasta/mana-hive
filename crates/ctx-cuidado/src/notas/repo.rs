use diesel::SqliteConnection;
use mana_kernel::Instante;

use super::{CareNote, NoteId, NoteInput};
use crate::CuidadoError;

pub trait NotasRepo {
    fn create_note_in_transaction(
        connection: &mut SqliteConnection,
        id: NoteId,
        input: NoteInput,
        now: Instante,
    ) -> Result<CareNote, CuidadoError>;

    fn list_notes_for_resident(
        connection: &mut SqliteConnection,
        resident_id: &str,
        limit: i64,
    ) -> Result<Vec<CareNote>, CuidadoError>;
}
