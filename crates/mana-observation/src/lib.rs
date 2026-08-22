//! Observacion: evidencia del detector y proyeccion de estado por cama.
//!
//! **No es un `ctx-*` y no debe serlo.** No decide que significa una alarma ni
//! administra residentes: es evidencia externa mas una proyeccion operacional.
//! Su retencion, volumen y transporte cambian sin cambiar el modelo del
//! Registro, y su destino es Parquet.
//!
//! Por eso `xtask verificar-contextos` no lo vigila: esa regla habla de
//! contextos de negocio. La que si aplica es la inversa —**ningun `ctx-*` puede
//! depender de este crate**— o el subsistema se vuelve la puerta trasera por la
//! que dos contextos se tocan.

mod common;
mod error;
pub mod evidence;
pub mod schema;
mod sqlite;
pub mod state;
pub mod summaries;

pub use error::ObservationError;
pub use evidence::{new_event_id, EventInput, Resolution, SensorEvent, SensorEventId};
pub use mana_storage::DbPool;
pub use state::{BedState, Freshness, FreshnessThresholds};
pub use summaries::{
    sqlite::Upsert, BathroomSummary, BathroomSummaryInput, MobilitySummary, MobilitySummaryInput,
    Provenance, SleepSummary, SleepSummaryInput,
};

use diesel::SqliteConnection;
use diesel_migrations::{embed_migrations, EmbeddedMigrations};
use mana_kernel::Instante;
use mana_storage::{connection as get_connection, DbConnection};

pub const MIGRATIONS: EmbeddedMigrations = embed_migrations!();

pub fn run_migrations(pool: &DbPool) -> Result<(), ObservationError> {
    mana_storage::run_migrations(pool, MIGRATIONS).map_err(ObservationError::from)
}

/// Resultado de ingerir un evento.
///
/// `duplicate` no es un error: es el reintento del bridge, y el contrato lo
/// devuelve como `200` con `duplicate: true` en vez de `201`.
#[derive(Clone, Debug)]
pub struct Ingestion {
    pub event: SensorEvent,
    pub duplicate: bool,
}

#[derive(Clone)]
pub struct ObservationStore {
    pub(crate) pool: DbPool,
}

impl ObservationStore {
    pub fn new(pool: DbPool) -> Self {
        Self { pool }
    }

    pub fn pool(&self) -> &DbPool {
        &self.pool
    }

    fn connection(&self) -> Result<DbConnection, ObservationError> {
        get_connection(&self.pool).map_err(ObservationError::from)
    }

    /// Acepta un evento y actualiza la proyeccion de su cama, si resolvio.
    ///
    /// Es idempotente por `source_event_id`: reintentar devuelve el evento ya
    /// guardado con `duplicate: true` y **no vuelve a proyectar**.
    pub fn ingest(&self, input: EventInput) -> Result<Ingestion, ObservationError> {
        let mut connection = self.connection()?;
        sqlite::ingest_in_transaction(&mut connection, input)
    }

    pub fn current_state(&self, bed_id: &str) -> Result<Option<BedState>, ObservationError> {
        let mut connection = self.connection()?;
        sqlite::current_state(&mut connection, bed_id)
    }

    /// Estados de varias camas en una sola consulta. El board las pide de a
    /// decenas y hacerlo de a una seria un N+1 contra la conexion que tambien
    /// sirve la ingesta.
    pub fn bed_states(&self, bed_ids: &[String]) -> Result<Vec<BedState>, ObservationError> {
        let mut connection = self.connection()?;
        sqlite::bed_states(&mut connection, bed_ids)
    }

    pub fn events_for_bed(
        &self,
        bed_id: &str,
        limit: i64,
    ) -> Result<Vec<SensorEvent>, ObservationError> {
        let mut connection = self.connection()?;
        sqlite::events_for_bed(&mut connection, bed_id, limit)
    }

    pub fn ingest_sleep_summary(
        &self,
        input: SleepSummaryInput,
    ) -> Result<Upsert<SleepSummary>, ObservationError> {
        let mut connection = self.connection()?;
        summaries::sqlite::upsert_sleep(&mut connection, input)
    }

    pub fn ingest_mobility_summary(
        &self,
        input: MobilitySummaryInput,
    ) -> Result<Upsert<MobilitySummary>, ObservationError> {
        let mut connection = self.connection()?;
        summaries::sqlite::upsert_mobility(&mut connection, input)
    }

    pub fn ingest_bathroom_summary(
        &self,
        input: BathroomSummaryInput,
    ) -> Result<Upsert<BathroomSummary>, ObservationError> {
        let mut connection = self.connection()?;
        summaries::sqlite::upsert_bathroom(&mut connection, input)
    }

    pub fn resident_sleep(
        &self,
        resident_id: &str,
        limit: i64,
    ) -> Result<Vec<SleepSummary>, ObservationError> {
        let mut connection = self.connection()?;
        summaries::sqlite::list_sleep(&mut connection, resident_id, limit)
    }

    pub fn resident_mobility(
        &self,
        resident_id: &str,
        limit: i64,
    ) -> Result<Vec<MobilitySummary>, ObservationError> {
        let mut connection = self.connection()?;
        summaries::sqlite::list_mobility(&mut connection, resident_id, limit)
    }

    pub fn resident_bathroom(
        &self,
        resident_id: &str,
        limit: i64,
    ) -> Result<Vec<BathroomSummary>, ObservationError> {
        let mut connection = self.connection()?;
        summaries::sqlite::list_bathroom(&mut connection, resident_id, limit)
    }

    /// Cuantos eventos llegaron de una `monitor_key` que no resuelve a ninguna
    /// cama. Es una superficie que alguien tiene que mirar: hoy una camara mal
    /// vinculada no produce un solo aviso.
    pub fn unresolved_count(&self) -> Result<i64, ObservationError> {
        let mut connection = self.connection()?;
        sqlite::unresolved_count(&mut connection)
    }
}

/// Ingiere un evento dentro de una transaccion ya abierta.
///
/// Es la puerta que usa `mana-app`: resolver `monitor_key` -> cama -> residente
/// cruza Residencia y Poblacion, y esa resolucion tiene que ocurrir en la misma
/// transaccion que la escritura o dos eventos concurrentes podrian proyectar
/// con ocupantes distintos.
pub fn ingest_in_transaction(
    connection: &mut SqliteConnection,
    input: EventInput,
) -> Result<Ingestion, ObservationError> {
    sqlite::ingest_in_transaction(connection, input)
}

/// Descarta la proyeccion de una cama.
///
/// Se expone suelta y no como metodo del store porque quien la llama es
/// `mana-app`: cambiar el ocupante de una cama descarta su proyeccion **en la
/// misma transaccion** que la asignacion (invariante 6). Ese cruce no puede
/// tomar su propia conexion.
pub fn clear_projection_in_transaction(
    connection: &mut SqliteConnection,
    bed_id: &str,
) -> Result<(), ObservationError> {
    sqlite::clear_projection(connection, bed_id)
}

/// El estado proyectado de una cama, dentro de una transaccion en curso.
///
/// El motor de alarmas evalua contra la proyeccion, y tiene que leerla en la
/// misma transaccion que acaba de escribirla o evaluaria contra el estado
/// anterior.
pub fn current_state_in_transaction(
    connection: &mut SqliteConnection,
    bed_id: &str,
) -> Result<Option<BedState>, ObservationError> {
    sqlite::current_state(connection, bed_id)
}

/// El ultimo estado distinto anterior a un instante. Ver
/// [`sqlite::previous_distinct_state`].
pub fn previous_distinct_state_in_transaction(
    connection: &mut SqliteConnection,
    bed_id: &str,
    before: &Instante,
    state: &str,
) -> Result<Option<String>, ObservationError> {
    sqlite::previous_distinct_state(connection, bed_id, &before.to_string(), state)
}

/// Persiste un scene event desde NATS para event sourcing.
pub fn persist_scene_event(
    connection: &mut SqliteConnection,
    event: &mana_engine_v2::SceneEvent,
) -> Result<(), ObservationError> {
    use crate::schema::scene_events;
    use diesel::prelude::*;

    let payload = serde_json::to_string(event).unwrap_or_default();
    let trigger_type = match &event.trigger {
        mana_engine_v2::scene_event::TriggerInfo::Perception { .. } => Some("perception"),
        mana_engine_v2::scene_event::TriggerInfo::DwellCompleted { .. } => Some("dwell"),
        mana_engine_v2::scene_event::TriggerInfo::TransitionDetected { .. } => Some("transition"),
        mana_engine_v2::scene_event::TriggerInfo::ObjectChange { .. } => Some("object_change"),
    };
    let from_state = match &event.trigger {
        mana_engine_v2::scene_event::TriggerInfo::TransitionDetected { from_state, .. } => Some(format!("{:?}", from_state)),
        _ => None,
    };
    let to_state = match &event.trigger {
        mana_engine_v2::scene_event::TriggerInfo::TransitionDetected { to_state, .. } => Some(format!("{:?}", to_state)),
        _ => None,
    };
    let event_id = format!("scene-{}", event.timestamp.timestamp_millis());

    diesel::insert_into(scene_events::table)
        .values((
            scene_events::id.eq(&event_id),
            scene_events::event_id.eq(&event_id),
            scene_events::bed_id.eq(&event.bed_id),
            scene_events::resident_id.eq(event.resident_id.as_deref()),
            scene_events::event_type.eq(format!("{:?}", event.event_type)),
            scene_events::from_state.eq(&from_state),
            scene_events::to_state.eq(&to_state),
            scene_events::trigger_type.eq(&trigger_type),
            scene_events::timestamp.eq(event.timestamp.to_rfc3339()),
            scene_events::payload_json.eq(&payload),
            scene_events::received_at.eq(chrono::Utc::now().to_rfc3339()),
        ))
        .execute(connection)
        .map_err(|e| ObservationError::Database(e))?;

    Ok(())
}

/// Persiste un notification event desde NATS para event sourcing.
pub fn persist_notification_event(
    connection: &mut SqliteConnection,
    id: &str,
    category: &str,
    bed_id: &str,
    resident_id: Option<&str>,
    event_type: &str,
    timestamp: &str,
    rule_id: Option<&str>,
    risk_level: Option<&str>,
    payload_json: &str,
) -> Result<(), ObservationError> {
    use crate::schema::notification_events;
    use diesel::prelude::*;

    diesel::insert_into(notification_events::table)
        .values((
            notification_events::id.eq(id),
            notification_events::category.eq(category),
            notification_events::bed_id.eq(bed_id),
            notification_events::resident_id.eq(resident_id),
            notification_events::event_type.eq(event_type),
            notification_events::timestamp.eq(timestamp),
            notification_events::rule_id.eq(rule_id),
            notification_events::risk_level.eq(risk_level),
            notification_events::payload_json.eq(payload_json),
            notification_events::received_at.eq(chrono::Utc::now().to_rfc3339()),
        ))
        .execute(connection)
        .map_err(|e| ObservationError::Database(e))?;

    Ok(())
}

#[cfg(test)]
mod tests;
