//! Subdominio de planograma: la disposicion espacial de las habitaciones
//! sobre el plano de un ala.
//!
//! Agregado `WingPlanogram` (raiz: `WingId`): a lo sumo un placement activo
//! por habitacion y el guardado reemplaza la version activa por completo.

use thiserror::Error;

use crate::common::random_id;
use crate::estructura::{RoomId, WingId};

pub mod repo;
pub mod sqlite;

#[derive(Clone, Debug, Eq, PartialEq, Error)]
pub enum PlanogramaError {
    #[error("coordenadas del planograma invalidas")]
    InvalidPlanogramCoordinate,
    #[error("sort_order no puede ser negativo")]
    NegativeSortOrder,
}

#[derive(Clone, Debug)]
pub struct PlanogramPlacementInput {
    pub room_id: RoomId,
    pub x: f64,
    pub y: f64,
    pub sort_order: i32,
}

impl PlanogramPlacementInput {
    pub fn validate(&self) -> Result<(), PlanogramaError> {
        if !self.x.is_finite() || !self.y.is_finite() {
            return Err(PlanogramaError::InvalidPlanogramCoordinate);
        }
        if self.sort_order < 0 {
            return Err(PlanogramaError::NegativeSortOrder);
        }
        Ok(())
    }
}

#[derive(Clone, Debug)]
pub struct PlanogramEntry {
    pub id: String,
    pub wing_id: WingId,
    pub room_id: RoomId,
    pub x: f64,
    pub y: f64,
    pub sort_order: i32,
    pub room_number: String,
    pub room_type: String,
    pub stream_key: Option<String>,
}

pub fn new_planogram_id() -> String {
    random_id("placement")
}
