//! Subdominio de turnos laborales: grilla de turnos por facility.

pub mod repo;
pub mod sqlite;

use mana_kernel::{define_kinds, Actor, Id, Instante};
use thiserror::Error;

define_kinds!(ShiftKind);

pub type ShiftId = Id<ShiftKind>;

#[derive(Clone, Debug, Eq, PartialEq, Error)]
pub enum TurnosError {
    #[error("la clave del turno no puede estar vacia")]
    EmptyKey,
    #[error("la etiqueta del turno no puede estar vacia")]
    EmptyLabel,
    #[error("la clave excede la longitud maxima de {0} caracteres")]
    KeyTooLong(usize),
    #[error("la etiqueta excede la longitud maxima de {0} caracteres")]
    LabelTooLong(usize),
    #[error("start_minute debe estar entre 0 y 1439")]
    InvalidStartMinute,
    #[error("no hay turnos para reemplazar")]
    EmptyGrid,
}

const MAX_KEY: usize = 40;
const MAX_LABEL: usize = 80;

#[derive(Clone, Debug)]
pub struct FacilityShift {
    pub id: ShiftId,
    pub facility_id: String,
    pub key: String,
    pub label: String,
    pub start_minute: i32,
    pub sort_order: i32,
    pub retired_at: Option<Instante>,
    pub retired_by: Option<Id<Actor>>,
    pub created_at: Instante,
    pub updated_at: Instante,
}

#[derive(Clone, Debug)]
pub struct ShiftInput {
    pub key: String,
    pub label: String,
    pub start_minute: i32,
}

#[derive(Clone, Debug)]
pub struct ShiftGrid {
    pub facility_id: String,
    pub shifts: Vec<FacilityShift>,
}

#[derive(Clone, Debug)]
pub struct ReplaceGridResult {
    pub grid: ShiftGrid,
    pub coverages_cleared: i64,
}

impl FacilityShift {
    pub fn create(
        id: ShiftId,
        facility_id: &str,
        input: ShiftInput,
        sort_order: i32,
        now: Instante,
    ) -> Result<Self, TurnosError> {
        let key = validate_key(&input.key)?;
        let label = validate_label(&input.label)?;
        if !(0..1440).contains(&input.start_minute) {
            return Err(TurnosError::InvalidStartMinute);
        }
        Ok(Self {
            id,
            facility_id: facility_id.to_owned(),
            key,
            label,
            start_minute: input.start_minute,
            sort_order,
            retired_at: None,
            retired_by: None,
            created_at: now,
            updated_at: now,
        })
    }
}

pub fn new_shift_id() -> ShiftId {
    Id::new(crate::common::random_id("shift"))
}

fn validate_key(value: &str) -> Result<String, TurnosError> {
    let value = value.trim();
    if value.is_empty() {
        return Err(TurnosError::EmptyKey);
    }
    if value.chars().count() > MAX_KEY {
        return Err(TurnosError::KeyTooLong(MAX_KEY));
    }
    Ok(value.to_owned())
}

fn validate_label(value: &str) -> Result<String, TurnosError> {
    let value = value.trim();
    if value.is_empty() {
        return Err(TurnosError::EmptyLabel);
    }
    if value.chars().count() > MAX_LABEL {
        return Err(TurnosError::LabelTooLong(MAX_LABEL));
    }
    Ok(value.to_owned())
}

#[cfg(test)]
mod tests {
    use super::*;

    fn instant() -> Instante {
        "2026-08-18T12:00:00.000Z".parse().unwrap()
    }

    fn input() -> ShiftInput {
        ShiftInput {
            key: "morning".to_owned(),
            label: "Morning".to_owned(),
            start_minute: 480,
        }
    }

    #[test]
    fn creates_shift_and_validates_fields() {
        let shift =
            FacilityShift::create(new_shift_id(), "facility-1", input(), 1, instant()).unwrap();
        assert_eq!(shift.key, "morning");
        assert_eq!(shift.start_minute, 480);
        assert_eq!(shift.sort_order, 1);

        assert!(matches!(
            FacilityShift::create(
                new_shift_id(),
                "facility-1",
                ShiftInput {
                    key: "".to_owned(),
                    ..input()
                },
                0,
                instant(),
            ),
            Err(TurnosError::EmptyKey)
        ));

        assert!(matches!(
            FacilityShift::create(
                new_shift_id(),
                "facility-1",
                ShiftInput {
                    start_minute: 1440,
                    ..input()
                },
                0,
                instant(),
            ),
            Err(TurnosError::InvalidStartMinute)
        ));
    }
}
