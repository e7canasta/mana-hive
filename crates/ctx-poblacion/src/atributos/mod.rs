//! Subdominio de atributos del residente: afirmaciones fechadas con
//! provenance. No son un diagnostico ni un array libre de traits; el
//! vocabulario de `code` se valida en el limite (Politica/catalogo).

pub mod repo;
pub mod sqlite;

use chrono::NaiveDate;
use mana_kernel::{define_kinds, Actor, Id, Instante};
use thiserror::Error;

use crate::residentes::ResidentId;

define_kinds!(AttributeKind);

pub type AttributeId = Id<AttributeKind>;

const MAX_VALUE: usize = 200;
const MAX_SOURCE: usize = 120;
const MAX_SOURCE_REF: usize = 200;

#[derive(Clone, Debug, Eq, PartialEq, Error)]
pub enum AtributosError {
    #[error("codigo de atributo desconocido: {code}")]
    UnknownCode { code: String },
    #[error("{field} no puede estar vacio")]
    EmptyField { field: &'static str },
    #[error("{field} excede la longitud maxima de {max} caracteres")]
    FieldTooLong { field: &'static str, max: usize },
    #[error("{field} no es una fecha valida (YYYY-MM-DD)")]
    InvalidDate { field: &'static str },
    #[error("valid_to no puede preceder a valid_from")]
    InvalidInterval,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum AttributeCode {
    FallRisk,
    Wandering,
}

impl AttributeCode {
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::FallRisk => "fall_risk",
            Self::Wandering => "wandering",
        }
    }

    pub fn parse(value: &str) -> Result<Self, AtributosError> {
        match value {
            "fall_risk" => Ok(Self::FallRisk),
            "wandering" => Ok(Self::Wandering),
            other => Err(AtributosError::UnknownCode {
                code: other.to_owned(),
            }),
        }
    }
}

#[derive(Clone, Debug)]
pub struct AttributeInput {
    pub resident_id: ResidentId,
    pub code: String,
    pub value: String,
    pub source: String,
    pub source_ref: Option<String>,
    pub recorded_by: Option<Id<Actor>>,
    pub valid_from: String,
    pub valid_to: Option<String>,
}

#[derive(Clone, Debug)]
pub struct ResidentAttribute {
    pub id: AttributeId,
    pub resident_id: ResidentId,
    pub code: AttributeCode,
    pub value: String,
    pub source: String,
    pub source_ref: Option<String>,
    pub recorded_by: Option<Id<Actor>>,
    pub recorded_at: Instante,
    pub valid_from: NaiveDate,
    pub valid_to: Option<NaiveDate>,
}

impl ResidentAttribute {
    pub fn create(
        id: AttributeId,
        input: AttributeInput,
        now: Instante,
    ) -> Result<Self, AtributosError> {
        let code = AttributeCode::parse(input.code.trim())?;
        let value = text(&input.value, "value", MAX_VALUE)?;
        let source = text(&input.source, "source", MAX_SOURCE)?;
        let source_ref = input
            .source_ref
            .as_ref()
            .map(|value| text(value, "source_ref", MAX_SOURCE_REF))
            .transpose()?;
        let valid_from = parse_date(&input.valid_from, "valid_from")?;
        let valid_to = input
            .valid_to
            .as_ref()
            .map(|value| parse_date(value, "valid_to"))
            .transpose()?;
        if let Some(valid_to) = valid_to {
            if valid_to < valid_from {
                return Err(AtributosError::InvalidInterval);
            }
        }
        Ok(Self {
            id,
            resident_id: input.resident_id,
            code,
            value,
            source,
            source_ref,
            recorded_by: input.recorded_by,
            recorded_at: now,
            valid_from,
            valid_to,
        })
    }
}

pub fn new_attribute_id() -> AttributeId {
    Id::new(crate::common::random_id("attribute"))
}

fn text(value: &str, field: &'static str, max: usize) -> Result<String, AtributosError> {
    let value = value.trim();
    if value.is_empty() {
        return Err(AtributosError::EmptyField { field });
    }
    if value.chars().count() > max {
        return Err(AtributosError::FieldTooLong { field, max });
    }
    Ok(value.to_owned())
}

fn parse_date(value: &str, field: &'static str) -> Result<NaiveDate, AtributosError> {
    NaiveDate::parse_from_str(value.trim(), "%Y-%m-%d")
        .map_err(|_| AtributosError::InvalidDate { field })
}

#[cfg(test)]
mod tests {
    use super::*;

    fn instant() -> Instante {
        "2026-08-18T12:00:00.000Z".parse().unwrap()
    }

    fn input() -> AttributeInput {
        AttributeInput {
            resident_id: ResidentId::new("resident-1"),
            code: "fall_risk".to_owned(),
            value: "alta".to_owned(),
            source: "evaluacion".to_owned(),
            source_ref: Some("eval-42".to_owned()),
            recorded_by: Some(Id::new("actor-1")),
            valid_from: "2026-08-01".to_owned(),
            valid_to: None,
        }
    }

    #[test]
    fn validates_vocabulary_and_interval() {
        let attribute =
            ResidentAttribute::create(AttributeId::new("attribute-1"), input(), instant()).unwrap();
        assert_eq!(attribute.code, AttributeCode::FallRisk);
        assert_eq!(attribute.source, "evaluacion");

        assert!(matches!(
            ResidentAttribute::create(
                AttributeId::new("attribute-2"),
                AttributeInput {
                    code: "diagnosis".to_owned(),
                    ..input()
                },
                instant(),
            ),
            Err(AtributosError::UnknownCode { .. })
        ));
        assert!(matches!(
            ResidentAttribute::create(
                AttributeId::new("attribute-3"),
                AttributeInput {
                    valid_to: Some("2026-07-01".to_owned()),
                    ..input()
                },
                instant(),
            ),
            Err(AtributosError::InvalidInterval)
        ));
        assert!(matches!(
            ResidentAttribute::create(
                AttributeId::new("attribute-4"),
                AttributeInput {
                    source: " ".to_owned(),
                    ..input()
                },
                instant(),
            ),
            Err(AtributosError::EmptyField { field: "source" })
        ));
    }
}
