//! Care notes: append-only continuity records for a resident.

pub mod repo;
pub mod sqlite;

use mana_kernel::{define_kinds, Actor, Id, Instante};
use thiserror::Error;

define_kinds!(NoteKind);

pub type NoteId = Id<NoteKind>;

#[derive(Clone, Debug, Eq, PartialEq, Error)]
pub enum NotasError {
    #[error("note body cannot be empty")]
    EmptyBody,
    #[error("note body exceeds max length of {0} characters")]
    BodyTooLong(usize),
}

const MAX_BODY: usize = 4000;

#[derive(Clone, Debug)]
pub struct CareNote {
    pub id: NoteId,
    pub resident_id: String,
    pub author_id: Id<Actor>,
    pub kind: String,
    pub body: String,
    pub duration_min: Option<i32>,
    pub created_at: Instante,
    pub updated_at: Instante,
}

#[derive(Clone, Debug)]
pub struct NoteInput {
    pub resident_id: String,
    pub author_id: Id<Actor>,
    pub kind: String,
    pub body: String,
    pub duration_min: Option<i32>,
}

impl CareNote {
    pub fn create(id: NoteId, input: NoteInput, now: Instante) -> Result<Self, NotasError> {
        let body = validate_body(&input.body)?;
        let kind = if input.kind.trim().is_empty() {
            "general".to_owned()
        } else {
            input.kind.trim().to_owned()
        };
        Ok(Self {
            id,
            resident_id: input.resident_id,
            author_id: input.author_id,
            kind,
            body,
            duration_min: input.duration_min,
            created_at: now,
            updated_at: now,
        })
    }
}

pub fn new_note_id() -> NoteId {
    Id::new(crate::common::random_id("note"))
}

fn validate_body(value: &str) -> Result<String, NotasError> {
    let value = value.trim();
    if value.is_empty() {
        return Err(NotasError::EmptyBody);
    }
    if value.chars().count() > MAX_BODY {
        return Err(NotasError::BodyTooLong(MAX_BODY));
    }
    Ok(value.to_owned())
}

#[cfg(test)]
mod tests {
    use super::*;

    fn instant() -> Instante {
        "2026-08-18T12:00:00.000Z".parse().unwrap()
    }

    fn input() -> NoteInput {
        NoteInput {
            resident_id: "resident-1".to_owned(),
            author_id: Id::new("user-1"),
            kind: "general".to_owned(),
            body: "Patient resting comfortably".to_owned(),
            duration_min: None,
        }
    }

    #[test]
    fn creates_note_and_validates_body() {
        let note = CareNote::create(new_note_id(), input(), instant()).unwrap();
        assert_eq!(note.body, "Patient resting comfortably");
        assert_eq!(note.kind, "general");
        assert!(note.duration_min.is_none());

        assert!(matches!(
            CareNote::create(
                new_note_id(),
                NoteInput {
                    body: "  ".to_owned(),
                    ..input()
                },
                instant(),
            ),
            Err(NotasError::EmptyBody)
        ));
    }

    #[test]
    fn defaults_kind_to_general() {
        let note = CareNote::create(
            new_note_id(),
            NoteInput {
                kind: "".to_owned(),
                ..input()
            },
            instant(),
        )
        .unwrap();
        assert_eq!(note.kind, "general");
    }
}
