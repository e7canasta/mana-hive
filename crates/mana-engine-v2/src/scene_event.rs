use serde::{Deserialize, Serialize};

use crate::fsm::PersonState;

/// Tipos de evento de escena.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub enum SceneEventType {
    /// Vino de un perception event del edge
    Perception,
    /// Se cumplió un dwell timer
    Dwell,
    /// Hubo un cambio de estado (FSM transition)
    Transition,
    /// Cambio de objeto o room state
    Change,
}

impl SceneEventType {
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::Perception => "perception",
            Self::Dwell => "dwell",
            Self::Transition => "transition",
            Self::Change => "change",
        }
    }
}

/// Información del trigger que causó el evento.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum TriggerInfo {
    /// Perception event del edge
    Perception {
        perception_event_id: String,
        confidence: f64,
    },
    /// Dwell timer completado
    DwellCompleted {
        rule_id: String,
        duration_minutes: i32,
        threshold_minutes: i32,
    },
    /// Transición detectada
    TransitionDetected {
        from_state: PersonState,
        to_state: PersonState,
    },
    /// Cambio de objeto
    ObjectChange {
        object: String,
        from_state: String,
        to_state: String,
    },
}

/// Persona de interés en la escena.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PersonOfInterest {
    pub resident_id: String,
    pub state: PersonState,
    pub state_since: chrono::DateTime<chrono::Utc>,
    pub location: Location,
    pub sleeping: Option<bool>,
    pub confidence: f64,
}

/// Ubicación de la persona.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub enum Location {
    Bed,
    Bathroom,
    Hallway,
    Room,
    Outdoor,
    Chair,
    Wheelchair,
    Unknown,
}

/// Estado de la cama.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct BedState {
    pub occupancy: BedOccupancy,
}

/// Ocupación de la cama.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub enum BedOccupancy {
    Occupied,
    Empty,
    Unknown,
}

/// Estado del sillón.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ChairState {
    pub occupancy: ObjectOccupancy,
}

/// Estado de la silla de ruedas.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct WheelchairState {
    pub occupancy: ObjectOccupancy,
}

/// Ocupación de objetos (sillón, silla de ruedas).
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub enum ObjectOccupancy {
    Occupied,
    Empty,
    Unknown,
}

/// Estado del andador.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct WalkerState {
    pub presence: ObjectPresence,
}

/// Presencia de objetos (andador).
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub enum ObjectPresence {
    Present,
    Absent,
    Unknown,
}

/// Estado de la habitación.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct RoomState {
    pub occupancy: RoomOccupancy,
    pub resident_count: i32,
    pub staff_count: i32,
    pub visitor_count: i32,
}

/// Ocupación de la habitación.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub enum RoomOccupancy {
    Empty,
    Resident,
    Staff,
    ResidentAndStaff,
    ResidentAndVisitor,
}

/// Referencia a personal acompañante.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct StaffRef {
    pub staff_id: String,
    pub name: String,
    pub role: String,
}

/// Evento completo de escena.
///
/// Cada scene event es autocontenido: contiene todo el estado de la escena
/// en el momento del evento.
///
/// Campos agrupados lógicamente (envelope / trigger / estado):
/// - **Envelope**: `event_type`, `bed_id`, `resident_id`, `timestamp`, `trace_id`
/// - **Trigger**: `trigger`
/// - **Estado**: `poi`, `bed`, `chair`, `wheelchair`, `walker`, `room`, `accompanied_by`
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SceneEvent {
    // === Envelope ===
    pub event_type: SceneEventType,
    pub bed_id: String,
    pub resident_id: Option<String>,
    pub timestamp: chrono::DateTime<chrono::Utc>,
    #[serde(default)]
    pub trace_id: Option<String>,

    // === Trigger ===
    pub trigger: TriggerInfo,

    // === Estado de la escena ===
    pub poi: PersonOfInterest,
    pub bed: BedState,
    pub chair: ChairState,
    pub wheelchair: WheelchairState,
    pub walker: WalkerState,
    pub room: RoomState,
    pub accompanied_by: Option<StaffRef>,
}

/// Envelope común a todos los scene events. Extraído para claridad en constructores.
#[derive(Debug, Clone)]
pub struct SceneEventCore {
    pub event_type: SceneEventType,
    pub bed_id: String,
    pub resident_id: Option<String>,
    pub timestamp: chrono::DateTime<chrono::Utc>,
    pub trace_id: Option<String>,
}

impl SceneEvent {
    /// Crea un scene event de percepción.
    pub fn perception(
        bed_id: String,
        resident_id: Option<String>,
        poi: PersonOfInterest,
        perception_event_id: String,
        confidence: f64,
        bed: BedState,
        chair: ChairState,
        wheelchair: WheelchairState,
        walker: WalkerState,
        room: RoomState,
        trace_id: Option<String>,
    ) -> Self {
        Self {
            event_type: SceneEventType::Perception,
            bed_id,
            resident_id,
            timestamp: chrono::Utc::now(),
            trace_id,
            trigger: TriggerInfo::Perception {
                perception_event_id,
                confidence,
            },
            poi,
            bed,
            chair,
            wheelchair,
            walker,
            room,
            accompanied_by: None,
        }
    }

    /// Crea un scene event de transición.
    pub fn transition(
        bed_id: String,
        resident_id: Option<String>,
        poi: PersonOfInterest,
        from_state: PersonState,
        to_state: PersonState,
        bed: BedState,
        chair: ChairState,
        wheelchair: WheelchairState,
        walker: WalkerState,
        room: RoomState,
        trace_id: Option<String>,
    ) -> Self {
        Self {
            event_type: SceneEventType::Transition,
            bed_id,
            resident_id,
            timestamp: chrono::Utc::now(),
            trace_id,
            trigger: TriggerInfo::TransitionDetected {
                from_state,
                to_state,
            },
            poi,
            bed,
            chair,
            wheelchair,
            walker,
            room,
            accompanied_by: None,
        }
    }
}
