//! Lo que informo el detector. Inmutable una vez aceptado.

use mana_kernel::{define_kinds, Id, Instante};

use crate::error::ObservationError;

define_kinds!(SensorEventKind);

pub type SensorEventId = Id<SensorEventKind>;

pub fn new_event_id() -> SensorEventId {
    Id::new(crate::common::random_id("evt"))
}

/// A que cama y residente corresponde un evento.
///
/// El detector manda `monitor_key`; traducirlo a una cama es un cruce que puede
/// fallar. Modelarlo como enum obliga a que cada consumidor trate el caso sin
/// resolver, en vez de encontrarse un `None` y seguir de largo.
#[derive(Clone, Debug, Eq, PartialEq)]
pub enum Resolution {
    Resolved {
        bed_id: String,
        resident_id: Option<String>,
    },
    /// La `monitor_key` no corresponde a ninguna cama activa. El evento se
    /// acepta igual y queda contable.
    Unresolved,
}

impl Resolution {
    pub fn bed_id(&self) -> Option<&str> {
        match self {
            Self::Resolved { bed_id, .. } => Some(bed_id),
            Self::Unresolved => None,
        }
    }

    pub fn resident_id(&self) -> Option<&str> {
        match self {
            Self::Resolved { resident_id, .. } => resident_id.as_deref(),
            Self::Unresolved => None,
        }
    }

    pub fn is_resolved(&self) -> bool {
        matches!(self, Self::Resolved { .. })
    }
}

/// Lo que el bridge entrega. No lleva `received_at`: lo pone el hub
/// (invariante 3), asi que la fuente no puede falsearlo.
#[derive(Clone, Debug)]
pub struct EventInput {
    pub source_event_id: String,
    pub monitor_key: String,
    pub resolution: Resolution,
    pub kind: String,
    pub room_state: Option<String>,
    pub substate: Option<String>,
    pub zone: Option<String>,
    pub state: Option<String>,
    /// `None` es "el detector no informo", nunca "no esta durmiendo".
    pub sleeping: Option<bool>,
    pub occurred_at: Instante,
    pub payload_json: String,
}

impl EventInput {
    pub fn validate(&self) -> Result<(), ObservationError> {
        if self.source_event_id.trim().is_empty() {
            return Err(ObservationError::Validation(
                "source_event_id es obligatorio".to_owned(),
            ));
        }
        if self.monitor_key.trim().is_empty() {
            return Err(ObservationError::Validation(
                "monitor_key es obligatorio".to_owned(),
            ));
        }
        if self.kind.trim().is_empty() {
            return Err(ObservationError::Validation(
                "kind es obligatorio".to_owned(),
            ));
        }
        serde_json::from_str::<serde_json::Value>(&self.payload_json)
            .map_err(|error| ObservationError::Validation(format!("payload_json: {error}")))?;
        Ok(())
    }
}

#[derive(Clone, Debug)]
pub struct SensorEvent {
    pub id: SensorEventId,
    pub source_event_id: String,
    pub monitor_key: String,
    pub resolution: Resolution,
    pub kind: String,
    pub room_state: Option<String>,
    pub substate: Option<String>,
    pub zone: Option<String>,
    pub state: Option<String>,
    pub sleeping: Option<bool>,
    pub occurred_at: Instante,
    pub received_at: Instante,
    pub payload_json: String,
}
