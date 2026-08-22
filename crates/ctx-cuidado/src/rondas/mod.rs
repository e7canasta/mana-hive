//! Subdominio de rondas: visitas operativas a un ala con tareas snapshot.

pub mod repo;
pub mod sqlite;

use mana_kernel::{define_kinds, Actor, Id, Instante};
use thiserror::Error;

define_kinds!(RoundKind, TaskKind);

pub type RoundId = Id<RoundKind>;
pub type TaskId = Id<TaskKind>;

#[derive(Clone, Debug, Eq, PartialEq, Error)]
pub enum RondasError {
    #[error("ya existe una ronda en progreso para este ala")]
    AlreadyInProgress,
    #[error("no se puede crear una ronda sin residentes asignados")]
    EmptyRound,
    #[error("no se puede completar una ronda con tareas pendientes")]
    PendingTasks,
    #[error("la ronda ya fue completada o cancelada")]
    AlreadyCompleted,
    #[error("la ronda no existe")]
    NotFound,
    #[error("dato persistido invalido: {0}")]
    InvalidStoredData(String),
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum RoundStatus {
    InProgress,
    Completed,
    Cancelled,
}

impl RoundStatus {
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::InProgress => "in_progress",
            Self::Completed => "completed",
            Self::Cancelled => "cancelled",
        }
    }

    pub fn parse(value: &str) -> Result<Self, RondasError> {
        match value {
            "in_progress" => Ok(Self::InProgress),
            "completed" => Ok(Self::Completed),
            "cancelled" => Ok(Self::Cancelled),
            other => Err(RondasError::InvalidStoredData(format!(
                "invalid round status: {other}"
            ))),
        }
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum TaskStatus {
    Pending,
    Completed,
}

impl TaskStatus {
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::Pending => "pending",
            Self::Completed => "completed",
        }
    }

    pub fn parse(value: &str) -> Result<Self, RondasError> {
        match value {
            "pending" => Ok(Self::Pending),
            "completed" => Ok(Self::Completed),
            other => Err(RondasError::InvalidStoredData(format!(
                "invalid task status: {other}"
            ))),
        }
    }
}

#[derive(Clone, Debug)]
pub struct Round {
    pub id: RoundId,
    pub wing_id: String,
    pub status: RoundStatus,
    pub scheduled_for: Option<String>,
    pub started_at: Instante,
    pub completed_at: Option<Instante>,
    pub started_by: Id<Actor>,
    pub completed_by: Option<Id<Actor>>,
    pub created_at: Instante,
    pub updated_at: Instante,
}

#[derive(Clone, Debug)]
pub struct RoundTask {
    pub id: TaskId,
    pub round_id: RoundId,
    pub resident_id: String,
    pub bed_id: String,
    pub status: TaskStatus,
    pub note: Option<String>,
    pub completed_at: Option<Instante>,
    pub completed_by: Option<Id<Actor>>,
    pub created_at: Instante,
    pub updated_at: Instante,
}

#[derive(Clone, Debug)]
pub struct TaskSnapshot {
    pub resident_id: String,
    pub bed_id: String,
}

#[derive(Clone, Debug)]
pub struct TaskUpdate {
    pub status: Option<TaskStatus>,
    pub note: Option<Option<String>>,
}

impl Round {
    pub fn create(
        id: RoundId,
        wing_id: &str,
        by: Id<Actor>,
        now: Instante,
    ) -> Result<Self, RondasError> {
        if wing_id.trim().is_empty() {
            return Err(RondasError::EmptyRound);
        }
        Ok(Self {
            id,
            wing_id: wing_id.to_owned(),
            status: RoundStatus::InProgress,
            scheduled_for: None,
            started_at: now,
            completed_at: None,
            started_by: by,
            completed_by: None,
            created_at: now,
            updated_at: now,
        })
    }

    pub fn complete(&mut self, by: Id<Actor>, now: Instante) -> Result<(), RondasError> {
        if self.status != RoundStatus::InProgress {
            return Err(RondasError::AlreadyCompleted);
        }
        self.status = RoundStatus::Completed;
        self.completed_at = Some(now);
        self.completed_by = Some(by);
        self.updated_at = now;
        Ok(())
    }

    pub fn cancel(&mut self, by: Id<Actor>, now: Instante) -> Result<(), RondasError> {
        if self.status != RoundStatus::InProgress {
            return Err(RondasError::AlreadyCompleted);
        }
        self.status = RoundStatus::Cancelled;
        self.completed_at = Some(now);
        self.completed_by = Some(by);
        self.updated_at = now;
        Ok(())
    }
}

impl RoundTask {
    pub fn complete(
        &mut self,
        by: Id<Actor>,
        now: Instante,
        note: Option<String>,
    ) -> Result<(), RondasError> {
        self.status = TaskStatus::Completed;
        self.completed_at = Some(now);
        self.completed_by = Some(by);
        if note.is_some() {
            self.note = note;
        }
        self.updated_at = now;
        Ok(())
    }

    pub fn reopen(&mut self, now: Instante) -> Result<(), RondasError> {
        self.status = TaskStatus::Pending;
        self.completed_at = None;
        self.completed_by = None;
        self.note = None;
        self.updated_at = now;
        Ok(())
    }
}

pub fn new_round_id() -> RoundId {
    Id::new(crate::common::random_id("round"))
}

pub fn new_task_id() -> TaskId {
    Id::new(crate::common::random_id("task"))
}

#[cfg(test)]
mod tests {
    use super::*;

    fn instant() -> Instante {
        "2026-08-18T12:00:00.000Z".parse().unwrap()
    }

    fn actor() -> Id<Actor> {
        Id::new("user-1")
    }

    #[test]
    fn creates_round_and_transitions() {
        let mut round = Round::create(new_round_id(), "wing-1", actor(), instant()).unwrap();
        assert_eq!(round.status, RoundStatus::InProgress);
        assert_eq!(round.wing_id, "wing-1");

        round.complete(actor(), instant()).unwrap();
        assert_eq!(round.status, RoundStatus::Completed);
        assert!(round.completed_at.is_some());
        assert!(matches!(
            round.complete(actor(), instant()),
            Err(RondasError::AlreadyCompleted)
        ));
    }

    #[test]
    fn cancel_and_complete_are_exclusive() {
        let mut round = Round::create(new_round_id(), "wing-1", actor(), instant()).unwrap();
        round.cancel(actor(), instant()).unwrap();
        assert_eq!(round.status, RoundStatus::Cancelled);
        assert!(matches!(
            round.complete(actor(), instant()),
            Err(RondasError::AlreadyCompleted)
        ));
    }

    #[test]
    fn task_complete_and_reopen() {
        let mut task = RoundTask {
            id: new_task_id(),
            round_id: RoundId::new("round-1"),
            resident_id: "resident-1".to_owned(),
            bed_id: "bed-1".to_owned(),
            status: TaskStatus::Pending,
            note: None,
            completed_at: None,
            completed_by: None,
            created_at: instant(),
            updated_at: instant(),
        };
        task.complete(actor(), instant(), Some("note".to_owned()))
            .unwrap();
        assert_eq!(task.status, TaskStatus::Completed);
        assert_eq!(task.note.as_deref(), Some("note"));

        task.reopen(instant()).unwrap();
        assert_eq!(task.status, TaskStatus::Pending);
        assert!(task.completed_at.is_none());
        assert!(task.note.is_none());
    }
}
