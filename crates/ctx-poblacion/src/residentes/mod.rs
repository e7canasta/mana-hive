//! Subdominio de padron: residentes y su ciclo de vida de admision.
//!
//! El `status` es un hecho del ciclo clinico (activo o egresado), no el flag
//! generico de retiro. El egreso cierra la asignacion abierta en la misma
//! transaccion (invariante 6) pero es una accion de negocio separada de la
//! liberacion de camas (invariante 5).

pub mod repo;
pub mod sqlite;

use chrono::NaiveDate;
use mana_kernel::{define_kinds, Actor, Id, Instante};
use thiserror::Error;

use crate::asignaciones::BedAssignment;

define_kinds!(ResidentKind);

pub type ResidentId = Id<ResidentKind>;

const MAX_FULL_NAME: usize = 120;
const MAX_EXTERNAL_ID: usize = 120;

#[derive(Clone, Debug, Eq, PartialEq, Error)]
pub enum ResidentesError {
    #[error("{field} no puede estar vacio")]
    EmptyField { field: &'static str },
    #[error("{field} excede la longitud maxima de {max} caracteres")]
    FieldTooLong { field: &'static str, max: usize },
    #[error("{field} no es una fecha valida (YYYY-MM-DD)")]
    InvalidDate { field: &'static str },
    #[error("el estado almacenado no es valido: {status}")]
    InvalidStatus { status: String },
    #[error("no hay campos para actualizar")]
    EmptyUpdate,
    #[error("el residente ya esta egresado")]
    AlreadyDischarged,
    #[error("la fecha de egreso no puede preceder a la de ingreso")]
    DischargeBeforeAdmission,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum ResidentStatus {
    Active,
    Discharged,
}

impl ResidentStatus {
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::Active => "active",
            Self::Discharged => "discharged",
        }
    }

    pub fn parse(value: &str) -> Result<Self, ResidentesError> {
        match value {
            "active" => Ok(Self::Active),
            "discharged" => Ok(Self::Discharged),
            other => Err(ResidentesError::InvalidStatus {
                status: other.to_owned(),
            }),
        }
    }
}

#[derive(Clone, Debug)]
pub struct ResidentInput {
    pub full_name: String,
    pub external_id: Option<String>,
    pub birth_date: Option<String>,
    pub admission_date: Option<String>,
}

#[derive(Clone, Debug, Default)]
pub struct ResidentUpdate {
    pub full_name: Option<String>,
    pub external_id: Option<Option<String>>,
    pub birth_date: Option<Option<String>>,
    pub admission_date: Option<Option<String>>,
}

#[derive(Clone, Debug)]
pub struct Resident {
    pub id: ResidentId,
    pub external_id: Option<String>,
    pub full_name: String,
    pub birth_date: Option<NaiveDate>,
    pub admission_date: Option<NaiveDate>,
    pub status: ResidentStatus,
    pub discharged_at: Option<NaiveDate>,
    pub discharged_by: Option<Id<Actor>>,
    pub created_at: Instante,
    pub updated_at: Instante,
}

#[derive(Clone, Debug)]
pub struct DischargeResult {
    pub resident: Resident,
    pub closed_assignment: Option<BedAssignment>,
}

impl Resident {
    pub fn create(
        id: ResidentId,
        input: ResidentInput,
        now: Instante,
    ) -> Result<Self, ResidentesError> {
        let full_name = text(&input.full_name, "full_name", MAX_FULL_NAME)?;
        let external_id = optional_text(&input.external_id, "external_id", MAX_EXTERNAL_ID)?;
        let birth_date = optional_date(&input.birth_date, "birth_date")?;
        let admission_date = optional_date(&input.admission_date, "admission_date")?;
        Ok(Self {
            id,
            external_id,
            full_name,
            birth_date,
            admission_date,
            status: ResidentStatus::Active,
            discharged_at: None,
            discharged_by: None,
            created_at: now,
            updated_at: now,
        })
    }

    pub fn apply_update(
        &mut self,
        input: ResidentUpdate,
        now: Instante,
    ) -> Result<(), ResidentesError> {
        if input.full_name.is_none()
            && input.external_id.is_none()
            && input.birth_date.is_none()
            && input.admission_date.is_none()
        {
            return Err(ResidentesError::EmptyUpdate);
        }
        if let Some(full_name) = input.full_name {
            self.full_name = text(&full_name, "full_name", MAX_FULL_NAME)?;
        }
        if let Some(external_id) = input.external_id {
            self.external_id = optional_text(&external_id, "external_id", MAX_EXTERNAL_ID)?;
        }
        if let Some(birth_date) = input.birth_date {
            self.birth_date = optional_date(&birth_date, "birth_date")?;
        }
        if let Some(admission_date) = input.admission_date {
            self.admission_date = optional_date(&admission_date, "admission_date")?;
        }
        self.updated_at = now;
        Ok(())
    }

    /// Egreso: cierra el ciclo activo y valida que la fecha de egreso no
    /// preceda a la de ingreso (invariante 7).
    pub fn discharge(
        &mut self,
        date: NaiveDate,
        by: Id<Actor>,
        now: Instante,
    ) -> Result<(), ResidentesError> {
        if self.status != ResidentStatus::Active {
            return Err(ResidentesError::AlreadyDischarged);
        }
        if let Some(admission_date) = self.admission_date {
            if date < admission_date {
                return Err(ResidentesError::DischargeBeforeAdmission);
            }
        }
        self.status = ResidentStatus::Discharged;
        self.discharged_at = Some(date);
        self.discharged_by = Some(by);
        self.updated_at = now;
        Ok(())
    }
}

pub fn new_resident_id() -> ResidentId {
    Id::new(crate::common::random_id("resident"))
}

fn text(value: &str, field: &'static str, max: usize) -> Result<String, ResidentesError> {
    let value = value.trim();
    if value.is_empty() {
        return Err(ResidentesError::EmptyField { field });
    }
    if value.chars().count() > max {
        return Err(ResidentesError::FieldTooLong { field, max });
    }
    Ok(value.to_owned())
}

fn optional_text(
    value: &Option<String>,
    field: &'static str,
    max: usize,
) -> Result<Option<String>, ResidentesError> {
    value
        .as_ref()
        .map(|value| text(value, field, max))
        .transpose()
}

fn optional_date(
    value: &Option<String>,
    field: &'static str,
) -> Result<Option<NaiveDate>, ResidentesError> {
    value
        .as_ref()
        .map(|value| parse_date(value, field))
        .transpose()
}

pub(crate) fn parse_date(value: &str, field: &'static str) -> Result<NaiveDate, ResidentesError> {
    NaiveDate::parse_from_str(value.trim(), "%Y-%m-%d")
        .map_err(|_| ResidentesError::InvalidDate { field })
}

#[cfg(test)]
mod tests {
    use super::*;

    fn instant() -> Instante {
        "2026-08-18T12:00:00.000Z".parse().unwrap()
    }

    fn input() -> ResidentInput {
        ResidentInput {
            full_name: "Carmen Ruiz".to_owned(),
            external_id: Some("ext-1".to_owned()),
            birth_date: Some("1948-03-02".to_owned()),
            admission_date: Some("2026-01-15".to_owned()),
        }
    }

    #[test]
    fn creates_an_active_resident_and_validates_fields() {
        let resident = Resident::create(ResidentId::new("resident-1"), input(), instant()).unwrap();
        assert_eq!(resident.status, ResidentStatus::Active);
        assert_eq!(resident.full_name, "Carmen Ruiz");
        assert!(resident.discharged_at.is_none());

        assert!(matches!(
            Resident::create(
                ResidentId::new("resident-2"),
                ResidentInput {
                    full_name: "  ".to_owned(),
                    ..input()
                },
                instant(),
            ),
            Err(ResidentesError::EmptyField { field: "full_name" })
        ));
        assert!(matches!(
            Resident::create(
                ResidentId::new("resident-3"),
                ResidentInput {
                    birth_date: Some("03/02/1948".to_owned()),
                    ..input()
                },
                instant(),
            ),
            Err(ResidentesError::InvalidDate {
                field: "birth_date"
            })
        ));
    }

    #[test]
    fn rejects_empty_updates_and_invalid_dates() {
        let mut resident =
            Resident::create(ResidentId::new("resident-1"), input(), instant()).unwrap();
        assert!(matches!(
            resident.apply_update(ResidentUpdate::default(), instant()),
            Err(ResidentesError::EmptyUpdate)
        ));
        assert!(matches!(
            resident.apply_update(
                ResidentUpdate {
                    admission_date: Some(Some("2026/01/15".to_owned())),
                    ..Default::default()
                },
                instant(),
            ),
            Err(ResidentesError::InvalidDate {
                field: "admission_date"
            })
        ));
    }

    #[test]
    fn discharge_validates_dates_and_state() {
        let mut resident =
            Resident::create(ResidentId::new("resident-1"), input(), instant()).unwrap();
        assert!(matches!(
            resident.discharge(
                NaiveDate::from_ymd_opt(2025, 12, 31).unwrap(),
                Id::new("actor-1"),
                instant()
            ),
            Err(ResidentesError::DischargeBeforeAdmission)
        ));

        let date = NaiveDate::from_ymd_opt(2026, 8, 1).unwrap();
        resident
            .discharge(date, Id::new("actor-1"), instant())
            .unwrap();
        assert_eq!(resident.status, ResidentStatus::Discharged);
        assert_eq!(resident.discharged_at, Some(date));
        assert!(matches!(
            resident.discharge(date, Id::new("actor-2"), instant()),
            Err(ResidentesError::AlreadyDischarged)
        ));
    }
}
