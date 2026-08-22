//! Cliente de Observacion: ingesta de evidencia y read models compuestos.

use reqwest::Method;
use serde::{Deserialize, Serialize};

use crate::poblacion::path;
use crate::transport::{ApiResponse, ManaClient, ManaError};

#[derive(Clone, Debug, Default, Deserialize, Serialize)]
pub struct IngestEventRequest {
    pub source_event_id: String,
    pub monitor_key: String,
    pub kind: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub room_state: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub substate: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub zone: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub state: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub sleeping: Option<bool>,
    pub occurred_at: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub payload_json: Option<String>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct IngestEventResponse {
    pub id: String,
    pub duplicate: bool,
    /// `false` cuando la `monitor_key` no corresponde a ninguna cama activa. El
    /// evento quedo guardado igual: **no hay que reintentar**.
    pub resolved: bool,
    pub monitor_key: String,
    #[serde(default)]
    pub bed_id: Option<String>,
    #[serde(default)]
    pub resident_id: Option<String>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct BedState {
    pub bed_id: String,
    #[serde(default)]
    pub resident_id: Option<String>,
    #[serde(default)]
    pub room_state: Option<String>,
    pub state: String,
    #[serde(default)]
    pub substate: Option<String>,
    /// `None` es "el detector no informo", nunca `false`.
    #[serde(default)]
    pub sleeping: Option<bool>,
    #[serde(default)]
    pub state_since: Option<String>,
    pub updated_at: String,
    pub freshness: String,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct ResidentRef {
    pub id: String,
    pub full_name: String,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct CurrentStateResponse {
    pub resident: ResidentRef,
    #[serde(default)]
    pub bed_id: Option<String>,
    #[serde(default)]
    pub current_state: Option<BedState>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct SensorEvent {
    pub id: String,
    pub monitor_key: String,
    #[serde(default)]
    pub bed_id: Option<String>,
    pub kind: String,
    #[serde(default)]
    pub state: Option<String>,
    pub occurred_at: String,
    pub received_at: String,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct EventsResponse {
    pub resident_id: String,
    #[serde(default)]
    pub bed_id: Option<String>,
    #[serde(default)]
    pub events: Vec<SensorEvent>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct BoardBed {
    pub id: String,
    pub label: String,
    #[serde(default)]
    pub monitor_key: Option<String>,
    #[serde(default)]
    pub resident_name: Option<String>,
    #[serde(default)]
    pub current_state: Option<BedState>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct BoardRoom {
    pub id: String,
    pub number: String,
    #[serde(default)]
    pub beds: Vec<BoardBed>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct BoardWing {
    pub id: String,
    pub name: String,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct BoardResponse {
    pub wing: BoardWing,
    #[serde(default)]
    pub rooms: Vec<BoardRoom>,
    /// Eventos de una `monitor_key` sin vincular. Si sube, hay una camara
    /// mirando algo que el sistema no sabe a quien atribuir.
    pub unresolved_events: i64,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct CompanionRoom {
    pub room_id: String,
    pub room_number: String,
    pub wing_id: String,
    #[serde(default)]
    pub stream_key: Option<String>,
    #[serde(default)]
    pub occupants: Vec<String>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct CompanionRoomsResponse {
    #[serde(default)]
    pub rooms: Vec<CompanionRoom>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct SummaryIngestResponse {
    pub id: String,
    pub resident_id: String,
    pub observed_on: String,
    /// `true` cuando la fuente recalculo un dia ya cargado.
    pub replaced: bool,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct SleepSummary {
    pub observed_on: String,
    pub calm_minutes: i32,
    pub restless_minutes: i32,
    pub awake_minutes: i32,
    pub out_of_bed_minutes: i32,
    pub bed_exit_count: i32,
    pub wake_count: i32,
    pub in_bed_minutes: i32,
    #[serde(default)]
    pub efficiency: Option<f64>,
    pub source: String,
    pub model_version: String,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct MobilitySummary {
    pub observed_on: String,
    pub in_bed_minutes: i32,
    pub out_of_bed_minutes: i32,
    pub out_of_sight_minutes: i32,
    pub walking_minutes: i32,
    #[serde(default)]
    pub distance_meters: Option<f64>,
    pub transfer_count: i32,
    pub source: String,
    pub model_version: String,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct BathroomSummary {
    pub observed_on: String,
    pub visit_count: i32,
    pub night_visit_count: i32,
    pub assisted_count: i32,
    pub total_minutes: i32,
    pub longest_visit_minutes: i32,
    #[serde(default)]
    pub average_visit_minutes: Option<f64>,
    pub source: String,
    pub model_version: String,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct SummariesResponse<T> {
    pub resident_id: String,
    #[serde(default = "Vec::new")]
    pub summaries: Vec<T>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct TimelineResponse {
    pub resident_id: String,
    #[serde(default)]
    pub sleep: Vec<SleepSummary>,
    #[serde(default)]
    pub mobility: Vec<MobilitySummary>,
    #[serde(default)]
    pub bathroom: Vec<BathroomSummary>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct ReportsSummaryResponse {
    pub residents: i64,
    pub beds: i64,
    pub occupied_beds: i64,
    pub observed_beds: i64,
    pub unresolved_events: i64,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct PeekResponse {
    pub room_id: String,
    #[serde(default)]
    pub stream_key: Option<String>,
    pub privacy_regions: usize,
    pub granted_to: String,
    pub granted_at: String,
}

impl ManaClient {
    pub async fn ingest_event(
        &self,
        secret: &str,
        request: IngestEventRequest,
    ) -> Result<ApiResponse<IngestEventResponse>, ManaError> {
        self.ingest_with_secret("/internal/v1/events", secret, request)
            .await
    }

    pub async fn wing_board(&self, wing_id: &str) -> Result<ApiResponse<BoardResponse>, ManaError> {
        let url = path("/api/v1/wings", wing_id)? + "/board";
        self.request(Method::GET, &url).await
    }

    pub async fn companion_rooms(&self) -> Result<ApiResponse<CompanionRoomsResponse>, ManaError> {
        self.request(Method::GET, "/api/v1/companion/rooms").await
    }

    pub async fn peek_room(&self, room_id: &str) -> Result<ApiResponse<PeekResponse>, ManaError> {
        let url = path("/api/v1/rooms", room_id)? + "/peek";
        self.request(Method::POST, &url).await
    }

    pub async fn resident_current_state(
        &self,
        resident_id: &str,
    ) -> Result<ApiResponse<CurrentStateResponse>, ManaError> {
        let url = path("/api/v1/residents", resident_id)? + "/current-state";
        self.request(Method::GET, &url).await
    }

    pub async fn resident_events(
        &self,
        resident_id: &str,
    ) -> Result<ApiResponse<EventsResponse>, ManaError> {
        let url = path("/api/v1/residents", resident_id)? + "/events";
        self.request(Method::GET, &url).await
    }

    pub async fn resident_sleep(
        &self,
        resident_id: &str,
    ) -> Result<ApiResponse<SummariesResponse<SleepSummary>>, ManaError> {
        let url = path("/api/v1/residents", resident_id)? + "/sleep";
        self.request(Method::GET, &url).await
    }

    pub async fn resident_mobility(
        &self,
        resident_id: &str,
    ) -> Result<ApiResponse<SummariesResponse<MobilitySummary>>, ManaError> {
        let url = path("/api/v1/residents", resident_id)? + "/mobility";
        self.request(Method::GET, &url).await
    }

    pub async fn resident_bathroom(
        &self,
        resident_id: &str,
    ) -> Result<ApiResponse<SummariesResponse<BathroomSummary>>, ManaError> {
        let url = path("/api/v1/residents", resident_id)? + "/bathroom";
        self.request(Method::GET, &url).await
    }

    pub async fn resident_timeline(
        &self,
        resident_id: &str,
    ) -> Result<ApiResponse<TimelineResponse>, ManaError> {
        let url = path("/api/v1/residents", resident_id)? + "/timeline";
        self.request(Method::GET, &url).await
    }

    pub async fn reports_summary(&self) -> Result<ApiResponse<ReportsSummaryResponse>, ManaError> {
        self.request(Method::GET, "/api/v1/reports/summary").await
    }

    pub async fn ingest_sleep_summary<B: Serialize>(
        &self,
        secret: &str,
        request: B,
    ) -> Result<ApiResponse<SummaryIngestResponse>, ManaError> {
        self.ingest_with_secret("/internal/v1/clinical/sleep-summaries", secret, request)
            .await
    }

    pub async fn ingest_mobility_summary<B: Serialize>(
        &self,
        secret: &str,
        request: B,
    ) -> Result<ApiResponse<SummaryIngestResponse>, ManaError> {
        self.ingest_with_secret("/internal/v1/clinical/mobility-summaries", secret, request)
            .await
    }

    pub async fn ingest_bathroom_summary<B: Serialize>(
        &self,
        secret: &str,
        request: B,
    ) -> Result<ApiResponse<SummaryIngestResponse>, ManaError> {
        self.ingest_with_secret("/internal/v1/clinical/bathroom-summaries", secret, request)
            .await
    }

    /// Las rutas `/internal/` no usan sesion: autentican al bridge con un
    /// secreto compartido. Es un canal distinto y por eso no pasa por el
    /// `request_json` con bearer.
    async fn ingest_with_secret<T, B>(
        &self,
        path: &str,
        secret: &str,
        body: B,
    ) -> Result<ApiResponse<T>, ManaError>
    where
        T: serde::de::DeserializeOwned,
        B: Serialize,
    {
        self.request_json_with_headers(Method::POST, path, body, &[("x-clinical-secret", secret)])
            .await
    }
}
