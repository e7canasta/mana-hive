use serde::{Deserialize, Serialize};

/// Tipo de evidencia
#[derive(Clone, Debug, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "snake_case")]
pub enum EvidenceType {
    Transition,
    Dwell,
    ObjectChange,
    RoomChange,
    Custom,
}

/// Categoría del evento (de sentinel)
#[derive(Clone, Debug, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "snake_case")]
pub enum EventCategory {
    Off,
    Notify,
    Alarm,
    Mark,
}

/// Evidencia: evento significativo destacado
#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct Evidence {
    pub id: String,
    pub bed_id: String,
    pub resident_id: Option<String>,
    pub evidence_type: EvidenceType,
    pub category: EventCategory,
    pub scene_event_id: String,
    pub scene_event_json: String,
    pub rule_id: Option<String>,
    pub shift: Option<String>,
    pub risk_level: Option<String>,
    pub timestamp: String,
    pub created_at: String,
}

/// Input para crear evidence
#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct EvidenceInput {
    pub bed_id: String,
    pub resident_id: Option<String>,
    pub evidence_type: EvidenceType,
    pub category: EventCategory,
    pub scene_event_id: String,
    pub scene_event_json: String,
    pub rule_id: Option<String>,
    pub shift: Option<String>,
    pub risk_level: Option<String>,
    pub timestamp: String,
}

/// Filtros para buscar evidence
#[derive(Clone, Debug, Default)]
pub struct EvidenceFilter {
    pub bed_id: Option<String>,
    pub resident_id: Option<String>,
    pub category: Option<EventCategory>,
    pub since: Option<String>,
    pub until: Option<String>,
    pub limit: Option<i64>,
}
