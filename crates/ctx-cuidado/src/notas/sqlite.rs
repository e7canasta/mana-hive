use diesel::prelude::*;
use diesel::SqliteConnection;
use mana_kernel::{Actor, Id, Instante};

use crate::common::parse_instant;
use crate::schema::care_notes;
use crate::CuidadoError;

use super::repo::NotasRepo;
use super::{CareNote, NoteId, NoteInput};

#[derive(Queryable, Selectable)]
#[diesel(table_name = care_notes)]
#[diesel(check_for_backend(diesel::sqlite::Sqlite))]
struct NoteRow {
    id: String,
    resident_id: String,
    author_id: String,
    kind: String,
    body: String,
    duration_min: Option<i32>,
    created_at: String,
    updated_at: String,
}

#[derive(Insertable)]
#[diesel(table_name = care_notes)]
struct NewNoteRow<'a> {
    id: &'a str,
    resident_id: &'a str,
    author_id: &'a str,
    kind: &'a str,
    body: &'a str,
    duration_min: Option<i32>,
    created_at: &'a str,
    updated_at: &'a str,
}

impl NotasRepo for SqliteConnection {
    fn create_note_in_transaction(
        connection: &mut SqliteConnection,
        id: NoteId,
        input: NoteInput,
        now: Instante,
    ) -> Result<CareNote, CuidadoError> {
        let note = CareNote::create(id, input, now)?;
        let created_at = note.created_at.to_string();
        let updated_at = note.updated_at.to_string();
        diesel::insert_into(care_notes::table)
            .values(NewNoteRow {
                id: note.id.as_str(),
                resident_id: &note.resident_id,
                author_id: note.author_id.as_str(),
                kind: &note.kind,
                body: &note.body,
                duration_min: note.duration_min,
                created_at: &created_at,
                updated_at: &updated_at,
            })
            .execute(connection)
            .map_err(CuidadoError::database)?;
        Ok(note)
    }

    fn list_notes_for_resident(
        connection: &mut SqliteConnection,
        resident_id: &str,
        limit: i64,
    ) -> Result<Vec<CareNote>, CuidadoError> {
        care_notes::table
            .filter(care_notes::resident_id.eq(resident_id))
            .select(NoteRow::as_select())
            .order((care_notes::created_at.desc(), care_notes::id.desc()))
            .limit(limit)
            .load::<NoteRow>(connection)
            .map_err(CuidadoError::database)?
            .into_iter()
            .map(CareNote::try_from)
            .collect()
    }
}

impl TryFrom<NoteRow> for CareNote {
    type Error = CuidadoError;

    fn try_from(row: NoteRow) -> Result<Self, CuidadoError> {
        let created_at = parse_instant("created_at", row.created_at)?;
        let updated_at = parse_instant("updated_at", row.updated_at)?;
        Ok(Self {
            id: NoteId::new(row.id),
            resident_id: row.resident_id,
            author_id: Id::<Actor>::new(row.author_id),
            kind: row.kind,
            body: row.body,
            duration_min: row.duration_min,
            created_at,
            updated_at,
        })
    }
}
