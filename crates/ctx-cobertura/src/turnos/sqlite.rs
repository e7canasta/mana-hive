use diesel::prelude::*;
use diesel::SqliteConnection;
use mana_kernel::Instante;

use crate::common::parse_instant;
use crate::schema::{facility_shifts, unit_shift_coverages};
use crate::CoberturaError;

use super::repo::TurnosRepo;
use super::{FacilityShift, ReplaceGridResult, ShiftGrid, ShiftId, ShiftInput};

#[derive(Queryable, Selectable)]
#[diesel(table_name = facility_shifts)]
#[diesel(check_for_backend(diesel::sqlite::Sqlite))]
struct ShiftRow {
    id: String,
    facility_id: String,
    key: String,
    label: String,
    start_minute: i32,
    sort_order: i32,
    retired_at: Option<String>,
    retired_by: Option<String>,
    created_at: String,
    updated_at: String,
}

#[derive(Insertable)]
#[diesel(table_name = facility_shifts)]
struct NewShiftRow<'a> {
    id: &'a str,
    facility_id: &'a str,
    key: &'a str,
    label: &'a str,
    start_minute: i32,
    sort_order: i32,
    retired_at: Option<&'a str>,
    retired_by: Option<&'a str>,
    created_at: &'a str,
    updated_at: &'a str,
}

impl TurnosRepo for SqliteConnection {
    fn get_grid(
        connection: &mut SqliteConnection,
        facility_id: &str,
    ) -> Result<ShiftGrid, CoberturaError> {
        let shifts = facility_shifts::table
            .filter(facility_shifts::facility_id.eq(facility_id))
            .filter(facility_shifts::retired_at.is_null())
            .select(ShiftRow::as_select())
            .order((facility_shifts::sort_order.asc(), facility_shifts::id.asc()))
            .load::<ShiftRow>(connection)
            .map_err(CoberturaError::database)?
            .into_iter()
            .map(FacilityShift::try_from)
            .collect::<Result<Vec<_>, _>>()?;
        Ok(ShiftGrid {
            facility_id: facility_id.to_owned(),
            shifts,
        })
    }

    fn replace_grid_in_transaction(
        connection: &mut SqliteConnection,
        facility_id: &str,
        shifts: Vec<ShiftInput>,
        now: Instante,
    ) -> Result<ReplaceGridResult, CoberturaError> {
        if shifts.is_empty() {
            return Err(CoberturaError::Turnos(super::TurnosError::EmptyGrid));
        }

        let now_str = now.to_string();

        // Retire all existing shifts for this facility
        let old_shifts: Vec<ShiftRow> = facility_shifts::table
            .filter(facility_shifts::facility_id.eq(facility_id))
            .filter(facility_shifts::retired_at.is_null())
            .select(ShiftRow::as_select())
            .load(connection)
            .map_err(CoberturaError::database)?;

        let old_keys: Vec<String> = old_shifts.iter().map(|s| s.key.clone()).collect();

        diesel::update(
            facility_shifts::table
                .filter(facility_shifts::facility_id.eq(facility_id))
                .filter(facility_shifts::retired_at.is_null()),
        )
        .set((
            facility_shifts::retired_at.eq(&now_str),
            facility_shifts::updated_at.eq(&now_str),
        ))
        .execute(connection)
        .map_err(CoberturaError::database)?;

        // Close coverages that used removed shift keys
        let new_keys: std::collections::HashSet<&str> =
            shifts.iter().map(|s| s.key.as_str()).collect();
        let removed_keys: Vec<&str> = old_keys
            .iter()
            .filter(|k| !new_keys.contains(k.as_str()))
            .map(|s| s.as_str())
            .collect();

        let mut coverages_cleared = 0i64;
        for key in &removed_keys {
            let count = diesel::update(
                unit_shift_coverages::table
                    .filter(unit_shift_coverages::shift_key.eq(key))
                    .filter(unit_shift_coverages::valid_to.is_null()),
            )
            .set(unit_shift_coverages::valid_to.eq(&now_str))
            .execute(connection)
            .map_err(CoberturaError::database)? as i64;
            coverages_cleared += count;
        }

        // Insert new shifts
        for (i, input) in shifts.iter().enumerate() {
            let id = super::new_shift_id();
            let created_at = now.to_string();
            let updated_at = now.to_string();
            diesel::insert_into(facility_shifts::table)
                .values(NewShiftRow {
                    id: id.as_str(),
                    facility_id,
                    key: &input.key,
                    label: &input.label,
                    start_minute: input.start_minute,
                    sort_order: (i + 1) as i32,
                    retired_at: None,
                    retired_by: None,
                    created_at: &created_at,
                    updated_at: &updated_at,
                })
                .execute(connection)
                .map_err(CoberturaError::database)?;
        }

        let grid = <Self as TurnosRepo>::get_grid(connection, facility_id)?;
        Ok(ReplaceGridResult {
            grid,
            coverages_cleared,
        })
    }

    fn list_shifts(
        connection: &mut SqliteConnection,
        facility_id: &str,
    ) -> Result<Vec<FacilityShift>, CoberturaError> {
        facility_shifts::table
            .filter(facility_shifts::facility_id.eq(facility_id))
            .filter(facility_shifts::retired_at.is_null())
            .select(ShiftRow::as_select())
            .order((facility_shifts::sort_order.asc(), facility_shifts::id.asc()))
            .load::<ShiftRow>(connection)
            .map_err(CoberturaError::database)?
            .into_iter()
            .map(FacilityShift::try_from)
            .collect()
    }

    fn ensure_shift_exists(
        connection: &mut SqliteConnection,
        facility_id: &str,
        shift_key: &str,
    ) -> Result<(), CoberturaError> {
        let exists = facility_shifts::table
            .filter(facility_shifts::facility_id.eq(facility_id))
            .filter(facility_shifts::key.eq(shift_key))
            .filter(facility_shifts::retired_at.is_null())
            .select(facility_shifts::id)
            .first::<String>(connection)
            .optional()
            .map_err(CoberturaError::database)?;
        if exists.is_some() {
            Ok(())
        } else {
            Err(CoberturaError::NotFound)
        }
    }
}

impl TryFrom<ShiftRow> for FacilityShift {
    type Error = CoberturaError;

    fn try_from(row: ShiftRow) -> Result<Self, CoberturaError> {
        let created_at = parse_instant("created_at", row.created_at)?;
        let updated_at = parse_instant("updated_at", row.updated_at)?;
        let retired_at = row
            .retired_at
            .map(|v| parse_instant("retired_at", v))
            .transpose()?;
        Ok(Self {
            id: ShiftId::new(row.id),
            facility_id: row.facility_id,
            key: row.key,
            label: row.label,
            start_minute: row.start_minute,
            sort_order: row.sort_order,
            retired_at,
            retired_by: row.retired_by.map(mana_kernel::Id::new),
            created_at,
            updated_at,
        })
    }
}
