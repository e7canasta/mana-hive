use chrono::NaiveDate;
use diesel::prelude::*;
use diesel::{OptionalExtension, SqliteConnection};
use mana_kernel::{Actor, Id, Instante};

use crate::common::{parse_date, parse_instant, stored_domain};
use crate::schema::residents;
use crate::PoblacionError;

use super::repo::ResidentesRepo;
use super::{DischargeResult, Resident, ResidentId, ResidentInput, ResidentStatus, ResidentUpdate};
use crate::asignaciones::repo::AsignacionesRepo;

#[derive(Queryable, Selectable)]
#[diesel(table_name = residents)]
#[diesel(check_for_backend(diesel::sqlite::Sqlite))]
pub(crate) struct ResidentRow {
    pub(crate) id: String,
    pub(crate) external_id: Option<String>,
    pub(crate) full_name: String,
    pub(crate) birth_date: Option<String>,
    pub(crate) admission_date: Option<String>,
    pub(crate) status: String,
    pub(crate) discharged_at: Option<String>,
    pub(crate) discharged_by: Option<String>,
    pub(crate) created_at: String,
    pub(crate) updated_at: String,
}

#[derive(Insertable)]
#[diesel(table_name = residents)]
struct NewResidentRow<'a> {
    id: &'a str,
    external_id: Option<&'a str>,
    full_name: &'a str,
    birth_date: Option<&'a str>,
    admission_date: Option<&'a str>,
    status: &'a str,
    discharged_at: Option<&'a str>,
    discharged_by: Option<&'a str>,
    created_at: &'a str,
    updated_at: &'a str,
}

#[derive(AsChangeset)]
#[diesel(table_name = residents)]
#[diesel(treat_none_as_null = true)]
struct ResidentChangeset<'a> {
    external_id: Option<&'a str>,
    full_name: &'a str,
    birth_date: Option<&'a str>,
    admission_date: Option<&'a str>,
    status: &'a str,
    discharged_at: Option<&'a str>,
    discharged_by: Option<&'a str>,
    updated_at: &'a str,
}

impl ResidentesRepo for SqliteConnection {
    fn create_resident_in_transaction(
        connection: &mut SqliteConnection,
        id: ResidentId,
        input: ResidentInput,
        now: Instante,
    ) -> Result<Resident, PoblacionError> {
        let resident = Resident::create(id, input, now)?;
        insert_row(connection, &resident)?;
        Ok(resident)
    }

    fn update_resident_in_transaction(
        connection: &mut SqliteConnection,
        id: &ResidentId,
        input: ResidentUpdate,
        now: Instante,
    ) -> Result<Resident, PoblacionError> {
        let mut resident = <Self as ResidentesRepo>::get_resident(connection, id)?;
        resident.apply_update(input, now)?;
        update_row(connection, &resident)?;
        Ok(resident)
    }

    fn discharge_in_transaction(
        connection: &mut SqliteConnection,
        id: &ResidentId,
        date: NaiveDate,
        by: Id<Actor>,
        now: Instante,
    ) -> Result<DischargeResult, PoblacionError> {
        let mut resident = <Self as ResidentesRepo>::get_resident(connection, id)?;
        resident.discharge(date, by, now)?;
        update_row(connection, &resident)?;
        let closed =
            <SqliteConnection as AsignacionesRepo>::close_open_for_resident_in_transaction(
                connection, id, now,
            )?;
        Ok(DischargeResult {
            resident,
            closed_assignment: closed,
        })
    }

    fn list_residents(
        connection: &mut SqliteConnection,
        query: Option<&str>,
    ) -> Result<Vec<Resident>, PoblacionError> {
        let mut filter = residents::table.into_boxed();
        if let Some(query) = query.map(str::trim).filter(|query| !query.is_empty()) {
            let pattern = format!("%{}%", query.to_lowercase());
            filter = filter.filter(residents::full_name.like(pattern));
        }
        filter
            .select(ResidentRow::as_select())
            .order((residents::full_name.asc(), residents::id.asc()))
            .load::<ResidentRow>(connection)
            .map_err(PoblacionError::database)?
            .into_iter()
            .map(Resident::try_from)
            .collect()
    }

    fn get_resident(
        connection: &mut SqliteConnection,
        id: &ResidentId,
    ) -> Result<Resident, PoblacionError> {
        residents::table
            .filter(residents::id.eq(id.as_str()))
            .select(ResidentRow::as_select())
            .first(connection)
            .optional()
            .map_err(PoblacionError::database)?
            .map(Resident::try_from)
            .transpose()?
            .ok_or(PoblacionError::NotFound)
    }

    fn ensure_resident_active(
        connection: &mut SqliteConnection,
        id: &ResidentId,
    ) -> Result<(), PoblacionError> {
        let status = residents::table
            .filter(residents::id.eq(id.as_str()))
            .select(residents::status)
            .first::<String>(connection)
            .optional()
            .map_err(PoblacionError::database)?;
        match status.as_deref() {
            Some("active") => Ok(()),
            _ => Err(PoblacionError::NotFound),
        }
    }
}

fn insert_row(
    connection: &mut SqliteConnection,
    resident: &Resident,
) -> Result<(), PoblacionError> {
    let created_at = resident.created_at.to_string();
    let updated_at = resident.updated_at.to_string();
    let external_id = resident.external_id.as_deref();
    let birth_date = resident.birth_date.map(|date| date.to_string());
    let admission_date = resident.admission_date.map(|date| date.to_string());
    let discharged_at = resident.discharged_at.map(|date| date.to_string());
    let discharged_by = resident.discharged_by.as_ref().map(ToString::to_string);
    diesel::insert_into(residents::table)
        .values(NewResidentRow {
            id: resident.id.as_str(),
            external_id,
            full_name: &resident.full_name,
            birth_date: birth_date.as_deref(),
            admission_date: admission_date.as_deref(),
            status: resident.status.as_str(),
            discharged_at: discharged_at.as_deref(),
            discharged_by: discharged_by.as_deref(),
            created_at: &created_at,
            updated_at: &updated_at,
        })
        .execute(connection)
        .map_err(PoblacionError::database)?;
    Ok(())
}

fn update_row(
    connection: &mut SqliteConnection,
    resident: &Resident,
) -> Result<(), PoblacionError> {
    let updated_at = resident.updated_at.to_string();
    let external_id = resident.external_id.as_deref();
    let birth_date = resident.birth_date.map(|date| date.to_string());
    let admission_date = resident.admission_date.map(|date| date.to_string());
    let discharged_at = resident.discharged_at.map(|date| date.to_string());
    let discharged_by = resident.discharged_by.as_ref().map(ToString::to_string);
    diesel::update(residents::table.find(resident.id.as_str()))
        .set(ResidentChangeset {
            external_id,
            full_name: &resident.full_name,
            birth_date: birth_date.as_deref(),
            admission_date: admission_date.as_deref(),
            status: resident.status.as_str(),
            discharged_at: discharged_at.as_deref(),
            discharged_by: discharged_by.as_deref(),
            updated_at: &updated_at,
        })
        .execute(connection)
        .map_err(PoblacionError::database)?;
    Ok(())
}

impl TryFrom<ResidentRow> for Resident {
    type Error = PoblacionError;

    fn try_from(row: ResidentRow) -> Result<Self, PoblacionError> {
        let created_at = parse_instant("created_at", row.created_at)?;
        let updated_at = parse_instant("updated_at", row.updated_at)?;
        let mut resident = Resident::create(
            ResidentId::new(row.id),
            ResidentInput {
                full_name: row.full_name,
                external_id: row.external_id,
                birth_date: row.birth_date,
                admission_date: row.admission_date,
            },
            created_at,
        )
        .map_err(|error| stored_domain("resident", error))?;
        resident.status =
            ResidentStatus::parse(&row.status).map_err(|error| stored_domain("resident", error))?;
        resident.discharged_at = row
            .discharged_at
            .map(|value| parse_date("discharged_at", value))
            .transpose()?;
        resident.discharged_by = row.discharged_by.map(Id::<Actor>::new);
        resident.updated_at = updated_at;
        Ok(resident)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::asignaciones::repo::AsignacionesRepo;
    use crate::asignaciones::{new_assignment_id, BedRef};
    use crate::testsupport::{instant, store};

    fn resident_input() -> ResidentInput {
        ResidentInput {
            full_name: "Carmen Ruiz".to_owned(),
            external_id: Some("ext-carmen".to_owned()),
            birth_date: Some("1948-03-02".to_owned()),
            admission_date: Some("2026-01-15".to_owned()),
        }
    }

    #[test]
    fn creates_lists_and_filters_residents() {
        let store = store();
        let resident = store
            .create_resident(
                ResidentInput {
                    full_name: "Carmen Ruiz".to_owned(),
                    ..resident_input()
                },
                instant(),
            )
            .unwrap();
        store
            .create_resident(
                ResidentInput {
                    full_name: "Pedro Gomez".to_owned(),
                    external_id: None,
                    birth_date: None,
                    admission_date: None,
                },
                instant(),
            )
            .unwrap();

        assert_eq!(store.list_residents(None).unwrap().len(), 2);
        assert_eq!(store.list_residents(Some("car")).unwrap().len(), 1);
        assert_eq!(store.list_residents(Some("zzz")).unwrap().len(), 0);
        assert_eq!(
            store.get_resident(&resident.id).unwrap().full_name,
            "Carmen Ruiz"
        );
    }

    #[test]
    fn duplicate_external_id_conflicts() {
        let store = store();
        store.create_resident(resident_input(), instant()).unwrap();
        assert!(matches!(
            store.create_resident(resident_input(), instant()),
            Err(PoblacionError::Conflict)
        ));
    }

    #[test]
    fn update_clears_nullable_fields() {
        let store = store();
        let resident = store.create_resident(resident_input(), instant()).unwrap();
        let updated = store
            .update_resident(
                &resident.id,
                ResidentUpdate {
                    external_id: Some(None),
                    birth_date: Some(None),
                    ..Default::default()
                },
                instant(),
            )
            .unwrap();
        assert!(updated.external_id.is_none());
        assert!(updated.birth_date.is_none());
        assert_eq!(updated.full_name, "Carmen Ruiz");
    }

    #[test]
    fn discharge_closes_the_open_assignment_in_the_same_transaction() {
        let store = store();
        let resident = store.create_resident(resident_input(), instant()).unwrap();
        let mut connection = store.pool.get().unwrap();
        <SqliteConnection as AsignacionesRepo>::assign_in_transaction(
            &mut connection,
            new_assignment_id(),
            resident.id.clone(),
            &BedRef::new("bed-1").unwrap(),
            instant(),
            None,
        )
        .unwrap();
        drop(connection);

        let result = store
            .discharge(&resident.id, "2026-08-01", "actor-1", instant())
            .unwrap();
        assert_eq!(result.resident.status, ResidentStatus::Discharged);
        let closed = result.closed_assignment.unwrap();
        assert!(closed.ends_at.is_some());
    }

    #[test]
    fn discharge_with_date_before_admission_is_rejected() {
        let store = store();
        let resident = store.create_resident(resident_input(), instant()).unwrap();
        assert!(matches!(
            store.discharge(&resident.id, "2025-01-01", "actor-1", instant()),
            Err(PoblacionError::Residentes(
                crate::residentes::ResidentesError::DischargeBeforeAdmission
            ))
        ));
    }
}
