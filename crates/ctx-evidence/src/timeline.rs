use serde::{Deserialize, Serialize};

/// Evento en una timeline
#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct TimelineEvent {
    pub event_id: String,
    pub event_type: String,
    pub timestamp: String,
    pub event_json: String,
}

/// Timeline: evento central con contexto temporal
#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct Timeline {
    pub id: String,
    pub bed_id: String,
    pub resident_id: Option<String>,
    pub anchor_event_id: String,
    pub anchor_event_json: String,
    pub before_events: Vec<TimelineEvent>,
    pub after_events: Vec<TimelineEvent>,
    pub window_start: String,
    pub window_end: String,
    pub created_at: String,
    pub closed_at: Option<String>,
}

/// Input para crear timeline
#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct TimelineInput {
    pub bed_id: String,
    pub resident_id: Option<String>,
    pub anchor_event_id: String,
    pub anchor_event_json: String,
    pub before_events: Vec<TimelineEvent>,
    pub window_start: String,
    pub window_end: String,
}

/// Input para cerrar timeline
#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct TimelineCloseInput {
    pub after_events: Vec<TimelineEvent>,
}

/// Filtros para buscar timelines
#[derive(Clone, Debug, Default)]
pub struct TimelineFilter {
    pub bed_id: Option<String>,
    pub resident_id: Option<String>,
    pub since: Option<String>,
    pub until: Option<String>,
    pub limit: Option<i64>,
}
