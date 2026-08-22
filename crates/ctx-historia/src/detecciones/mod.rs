pub mod repo;
pub mod sqlite;

use mana_kernel::{define_kinds, Id, Instante};
use thiserror::Error;

define_kinds!(DetectionKind);

pub type DetectionId = Id<DetectionKind>;

#[derive(Clone, Debug, Eq, PartialEq, Error)]
pub enum DeteccionesError {
    #[error("source_record_id duplicado")]
    DuplicateSourceRecord,
    #[error("deteccion no encontrada")]
    NotFound,
    #[error("dato persistido invalido: {0}")]
    InvalidStoredData(String),
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum IncidentKind {
    Fall,
    BedExit,
    Wandering,
    Transfer,
    Other,
}

impl IncidentKind {
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::Fall => "fall",
            Self::BedExit => "bed_exit",
            Self::Wandering => "wandering",
            Self::Transfer => "transfer",
            Self::Other => "other",
        }
    }

    pub fn parse(value: &str) -> Result<Self, DeteccionesError> {
        match value {
            "fall" => Ok(Self::Fall),
            "bed_exit" => Ok(Self::BedExit),
            "wandering" => Ok(Self::Wandering),
            "transfer" => Ok(Self::Transfer),
            "other" => Ok(Self::Other),
            other => Err(DeteccionesError::InvalidStoredData(format!(
                "invalid incident kind: {other}"
            ))),
        }
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum Severity {
    Low,
    Medium,
    High,
    Critical,
}

impl Severity {
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::Low => "low",
            Self::Medium => "medium",
            Self::High => "high",
            Self::Critical => "critical",
        }
    }

    pub fn parse(value: &str) -> Result<Self, DeteccionesError> {
        match value {
            "low" => Ok(Self::Low),
            "medium" => Ok(Self::Medium),
            "high" => Ok(Self::High),
            "critical" => Ok(Self::Critical),
            other => Err(DeteccionesError::InvalidStoredData(format!(
                "invalid severity: {other}"
            ))),
        }
    }
}

#[derive(Clone, Debug)]
pub struct IncidentDetection {
    pub id: DetectionId,
    pub source_record_id: String,
    pub resident_id: String,
    pub bed_id: Option<String>,
    pub source_alert_id: Option<String>,
    pub kind: IncidentKind,
    pub severity: Severity,
    pub occurred_at: Instante,
    pub location: Option<String>,
    pub activity: Option<String>,
    pub injury_status: String,
    pub self_recovery: Option<bool>,
    pub response_seconds: Option<i32>,
    pub narrative: Option<String>,
    pub interventions_json: String,
    pub source: String,
    pub model_version: String,
    pub confidence: Option<f64>,
    pub provenance_json: String,
    pub created_at: Instante,
}

#[derive(Clone, Debug)]
pub struct DetectionInput {
    pub source_record_id: String,
    pub resident_id: String,
    pub bed_id: Option<String>,
    pub source_alert_id: Option<String>,
    pub kind: IncidentKind,
    pub severity: Severity,
    pub occurred_at: Instante,
    pub location: Option<String>,
    pub activity: Option<String>,
    pub injury_status: String,
    pub self_recovery: Option<bool>,
    pub response_seconds: Option<i32>,
    pub narrative: Option<String>,
    pub interventions_json: Option<String>,
    pub source: String,
    pub model_version: String,
    pub confidence: Option<f64>,
    pub provenance_json: Option<String>,
}

pub fn new_detection_id() -> DetectionId {
    Id::new(crate::common::random_id("det"))
}
