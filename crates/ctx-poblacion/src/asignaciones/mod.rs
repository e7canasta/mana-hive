//! Subdominio de asignaciones residente-cama.
//!
//! `BedAssignment` es un agregado de asociacion con su propia frontera de
//! consistencia: la unicidad abarca todos los residentes y camas, por eso es
//! separado de `Resident`. Guarda solo IDs e intervalo; el read model con
//! habitacion y ala lo compone `mana-app`.

pub mod repo;
pub mod sqlite;

use mana_kernel::{define_kinds, Actor, Id, Instante};
use thiserror::Error;

use crate::residentes::ResidentId;

define_kinds!(AssignmentKind);

pub type AssignmentId = Id<AssignmentKind>;

const MAX_BED_REF: usize = 160;

#[derive(Clone, Debug, Eq, PartialEq, Error)]
pub enum AsignacionesError {
    #[error("bed_id no puede estar vacio")]
    EmptyBedRef,
    #[error("bed_id excede la longitud maxima de {max} caracteres")]
    BedRefTooLong { max: usize },
    #[error("la asignacion ya esta cerrada")]
    AlreadyClosed,
    #[error("el intervalo nuevo se solapa con el historial del {side}")]
    OverlappingInterval { side: &'static str },
    #[error("la cama no tiene una asignacion abierta para liberar")]
    FreeBed,
}

/// Referencia opaca a una cama de Residencia. No cruza contextos: se valida
/// contra Residencia desde `mana-app` y aca solo se almacena.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct BedRef(String);

impl BedRef {
    pub fn new(value: impl Into<String>) -> Result<Self, AsignacionesError> {
        let value = value.into().trim().to_owned();
        if value.is_empty() {
            return Err(AsignacionesError::EmptyBedRef);
        }
        if value.chars().count() > MAX_BED_REF {
            return Err(AsignacionesError::BedRefTooLong { max: MAX_BED_REF });
        }
        Ok(Self(value))
    }

    pub fn as_str(&self) -> &str {
        &self.0
    }
}

#[derive(Clone, Debug)]
pub struct BedAssignment {
    pub id: AssignmentId,
    pub resident_id: ResidentId,
    pub bed_id: BedRef,
    pub starts_at: Instante,
    pub ends_at: Option<Instante>,
    pub created_at: Instante,
    pub created_by: Option<Id<Actor>>,
}

impl BedAssignment {
    pub fn assign(
        id: AssignmentId,
        resident_id: ResidentId,
        bed_id: BedRef,
        starts_at: Instante,
        created_by: Option<Id<Actor>>,
    ) -> Self {
        Self {
            id,
            resident_id,
            bed_id,
            starts_at,
            ends_at: None,
            created_at: starts_at,
            created_by,
        }
    }

    /// Cierra la asignacion; la cama queda libre (invariante 5: no toca el
    /// residente).
    pub fn close(&mut self, ends_at: Instante) -> Result<(), AsignacionesError> {
        if self.ends_at.is_some() {
            return Err(AsignacionesError::AlreadyClosed);
        }
        self.ends_at = Some(ends_at);
        Ok(())
    }

    pub fn is_open(&self) -> bool {
        self.ends_at.is_none()
    }
}

#[derive(Clone, Debug)]
pub struct AssignResult {
    pub created: BedAssignment,
    pub resident_closed: Option<BedAssignment>,
    pub bed_closed: Option<BedAssignment>,
}

pub fn new_assignment_id() -> AssignmentId {
    Id::new(crate::common::random_id("assignment"))
}

#[cfg(test)]
mod tests {
    use super::*;

    fn instant() -> Instante {
        "2026-08-18T12:00:00.000Z".parse().unwrap()
    }

    #[test]
    fn bed_ref_rejects_empty_values() {
        assert!(matches!(
            BedRef::new("  "),
            Err(AsignacionesError::EmptyBedRef)
        ));
        assert_eq!(BedRef::new("bed-1").unwrap().as_str(), "bed-1");
    }

    #[test]
    fn close_is_not_idempotent() {
        let mut assignment = BedAssignment::assign(
            AssignmentId::new("assignment-1"),
            ResidentId::new("resident-1"),
            BedRef::new("bed-1").unwrap(),
            instant(),
            None,
        );
        assert!(assignment.is_open());
        assignment.close(instant()).unwrap();
        assert!(!assignment.is_open());
        assert!(matches!(
            assignment.close(instant()),
            Err(AsignacionesError::AlreadyClosed)
        ));
    }
}
