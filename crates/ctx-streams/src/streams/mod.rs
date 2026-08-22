//! Subdominio de streams: cámaras y regiones de interés por stream.
//!
//! Cada cámara se vincula a una room y tiene regiones poligonales que
//! definen qué parte del frame corresponde a qué objeto/área.
//!
//! Las regiones estáticas (bathroom, hallway, exit, bed, furniture) se
//! configuran una vez. Las dinámicas (person, object) las actualiza el
//! ia-server en tiempo real.

use std::fmt;

use mana_kernel::{define_kinds, Id};
use serde::{Deserialize, Serialize};
use thiserror::Error;

use crate::common::random_id;

define_kinds!(StreamKind, StreamRegionKind);

pub type StreamId = Id<StreamKind>;
pub type StreamRegionId = Id<StreamRegionKind>;

pub mod repo;
pub mod sqlite;

#[derive(Clone, Debug, Eq, PartialEq, Error)]
pub enum StreamsError {
    #[error("region_type invalido: {0}")]
    InvalidRegionType(String),
    #[error("points no es un polígono válido: {0}")]
    InvalidPoints(String),
    #[error("no hay campos para actualizar")]
    EmptyUpdate,
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum StreamRegionType {
    Bathroom,
    Hallway,
    Exit,
    Bed,
    Furniture,
    Person,
    Object,
}

impl StreamRegionType {
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::Bathroom => "bathroom",
            Self::Hallway => "hallway",
            Self::Exit => "exit",
            Self::Bed => "bed",
            Self::Furniture => "furniture",
            Self::Person => "person",
            Self::Object => "object",
        }
    }

    pub fn is_static(&self) -> bool {
        matches!(
            self,
            Self::Bathroom | Self::Hallway | Self::Exit | Self::Bed | Self::Furniture
        )
    }

    pub fn parse(s: &str) -> Result<Self, StreamsError> {
        match s {
            "bathroom" => Ok(Self::Bathroom),
            "hallway" => Ok(Self::Hallway),
            "exit" => Ok(Self::Exit),
            "bed" => Ok(Self::Bed),
            "furniture" => Ok(Self::Furniture),
            "person" => Ok(Self::Person),
            "object" => Ok(Self::Object),
            other => Err(StreamsError::InvalidRegionType(other.to_owned())),
        }
    }
}

impl fmt::Display for StreamRegionType {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(self.as_str())
    }
}

pub type Points = Vec<(f64, f64)>;

#[derive(Clone, Debug)]
pub struct StreamInput {
    pub room_id: String,
    pub stream_key: String,
    pub name: Option<String>,
}

#[derive(Clone, Debug)]
pub struct Stream {
    pub id: StreamId,
    pub room_id: String,
    pub stream_key: String,
    pub name: Option<String>,
}

#[derive(Clone, Debug)]
pub struct StreamRegionInput {
    pub region_type: StreamRegionType,
    pub points: Points,
    pub label: Option<String>,
}

#[derive(Clone, Debug)]
pub struct StreamRegion {
    pub id: StreamRegionId,
    pub stream_id: StreamId,
    pub region_type: StreamRegionType,
    pub points: Points,
    pub label: Option<String>,
    pub is_static: bool,
    pub updated_by: Option<String>,
}

pub fn new_stream_id() -> String {
    random_id("stream")
}

pub fn new_stream_region_id() -> String {
    random_id("region")
}

pub fn validate_points(points: &[(f64, f64)]) -> Result<(), StreamsError> {
    if points.len() < 3 {
        return Err(StreamsError::InvalidPoints(format!(
            "necesita al menos 3 puntos, tiene {}",
            points.len()
        )));
    }
    for (i, &(x, y)) in points.iter().enumerate() {
        if !x.is_finite() || !y.is_finite() {
            return Err(StreamsError::InvalidPoints(format!(
                "punto {i} no es finito: ({x}, {y})"
            )));
        }
        if !(0.0..=1.0).contains(&x) || !(0.0..=1.0).contains(&y) {
            return Err(StreamsError::InvalidPoints(format!(
                "punto {i} fuera de rango [0,1]: ({x}, {y})"
            )));
        }
    }
    Ok(())
}
