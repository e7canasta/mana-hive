//! Subdominio de privacidad: las regiones que enmascaran la transmision de
//! video de una habitacion.
//!
//! Agregado `RoomPrivacyConfig` (raiz: `RoomId`): hasta `MAX_PRIVACY_REGIONS`
//! regiones normalizadas dentro de 0..1 y guardado por reemplazo de la
//! version activa.

use mana_kernel::Instante;
use thiserror::Error;

use crate::common::random_id;
use crate::estructura::RoomId;

pub mod repo;
pub mod sqlite;

pub const MAX_PRIVACY_REGIONS: usize = 8;

#[derive(Clone, Debug, Eq, PartialEq, Error)]
pub enum PrivacidadError {
    #[error("region de privacidad invalida: debe estar normalizada dentro de 0..1")]
    InvalidPrivacyRegion,
    #[error("hay mas de {max} regiones de privacidad")]
    TooManyPrivacyRegions { max: usize },
}

#[derive(Clone, Debug)]
pub struct PrivacyRegionInput {
    pub x: f64,
    pub y: f64,
    pub w: f64,
    pub h: f64,
}

impl PrivacyRegionInput {
    pub fn validate(&self) -> Result<(), PrivacidadError> {
        let values = [self.x, self.y, self.w, self.h];
        if !values.iter().all(|value| value.is_finite()) {
            return Err(PrivacidadError::InvalidPrivacyRegion);
        }
        if self.x < 0.0
            || self.y < 0.0
            || self.w <= 0.0
            || self.h <= 0.0
            || self.x + self.w > 1.0
            || self.y + self.h > 1.0
        {
            return Err(PrivacidadError::InvalidPrivacyRegion);
        }
        Ok(())
    }
}

#[derive(Clone, Debug)]
pub struct PrivacyRegion {
    pub id: String,
    pub room_id: RoomId,
    pub x: f64,
    pub y: f64,
    pub w: f64,
    pub h: f64,
    pub created_at: Instante,
    pub updated_at: Instante,
}

impl PrivacyRegion {
    pub fn create(
        id: String,
        room_id: RoomId,
        input: PrivacyRegionInput,
        now: Instante,
    ) -> Result<Self, PrivacidadError> {
        input.validate()?;
        Ok(Self {
            id,
            room_id,
            x: input.x,
            y: input.y,
            w: input.w,
            h: input.h,
            created_at: now,
            updated_at: now,
        })
    }
}

pub fn new_privacy_region_id() -> String {
    random_id("privacy")
}
