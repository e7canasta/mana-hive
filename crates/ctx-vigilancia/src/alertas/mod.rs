pub mod repo;
pub mod sqlite;

use mana_kernel::Instante;

use crate::error::VigilanciaError;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum AlertasError {
    InvalidTransition,
    MissingActor,
    InvalidStatus,
    InvalidEvidenceKind,
    InvalidLevel,
}

impl std::fmt::Display for AlertasError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::InvalidTransition => write!(f, "transicion de estado invalida"),
            Self::MissingActor => write!(f, "actor requerido para este estado"),
            Self::InvalidStatus => write!(f, "estado de alerta invalido"),
            Self::InvalidEvidenceKind => write!(f, "tipo de evidencia invalido"),
            Self::InvalidLevel => write!(f, "nivel de severidad invalido"),
        }
    }
}

impl From<AlertasError> for VigilanciaError {
    fn from(error: AlertasError) -> Self {
        VigilanciaError::validation(error.to_string())
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum AlertStatus {
    Open,
    Acknowledged,
    Attending,
    Resolved,
}

impl AlertStatus {
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::Open => "open",
            Self::Acknowledged => "acknowledged",
            Self::Attending => "attending",
            Self::Resolved => "resolved",
        }
    }

    pub fn parse(value: &str) -> Result<Self, AlertasError> {
        match value {
            "open" => Ok(Self::Open),
            "acknowledged" => Ok(Self::Acknowledged),
            "attending" => Ok(Self::Attending),
            "resolved" => Ok(Self::Resolved),
            _ => Err(AlertasError::InvalidStatus),
        }
    }

    pub fn can_transition_to(&self, target: &AlertStatus) -> bool {
        matches!(
            (self, target),
            (Self::Open, Self::Acknowledged)
                | (Self::Acknowledged, Self::Attending)
                | (Self::Attending, Self::Resolved)
        )
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum EvidenceKind {
    SensorEvent,
    DwellWindow,
    Manual,
}

impl EvidenceKind {
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::SensorEvent => "sensor_event",
            Self::DwellWindow => "dwell_window",
            Self::Manual => "manual",
        }
    }

    pub fn parse(value: &str) -> Result<Self, AlertasError> {
        match value {
            "sensor_event" => Ok(Self::SensorEvent),
            "dwell_window" => Ok(Self::DwellWindow),
            "manual" => Ok(Self::Manual),
            _ => Err(AlertasError::InvalidEvidenceKind),
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum AlertLevel {
    Low,
    Medium,
    High,
    Critical,
}

impl AlertLevel {
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::Low => "low",
            Self::Medium => "medium",
            Self::High => "high",
            Self::Critical => "critical",
        }
    }

    pub fn parse(value: &str) -> Result<Self, AlertasError> {
        match value {
            "low" => Ok(Self::Low),
            "medium" => Ok(Self::Medium),
            "high" => Ok(Self::High),
            "critical" => Ok(Self::Critical),
            _ => Err(AlertasError::InvalidLevel),
        }
    }
}

#[derive(Debug, Clone)]
pub struct AlertInput {
    pub resident_id: Option<String>,
    pub bed_id: String,
    pub evidence_kind: EvidenceKind,
    pub evidence_ref: Option<String>,
    pub rule_id: String,
    pub level: AlertLevel,
    pub title: String,
    pub detail: Option<String>,
    pub occurred_at: Instante,
}

#[derive(Debug, Clone)]
pub struct Alert {
    pub id: String,
    pub resident_id: Option<String>,
    pub bed_id: String,
    pub evidence_kind: EvidenceKind,
    pub evidence_ref: Option<String>,
    pub rule_id: String,
    pub level: AlertLevel,
    pub status: AlertStatus,
    pub status_actor_id: Option<String>,
    pub status_at: Option<Instante>,
    pub title: String,
    pub detail: Option<String>,
    pub occurred_at: Instante,
    pub escalation_level: i32,
    pub escalated_at: Option<Instante>,
    pub escalated_to: Option<String>,
    pub created_at: Instante,
    pub updated_at: Instante,
}

#[derive(Debug, Clone)]
pub struct TransitionInput {
    pub from_status: Option<AlertStatus>,
    pub to_status: AlertStatus,
    pub actor_id: Option<String>,
    pub occurred_at: Instante,
}

#[derive(Debug, Clone)]
pub struct AlertTransition {
    pub id: String,
    pub alert_id: String,
    pub from_status: Option<AlertStatus>,
    pub to_status: AlertStatus,
    pub actor_id: Option<String>,
    pub occurred_at: Instante,
    pub sequence: i32,
}

#[derive(Debug, Clone)]
pub struct EscalationInput {
    pub level: i32,
    pub target_id: String,
    pub occurred_at: Instante,
}

pub fn new_alert_id() -> String {
    use base64::Engine;
    let bytes: [u8; 16] = rand::random();
    base64::engine::general_purpose::URL_SAFE_NO_PAD.encode(bytes)
}
