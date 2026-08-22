use ctx_cuidado::{NoteInput, TaskSnapshot, TaskStatus, TaskUpdate};

use crate::{error::AppFailure, identidad::required_token, state::AppState};

#[derive(Clone, Debug)]
pub struct CreateRoundCommand {
    pub wing_id: String,
}

#[derive(Clone, Debug)]
pub struct UpdateTaskCommand {
    pub status: Option<String>,
    pub note: Option<Option<String>>,
}

#[derive(Clone, Debug)]
pub struct CreateNoteCommand {
    pub body: String,
    pub kind: Option<String>,
    pub duration_min: Option<i32>,
}

#[derive(Clone, Debug, serde::Serialize)]
pub struct RoundView {
    pub id: String,
    /// El contrato del cliente las declara obligatorias: una ronda sin sus
    /// tareas no se puede mostrar.
    pub tasks: Vec<TaskView>,
    pub wing_id: String,
    pub status: String,
    pub scheduled_for: Option<String>,
    pub started_at: String,
    pub completed_at: Option<String>,
    pub started_by: String,
    pub completed_by: Option<String>,
}

#[derive(Clone, Debug, serde::Serialize)]
pub struct TaskView {
    pub id: String,
    pub round_id: String,
    pub resident_id: String,
    pub bed_id: String,
    pub status: String,
    /// Nombre y ubicacion vienen de Poblacion y Residencia. El cliente pinta la
    /// tarea sin tener que resolver dos IDs mas por fila.
    pub full_name: String,
    pub room_number: String,
    pub bed_label: Option<String>,
    pub note: Option<String>,
    pub completed_at: Option<String>,
    pub completed_by: Option<String>,
}

/// La actividad de cuidado de un residente en una ventana.
///
/// El cliente pide `{ period, events }` y no una lista de notas: una nota es
/// una forma de cuidado entre otras, y la vista habla de actividad.
#[derive(Clone, Debug, serde::Serialize)]
pub struct CareActivityView {
    pub id: String,
    pub kind: String,
    pub occurred_at: String,
    pub duration_minutes: Option<i32>,
    pub author_id: Option<String>,
    /// `true` cuando el cuidado no lo disparo una alerta. Hoy toda nota lo es;
    /// cuando la atencion de alertas alimente esta vista, dejara de serlo.
    pub proactive: bool,
}

#[derive(Clone, Debug, serde::Serialize)]
pub struct CarePeriodView {
    pub from: String,
    pub to: String,
    pub days: i64,
}

#[derive(Clone, Debug, serde::Serialize)]
pub struct CareActivityResponseView {
    pub period: CarePeriodView,
    pub events: Vec<CareActivityView>,
}

#[derive(Clone, Debug, serde::Serialize)]
pub struct NoteView {
    pub id: String,
    pub resident_id: String,
    pub author_id: String,
    pub kind: String,
    pub body: String,
    pub duration_min: Option<i32>,
    pub created_at: String,
}

/// `resident_id -> nombre` y `bed_id -> (habitacion, cama)`.
type TaskLookups = (
    std::collections::HashMap<String, String>,
    std::collections::HashMap<String, (String, String)>,
);

impl AppState {
    pub async fn care_activity(
        &self,
        token: &str,
        resident_id: &str,
        days: i64,
    ) -> Result<CareActivityResponseView, AppFailure> {
        required_token(token)?;
        let now = mana_kernel::Instante::now();
        let from = *now.as_datetime() - chrono::Duration::days(days);
        let notes = self.cuidado.list_notes(resident_id, days * 8)?;
        let events = notes
            .into_iter()
            .filter(|note| *note.created_at.as_datetime() >= from)
            .map(|note| CareActivityView {
                id: note.id.into_string(),
                kind: note.kind,
                occurred_at: note.created_at.to_string(),
                duration_minutes: note.duration_min,
                author_id: Some(note.author_id.into_string()),
                proactive: true,
            })
            .collect();
        Ok(CareActivityResponseView {
            period: CarePeriodView {
                from: mana_kernel::Instante::new(from).to_string(),
                to: now.to_string(),
                days,
            },
            events,
        })
    }

    // -- Rounds --

    pub async fn get_current_round(
        &self,
        token: &str,
        wing_id: &str,
    ) -> Result<Option<RoundView>, AppFailure> {
        required_token(token)?;
        let Some(round) = self.cuidado.current_round(wing_id)? else {
            return Ok(None);
        };
        let tasks = self.tasks_of(&round)?;
        Ok(Some(round_view_with_tasks(round, tasks)))
    }

    pub async fn get_round(
        &self,
        token: &str,
        round_id: &str,
    ) -> Result<(RoundView, Vec<TaskView>), AppFailure> {
        required_token(token)?;
        let id = ctx_cuidado::RoundId::new(round_id);
        let round = self.cuidado.get_round(&id)?;
        let tasks = self.tasks_of(&round)?;
        Ok((round_view_with_tasks(round, tasks.clone()), tasks))
    }

    pub async fn list_rounds(
        &self,
        token: &str,
        wing_id: &str,
        limit: i64,
    ) -> Result<Vec<RoundView>, AppFailure> {
        required_token(token)?;
        let rounds = self.cuidado.list_rounds(wing_id, limit)?;
        let mut views = Vec::with_capacity(rounds.len());
        for round in rounds {
            let tasks = self.tasks_of(&round)?;
            views.push(round_view_with_tasks(round, tasks));
        }
        Ok(views)
    }

    pub async fn create_round(
        &self,
        token: &str,
        command: CreateRoundCommand,
    ) -> Result<RoundView, AppFailure> {
        let actor = required_token(token)?;
        let now = mana_kernel::Instante::now();

        // Snapshot current assignments for this wing via poblacion
        let open = self.poblacion.list_open_assignments()?;
        let beds = self.residence.list_beds_all()?;
        let beds_map: std::collections::HashMap<String, _> = beds
            .into_iter()
            .map(|b| (b.bed.id.as_str().to_owned(), b))
            .collect();
        let tasks: Vec<TaskSnapshot> = open
            .iter()
            .filter_map(|a| {
                beds_map.get(a.bed_id.as_str()).and_then(|b| {
                    if b.wing_id.as_str() == command.wing_id {
                        Some(TaskSnapshot {
                            resident_id: a.resident_id.as_str().to_owned(),
                            bed_id: a.bed_id.as_str().to_owned(),
                        })
                    } else {
                        None
                    }
                })
            })
            .collect();

        let round = self.cuidado.create_round(
            &command.wing_id,
            mana_kernel::Id::new(&actor),
            tasks,
            now,
        )?;
        // La ronda recien creada ya trae sus tareas: el panel la pinta con la
        // misma respuesta y no necesita una segunda vuelta.
        let tasks = self.tasks_of(&round)?;
        Ok(round_view_with_tasks(round, tasks))
    }

    pub async fn complete_round(
        &self,
        token: &str,
        round_id: &str,
    ) -> Result<RoundView, AppFailure> {
        let actor = required_token(token)?;
        let id = ctx_cuidado::RoundId::new(round_id);
        let round = self.cuidado.complete_round(
            &id,
            mana_kernel::Id::new(&actor),
            mana_kernel::Instante::now(),
        )?;
        Ok(round_view(round))
    }

    pub async fn cancel_round(&self, token: &str, round_id: &str) -> Result<RoundView, AppFailure> {
        let actor = required_token(token)?;
        let id = ctx_cuidado::RoundId::new(round_id);
        let round = self.cuidado.cancel_round(
            &id,
            mana_kernel::Id::new(&actor),
            mana_kernel::Instante::now(),
        )?;
        Ok(round_view(round))
    }

    // -- Tasks --

    pub async fn update_task(
        &self,
        token: &str,
        task_id: &str,
        command: UpdateTaskCommand,
    ) -> Result<TaskView, AppFailure> {
        let actor = required_token(token)?;
        let id = ctx_cuidado::TaskId::new(task_id);
        let status = command
            .status
            .as_deref()
            .map(|s| match s {
                "completed" => Ok(TaskStatus::Completed),
                "pending" => Ok(TaskStatus::Pending),
                _ => Err(AppFailure::validation(
                    "invalid task status",
                    Some("status"),
                )),
            })
            .transpose()?;
        let task = self.cuidado.update_task(
            &id,
            TaskUpdate {
                status,
                note: command.note,
            },
            mana_kernel::Id::new(&actor),
            mana_kernel::Instante::now(),
        )?;
        Ok(task_view(task))
    }

    pub async fn list_tasks(
        &self,
        token: &str,
        round_id: &str,
    ) -> Result<Vec<TaskView>, AppFailure> {
        required_token(token)?;
        let id = ctx_cuidado::RoundId::new(round_id);
        let tasks = self.cuidado.list_tasks(&id)?;
        Ok(tasks.into_iter().map(task_view).collect())
    }

    // -- Notes --

    pub async fn list_notes(
        &self,
        token: &str,
        resident_id: &str,
        limit: i64,
    ) -> Result<Vec<NoteView>, AppFailure> {
        required_token(token)?;
        let notes = self.cuidado.list_notes(resident_id, limit)?;
        Ok(notes.into_iter().map(note_view).collect())
    }

    pub async fn create_note(
        &self,
        token: &str,
        resident_id: &str,
        command: CreateNoteCommand,
    ) -> Result<NoteView, AppFailure> {
        let actor = required_token(token)?;
        let note = self.cuidado.create_note(
            NoteInput {
                resident_id: resident_id.to_owned(),
                author_id: mana_kernel::Id::new(&actor),
                kind: command.kind.unwrap_or_else(|| "general".to_owned()),
                body: command.body,
                duration_min: command.duration_min,
            },
            mana_kernel::Instante::now(),
        )?;
        Ok(note_view(note))
    }
}

impl AppState {
    /// Mapas para resolver una tarea: `resident_id -> nombre` y
    /// `bed_id -> (habitacion, cama)`. Se arman una vez por request, no una vez
    /// por tarea.
    fn task_lookups(&self) -> Result<TaskLookups, AppFailure> {
        let names = self
            .poblacion
            .list_residents(None)?
            .into_iter()
            .map(|resident| (resident.id.as_str().to_owned(), resident.full_name))
            .collect();
        let beds = self
            .residence
            .list_beds_all()?
            .into_iter()
            .map(|entry| {
                (
                    entry.bed.id.as_str().to_owned(),
                    (entry.room_number, entry.bed.label),
                )
            })
            .collect();
        Ok((names, beds))
    }

    fn tasks_of(&self, round: &ctx_cuidado::Round) -> Result<Vec<TaskView>, AppFailure> {
        let (names, beds) = self.task_lookups()?;
        Ok(self
            .cuidado
            .list_tasks(&round.id)?
            .into_iter()
            .map(|task| task_view_resolved(task, &names, &beds))
            .collect())
    }
}

fn round_view(round: ctx_cuidado::Round) -> RoundView {
    round_view_with_tasks(round, Vec::new())
}

fn round_view_with_tasks(round: ctx_cuidado::Round, tasks: Vec<TaskView>) -> RoundView {
    RoundView {
        id: round.id.into_string(),
        tasks,
        wing_id: round.wing_id,
        status: round.status.as_str().to_owned(),
        scheduled_for: round.scheduled_for,
        started_at: round.started_at.to_string(),
        completed_at: round.completed_at.map(|t| t.to_string()),
        started_by: round.started_by.into_string(),
        completed_by: round.completed_by.map(|t| t.into_string()),
    }
}

fn task_view(task: ctx_cuidado::RoundTask) -> TaskView {
    task_view_resolved(
        task,
        &std::collections::HashMap::new(),
        &std::collections::HashMap::new(),
    )
}

/// Cruza la tarea con Poblacion (nombre) y Residencia (habitacion y cama).
fn task_view_resolved(
    task: ctx_cuidado::RoundTask,
    names: &std::collections::HashMap<String, String>,
    beds: &std::collections::HashMap<String, (String, String)>,
) -> TaskView {
    let full_name = names.get(&task.resident_id).cloned().unwrap_or_default();
    let (room_number, bed_label) = beds
        .get(&task.bed_id)
        .cloned()
        .unwrap_or_else(|| (String::new(), String::new()));
    TaskView {
        id: task.id.into_string(),
        round_id: task.round_id.into_string(),
        resident_id: task.resident_id,
        bed_id: task.bed_id,
        status: task.status.as_str().to_owned(),
        full_name,
        room_number,
        bed_label: Some(bed_label).filter(|value| !value.is_empty()),
        note: task.note,
        completed_at: task.completed_at.map(|t| t.to_string()),
        completed_by: task.completed_by.map(|t| t.into_string()),
    }
}

fn note_view(note: ctx_cuidado::CareNote) -> NoteView {
    NoteView {
        id: note.id.into_string(),
        resident_id: note.resident_id,
        author_id: note.author_id.into_string(),
        kind: note.kind,
        body: note.body,
        duration_min: note.duration_min,
        created_at: note.created_at.to_string(),
    }
}
