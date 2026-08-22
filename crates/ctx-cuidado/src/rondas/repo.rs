use diesel::SqliteConnection;
use mana_kernel::{Actor, Id, Instante};

use super::{Round, RoundId, RoundStatus, RoundTask, TaskId, TaskSnapshot, TaskUpdate};
use crate::CuidadoError;

pub trait RondasRepo {
    fn create_round_in_transaction(
        connection: &mut SqliteConnection,
        id: RoundId,
        wing_id: &str,
        by: Id<Actor>,
        tasks: Vec<TaskSnapshot>,
        now: Instante,
    ) -> Result<Round, CuidadoError>;

    fn complete_round_in_transaction(
        connection: &mut SqliteConnection,
        id: &RoundId,
        by: Id<Actor>,
        now: Instante,
    ) -> Result<Round, CuidadoError>;

    fn cancel_round_in_transaction(
        connection: &mut SqliteConnection,
        id: &RoundId,
        by: Id<Actor>,
        now: Instante,
    ) -> Result<Round, CuidadoError>;

    fn get_round(connection: &mut SqliteConnection, id: &RoundId) -> Result<Round, CuidadoError>;

    fn list_rounds(
        connection: &mut SqliteConnection,
        wing_id: &str,
        limit: i64,
    ) -> Result<Vec<Round>, CuidadoError>;

    fn current_round(
        connection: &mut SqliteConnection,
        wing_id: &str,
    ) -> Result<Option<Round>, CuidadoError>;

    fn update_task_in_transaction(
        connection: &mut SqliteConnection,
        id: &TaskId,
        update: TaskUpdate,
        by: Id<Actor>,
        now: Instante,
    ) -> Result<RoundTask, CuidadoError>;

    fn list_tasks(
        connection: &mut SqliteConnection,
        round_id: &RoundId,
    ) -> Result<Vec<RoundTask>, CuidadoError>;

    fn get_task(connection: &mut SqliteConnection, id: &TaskId) -> Result<RoundTask, CuidadoError>;

    fn count_pending_tasks(
        connection: &mut SqliteConnection,
        round_id: &RoundId,
    ) -> Result<i64, CuidadoError>;

    fn round_status(
        connection: &mut SqliteConnection,
        round_id: &RoundId,
    ) -> Result<RoundStatus, CuidadoError>;
}
