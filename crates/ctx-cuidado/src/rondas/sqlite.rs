use diesel::prelude::*;
use diesel::{OptionalExtension, SqliteConnection};
use mana_kernel::{Actor, Id, Instante};

use crate::common::parse_instant;
use crate::schema::{round_tasks, rounds};
use crate::CuidadoError;

use super::repo::RondasRepo;
use super::{Round, RoundId, RoundStatus, RoundTask, TaskId, TaskSnapshot, TaskStatus, TaskUpdate};

#[derive(Queryable, Selectable)]
#[diesel(table_name = rounds)]
#[diesel(check_for_backend(diesel::sqlite::Sqlite))]
struct RoundRow {
    id: String,
    wing_id: String,
    status: String,
    scheduled_for: Option<String>,
    started_at: String,
    completed_at: Option<String>,
    started_by: String,
    completed_by: Option<String>,
    created_at: String,
    updated_at: String,
}

#[derive(Insertable)]
#[diesel(table_name = rounds)]
struct NewRoundRow<'a> {
    id: &'a str,
    wing_id: &'a str,
    status: &'a str,
    scheduled_for: Option<&'a str>,
    started_at: &'a str,
    completed_at: Option<&'a str>,
    started_by: &'a str,
    completed_by: Option<&'a str>,
    created_at: &'a str,
    updated_at: &'a str,
}

#[derive(AsChangeset)]
#[diesel(table_name = rounds)]
#[diesel(treat_none_as_null = true)]
struct RoundChangeset<'a> {
    status: &'a str,
    completed_at: Option<&'a str>,
    completed_by: Option<&'a str>,
    updated_at: &'a str,
}

#[derive(Queryable, Selectable)]
#[diesel(table_name = round_tasks)]
#[diesel(check_for_backend(diesel::sqlite::Sqlite))]
struct TaskRow {
    id: String,
    round_id: String,
    resident_id: String,
    bed_id: String,
    status: String,
    note: Option<String>,
    completed_at: Option<String>,
    completed_by: Option<String>,
    created_at: String,
    updated_at: String,
}

#[derive(Insertable)]
#[diesel(table_name = round_tasks)]
struct NewTaskRow<'a> {
    id: &'a str,
    round_id: &'a str,
    resident_id: &'a str,
    bed_id: &'a str,
    status: &'a str,
    note: Option<&'a str>,
    completed_at: Option<&'a str>,
    completed_by: Option<&'a str>,
    created_at: &'a str,
    updated_at: &'a str,
}

#[derive(AsChangeset)]
#[diesel(table_name = round_tasks)]
#[diesel(treat_none_as_null = true)]
struct TaskChangeset<'a> {
    status: &'a str,
    note: Option<&'a str>,
    completed_at: Option<&'a str>,
    completed_by: Option<&'a str>,
    updated_at: &'a str,
}

impl RondasRepo for SqliteConnection {
    fn create_round_in_transaction(
        connection: &mut SqliteConnection,
        id: RoundId,
        wing_id: &str,
        by: Id<Actor>,
        tasks: Vec<TaskSnapshot>,
        now: Instante,
    ) -> Result<Round, CuidadoError> {
        if tasks.is_empty() {
            return Err(CuidadoError::Rondas(super::RondasError::EmptyRound));
        }

        // Invariant 1: no existing in_progress round for this wing
        let existing = rounds::table
            .filter(rounds::wing_id.eq(wing_id))
            .filter(rounds::status.eq("in_progress"))
            .select(rounds::id)
            .first::<String>(connection)
            .optional()
            .map_err(CuidadoError::database)?;
        if existing.is_some() {
            return Err(CuidadoError::Rondas(super::RondasError::AlreadyInProgress));
        }

        let round = Round::create(id, wing_id, by, now)?;
        let started_at = round.started_at.to_string();
        let created_at = round.created_at.to_string();
        let updated_at = round.updated_at.to_string();
        diesel::insert_into(rounds::table)
            .values(NewRoundRow {
                id: round.id.as_str(),
                wing_id: &round.wing_id,
                status: round.status.as_str(),
                scheduled_for: None,
                started_at: &started_at,
                completed_at: None,
                started_by: round.started_by.as_str(),
                completed_by: None,
                created_at: &created_at,
                updated_at: &updated_at,
            })
            .execute(connection)
            .map_err(CuidadoError::database)?;

        for snapshot in tasks {
            let task_id = super::new_task_id();
            let task_created = now.to_string();
            let task_updated = now.to_string();
            diesel::insert_into(round_tasks::table)
                .values(NewTaskRow {
                    id: task_id.as_str(),
                    round_id: round.id.as_str(),
                    resident_id: &snapshot.resident_id,
                    bed_id: &snapshot.bed_id,
                    status: "pending",
                    note: None,
                    completed_at: None,
                    completed_by: None,
                    created_at: &task_created,
                    updated_at: &task_updated,
                })
                .execute(connection)
                .map_err(CuidadoError::database)?;
        }

        Ok(round)
    }

    fn complete_round_in_transaction(
        connection: &mut SqliteConnection,
        id: &RoundId,
        by: Id<Actor>,
        now: Instante,
    ) -> Result<Round, CuidadoError> {
        let pending = <Self as RondasRepo>::count_pending_tasks(connection, id)?;
        if pending > 0 {
            return Err(CuidadoError::Rondas(super::RondasError::PendingTasks));
        }
        let mut round = <Self as RondasRepo>::get_round(connection, id)?;
        round.complete(by, now)?;
        update_round_row(connection, &round)?;
        Ok(round)
    }

    fn cancel_round_in_transaction(
        connection: &mut SqliteConnection,
        id: &RoundId,
        by: Id<Actor>,
        now: Instante,
    ) -> Result<Round, CuidadoError> {
        let mut round = <Self as RondasRepo>::get_round(connection, id)?;
        round.cancel(by, now)?;
        update_round_row(connection, &round)?;
        Ok(round)
    }

    fn get_round(connection: &mut SqliteConnection, id: &RoundId) -> Result<Round, CuidadoError> {
        rounds::table
            .filter(rounds::id.eq(id.as_str()))
            .select(RoundRow::as_select())
            .first(connection)
            .optional()
            .map_err(CuidadoError::database)?
            .map(Round::try_from)
            .transpose()?
            .ok_or(CuidadoError::Rondas(super::RondasError::NotFound))
    }

    fn list_rounds(
        connection: &mut SqliteConnection,
        wing_id: &str,
        limit: i64,
    ) -> Result<Vec<Round>, CuidadoError> {
        rounds::table
            .filter(rounds::wing_id.eq(wing_id))
            .select(RoundRow::as_select())
            .order((rounds::started_at.desc(), rounds::id.desc()))
            .limit(limit)
            .load::<RoundRow>(connection)
            .map_err(CuidadoError::database)?
            .into_iter()
            .map(Round::try_from)
            .collect()
    }

    fn current_round(
        connection: &mut SqliteConnection,
        wing_id: &str,
    ) -> Result<Option<Round>, CuidadoError> {
        rounds::table
            .filter(rounds::wing_id.eq(wing_id))
            .filter(rounds::status.eq("in_progress"))
            .select(RoundRow::as_select())
            .first(connection)
            .optional()
            .map_err(CuidadoError::database)?
            .map(Round::try_from)
            .transpose()
    }

    fn update_task_in_transaction(
        connection: &mut SqliteConnection,
        id: &TaskId,
        update: TaskUpdate,
        by: Id<Actor>,
        now: Instante,
    ) -> Result<RoundTask, CuidadoError> {
        let mut task = <Self as RondasRepo>::get_task(connection, id)?;
        let round_status = <Self as RondasRepo>::round_status(connection, &task.round_id)?;
        if round_status != RoundStatus::InProgress {
            return Err(CuidadoError::Rondas(super::RondasError::AlreadyCompleted));
        }
        if let Some(status) = update.status {
            match status {
                TaskStatus::Completed => task.complete(by, now, update.note.unwrap_or(None))?,
                TaskStatus::Pending => task.reopen(now)?,
            }
        } else if let Some(note) = update.note {
            task.note = note;
            task.updated_at = now;
        }
        update_task_row(connection, &task)?;
        Ok(task)
    }

    fn list_tasks(
        connection: &mut SqliteConnection,
        round_id: &RoundId,
    ) -> Result<Vec<RoundTask>, CuidadoError> {
        round_tasks::table
            .filter(round_tasks::round_id.eq(round_id.as_str()))
            .select(TaskRow::as_select())
            .order((round_tasks::status.asc(), round_tasks::id.asc()))
            .load::<TaskRow>(connection)
            .map_err(CuidadoError::database)?
            .into_iter()
            .map(RoundTask::try_from)
            .collect()
    }

    fn get_task(connection: &mut SqliteConnection, id: &TaskId) -> Result<RoundTask, CuidadoError> {
        round_tasks::table
            .filter(round_tasks::id.eq(id.as_str()))
            .select(TaskRow::as_select())
            .first(connection)
            .optional()
            .map_err(CuidadoError::database)?
            .map(RoundTask::try_from)
            .transpose()?
            .ok_or(CuidadoError::NotFound)
    }

    fn count_pending_tasks(
        connection: &mut SqliteConnection,
        round_id: &RoundId,
    ) -> Result<i64, CuidadoError> {
        round_tasks::table
            .filter(round_tasks::round_id.eq(round_id.as_str()))
            .filter(round_tasks::status.eq("pending"))
            .count()
            .get_result(connection)
            .map_err(CuidadoError::database)
    }

    fn round_status(
        connection: &mut SqliteConnection,
        round_id: &RoundId,
    ) -> Result<RoundStatus, CuidadoError> {
        let status = rounds::table
            .filter(rounds::id.eq(round_id.as_str()))
            .select(rounds::status)
            .first::<String>(connection)
            .optional()
            .map_err(CuidadoError::database)?
            .ok_or(CuidadoError::Rondas(super::RondasError::NotFound))?;
        Ok(RoundStatus::parse(&status)?)
    }
}

fn update_round_row(connection: &mut SqliteConnection, round: &Round) -> Result<(), CuidadoError> {
    let updated_at = round.updated_at.to_string();
    let completed_at = round.completed_at.map(|t| t.to_string());
    let completed_by = round.completed_by.as_ref().map(ToString::to_string);
    diesel::update(rounds::table.find(round.id.as_str()))
        .set(RoundChangeset {
            status: round.status.as_str(),
            completed_at: completed_at.as_deref(),
            completed_by: completed_by.as_deref(),
            updated_at: &updated_at,
        })
        .execute(connection)
        .map_err(CuidadoError::database)?;
    Ok(())
}

fn update_task_row(
    connection: &mut SqliteConnection,
    task: &RoundTask,
) -> Result<(), CuidadoError> {
    let updated_at = task.updated_at.to_string();
    let completed_at = task.completed_at.map(|t| t.to_string());
    let completed_by = task.completed_by.as_ref().map(ToString::to_string);
    diesel::update(round_tasks::table.find(task.id.as_str()))
        .set(TaskChangeset {
            status: task.status.as_str(),
            note: task.note.as_deref(),
            completed_at: completed_at.as_deref(),
            completed_by: completed_by.as_deref(),
            updated_at: &updated_at,
        })
        .execute(connection)
        .map_err(CuidadoError::database)?;
    Ok(())
}

impl TryFrom<RoundRow> for Round {
    type Error = CuidadoError;

    fn try_from(row: RoundRow) -> Result<Self, CuidadoError> {
        let started_at = parse_instant("started_at", row.started_at)?;
        let completed_at = row
            .completed_at
            .map(|v| parse_instant("completed_at", v))
            .transpose()?;
        let created_at = parse_instant("created_at", row.created_at)?;
        let updated_at = parse_instant("updated_at", row.updated_at)?;
        Ok(Self {
            id: RoundId::new(row.id),
            wing_id: row.wing_id,
            status: RoundStatus::parse(&row.status)?,
            scheduled_for: row.scheduled_for,
            started_at,
            completed_at,
            started_by: Id::<Actor>::new(row.started_by),
            completed_by: row.completed_by.map(Id::<Actor>::new),
            created_at,
            updated_at,
        })
    }
}

impl TryFrom<TaskRow> for RoundTask {
    type Error = CuidadoError;

    fn try_from(row: TaskRow) -> Result<Self, CuidadoError> {
        let completed_at = row
            .completed_at
            .map(|v| parse_instant("completed_at", v))
            .transpose()?;
        let created_at = parse_instant("created_at", row.created_at)?;
        let updated_at = parse_instant("updated_at", row.updated_at)?;
        Ok(Self {
            id: TaskId::new(row.id),
            round_id: RoundId::new(row.round_id),
            resident_id: row.resident_id,
            bed_id: row.bed_id,
            status: TaskStatus::parse(&row.status)?,
            note: row.note,
            completed_at,
            completed_by: row.completed_by.map(Id::<Actor>::new),
            created_at,
            updated_at,
        })
    }
}
