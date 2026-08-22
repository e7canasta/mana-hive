use diesel::prelude::*;
use diesel::SqliteConnection;
use mana_kernel::{Actor, Id, Instante};

use crate::common::{parse_date, parse_instant, stored_domain};
use crate::schema::resident_attributes;
use crate::PoblacionError;

use super::repo::AtributosRepo;
use super::{AttributeId, AttributeInput, ResidentAttribute};
use crate::residentes::{repo::ResidentesRepo, ResidentId};

#[derive(Queryable, Selectable)]
#[diesel(table_name = resident_attributes)]
#[diesel(check_for_backend(diesel::sqlite::Sqlite))]
pub(crate) struct AttributeRow {
    pub(crate) id: String,
    pub(crate) resident_id: String,
    pub(crate) code: String,
    pub(crate) value: String,
    pub(crate) source: String,
    pub(crate) source_ref: Option<String>,
    pub(crate) recorded_by: Option<String>,
    pub(crate) recorded_at: String,
    pub(crate) valid_from: String,
    pub(crate) valid_to: Option<String>,
}

#[derive(Insertable)]
#[diesel(table_name = resident_attributes)]
struct NewAttributeRow<'a> {
    id: &'a str,
    resident_id: &'a str,
    code: &'a str,
    value: &'a str,
    source: &'a str,
    source_ref: Option<&'a str>,
    recorded_by: Option<&'a str>,
    recorded_at: &'a str,
    valid_from: &'a str,
    valid_to: Option<&'a str>,
}

impl AtributosRepo for SqliteConnection {
    fn create_attribute_in_transaction(
        connection: &mut SqliteConnection,
        id: AttributeId,
        input: AttributeInput,
        now: Instante,
    ) -> Result<ResidentAttribute, PoblacionError> {
        <SqliteConnection as ResidentesRepo>::get_resident(connection, &input.resident_id)?;
        let attribute = ResidentAttribute::create(id, input, now)?;
        let recorded_at = attribute.recorded_at.to_string();
        let valid_from = attribute.valid_from.to_string();
        let valid_to = attribute.valid_to.map(|date| date.to_string());
        let source_ref = attribute.source_ref.as_deref();
        let recorded_by = attribute.recorded_by.as_ref().map(ToString::to_string);
        diesel::insert_into(resident_attributes::table)
            .values(NewAttributeRow {
                id: attribute.id.as_str(),
                resident_id: attribute.resident_id.as_str(),
                code: attribute.code.as_str(),
                value: &attribute.value,
                source: &attribute.source,
                source_ref,
                recorded_by: recorded_by.as_deref(),
                recorded_at: &recorded_at,
                valid_from: &valid_from,
                valid_to: valid_to.as_deref(),
            })
            .execute(connection)
            .map_err(PoblacionError::database)?;
        Ok(attribute)
    }

    fn list_attributes(
        connection: &mut SqliteConnection,
        resident_id: &ResidentId,
    ) -> Result<Vec<ResidentAttribute>, PoblacionError> {
        <SqliteConnection as ResidentesRepo>::get_resident(connection, resident_id)?;
        resident_attributes::table
            .filter(resident_attributes::resident_id.eq(resident_id.as_str()))
            .select(AttributeRow::as_select())
            .order(resident_attributes::valid_from.asc())
            .load::<AttributeRow>(connection)
            .map_err(PoblacionError::database)?
            .into_iter()
            .map(ResidentAttribute::try_from)
            .collect()
    }
}

impl TryFrom<AttributeRow> for ResidentAttribute {
    type Error = PoblacionError;

    fn try_from(row: AttributeRow) -> Result<Self, PoblacionError> {
        let recorded_at = parse_instant("recorded_at", row.recorded_at)?;
        let valid_from = parse_date("valid_from", row.valid_from)?;
        let valid_to = row
            .valid_to
            .map(|value| parse_date("valid_to", value))
            .transpose()?;
        ResidentAttribute::create(
            AttributeId::new(row.id),
            AttributeInput {
                resident_id: ResidentId::new(row.resident_id),
                code: row.code,
                value: row.value,
                source: row.source,
                source_ref: row.source_ref,
                recorded_by: row.recorded_by.map(Id::<Actor>::new),
                valid_from: valid_from.to_string(),
                valid_to: valid_to.map(|date| date.to_string()),
            },
            recorded_at,
        )
        .map_err(|error| stored_domain("attribute", error))
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::atributos::new_attribute_id;
    use crate::residentes::ResidentInput;
    use crate::testsupport::{instant, store};

    fn attribute_input(resident_id: &ResidentId) -> AttributeInput {
        AttributeInput {
            resident_id: resident_id.clone(),
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
    fn attributes_require_an_existing_resident_and_keep_provenance() {
        let store = store();
        let resident = store
            .create_resident(
                ResidentInput {
                    full_name: "Carmen Ruiz".to_owned(),
                    external_id: None,
                    birth_date: None,
                    admission_date: Some("2026-01-15".to_owned()),
                },
                instant(),
            )
            .unwrap();

        let attribute = store
            .create_attribute(new_attribute_id(), attribute_input(&resident.id), instant())
            .unwrap();
        assert_eq!(attribute.code.as_str(), "fall_risk");
        assert_eq!(attribute.source, "evaluacion");
        assert_eq!(attribute.recorded_by.unwrap().as_str(), "actor-1");
        assert_eq!(store.list_attributes(&resident.id).unwrap().len(), 1);

        assert!(matches!(
            store.create_attribute(
                new_attribute_id(),
                AttributeInput {
                    resident_id: ResidentId::new("resident-missing"),
                    ..attribute_input(&resident.id)
                },
                instant(),
            ),
            Err(PoblacionError::NotFound)
        ));
    }
}
