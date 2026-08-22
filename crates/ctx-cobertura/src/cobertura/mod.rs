//! Subdominio de cobertura: asignacion temporal de un staff group a un ala y turno.

pub mod repo;
pub mod sqlite;

use mana_kernel::{define_kinds, Actor, Id, Instante};
use thiserror::Error;

define_kinds!(CoverageKind);

pub type CoverageId = Id<CoverageKind>;

#[derive(Clone, Debug, Eq, PartialEq, Error)]
pub enum CoberturaDomainError {
    #[error("el turno no existe en esta facility")]
    ShiftNotFound,
    #[error("ya existe una cobertura para este ala y turno")]
    AlreadyCovered,
}

#[derive(Clone, Debug)]
pub struct WingCoverage {
    pub id: CoverageId,
    pub wing_id: String,
    pub staff_group_id: Option<String>,
    pub shift_key: String,
    pub valid_from: Instante,
    pub valid_to: Option<Instante>,
    pub created_at: Instante,
    pub created_by: Option<Id<Actor>>,
}

#[derive(Clone, Debug)]
pub struct CoverageInput {
    pub wing_id: String,
    pub staff_group_id: Option<String>,
    pub shift_key: String,
}

#[derive(Clone, Debug)]
pub struct CoverageResult {
    pub coverage: WingCoverage,
    pub closed_previous: Option<WingCoverage>,
}

pub fn new_coverage_id() -> CoverageId {
    Id::new(crate::common::random_id("coverage"))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn coverage_id_has_prefix() {
        let id = new_coverage_id();
        assert!(id.as_str().starts_with("coverage-"));
    }
}
