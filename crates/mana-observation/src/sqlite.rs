//! Mapeo fila <-> dominio. Las `*Row` no salen del crate.

use diesel::prelude::*;
use mana_kernel::Instante;

use crate::{
    common::parse_instant,
    error::ObservationError,
    evidence::{new_event_id, EventInput, Resolution, SensorEvent},
    schema::{current_bed_states, sensor_events},
    state::BedState,
    Ingestion,
};

#[derive(Queryable, Selectable)]
#[diesel(table_name = sensor_events)]
struct SensorEventRow {
    id: String,
    source_event_id: String,
    monitor_key: String,
    bed_id: Option<String>,
    resident_id: Option<String>,
    kind: String,
    room_state: Option<String>,
    substate: Option<String>,
    zone: Option<String>,
    state: Option<String>,
    sleeping: Option<i32>,
    occurred_at: String,
    received_at: String,
    payload_json: String,
}

#[derive(Insertable)]
#[diesel(table_name = sensor_events)]
struct NewSensorEventRow {
    id: String,
    source_event_id: String,
    monitor_key: String,
    bed_id: Option<String>,
    resident_id: Option<String>,
    kind: String,
    room_state: Option<String>,
    substate: Option<String>,
    zone: Option<String>,
    state: Option<String>,
    sleeping: Option<i32>,
    occurred_at: String,
    received_at: String,
    payload_json: String,
}

#[derive(Queryable, Selectable)]
#[diesel(table_name = current_bed_states)]
struct BedStateRow {
    bed_id: String,
    resident_id: Option<String>,
    room_state: Option<String>,
    state: String,
    substate: Option<String>,
    sleeping: Option<i32>,
    state_since: Option<String>,
    updated_at: String,
    source: String,
    source_event_id: Option<String>,
}

/// `Option<i32>` -> `Option<bool>` sin pasar por `unwrap_or_default()`.
/// Convertir "no se" en `false` es la invariante 4 rota.
fn to_bool(value: Option<i32>) -> Option<bool> {
    value.map(|raw| raw != 0)
}

fn to_event(row: SensorEventRow) -> Result<SensorEvent, ObservationError> {
    let resolution = match row.bed_id {
        Some(bed_id) => Resolution::Resolved {
            bed_id,
            resident_id: row.resident_id,
        },
        None => Resolution::Unresolved,
    };
    Ok(SensorEvent {
        id: row.id.into(),
        source_event_id: row.source_event_id,
        monitor_key: row.monitor_key,
        resolution,
        kind: row.kind,
        room_state: row.room_state,
        substate: row.substate,
        zone: row.zone,
        state: row.state,
        sleeping: to_bool(row.sleeping),
        occurred_at: parse_instant("occurred_at", row.occurred_at)?,
        received_at: parse_instant("received_at", row.received_at)?,
        payload_json: row.payload_json,
    })
}

fn to_bed_state(row: BedStateRow) -> Result<BedState, ObservationError> {
    let state_since = match row.state_since {
        Some(raw) => Some(parse_instant("state_since", raw)?),
        None => None,
    };
    Ok(BedState {
        bed_id: row.bed_id,
        resident_id: row.resident_id,
        room_state: row.room_state,
        state: row.state,
        substate: row.substate,
        sleeping: to_bool(row.sleeping),
        state_since,
        updated_at: parse_instant("updated_at", row.updated_at)?,
        source: row.source,
        source_event_id: row.source_event_id,
    })
}

pub(crate) fn ingest_in_transaction(
    connection: &mut SqliteConnection,
    input: EventInput,
) -> Result<Ingestion, ObservationError> {
    input.validate()?;
    connection.transaction(|connection| {
        // El reintento del bridge llega antes que cualquier escritura.
        let existing: Option<SensorEventRow> = sensor_events::table
            .filter(sensor_events::source_event_id.eq(&input.source_event_id))
            .select(SensorEventRow::as_select())
            .first(connection)
            .optional()?;
        if let Some(row) = existing {
            return Ok(Ingestion {
                event: to_event(row)?,
                duplicate: true,
            });
        }

        // `received_at` lo pone el hub, no la fuente (invariante 3).
        let received_at = Instante::now();
        let id = new_event_id();
        let row = NewSensorEventRow {
            id: id.as_str().to_owned(),
            source_event_id: input.source_event_id.clone(),
            monitor_key: input.monitor_key.clone(),
            bed_id: input.resolution.bed_id().map(str::to_owned),
            resident_id: input.resolution.resident_id().map(str::to_owned),
            kind: input.kind.clone(),
            room_state: input.room_state.clone(),
            substate: input.substate.clone(),
            zone: input.zone.clone(),
            state: input.state.clone(),
            sleeping: input.sleeping.map(i32::from),
            occurred_at: input.occurred_at.to_string(),
            received_at: received_at.to_string(),
            payload_json: input.payload_json.clone(),
        };
        diesel::insert_into(sensor_events::table)
            .values(&row)
            .execute(connection)?;

        if let Resolution::Resolved {
            bed_id,
            resident_id,
        } = &input.resolution
        {
            project(
                connection,
                bed_id,
                resident_id.as_deref(),
                &input,
                &received_at,
                id.as_str(),
            )?;
        }

        Ok(Ingestion {
            event: SensorEvent {
                id,
                source_event_id: input.source_event_id,
                monitor_key: input.monitor_key,
                resolution: input.resolution,
                kind: input.kind,
                room_state: input.room_state,
                substate: input.substate,
                zone: input.zone,
                state: input.state,
                sleeping: input.sleeping,
                occurred_at: input.occurred_at,
                received_at,
                payload_json: input.payload_json,
            },
            duplicate: false,
        })
    })
}

/// `state_since` solo se mueve cuando el estado **cambia**. Un evento que
/// repite el mismo estado no reinicia el reloj, o una permanencia de cuarenta
/// minutos no venceria nunca.
fn project(
    connection: &mut SqliteConnection,
    bed_id: &str,
    resident_id: Option<&str>,
    input: &EventInput,
    received_at: &Instante,
    source_event_id: &str,
) -> Result<(), ObservationError> {
    let next_state = input.state.clone().unwrap_or_else(|| input.kind.clone());
    let previous: Option<BedStateRow> = current_bed_states::table
        .find(bed_id)
        .select(BedStateRow::as_select())
        .first(connection)
        .optional()?;

    let state_since = match &previous {
        Some(row) if row.state == next_state => row.state_since.clone(),
        _ => Some(input.occurred_at.to_string()),
    };

    let values = (
        current_bed_states::bed_id.eq(bed_id),
        current_bed_states::resident_id.eq(resident_id),
        current_bed_states::room_state.eq(input.room_state.as_deref()),
        current_bed_states::state.eq(next_state),
        current_bed_states::substate.eq(input.substate.as_deref()),
        current_bed_states::sleeping.eq(input.sleeping.map(i32::from)),
        current_bed_states::state_since.eq(state_since),
        current_bed_states::updated_at.eq(received_at.to_string()),
        current_bed_states::source.eq("detector"),
        current_bed_states::source_event_id.eq(Some(source_event_id)),
    );

    diesel::insert_into(current_bed_states::table)
        .values(values.clone())
        .on_conflict(current_bed_states::bed_id)
        .do_update()
        .set(values)
        .execute(connection)?;
    Ok(())
}

pub(crate) fn current_state(
    connection: &mut SqliteConnection,
    bed_id: &str,
) -> Result<Option<BedState>, ObservationError> {
    let row: Option<BedStateRow> = current_bed_states::table
        .find(bed_id)
        .select(BedStateRow::as_select())
        .first(connection)
        .optional()?;
    row.map(to_bed_state).transpose()
}

/// El ultimo estado **distinto** que la cama tuvo antes de un instante.
///
/// Es lo que convierte una observacion en una transicion: sin el estado del que
/// se viene, "de pie" es una postura y no un levantarse. Se excluye el estado
/// actual a proposito —un detector que repite el mismo estado no produce una
/// transicion nueva— y se desempata por `rowid`, porque `occurred_at` empata
/// entre escrituras del mismo milisegundo.
pub(crate) fn previous_distinct_state(
    connection: &mut SqliteConnection,
    bed_id: &str,
    before: &str,
    state: &str,
) -> Result<Option<String>, ObservationError> {
    let row: Option<Option<String>> = sensor_events::table
        .filter(sensor_events::bed_id.eq(bed_id))
        .filter(sensor_events::occurred_at.le(before))
        .filter(sensor_events::state.is_not_null())
        .filter(sensor_events::state.ne(state))
        .order((sensor_events::occurred_at.desc(), sensor_events::id.desc()))
        .select(sensor_events::state)
        .first(connection)
        .optional()?;
    Ok(row.flatten())
}

pub(crate) fn clear_projection(
    connection: &mut SqliteConnection,
    bed_id: &str,
) -> Result<(), ObservationError> {
    diesel::delete(current_bed_states::table.find(bed_id)).execute(connection)?;
    Ok(())
}

pub(crate) fn unresolved_count(connection: &mut SqliteConnection) -> Result<i64, ObservationError> {
    let total: i64 = sensor_events::table
        .filter(sensor_events::bed_id.is_null())
        .count()
        .get_result(connection)?;
    Ok(total)
}

pub(crate) fn events_for_bed(
    connection: &mut SqliteConnection,
    bed_id: &str,
    limit: i64,
) -> Result<Vec<SensorEvent>, ObservationError> {
    let rows: Vec<SensorEventRow> = sensor_events::table
        .filter(sensor_events::bed_id.eq(bed_id))
        .order(sensor_events::occurred_at.desc())
        .limit(limit)
        .select(SensorEventRow::as_select())
        .load(connection)?;
    rows.into_iter().map(to_event).collect()
}

/// Estados de varias camas de una sola consulta.
///
/// El board pide decenas de camas: hacerlo cama por cama seria un N+1 contra la
/// misma conexion que sirve la ingesta.
pub(crate) fn bed_states(
    connection: &mut SqliteConnection,
    bed_ids: &[String],
) -> Result<Vec<BedState>, ObservationError> {
    if bed_ids.is_empty() {
        return Ok(Vec::new());
    }
    let rows: Vec<BedStateRow> = current_bed_states::table
        .filter(current_bed_states::bed_id.eq_any(bed_ids))
        .select(BedStateRow::as_select())
        .load(connection)?;
    rows.into_iter().map(to_bed_state).collect()
}
