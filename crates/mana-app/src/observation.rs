//! Casos de uso de Observacion: ingesta de evidencia y read models compuestos.
//!
//! Los read models de este modulo son la unica parte del sistema donde seis
//! contextos se miran a la vez. Viven aca y no en un crate porque no son de
//! nadie: son una composicion, y darle dueño a una composicion es inventar una
//! propiedad que no existe.

use mana_observation::{
    evidence::{EventInput, Resolution},
    summaries::{BathroomSummaryInput, MobilitySummaryInput, Provenance, SleepSummaryInput},
    FreshnessThresholds,
};

use crate::{
    error::AppFailure,
    identidad::{require_capability, required_token},
    state::{AppState, Stores},
};

const DEFAULT_LIMIT: i64 = 30;
const MAX_LIMIT: i64 = 365;
const EVENT_LIMIT: i64 = 100;

fn clamp_limit(requested: Option<i64>) -> i64 {
    requested.unwrap_or(DEFAULT_LIMIT).clamp(1, MAX_LIMIT)
}

fn validate_bridge_secret(secret: &str) -> Result<(), AppFailure> {
    let expected =
        std::env::var("MANA_CLINICAL_SECRET").unwrap_or_else(|_| "clinical-dev-secret".to_owned());
    if secret != expected {
        return Err(AppFailure::new(
            mana_kernel::Fallo::Forbidden,
            "invalid ingest secret",
        ));
    }
    Ok(())
}

// ---------------------------------------------------------------- comandos

#[derive(Clone, Debug)]
pub struct IngestEventCommand {
    pub source_event_id: String,
    pub monitor_key: String,
    pub kind: String,
    pub room_state: Option<String>,
    pub substate: Option<String>,
    pub zone: Option<String>,
    pub state: Option<String>,
    pub sleeping: Option<bool>,
    pub occurred_at: String,
    pub payload_json: Option<String>,
}

#[derive(Clone, Debug)]
pub struct IngestSleepCommand {
    pub source_record_id: String,
    pub resident_id: String,
    pub observed_on: String,
    pub calm_minutes: i32,
    pub restless_minutes: i32,
    pub awake_minutes: i32,
    pub out_of_bed_minutes: i32,
    pub bed_exit_count: i32,
    pub wake_count: i32,
    pub source: String,
    pub model_version: String,
    pub confidence: Option<f64>,
    pub provenance_json: Option<String>,
}

#[derive(Clone, Debug)]
pub struct IngestMobilityCommand {
    pub source_record_id: String,
    pub resident_id: String,
    pub observed_on: String,
    pub in_bed_minutes: i32,
    pub out_of_bed_minutes: i32,
    pub out_of_sight_minutes: i32,
    pub walking_minutes: i32,
    pub distance_meters: Option<f64>,
    pub transfer_count: i32,
    pub source: String,
    pub model_version: String,
    pub confidence: Option<f64>,
    pub provenance_json: Option<String>,
}

#[derive(Clone, Debug)]
pub struct IngestBathroomCommand {
    pub source_record_id: String,
    pub resident_id: String,
    pub observed_on: String,
    pub visit_count: i32,
    pub night_visit_count: i32,
    pub assisted_count: i32,
    pub total_minutes: i32,
    pub longest_visit_minutes: i32,
    pub source: String,
    pub model_version: String,
    pub confidence: Option<f64>,
    pub provenance_json: Option<String>,
}

// ------------------------------------------------------------------ views

#[derive(Clone, Debug, serde::Serialize)]
pub struct IngestEventView {
    pub id: String,
    pub duplicate: bool,
    /// `false` cuando la `monitor_key` no corresponde a ninguna cama activa. El
    /// evento se guardo igual; el bridge no tiene que reintentar.
    pub resolved: bool,
    pub monitor_key: String,
    pub bed_id: Option<String>,
    pub resident_id: Option<String>,
    /// Las alertas que este evento genero. Vacio es una respuesta legitima —la
    /// mayoria de las observaciones no disparan nada— pero **poder verlo** es
    /// lo que distingue "no correspondia" de "el motor no miro".
    pub alerts: Vec<AlertaEmitidaView>,
}

/// Una alerta que nacio de esta ingesta.
#[derive(Clone, Debug, serde::Serialize)]
pub struct AlertaEmitidaView {
    pub rule_id: String,
    pub level: String,
    pub title: String,
}

#[derive(Clone, Debug, serde::Serialize)]
pub struct BedStateView {
    pub bed_id: String,
    pub resident_id: Option<String>,
    pub room_state: Option<String>,
    pub state: String,
    pub substate: Option<String>,
    pub sleeping: Option<bool>,
    pub state_since: Option<String>,
    pub updated_at: String,
    /// Derivada de `updated_at` en cada lectura, nunca persistida.
    pub freshness: String,
}

#[derive(Clone, Debug, serde::Serialize)]
pub struct ResidentRefView {
    pub id: String,
    pub full_name: String,
    pub external_id: Option<String>,
    pub admission_date: Option<String>,
    pub status: String,
}

#[derive(Clone, Debug, serde::Serialize)]
pub struct CurrentStateView {
    pub resident: ResidentRefView,
    pub bed_id: Option<String>,
    pub current_state: Option<BedStateView>,
}

#[derive(Clone, Debug, serde::Serialize)]
pub struct SensorEventView {
    pub id: String,
    pub monitor_key: String,
    pub bed_id: Option<String>,
    pub kind: String,
    pub room_state: Option<String>,
    pub substate: Option<String>,
    pub zone: Option<String>,
    pub state: Option<String>,
    pub sleeping: Option<bool>,
    pub occurred_at: String,
    pub received_at: String,
}

#[derive(Clone, Debug, serde::Serialize)]
pub struct EventsView {
    pub resident_id: String,
    pub bed_id: Option<String>,
    pub events: Vec<SensorEventView>,
}

/// La forma la fija `packages/contracts` —el board se anida por habitacion, no
/// se aplana por cama— porque el cliente es la autoridad del contrato wire.
#[derive(Clone, Debug, serde::Serialize)]
pub struct BoardBedView {
    pub id: String,
    pub label: String,
    pub monitor_key: Option<String>,
    pub resident_id: Option<String>,
    pub resident_name: Option<String>,
    // El contrato del cliente los declara opcionales y no nullables: una cama
    // que nunca informo omite el campo en vez de mandar `null`.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub sleeping: Option<bool>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub state: Option<String>,
    pub current_state: Option<BedStateView>,
}

#[derive(Clone, Debug, serde::Serialize)]
pub struct BoardRoomView {
    pub id: String,
    pub number: String,
    pub stream_key: Option<String>,
    pub beds: Vec<BoardBedView>,
}

#[derive(Clone, Debug, serde::Serialize)]
pub struct BoardWingView {
    pub id: String,
    pub name: String,
    pub floor: String,
}

#[derive(Clone, Debug, serde::Serialize)]
pub struct BoardView {
    pub wing: BoardWingView,
    pub rooms: Vec<BoardRoomView>,
    /// Eventos que llegaron de una `monitor_key` sin vincular. Es la falla que
    /// hoy no produce un solo aviso: aca es un numero en la vista.
    pub unresolved_events: i64,
}

#[derive(Clone, Debug, serde::Serialize)]
pub struct CompanionRoomView {
    pub room_id: String,
    pub room_number: String,
    pub wing_id: String,
    /// El companion pinta un plano: sin ala ni coordenadas no hay donde
    /// ubicar la habitacion.
    pub wing_name: String,
    pub wing_floor: String,
    pub x: f64,
    pub y: f64,
    pub sort_order: i32,
    pub stream_key: Option<String>,
    pub occupants: Vec<String>,
}

#[derive(Clone, Debug, serde::Serialize)]
pub struct CompanionRoomsView {
    /// El contrato lo declara literal: las habitaciones salen del planograma y
    /// el cliente lo verifica.
    pub source: &'static str,
    pub rooms: Vec<CompanionRoomView>,
}

#[derive(Clone, Debug, serde::Serialize)]
pub struct SleepSummaryView {
    pub id: String,
    pub observed_on: String,
    pub calm_minutes: i32,
    pub restless_minutes: i32,
    pub awake_minutes: i32,
    pub out_of_bed_minutes: i32,
    pub bed_exit_count: i32,
    pub wake_count: i32,
    /// Calculados por la API. El cliente no recalcula metricas clinicas desde
    /// filas parciales.
    pub in_bed_minutes: i32,
    pub efficiency: Option<f64>,
    pub source: String,
    pub model_version: String,
    pub confidence: Option<f64>,
}

#[derive(Clone, Debug, serde::Serialize)]
pub struct MobilitySummaryView {
    pub id: String,
    pub observed_on: String,
    pub in_bed_minutes: i32,
    pub out_of_bed_minutes: i32,
    pub out_of_sight_minutes: i32,
    pub walking_minutes: i32,
    pub distance_meters: Option<f64>,
    pub transfer_count: i32,
    pub source: String,
    pub model_version: String,
    pub confidence: Option<f64>,
}

#[derive(Clone, Debug, serde::Serialize)]
pub struct BathroomSummaryView {
    pub id: String,
    pub observed_on: String,
    pub visit_count: i32,
    pub night_visit_count: i32,
    pub assisted_count: i32,
    pub total_minutes: i32,
    pub longest_visit_minutes: i32,
    pub average_visit_minutes: Option<f64>,
    pub source: String,
    pub model_version: String,
    pub confidence: Option<f64>,
}

#[derive(Clone, Debug, serde::Serialize)]
pub struct SummaryIngestView<T> {
    pub id: String,
    pub resident_id: String,
    pub observed_on: String,
    /// `true` cuando la fuente recalculo un dia que ya estaba. No es un error.
    pub replaced: bool,
    /// El resumen tal como quedo, con sus derivados ya calculados. Quien
    /// ingiere no tiene que pedirlo de nuevo para saber que se guardo.
    pub summary: T,
}

/// Un hecho de la linea de tiempo del residente.
///
/// El contrato del cliente pide un feed cronologico homogeneo —evidencia,
/// alertas y cuidado juntos— y no tres listas paralelas de resumenes. En F8
/// devolvi las tres listas, que era comodo de construir y no era lo que el
/// panel sabe pintar.
#[derive(Clone, Debug, serde::Serialize)]
pub struct TimelineItemView {
    pub id: String,
    /// `sensor_event` · `alert` · `round_task` · `care_note`.
    #[serde(rename = "type")]
    pub kind: String,
    pub occurred_at: String,
    pub title: Option<String>,
    pub detail: Option<String>,
}

#[derive(Clone, Debug, serde::Serialize)]
pub struct TimelineView {
    pub resident_id: String,
    pub timeline: Vec<TimelineItemView>,
    /// Los resumenes diarios viajan aparte: son observacion agregada, no
    /// hechos con hora.
    pub sleep: Vec<SleepSummaryView>,
    pub mobility: Vec<MobilitySummaryView>,
    pub bathroom: Vec<BathroomSummaryView>,
}

#[derive(Clone, Debug, serde::Serialize)]
pub struct ReportsSummaryView {
    pub residents: i64,
    pub beds: i64,
    pub occupied_beds: i64,
    pub observed_beds: i64,
    pub unresolved_events: i64,
}

#[derive(Clone, Debug, serde::Serialize)]
pub struct PeekView {
    pub room_id: String,
    pub stream_key: Option<String>,
    pub privacy_regions: usize,
    pub granted_to: String,
    pub granted_at: String,
}

// ------------------------------------------------------------- conversion

fn bed_state_view(state: &mana_observation::BedState) -> BedStateView {
    let now = mana_kernel::Instante::now();
    BedStateView {
        bed_id: state.bed_id.clone(),
        resident_id: state.resident_id.clone(),
        room_state: state.room_state.clone(),
        state: state.state.clone(),
        substate: state.substate.clone(),
        sleeping: state.sleeping,
        state_since: state.state_since.map(|value| value.to_string()),
        updated_at: state.updated_at.to_string(),
        freshness: state
            .freshness(now, FreshnessThresholds::default())
            .as_str()
            .to_owned(),
    }
}

fn resident_ref_view(resident: &ctx_poblacion::Resident) -> ResidentRefView {
    ResidentRefView {
        id: resident.id.as_str().to_owned(),
        full_name: resident.full_name.clone(),
        external_id: resident.external_id.clone(),
        admission_date: resident.admission_date.map(|date| date.to_string()),
        status: resident.status.as_str().to_owned(),
    }
}

fn event_view(event: &mana_observation::SensorEvent) -> SensorEventView {
    SensorEventView {
        id: event.id.as_str().to_owned(),
        monitor_key: event.monitor_key.clone(),
        bed_id: event.resolution.bed_id().map(str::to_owned),
        kind: event.kind.clone(),
        room_state: event.room_state.clone(),
        substate: event.substate.clone(),
        zone: event.zone.clone(),
        state: event.state.clone(),
        sleeping: event.sleeping,
        occurred_at: event.occurred_at.to_string(),
        received_at: event.received_at.to_string(),
    }
}

fn sleep_view(summary: &mana_observation::SleepSummary) -> SleepSummaryView {
    SleepSummaryView {
        id: summary.id.as_str().to_owned(),
        observed_on: summary.observed_on.clone(),
        calm_minutes: summary.calm_minutes,
        restless_minutes: summary.restless_minutes,
        awake_minutes: summary.awake_minutes,
        out_of_bed_minutes: summary.out_of_bed_minutes,
        bed_exit_count: summary.bed_exit_count,
        wake_count: summary.wake_count,
        in_bed_minutes: summary.in_bed_minutes(),
        efficiency: summary.efficiency(),
        source: summary.provenance.source.clone(),
        model_version: summary.provenance.model_version.clone(),
        confidence: summary.provenance.confidence,
    }
}

fn mobility_view(summary: &mana_observation::MobilitySummary) -> MobilitySummaryView {
    MobilitySummaryView {
        id: summary.id.as_str().to_owned(),
        observed_on: summary.observed_on.clone(),
        in_bed_minutes: summary.in_bed_minutes,
        out_of_bed_minutes: summary.out_of_bed_minutes,
        out_of_sight_minutes: summary.out_of_sight_minutes,
        walking_minutes: summary.walking_minutes,
        distance_meters: summary.distance_meters,
        transfer_count: summary.transfer_count,
        source: summary.provenance.source.clone(),
        model_version: summary.provenance.model_version.clone(),
        confidence: summary.provenance.confidence,
    }
}

fn bathroom_view(summary: &mana_observation::BathroomSummary) -> BathroomSummaryView {
    BathroomSummaryView {
        id: summary.id.as_str().to_owned(),
        observed_on: summary.observed_on.clone(),
        visit_count: summary.visit_count,
        night_visit_count: summary.night_visit_count,
        assisted_count: summary.assisted_count,
        total_minutes: summary.total_minutes,
        longest_visit_minutes: summary.longest_visit_minutes,
        average_visit_minutes: summary.average_visit_minutes(),
        source: summary.provenance.source.clone(),
        model_version: summary.provenance.model_version.clone(),
        confidence: summary.provenance.confidence,
    }
}

fn provenance(
    source: String,
    model_version: String,
    confidence: Option<f64>,
    provenance_json: Option<String>,
) -> Provenance {
    Provenance {
        source,
        model_version,
        confidence,
        provenance_json: provenance_json.unwrap_or_else(|| "{}".to_owned()),
    }
}

/// La cama que ocupa un residente ahora, si ocupa alguna.
fn open_bed_for_resident(
    stores: &Stores,
    connection: &mut diesel::SqliteConnection,
    resident_id: &str,
) -> Result<Option<String>, AppFailure> {
    let assignments = stores
        .poblacion
        .list_open_assignments_in_transaction(connection)?;
    Ok(assignments
        .into_iter()
        .find(|assignment| assignment.resident_id.as_str() == resident_id)
        .map(|assignment| assignment.bed_id.as_str().to_owned()))
}

// --------------------------------------------------------------- ingesta

/// Resolve monitor_key → bed → resident inside a transaction.
///
/// The resolution happens inside the transaction to prevent race conditions
/// between concurrent events seeing different occupants.
fn resolve_bed(
    connection: &mut diesel::SqliteConnection,
    stores: &Stores,
    monitor_key: &str,
) -> Result<Resolution, AppFailure> {
    let bed = stores
        .residence
        .find_bed_by_monitor_key_in_transaction(connection, monitor_key)?;

    match bed {
        Some(bed) => {
            let bed_id = bed.id.as_str().to_owned();
            let assignments = stores
                .poblacion
                .list_open_assignments_in_transaction(connection)?;
            let resident_id = assignments
                .into_iter()
                .find(|assignment| assignment.bed_id.as_str() == bed_id)
                .map(|assignment| assignment.resident_id.as_str().to_owned());
            Ok(Resolution::Resolved {
                bed_id,
                resident_id,
            })
        }
        None => Ok(Resolution::Unresolved),
    }
}

impl AppState {
    /// Ingiere un evento del detector.
    ///
    /// Resolver `monitor_key` -> cama -> residente cruza Residencia y Poblacion,
    /// asi que ocurre dentro de la transaccion que escribe: si la resolucion
    /// pasara afuera, dos eventos concurrentes podrian proyectar con ocupantes
    /// distintos.
    ///
    /// Una `monitor_key` sin vincular **no** rechaza el evento. La evidencia se
    /// guarda igual y queda contable en `unresolved_events`.
    ///
    /// La evaluación de reglas de alarma ahora ocurre en Sentinel (downstream),
    /// no en Hub. Hub solo persiste y publica.
    pub async fn ingest_event(
        &self,
        secret: &str,
        command: IngestEventCommand,
    ) -> Result<IngestEventView, AppFailure> {
        validate_bridge_secret(secret)?;
        let occurred_at = command
            .occurred_at
            .parse()
            .map_err(|_| AppFailure::validation("invalid occurred_at", Some("occurred_at")))?;

        let view = self.transaction(move |connection, stores| {
            // 1. Resolve monitor_key → bed → resident
            let resolution = resolve_bed(connection, stores, &command.monitor_key)?;

            // 2. Persist the event
            let ingestion = mana_observation::ingest_in_transaction(
                connection,
                EventInput {
                    source_event_id: command.source_event_id,
                    monitor_key: command.monitor_key.clone(),
                    resolution,
                    kind: command.kind.clone(),
                    room_state: command.room_state.clone(),
                    substate: command.substate.clone(),
                    zone: command.zone,
                    state: command.state.clone(),
                    sleeping: command.sleeping,
                    occurred_at,
                    payload_json: command.payload_json.unwrap_or_else(|| "{}".to_owned()),
                },
            )?;

            // No more inline engine evaluation — Sentinel handles it downstream
            Ok(IngestEventView {
                id: ingestion.event.id.as_str().to_owned(),
                duplicate: ingestion.duplicate,
                resolved: ingestion.event.resolution.is_resolved(),
                monitor_key: ingestion.event.monitor_key.clone(),
                bed_id: ingestion.event.resolution.bed_id().map(str::to_owned),
                resident_id: ingestion.event.resolution.resident_id().map(str::to_owned),
                alerts: Vec::new(),
            })
        })
        .await?;

        // 4. Publish to NATS (after commit, best-effort)
        if !view.duplicate {
            if let Some(broker) = self.nats() {
                let trace_id = format!("trace-{}", uuid::Uuid::new_v4());
                let perception = mana_engine_v2::PerceptionEvent {
                    event_id: view.id.clone(),
                    trace_id: Some(trace_id.clone()),
                    monitor_key: view.monitor_key.clone(),
                    bed_id: view.bed_id.clone(),
                    resident_id: view.resident_id.clone(),
                    state: None,
                    sleeping: None,
                    zone: None,
                    extremities_out_of_bed: None,
                    body_parts_out: None,
                    objects: None,
                    room: None,
                    confidence: 0.0,
                    occurred_at: occurred_at.into(),
                };
                tracing::info!(
                    trace_id = %trace_id,
                    event_id = %view.id,
                    "Publishing perception event with trace"
                );
                if let Err(e) = broker.publish_perception(&perception).await {
                    tracing::warn!(error = %e, "Failed to publish evt_perception to NATS");
                }
            }
        }

        Ok(view)
    }

    pub async fn ingest_sleep_summary(
        &self,
        secret: &str,
        command: IngestSleepCommand,
    ) -> Result<SummaryIngestView<SleepSummaryView>, AppFailure> {
        validate_bridge_secret(secret)?;
        let result = self.observation.ingest_sleep_summary(SleepSummaryInput {
            source_record_id: command.source_record_id,
            resident_id: command.resident_id,
            observed_on: command.observed_on,
            calm_minutes: command.calm_minutes,
            restless_minutes: command.restless_minutes,
            awake_minutes: command.awake_minutes,
            out_of_bed_minutes: command.out_of_bed_minutes,
            bed_exit_count: command.bed_exit_count,
            wake_count: command.wake_count,
            provenance: provenance(
                command.source,
                command.model_version,
                command.confidence,
                command.provenance_json,
            ),
        })?;
        Ok(SummaryIngestView {
            id: result.summary.id.as_str().to_owned(),
            resident_id: result.summary.resident_id.clone(),
            observed_on: result.summary.observed_on.clone(),
            replaced: result.replaced,
            summary: sleep_view(&result.summary),
        })
    }

    pub async fn ingest_mobility_summary(
        &self,
        secret: &str,
        command: IngestMobilityCommand,
    ) -> Result<SummaryIngestView<MobilitySummaryView>, AppFailure> {
        validate_bridge_secret(secret)?;
        let result = self
            .observation
            .ingest_mobility_summary(MobilitySummaryInput {
                source_record_id: command.source_record_id,
                resident_id: command.resident_id,
                observed_on: command.observed_on,
                in_bed_minutes: command.in_bed_minutes,
                out_of_bed_minutes: command.out_of_bed_minutes,
                out_of_sight_minutes: command.out_of_sight_minutes,
                walking_minutes: command.walking_minutes,
                distance_meters: command.distance_meters,
                transfer_count: command.transfer_count,
                provenance: provenance(
                    command.source,
                    command.model_version,
                    command.confidence,
                    command.provenance_json,
                ),
            })?;
        Ok(SummaryIngestView {
            id: result.summary.id.as_str().to_owned(),
            resident_id: result.summary.resident_id.clone(),
            observed_on: result.summary.observed_on.clone(),
            replaced: result.replaced,
            summary: mobility_view(&result.summary),
        })
    }

    pub async fn ingest_bathroom_summary(
        &self,
        secret: &str,
        command: IngestBathroomCommand,
    ) -> Result<SummaryIngestView<BathroomSummaryView>, AppFailure> {
        validate_bridge_secret(secret)?;
        let result = self
            .observation
            .ingest_bathroom_summary(BathroomSummaryInput {
                source_record_id: command.source_record_id,
                resident_id: command.resident_id,
                observed_on: command.observed_on,
                visit_count: command.visit_count,
                night_visit_count: command.night_visit_count,
                assisted_count: command.assisted_count,
                total_minutes: command.total_minutes,
                longest_visit_minutes: command.longest_visit_minutes,
                provenance: provenance(
                    command.source,
                    command.model_version,
                    command.confidence,
                    command.provenance_json,
                ),
            })?;
        Ok(SummaryIngestView {
            id: result.summary.id.as_str().to_owned(),
            resident_id: result.summary.resident_id.clone(),
            observed_on: result.summary.observed_on.clone(),
            replaced: result.replaced,
            summary: bathroom_view(&result.summary),
        })
    }
}

// ------------------------------------------------------------ read models

impl AppState {
    pub async fn resident_current_state(
        &self,
        token: &str,
        resident_id: &str,
    ) -> Result<CurrentStateView, AppFailure> {
        let token = required_token(token)?;
        let resident_id = resident_id.to_owned();
        let enabled = self.enabled_capabilities.clone();
        self.transaction(move |connection, stores| {
            let actor = crate::identidad::authenticated_actor_in_transaction(
                &stores.identity,
                connection,
                &token,
                &enabled,
            )?;
            require_capability(&actor, "residents.live.read")?;

            let resident = stores
                .poblacion
                .get_resident(&resident_id.as_str().into())?;
            let bed_id = open_bed_for_resident(stores, connection, &resident_id)?;
            let state = match &bed_id {
                Some(bed_id) => stores.observation.current_state(bed_id)?,
                None => None,
            };
            Ok(CurrentStateView {
                resident: resident_ref_view(&resident),
                bed_id,
                current_state: state.as_ref().map(bed_state_view),
            })
        })
        .await
    }

    pub async fn resident_events(
        &self,
        token: &str,
        resident_id: &str,
    ) -> Result<EventsView, AppFailure> {
        let token = required_token(token)?;
        let resident_id = resident_id.to_owned();
        let enabled = self.enabled_capabilities.clone();
        self.transaction(move |connection, stores| {
            let actor = crate::identidad::authenticated_actor_in_transaction(
                &stores.identity,
                connection,
                &token,
                &enabled,
            )?;
            require_capability(&actor, "residents.live.read")?;

            let bed_id = open_bed_for_resident(stores, connection, &resident_id)?;
            let events = match &bed_id {
                Some(bed_id) => stores.observation.events_for_bed(bed_id, EVENT_LIMIT)?,
                None => Vec::new(),
            };
            Ok(EventsView {
                resident_id,
                bed_id,
                events: events.iter().map(event_view).collect(),
            })
        })
        .await
    }

    /// El board de un ala: la vista de la que cuelga el producto.
    ///
    /// Compone Residencia (alas, habitaciones, camas), Poblacion (quien ocupa
    /// cada cama) y Observacion (que informo el detector). Es la unica consulta
    /// del sistema que mira tres contextos a la vez, y por eso vive aca.
    /// El board de un ala: la vista de la que cuelga el producto.
    ///
    /// Compone Residencia (alas, habitaciones, camas), Poblacion (quien ocupa
    /// cada cama) y Observacion (que informo el detector). Es la unica consulta
    /// del sistema que mira tres contextos a la vez, y por eso vive aca.
    pub async fn wing_board(&self, token: &str, wing_id: &str) -> Result<BoardView, AppFailure> {
        let token = required_token(token)?;
        let wing_id = wing_id.to_owned();
        let enabled = self.enabled_capabilities.clone();
        self.transaction(move |connection, stores| {
            let actor = crate::identidad::authenticated_actor_in_transaction(
                &stores.identity,
                connection,
                &token,
                &enabled,
            )?;
            require_capability(&actor, "monitoring.board.read")?;

            let wing = stores.residence.get_wing(&wing_id.as_str().into())?;
            let rooms = stores.residence.list_rooms(&wing.id)?;
            let assignments = stores
                .poblacion
                .list_open_assignments_in_transaction(connection)?;
            let residents = stores.poblacion.list_residents(None)?;

            // Una sola consulta para todas las camas del ala, no una por cama.
            let mut all_beds = Vec::new();
            for room in &rooms {
                all_beds.push((room.clone(), stores.residence.list_beds(&room.id)?));
            }
            let bed_ids: Vec<String> = all_beds
                .iter()
                .flat_map(|(_, beds)| beds.iter().map(|bed| bed.id.as_str().to_owned()))
                .collect();
            let states = stores.observation.bed_states(&bed_ids)?;

            let mut board_rooms = Vec::with_capacity(all_beds.len());
            for (room, beds) in all_beds {
                let mut board_beds = Vec::with_capacity(beds.len());
                for bed in beds {
                    let bed_id = bed.id.as_str().to_owned();
                    let resident_id = assignments
                        .iter()
                        .find(|assignment| assignment.bed_id.as_str() == bed_id)
                        .map(|assignment| assignment.resident_id.as_str().to_owned());
                    let resident_name = resident_id.as_ref().and_then(|id| {
                        residents
                            .iter()
                            .find(|resident| resident.id.as_str() == id)
                            .map(|resident| resident.full_name.clone())
                    });
                    let state = states.iter().find(|state| state.bed_id == bed_id);
                    board_beds.push(BoardBedView {
                        id: bed_id,
                        label: bed.label.clone(),
                        monitor_key: bed.monitor_key.as_ref().map(|key| key.as_str().to_owned()),
                        resident_id,
                        resident_name,
                        sleeping: state.and_then(|state| state.sleeping),
                        state: state.map(|state| state.state.clone()),
                        current_state: state.map(bed_state_view),
                    });
                }
                board_rooms.push(BoardRoomView {
                    id: room.id.as_str().to_owned(),
                    number: room.number.clone(),
                    stream_key: room.stream_key.as_ref().map(|key| key.as_str().to_owned()),
                    beds: board_beds,
                });
            }

            Ok(BoardView {
                wing: BoardWingView {
                    id: wing.id.as_str().to_owned(),
                    name: wing.name.clone(),
                    floor: wing.floor.clone(),
                },
                rooms: board_rooms,
                unresolved_events: stores.observation.unresolved_count()?,
            })
        })
        .await
    }

    pub async fn companion_rooms(&self, token: &str) -> Result<CompanionRoomsView, AppFailure> {
        let token = required_token(token)?;
        let enabled = self.enabled_capabilities.clone();
        self.transaction(move |connection, stores| {
            let actor = crate::identidad::authenticated_actor_in_transaction(
                &stores.identity,
                connection,
                &token,
                &enabled,
            )?;
            require_capability(&actor, "monitoring.live.read")?;

            let assignments = stores
                .poblacion
                .list_open_assignments_in_transaction(connection)?;
            let residents = stores.poblacion.list_residents(None)?;

            let mut rooms = Vec::new();
            for wing in stores.residence.list_wings_all()? {
                let planogram = stores.residence.planogram(&wing.id)?;
                for room in stores.residence.list_rooms(&wing.id)? {
                    let placement = planogram
                        .iter()
                        .find(|entry| entry.room_id.as_str() == room.id.as_str());
                    let mut occupants = Vec::new();
                    for bed in stores.residence.list_beds(&room.id)? {
                        if let Some(assignment) = assignments
                            .iter()
                            .find(|assignment| assignment.bed_id.as_str() == bed.id.as_str())
                        {
                            if let Some(resident) = residents
                                .iter()
                                .find(|r| r.id.as_str() == assignment.resident_id.as_str())
                            {
                                occupants.push(resident.full_name.clone());
                            }
                        }
                    }
                    rooms.push(CompanionRoomView {
                        room_id: room.id.as_str().to_owned(),
                        room_number: room.number.clone(),
                        wing_id: wing.id.as_str().to_owned(),
                        wing_name: wing.name.clone(),
                        wing_floor: wing.floor.clone(),
                        x: placement.map(|entry| entry.x).unwrap_or(0.0),
                        y: placement.map(|entry| entry.y).unwrap_or(0.0),
                        sort_order: placement.map(|entry| entry.sort_order).unwrap_or(0),
                        stream_key: room.stream_key.as_ref().map(|k| k.as_str().to_owned()),
                        occupants,
                    });
                }
            }
            Ok(CompanionRoomsView {
                source: "planogram",
                rooms,
            })
        })
        .await
    }

    pub async fn resident_sleep(
        &self,
        token: &str,
        resident_id: &str,
        limit: Option<i64>,
    ) -> Result<Vec<SleepSummaryView>, AppFailure> {
        let summaries = self
            .read_summaries(
                token,
                "sleep.read",
                resident_id,
                limit,
                |store, id, limit| Ok(store.resident_sleep(id, limit)?),
            )
            .await?;
        Ok(summaries.iter().map(sleep_view).collect())
    }

    pub async fn resident_mobility(
        &self,
        token: &str,
        resident_id: &str,
        limit: Option<i64>,
    ) -> Result<Vec<MobilitySummaryView>, AppFailure> {
        let summaries = self
            .read_summaries(
                token,
                "mobility.read",
                resident_id,
                limit,
                |store, id, limit| Ok(store.resident_mobility(id, limit)?),
            )
            .await?;
        Ok(summaries.iter().map(mobility_view).collect())
    }

    pub async fn resident_bathroom(
        &self,
        token: &str,
        resident_id: &str,
        limit: Option<i64>,
    ) -> Result<Vec<BathroomSummaryView>, AppFailure> {
        let summaries = self
            .read_summaries(
                token,
                "bathroom.read",
                resident_id,
                limit,
                |store, id, limit| Ok(store.resident_bathroom(id, limit)?),
            )
            .await?;
        Ok(summaries.iter().map(bathroom_view).collect())
    }

    pub async fn resident_timeline(
        &self,
        token: &str,
        resident_id: &str,
        limit: Option<i64>,
    ) -> Result<TimelineView, AppFailure> {
        let token = required_token(token)?;
        let resident_id = resident_id.to_owned();
        let enabled = self.enabled_capabilities.clone();
        let limit = clamp_limit(limit);
        self.transaction(move |connection, stores| {
            let actor = crate::identidad::authenticated_actor_in_transaction(
                &stores.identity,
                connection,
                &token,
                &enabled,
            )?;
            // La linea de tiempo junta los tres ejes: pide las tres capabilities.
            require_capability(&actor, "sleep.read")?;
            require_capability(&actor, "mobility.read")?;
            require_capability(&actor, "bathroom.read")?;

            let mut timeline = Vec::new();

            let bed_id = open_bed_for_resident(stores, connection, &resident_id)?;
            if let Some(bed_id) = &bed_id {
                for event in stores.observation.events_for_bed(bed_id, EVENT_LIMIT)? {
                    timeline.push(TimelineItemView {
                        id: event.id.as_str().to_owned(),
                        kind: "sensor_event".to_owned(),
                        occurred_at: event.occurred_at.to_string(),
                        title: event.state.clone().or_else(|| Some(event.kind.clone())),
                        detail: event.room_state.clone(),
                    });
                }
            }

            for alert in stores
                .vigilancia
                .list_alerts(None, None, Some(&resident_id))?
            {
                timeline.push(TimelineItemView {
                    id: alert.id.clone(),
                    kind: "alert".to_owned(),
                    occurred_at: alert.occurred_at.to_string(),
                    title: Some(alert.title.clone()),
                    detail: alert.detail.clone(),
                });
            }

            for note in stores.cuidado.list_notes(&resident_id, limit)? {
                timeline.push(TimelineItemView {
                    id: note.id.as_str().to_owned(),
                    kind: "care_note".to_owned(),
                    occurred_at: note.created_at.to_string(),
                    title: Some(note.kind.clone()),
                    detail: Some(note.body.clone()),
                });
            }

            // Mas reciente primero: es como se lee una linea de tiempo clinica.
            timeline.sort_by(|a, b| b.occurred_at.cmp(&a.occurred_at));

            Ok(TimelineView {
                timeline,
                sleep: stores
                    .observation
                    .resident_sleep(&resident_id, limit)?
                    .iter()
                    .map(sleep_view)
                    .collect(),
                mobility: stores
                    .observation
                    .resident_mobility(&resident_id, limit)?
                    .iter()
                    .map(mobility_view)
                    .collect(),
                bathroom: stores
                    .observation
                    .resident_bathroom(&resident_id, limit)?
                    .iter()
                    .map(bathroom_view)
                    .collect(),
                resident_id,
            })
        })
        .await
    }

    /// Las tres lecturas de resumenes solo cambian en la capability y en la
    /// consulta. El resto —token, actor, limite— es identico, y repetirlo tres
    /// veces es donde se cuela una capability mal puesta.
    async fn read_summaries<T, F>(
        &self,
        token: &str,
        capability: &'static str,
        resident_id: &str,
        limit: Option<i64>,
        query: F,
    ) -> Result<Vec<T>, AppFailure>
    where
        T: Send + 'static,
        F: FnOnce(&mana_observation::ObservationStore, &str, i64) -> Result<Vec<T>, AppFailure>
            + Send
            + 'static,
    {
        let token = required_token(token)?;
        let resident_id = resident_id.to_owned();
        let enabled = self.enabled_capabilities.clone();
        let limit = clamp_limit(limit);
        self.transaction(move |connection, stores| {
            let actor = crate::identidad::authenticated_actor_in_transaction(
                &stores.identity,
                connection,
                &token,
                &enabled,
            )?;
            require_capability(&actor, capability)?;
            query(&stores.observation, &resident_id, limit)
        })
        .await
    }

    pub async fn reports_summary(&self, token: &str) -> Result<ReportsSummaryView, AppFailure> {
        let token = required_token(token)?;
        let enabled = self.enabled_capabilities.clone();
        self.transaction(move |connection, stores| {
            let actor = crate::identidad::authenticated_actor_in_transaction(
                &stores.identity,
                connection,
                &token,
                &enabled,
            )?;
            require_capability(&actor, "analytics.read")?;

            let residents = stores.poblacion.list_residents(None)?;
            let assignments = stores
                .poblacion
                .list_open_assignments_in_transaction(connection)?;

            let mut bed_ids = Vec::new();
            for wing in stores.residence.list_wings_all()? {
                for room in stores.residence.list_rooms(&wing.id)? {
                    for bed in stores.residence.list_beds(&room.id)? {
                        bed_ids.push(bed.id.as_str().to_owned());
                    }
                }
            }

            let observed = stores.observation.bed_states(&bed_ids)?.len();
            Ok(ReportsSummaryView {
                residents: residents.len() as i64,
                beds: bed_ids.len() as i64,
                occupied_beds: assignments.len() as i64,
                observed_beds: observed as i64,
                unresolved_events: stores.observation.unresolved_count()?,
            })
        })
        .await
    }

    /// Autoriza mirar el stream de una habitacion y deja la traza.
    ///
    /// No devuelve video: el stream va directo de las IA cells a los paneles.
    /// Lo que hace el hub es decir quien puede mirar y dejar registrado que
    /// miro, que es la parte auditable.
    pub async fn peek_room(&self, token: &str, room_id: &str) -> Result<PeekView, AppFailure> {
        let token = required_token(token)?;
        let room_id = room_id.to_owned();
        let enabled = self.enabled_capabilities.clone();
        self.transaction(move |connection, stores| {
            let actor = crate::identidad::authenticated_actor_in_transaction(
                &stores.identity,
                connection,
                &token,
                &enabled,
            )?;
            require_capability(&actor, "monitoring.live.read")?;

            let room = stores.residence.get_room(&room_id.as_str().into())?;
            let regions = stores.residence.privacy_regions(&room.id)?;
            let now = mana_kernel::Instante::now();

            let record = ctx_auditoria::AuditRecord::new(
                Some(crate::identidad::actor_id(&actor)),
                "room.peeked",
                "room",
                room.id.as_str(),
                serde_json::json!({ "stream_key": room.stream_key.as_ref().map(|k| k.as_str()) }),
            )?;
            stores.audit.record_in_transaction(connection, record)?;

            Ok(PeekView {
                room_id: room.id.as_str().to_owned(),
                stream_key: room.stream_key.as_ref().map(|k| k.as_str().to_owned()),
                privacy_regions: regions.len(),
                granted_to: crate::identidad::actor_id(&actor).as_str().to_owned(),
                granted_at: now.to_string(),
            })
        })
        .await
    }
}
