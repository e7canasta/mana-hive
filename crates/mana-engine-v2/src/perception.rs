use serde::{Deserialize, Serialize};

/// Evento de percepción que viene del edge (IA Server).
///
/// Contiene el estado detectado por los sensores/cámaras.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PerceptionEvent {
    /// ID del evento de percepción
    pub event_id: String,
    
    /// Trace ID para correlación cross-pipeline
    #[serde(default)]
    pub trace_id: Option<String>,
    
    /// ID del monitor (resuelto a bed_id por el hub)
    pub monitor_key: String,
    
    /// ID de la cama (resuelto por el hub)
    pub bed_id: Option<String>,
    
    /// ID del residente (resuelto por el hub)
    pub resident_id: Option<String>,
    
    /// Estado detectado (lying, sitting, standing, etc.)
    pub state: Option<String>,
    
    /// ¿Está durmiendo?
    pub sleeping: Option<bool>,
    
    /// Zona detectada
    pub zone: Option<String>,
    
    /// Extremidades fuera de la cama
    pub extremities_out_of_bed: Option<bool>,
    
    /// Partes del cuerpo fuera de la cama
    pub body_parts_out: Option<Vec<String>>,
    
    /// Objetos detectados
    pub objects: Option<serde_json::Value>,
    
    /// Habitación detectada
    pub room: Option<String>,
    
    /// Confianza de la detección (0.0 - 1.0)
    pub confidence: f64,
    
    /// Timestamp del evento
    pub occurred_at: chrono::DateTime<chrono::Utc>,
}

impl PerceptionEvent {
    /// Mapea el estado del perception event a PersonState.
    pub fn map_state_to_person_state(&self) -> crate::fsm::PersonState {
        use crate::fsm::PersonState;
        
        match self.state.as_deref() {
            Some("lying") => PersonState::Lying,
            Some("sitting") | Some("sitting_in_bed") => PersonState::SittingInBed,
            Some("bed_edge") => PersonState::BedEdge,
            Some("standing") => PersonState::Standing,
            Some("in_bathroom") => PersonState::InBathroom,
            Some("in_room") => PersonState::InRoom,
            Some("in_hallway") => PersonState::InHallway,
            Some("outdoor") => PersonState::Outdoor,
            Some("in_chair") => PersonState::InChair,
            Some("in_wheelchair") => PersonState::InWheelchair,
            _ => PersonState::Unknown,
        }
    }

    /// Mapea la zona a Location.
    pub fn map_zone_to_location(&self) -> crate::scene_event::Location {
        use crate::scene_event::Location;
        
        match self.zone.as_deref() {
            Some("bed") => Location::Bed,
            Some("bathroom") => Location::Bathroom,
            Some("hallway") => Location::Hallway,
            Some("room") => Location::Room,
            Some("outdoor") => Location::Outdoor,
            Some("chair") => Location::Chair,
            Some("wheelchair") => Location::Wheelchair,
            _ => Location::Unknown,
        }
    }
}
