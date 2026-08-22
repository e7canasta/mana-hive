//! Care context: operational rounds, tasks and continuity notes.
//!
//! The crate is divided into subdomains:
//!
//! - [`rondas`]: rounds (visits to a wing) with snapshot tasks.
//! - [`notas`]: append-only care notes for a resident.
//!
//! `ctx-cuidado` never imports Residencia, Poblacion or Observacion:
//! IDs travel as opaque references and cross-context coordination
//! lives in `mana-app`.

mod common;
mod error;

pub mod notas;
pub mod rondas;
pub mod schema;

pub use error::CuidadoError;
pub use mana_storage::DbPool;
pub use notas::{new_note_id, CareNote, NotasError, NoteId, NoteInput};
pub use rondas::{
    new_round_id, new_task_id, RondasError, Round, RoundId, RoundStatus, RoundStatus as Status,
    RoundTask, TaskId, TaskSnapshot, TaskStatus, TaskUpdate,
};

use diesel::prelude::*;
use diesel_migrations::{embed_migrations, EmbeddedMigrations};
use mana_kernel::{Actor, Id, Instante};
use mana_storage::{connection as get_connection, DbConnection};

use crate::notas::repo::NotasRepo;
use crate::rondas::repo::RondasRepo;

pub const MIGRATIONS: EmbeddedMigrations = embed_migrations!();

#[derive(Clone)]
pub struct CareStore {
    pub(crate) pool: DbPool,
}

pub fn run_migrations(pool: &DbPool) -> Result<(), CuidadoError> {
    mana_storage::run_migrations(pool, MIGRATIONS).map_err(CuidadoError::from)
}

impl CareStore {
    pub fn new(pool: DbPool) -> Self {
        Self { pool }
    }

    pub fn pool(&self) -> &DbPool {
        &self.pool
    }

    fn connection(&self) -> Result<DbConnection, CuidadoError> {
        get_connection(&self.pool).map_err(CuidadoError::from)
    }

    // -- Rounds --

    pub fn create_round(
        &self,
        wing_id: &str,
        by: Id<Actor>,
        tasks: Vec<TaskSnapshot>,
        now: Instante,
    ) -> Result<Round, CuidadoError> {
        let id = new_round_id();
        let mut connection = self.connection()?;
        connection.transaction(|c| {
            <SqliteConnection as RondasRepo>::create_round_in_transaction(
                c, id, wing_id, by, tasks, now,
            )
        })
    }

    pub fn complete_round(
        &self,
        id: &RoundId,
        by: Id<Actor>,
        now: Instante,
    ) -> Result<Round, CuidadoError> {
        let mut connection = self.connection()?;
        connection.transaction(|c| {
            <SqliteConnection as RondasRepo>::complete_round_in_transaction(c, id, by, now)
        })
    }

    pub fn cancel_round(
        &self,
        id: &RoundId,
        by: Id<Actor>,
        now: Instante,
    ) -> Result<Round, CuidadoError> {
        let mut connection = self.connection()?;
        connection.transaction(|c| {
            <SqliteConnection as RondasRepo>::cancel_round_in_transaction(c, id, by, now)
        })
    }

    pub fn get_round(&self, id: &RoundId) -> Result<Round, CuidadoError> {
        let mut connection = self.connection()?;
        <SqliteConnection as RondasRepo>::get_round(&mut connection, id)
    }

    pub fn list_rounds(&self, wing_id: &str, limit: i64) -> Result<Vec<Round>, CuidadoError> {
        let mut connection = self.connection()?;
        <SqliteConnection as RondasRepo>::list_rounds(&mut connection, wing_id, limit)
    }

    pub fn current_round(&self, wing_id: &str) -> Result<Option<Round>, CuidadoError> {
        let mut connection = self.connection()?;
        <SqliteConnection as RondasRepo>::current_round(&mut connection, wing_id)
    }

    // -- Tasks --

    pub fn update_task(
        &self,
        id: &TaskId,
        update: TaskUpdate,
        by: Id<Actor>,
        now: Instante,
    ) -> Result<RoundTask, CuidadoError> {
        let mut connection = self.connection()?;
        connection.transaction(|c| {
            <SqliteConnection as RondasRepo>::update_task_in_transaction(c, id, update, by, now)
        })
    }

    pub fn list_tasks(&self, round_id: &RoundId) -> Result<Vec<RoundTask>, CuidadoError> {
        let mut connection = self.connection()?;
        <SqliteConnection as RondasRepo>::list_tasks(&mut connection, round_id)
    }

    // -- Notes --

    pub fn create_note(&self, input: NoteInput, now: Instante) -> Result<CareNote, CuidadoError> {
        let id = new_note_id();
        let mut connection = self.connection()?;
        connection.transaction(|c| {
            <SqliteConnection as NotasRepo>::create_note_in_transaction(c, id, input, now)
        })
    }

    pub fn list_notes(&self, resident_id: &str, limit: i64) -> Result<Vec<CareNote>, CuidadoError> {
        let mut connection = self.connection()?;
        <SqliteConnection as NotasRepo>::list_notes_for_resident(
            &mut connection,
            resident_id,
            limit,
        )
    }
}

#[cfg(test)]
pub(crate) mod testsupport {
    use mana_kernel::Instante;
    use mana_storage::build_pool;

    use super::{run_migrations, CareStore};

    pub(crate) fn instant() -> Instante {
        "2026-08-18T12:00:00.000Z".parse().unwrap()
    }

    pub(crate) fn actor() -> mana_kernel::Id<mana_kernel::Actor> {
        mana_kernel::Id::new("user-1")
    }

    pub(crate) fn store() -> CareStore {
        let pool = build_pool(":memory:").unwrap();
        run_migrations(&pool).unwrap();
        CareStore::new(pool)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::testsupport::{actor, instant, store};

    fn snapshots() -> Vec<TaskSnapshot> {
        vec![
            TaskSnapshot {
                resident_id: "resident-1".to_owned(),
                bed_id: "bed-1".to_owned(),
            },
            TaskSnapshot {
                resident_id: "resident-2".to_owned(),
                bed_id: "bed-2".to_owned(),
            },
        ]
    }

    #[test]
    fn create_round_and_list_tasks() {
        let store = store();
        let round = store
            .create_round("wing-1", actor(), snapshots(), instant())
            .unwrap();
        assert_eq!(round.status, RoundStatus::InProgress);

        let tasks = store.list_tasks(&round.id).unwrap();
        assert_eq!(tasks.len(), 2);
        assert_eq!(tasks[0].status, TaskStatus::Pending);
    }

    #[test]
    fn second_active_round_same_wing_is_rejected() {
        let store = store();
        store
            .create_round("wing-1", actor(), snapshots(), instant())
            .unwrap();
        assert!(matches!(
            store.create_round("wing-1", actor(), snapshots(), instant()),
            Err(CuidadoError::Rondas(RondasError::AlreadyInProgress))
        ));
    }

    #[test]
    fn empty_round_is_rejected() {
        let store = store();
        assert!(matches!(
            store.create_round("wing-1", actor(), vec![], instant()),
            Err(CuidadoError::Rondas(RondasError::EmptyRound))
        ));
    }

    #[test]
    fn cannot_complete_with_pending_tasks() {
        let store = store();
        let round = store
            .create_round("wing-1", actor(), snapshots(), instant())
            .unwrap();
        assert!(matches!(
            store.complete_round(&round.id, actor(), instant()),
            Err(CuidadoError::Rondas(RondasError::PendingTasks))
        ));
    }

    #[test]
    fn complete_tasks_then_round() {
        let store = store();
        let round = store
            .create_round("wing-1", actor(), snapshots(), instant())
            .unwrap();
        let tasks = store.list_tasks(&round.id).unwrap();
        for task in &tasks {
            store
                .update_task(
                    &task.id,
                    TaskUpdate {
                        status: Some(TaskStatus::Completed),
                        note: None,
                    },
                    actor(),
                    instant(),
                )
                .unwrap();
        }
        let completed = store.complete_round(&round.id, actor(), instant()).unwrap();
        assert_eq!(completed.status, RoundStatus::Completed);
        assert!(completed.completed_at.is_some());
    }

    #[test]
    fn cannot_update_task_on_completed_round() {
        let store = store();
        let round = store
            .create_round("wing-1", actor(), snapshots(), instant())
            .unwrap();
        let tasks = store.list_tasks(&round.id).unwrap();
        for task in &tasks {
            store
                .update_task(
                    &task.id,
                    TaskUpdate {
                        status: Some(TaskStatus::Completed),
                        note: None,
                    },
                    actor(),
                    instant(),
                )
                .unwrap();
        }
        store.complete_round(&round.id, actor(), instant()).unwrap();
        assert!(matches!(
            store.update_task(
                &tasks[0].id,
                TaskUpdate {
                    status: Some(TaskStatus::Pending),
                    note: None,
                },
                actor(),
                instant(),
            ),
            Err(CuidadoError::Rondas(RondasError::AlreadyCompleted))
        ));
    }

    #[test]
    fn reopen_task_clears_completion() {
        let store = store();
        let round = store
            .create_round("wing-1", actor(), snapshots(), instant())
            .unwrap();
        let tasks = store.list_tasks(&round.id).unwrap();
        store
            .update_task(
                &tasks[0].id,
                TaskUpdate {
                    status: Some(TaskStatus::Completed),
                    note: Some(Some("done".to_owned())),
                },
                actor(),
                instant(),
            )
            .unwrap();
        let completed_task = store
            .update_task(
                &tasks[0].id,
                TaskUpdate {
                    status: Some(TaskStatus::Pending),
                    note: None,
                },
                actor(),
                instant(),
            )
            .unwrap();
        assert_eq!(completed_task.status, TaskStatus::Pending);
        assert!(completed_task.completed_at.is_none());
        assert!(completed_task.note.is_none());
    }

    #[test]
    fn create_and_list_notes() {
        let store = store();
        let note = store
            .create_note(
                NoteInput {
                    resident_id: "resident-1".to_owned(),
                    author_id: actor(),
                    kind: "general".to_owned(),
                    body: "Patient resting well".to_owned(),
                    duration_min: Some(15),
                },
                instant(),
            )
            .unwrap();
        assert_eq!(note.body, "Patient resting well");
        assert_eq!(note.duration_min, Some(15));

        let notes = store.list_notes("resident-1", 10).unwrap();
        assert_eq!(notes.len(), 1);
    }

    #[test]
    fn note_empty_body_is_rejected() {
        let store = store();
        assert!(matches!(
            store.create_note(
                NoteInput {
                    resident_id: "resident-1".to_owned(),
                    author_id: actor(),
                    kind: "general".to_owned(),
                    body: "  ".to_owned(),
                    duration_min: None,
                },
                instant(),
            ),
            Err(CuidadoError::Notas(NotasError::EmptyBody))
        ));
    }
}
