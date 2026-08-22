use serde::{Deserialize, Serialize};

/// Estado de la ventana
#[derive(Clone, Debug, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "snake_case")]
pub enum WindowState {
    Open,
    Closed,
    Expired,
}

/// Condición de cierre
#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(tag = "type")]
pub enum CloseCondition {
    #[serde(rename = "event_count")]
    EventCount { count: i32 },
    #[serde(rename = "specific_event")]
    SpecificEvent { event_type: String },
    #[serde(rename = "room_state_change")]
    RoomStateChange,
    #[serde(rename = "timeout")]
    Timeout { minutes: i32 },
    #[serde(rename = "sequence_pattern")]
    SequencePattern { before: usize, after: usize },
}

/// Evento categorizado en la ventana
#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct CategorizedEvent {
    pub event_id: String,
    pub event_type: String,
    pub timestamp: String,
    pub category: String,
    pub payload_json: String,
}

/// Clip Window: ventana de detección de patrones
#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct ClipWindow {
    pub window_id: String,
    pub bed_id: String,
    pub resident_id: Option<String>,
    pub started_at: String,
    pub ended_at: Option<String>,
    pub timeout_minutes: i32,
    pub events: Vec<CategorizedEvent>,
    pub state: WindowState,
    pub close_condition: CloseCondition,
    pub created_at: String,
    pub closed_at: Option<String>,
}

/// Input para crear clip window
#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct ClipWindowInput {
    pub bed_id: String,
    pub resident_id: Option<String>,
    pub started_at: String,
    pub timeout_minutes: i32,
    pub close_condition: CloseCondition,
}

/// Input para cerrar clip window
#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct ClipWindowCloseInput {
    pub ended_at: String,
    pub events: Vec<CategorizedEvent>,
    pub state: WindowState,
}

/// Filtros para buscar clip windows
#[derive(Clone, Debug, Default)]
pub struct ClipWindowFilter {
    pub bed_id: Option<String>,
    pub resident_id: Option<String>,
    pub state: Option<WindowState>,
    pub since: Option<String>,
    pub until: Option<String>,
    pub limit: Option<i64>,
}
