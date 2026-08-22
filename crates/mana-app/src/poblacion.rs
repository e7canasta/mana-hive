use std::collections::HashMap;

use ctx_identidad::IdentityStore;
use ctx_poblacion::{BedRef, PopulationStore, Resident, ResidentId, ResidentInput, ResidentUpdate};
use ctx_residencia::{ResidenceBed, ResidenceStore};
use mana_kernel::Fallo;
use serde_json::json;

use crate::{
    error::AppFailure,
    identidad::{
        actor_id, authenticated_actor, authenticated_actor_in_transaction, require_capability,
        required_token,
    },
    state::{AppState, Stores},
};

#[derive(Clone, Debug)]
pub struct CreateResidentCommand {
    pub full_name: String,
    pub external_id: Option<String>,
    pub birth_date: Option<String>,
    pub admission_date: Option<String>,
}

#[derive(Clone, Debug, Default)]
pub struct UpdateResidentCommand {
    pub full_name: Option<String>,
    pub external_id: Option<Option<String>>,
    pub birth_date: Option<Option<String>>,
    pub admission_date: Option<Option<String>>,
}

#[derive(Clone, Debug, Default)]
pub struct DischargeCommand {
    pub discharged_at: Option<String>,
}

#[derive(Clone, Debug)]
pub struct AssignBedCommand {
    pub bed_id: String,
    /// `None` es "ahora". No se rellena en el handler para que el default viva
    /// en un solo lugar.
    pub starts_at: Option<String>,
}

#[derive(Clone, Debug)]
pub struct DischargeResultView {
    pub resident: ResidentRecordView,
    pub assignments_closed: i64,
}

#[derive(Clone, Debug)]
pub struct ResidentRecordView {
    pub id: String,
    pub external_id: Option<String>,
    pub full_name: String,
    pub birth_date: Option<String>,
    pub admission_date: Option<String>,
    pub status: String,
    pub discharged_at: Option<String>,
    pub discharged_by: Option<String>,
    pub created_at: String,
    pub updated_at: String,
}

#[derive(Clone, Debug)]
pub struct RoomRefView {
    pub id: String,
    pub number: String,
    pub wing_id: String,
    pub wing_name: String,
}

#[derive(Clone, Debug)]
pub struct ResidentListItemView {
    pub id: String,
    pub external_id: Option<String>,
    pub full_name: String,
    pub birth_date: Option<String>,
    pub admission_date: Option<String>,
    pub status: String,
    pub discharged_at: Option<String>,
    pub room: Option<RoomRefView>,
    pub bed_id: Option<String>,
}

#[derive(Clone, Debug)]
pub struct BedAssignmentView {
    pub id: String,
    pub resident_id: String,
    pub bed_id: String,
    pub starts_at: String,
    pub ends_at: Option<String>,
    pub created_at: String,
    pub created_by: Option<String>,
}

impl AppState {
    pub async fn list_residents(
        &self,
        token: &str,
        query: Option<String>,
    ) -> Result<Vec<ResidentListItemView>, AppFailure> {
        let token = required_token(token)?;
        let enabled = self.enabled_capabilities.clone();
        run_poblacion_blocking(
            self.identity.clone(),
            self.residence.clone(),
            self.poblacion.clone(),
            move |identity, residence, poblacion| {
                let actor = authenticated_actor(&identity, &token, &enabled)?;
                require_capability(&actor, "master.structure.read")?;
                let residents = poblacion
                    .list_residents(query.as_deref())
                    .map_err(AppFailure::from)?;
                let open = poblacion
                    .list_open_assignments()
                    .map_err(AppFailure::from)?;
                let beds = residence.list_beds_all().map_err(AppFailure::from)?;
                Ok(build_list_items(residents, open, beds))
            },
        )
        .await
    }

    pub async fn resident_detail(
        &self,
        token: &str,
        resident_id: &str,
    ) -> Result<ResidentRecordView, AppFailure> {
        let token = required_token(token)?;
        let resident_id = required_id(resident_id)?;
        let enabled = self.enabled_capabilities.clone();
        run_poblacion_blocking(
            self.identity.clone(),
            self.residence.clone(),
            self.poblacion.clone(),
            move |identity, _residence, poblacion| {
                let actor = authenticated_actor(&identity, &token, &enabled)?;
                require_capability(&actor, "master.structure.read")?;
                let resident = poblacion
                    .get_resident(&ResidentId::new(resident_id))
                    .map_err(AppFailure::from)?;
                Ok(resident_record_view(resident))
            },
        )
        .await
    }

    pub async fn list_assignments(
        &self,
        token: &str,
        resident_id: &str,
    ) -> Result<Vec<BedAssignmentView>, AppFailure> {
        let token = required_token(token)?;
        let resident_id = required_id(resident_id)?;
        let enabled = self.enabled_capabilities.clone();
        run_poblacion_blocking(
            self.identity.clone(),
            self.residence.clone(),
            self.poblacion.clone(),
            move |identity, _residence, poblacion| {
                let actor = authenticated_actor(&identity, &token, &enabled)?;
                require_capability(&actor, "master.structure.read")?;
                poblacion
                    .list_assignments(&ResidentId::new(resident_id))
                    .map_err(AppFailure::from)
                    .map(|assignments| assignments.into_iter().map(bed_assignment_view).collect())
            },
        )
        .await
    }

    pub async fn create_resident(
        &self,
        token: &str,
        command: CreateResidentCommand,
    ) -> Result<ResidentRecordView, AppFailure> {
        let token = required_token(token)?;
        let enabled = self.enabled_capabilities.clone();
        self.transaction(move |connection, stores| {
            let Stores {
                identity,
                audit,
                poblacion,
                ..
            } = stores;
            let actor = authenticated_actor_in_transaction(identity, connection, &token, &enabled)?;
            require_capability(&actor, "master.structure.write")?;
            let created = poblacion.create_resident_in_transaction(
                connection,
                ctx_poblacion::new_resident_id(),
                ResidentInput {
                    full_name: command.full_name,
                    external_id: command.external_id,
                    birth_date: command.birth_date,
                    admission_date: command.admission_date,
                },
                mana_kernel::Instante::now(),
            )?;
            let record = ctx_auditoria::AuditRecord::new(
                Some(actor_id(&actor)),
                "resident.created",
                "resident",
                created.id.as_str(),
                json!({"full_name": &created.full_name}),
            )?;
            audit.record_in_transaction(connection, record)?;
            Ok(resident_record_view(created))
        })
        .await
    }

    pub async fn update_resident(
        &self,
        token: &str,
        resident_id: &str,
        command: UpdateResidentCommand,
    ) -> Result<ResidentRecordView, AppFailure> {
        let token = required_token(token)?;
        let resident_id = required_id(resident_id)?;
        let enabled = self.enabled_capabilities.clone();
        self.transaction(move |connection, stores| {
            let Stores {
                identity,
                audit,
                poblacion,
                ..
            } = stores;
            let actor = authenticated_actor_in_transaction(identity, connection, &token, &enabled)?;
            require_capability(&actor, "master.structure.write")?;
            let fields = resident_update_fields(&command);
            let updated = poblacion.update_resident_in_transaction(
                connection,
                &ResidentId::new(resident_id),
                ResidentUpdate {
                    full_name: command.full_name,
                    external_id: command.external_id,
                    birth_date: command.birth_date,
                    admission_date: command.admission_date,
                },
                mana_kernel::Instante::now(),
            )?;
            let record = ctx_auditoria::AuditRecord::new(
                Some(actor_id(&actor)),
                "resident.updated",
                "resident",
                updated.id.as_str(),
                json!({"fields": fields}),
            )?;
            audit.record_in_transaction(connection, record)?;
            Ok(resident_record_view(updated))
        })
        .await
    }

    /// Egreso: cambia el ciclo clinico del residente y cierra su asignacion
    /// abierta en la misma transaccion (invariantes 6 y 7).
    pub async fn discharge_resident(
        &self,
        token: &str,
        resident_id: &str,
        command: DischargeCommand,
    ) -> Result<DischargeResultView, AppFailure> {
        let token = required_token(token)?;
        let resident_id = required_id(resident_id)?;
        let enabled = self.enabled_capabilities.clone();
        self.transaction(move |connection, stores| {
            let Stores { identity, audit, poblacion, .. } = stores;
            let actor = authenticated_actor_in_transaction(identity, connection, &token, &enabled)?;
            require_capability(&actor, "master.structure.write")?;
            let date = discharge_date(&command)?;
            let result = poblacion.discharge_in_transaction(
                connection,
                &ResidentId::new(resident_id),
                date,
                actor_id(&actor),
                mana_kernel::Instante::now(),
            )?;
            if let Some(closed) = &result.closed_assignment {
                let record = ctx_auditoria::AuditRecord::new(
                    Some(actor_id(&actor)),
                    "assignment.closed",
                    "bed",
                    closed.bed_id.as_str(),
                    json!({"assignment_id": closed.id.as_str(), "reason": "discharge"}),
                )?;
                audit.record_in_transaction(connection, record)?;
            }
            let record = ctx_auditoria::AuditRecord::new(
                Some(actor_id(&actor)),
                "resident.discharged",
                "resident",
                result.resident.id.as_str(),
                json!({"discharged_at": result.resident.discharged_at.map(|date| date.to_string())}),
            )?;
            audit.record_in_transaction(connection, record)?;
            // Egreso no es lo mismo que liberar cama, pero cierra la que
            // hubiera: el cliente necesita saber cuantas, no adivinarlo.
            let assignments_closed = i64::from(result.closed_assignment.is_some());
            Ok(DischargeResultView {
                resident: resident_record_view(result.resident),
                assignments_closed,
            })
        })
        .await
    }

    /// Asignacion transaccional: valida residente (Poblacion) y cama
    /// (Residencia) en la misma transaccion, cierra las asignaciones abiertas
    /// de ambos lados y deja el rastro en auditoria.
    pub async fn assign_bed(
        &self,
        token: &str,
        resident_id: &str,
        command: AssignBedCommand,
    ) -> Result<BedAssignmentView, AppFailure> {
        let token = required_token(token)?;
        let resident_id = required_id(resident_id)?;
        let bed_id = required_id(&command.bed_id)?;
        let starts_at = match command.starts_at.as_deref().map(str::trim) {
            Some(value) if !value.is_empty() => value
                .parse()
                .map_err(|_| AppFailure::validation("invalid starts_at", Some("starts_at")))?,
            _ => mana_kernel::Instante::now(),
        };
        let enabled = self.enabled_capabilities.clone();
        self.transaction(move |connection, stores| {
            let Stores { identity, audit, residence, poblacion, .. } = stores;
            let actor = authenticated_actor_in_transaction(identity, connection, &token, &enabled)?;
            require_capability(&actor, "master.structure.write")?;
            residence.ensure_bed_active_in_transaction(
                connection,
                &ctx_residencia::BedId::new(bed_id.clone()),
            )?;
            let bed_ref = BedRef::new(bed_id)
                .map_err(|error| AppFailure::validation(error.to_string(), Some("bed_id")))?;
            let result = poblacion.assign_in_transaction(
                connection,
                ctx_poblacion::new_assignment_id(),
                ResidentId::new(resident_id),
                &bed_ref,
                starts_at,
                Some(actor_id(&actor)),
            )?;
            for closed in [result.resident_closed, result.bed_closed]
                .into_iter()
                .flatten()
            {
                let record = ctx_auditoria::AuditRecord::new(
                    Some(actor_id(&actor)),
                    "assignment.closed",
                    "bed",
                    closed.bed_id.as_str(),
                    json!({"assignment_id": closed.id.as_str(), "reason": "reassigned"}),
                )?;
                audit.record_in_transaction(connection, record)?;
            }
            let record = ctx_auditoria::AuditRecord::new(
                Some(actor_id(&actor)),
                "assignment.created",
                "resident",
                result.created.resident_id.as_str(),
                json!({"assignment_id": result.created.id.as_str(), "bed_id": result.created.bed_id.as_str()}),
            )?;
            audit.record_in_transaction(connection, record)?;
            Ok(bed_assignment_view(result.created))
        })
        .await
    }

    /// Libera la cama cerrando su asignacion abierta. Una cama libre es un
    /// `409 CONFLICT` deliberado.
    pub async fn release_bed(
        &self,
        token: &str,
        bed_id: &str,
    ) -> Result<BedAssignmentView, AppFailure> {
        let token = required_token(token)?;
        let bed_id = required_id(bed_id)?;
        let enabled = self.enabled_capabilities.clone();
        self.transaction(move |connection, stores| {
            let Stores {
                identity,
                audit,
                poblacion,
                ..
            } = stores;
            let actor = authenticated_actor_in_transaction(identity, connection, &token, &enabled)?;
            require_capability(&actor, "master.structure.write")?;
            let bed_ref = BedRef::new(bed_id)
                .map_err(|error| AppFailure::validation(error.to_string(), Some("bed_id")))?;
            let released = poblacion.release_in_transaction(
                connection,
                &bed_ref,
                mana_kernel::Instante::now(),
            )?;
            let record = ctx_auditoria::AuditRecord::new(
                Some(actor_id(&actor)),
                "assignment.released",
                "bed",
                released.bed_id.as_str(),
                json!({"assignment_id": released.id.as_str()}),
            )?;
            audit.record_in_transaction(connection, record)?;
            Ok(bed_assignment_view(released))
        })
        .await
    }
}

pub(crate) async fn run_poblacion_blocking<T, F>(
    identity: IdentityStore,
    residence: ResidenceStore,
    poblacion: PopulationStore,
    operation: F,
) -> Result<T, AppFailure>
where
    T: Send + 'static,
    F: FnOnce(IdentityStore, ResidenceStore, PopulationStore) -> Result<T, AppFailure>
        + Send
        + 'static,
{
    tokio::task::spawn_blocking(move || operation(identity, residence, poblacion))
        .await
        .map_err(|error| {
            tracing::error!(error = %error, "tarea SQLite abortada");
            AppFailure::new(Fallo::InternalError, "No se pudo completar la operacion")
        })?
}

fn required_id(value: &str) -> Result<String, AppFailure> {
    let value = value.trim();
    if value.is_empty() {
        Err(AppFailure::new(Fallo::NotFound, "Recurso no encontrado"))
    } else {
        Ok(value.to_owned())
    }
}

fn discharge_date(command: &DischargeCommand) -> Result<chrono::NaiveDate, AppFailure> {
    match &command.discharged_at {
        Some(value) => chrono::NaiveDate::parse_from_str(value.trim(), "%Y-%m-%d").map_err(|_| {
            AppFailure::validation("discharged_at debe ser YYYY-MM-DD", Some("discharged_at"))
        }),
        None => Ok(chrono::Utc::now().date_naive()),
    }
}

fn build_list_items(
    residents: Vec<Resident>,
    open: Vec<ctx_poblacion::BedAssignment>,
    beds: Vec<ResidenceBed>,
) -> Vec<ResidentListItemView> {
    let beds: HashMap<String, ResidenceBed> = beds
        .into_iter()
        .map(|bed| (bed.bed.id.as_str().to_owned(), bed))
        .collect();
    let open: HashMap<String, ctx_poblacion::BedAssignment> = open
        .into_iter()
        .map(|assignment| (assignment.resident_id.as_str().to_owned(), assignment))
        .collect();
    residents
        .into_iter()
        .map(|resident| {
            let assignment = open.get(resident.id.as_str());
            let (room, bed_id) = match assignment.and_then(|a| beds.get(a.bed_id.as_str())) {
                Some(bed) => (
                    Some(RoomRefView {
                        id: bed.bed.room_id.as_str().to_owned(),
                        number: bed.room_number.clone(),
                        wing_id: bed.wing_id.as_str().to_owned(),
                        wing_name: bed.wing_name.clone(),
                    }),
                    Some(bed.bed.id.as_str().to_owned()),
                ),
                None => (None, None),
            };
            ResidentListItemView {
                id: resident.id.into_string(),
                external_id: resident.external_id,
                full_name: resident.full_name,
                birth_date: resident.birth_date.map(|date| date.to_string()),
                admission_date: resident.admission_date.map(|date| date.to_string()),
                status: resident.status.as_str().to_owned(),
                discharged_at: resident.discharged_at.map(|date| date.to_string()),
                room,
                bed_id,
            }
        })
        .collect()
}

fn resident_record_view(resident: Resident) -> ResidentRecordView {
    ResidentRecordView {
        id: resident.id.into_string(),
        external_id: resident.external_id,
        full_name: resident.full_name,
        birth_date: resident.birth_date.map(|date| date.to_string()),
        admission_date: resident.admission_date.map(|date| date.to_string()),
        status: resident.status.as_str().to_owned(),
        discharged_at: resident.discharged_at.map(|date| date.to_string()),
        discharged_by: resident.discharged_by.map(|by| by.into_string()),
        created_at: resident.created_at.to_string(),
        updated_at: resident.updated_at.to_string(),
    }
}

fn bed_assignment_view(assignment: ctx_poblacion::BedAssignment) -> BedAssignmentView {
    BedAssignmentView {
        id: assignment.id.into_string(),
        resident_id: assignment.resident_id.into_string(),
        bed_id: assignment.bed_id.as_str().to_owned(),
        starts_at: assignment.starts_at.to_string(),
        ends_at: assignment.ends_at.map(|ends_at| ends_at.to_string()),
        created_at: assignment.created_at.to_string(),
        created_by: assignment.created_by.map(|by| by.into_string()),
    }
}

fn resident_update_fields(command: &UpdateResidentCommand) -> Vec<&'static str> {
    let mut fields = Vec::new();
    if command.full_name.is_some() {
        fields.push("full_name");
    }
    if command.external_id.is_some() {
        fields.push("external_id");
    }
    if command.birth_date.is_some() {
        fields.push("birth_date");
    }
    if command.admission_date.is_some() {
        fields.push("admission_date");
    }
    fields
}
