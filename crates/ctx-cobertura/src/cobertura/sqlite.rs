use diesel::prelude::*;
use diesel::SqliteConnection;
use mana_kernel::{Actor, Id, Instante};

use crate::common::parse_instant;
use crate::schema::unit_shift_coverages;
use crate::CoberturaError;

use super::repo::CoberturaRepo;
use super::{CoverageInput, CoverageResult, WingCoverage};

#[derive(Queryable, Selectable)]
#[diesel(table_name = unit_shift_coverages)]
#[diesel(check_for_backend(diesel::sqlite::Sqlite))]
struct CoverageRow {
    id: String,
    wing_id: String,
    staff_group_id: Option<String>,
    shift_key: String,
    valid_from: String,
    valid_to: Option<String>,
    created_at: String,
    created_by: Option<String>,
}

#[derive(Insertable)]
#[diesel(table_name = unit_shift_coverages)]
struct NewCoverageRow<'a> {
    id: &'a str,
    wing_id: &'a str,
    staff_group_id: Option<&'a str>,
    shift_key: &'a str,
    valid_from: &'a str,
    valid_to: Option<&'a str>,
    created_at: &'a str,
    created_by: Option<&'a str>,
}

impl CoberturaRepo for SqliteConnection {
    fn assign_coverage_in_transaction(
        connection: &mut SqliteConnection,
        input: CoverageInput,
        now: Instante,
        by: Option<Id<Actor>>,
    ) -> Result<CoverageResult, CoberturaError> {
        // Shift key validation happens at mana-app level (invariant 1 + 4).
        // Here we only enforce temporal uniqueness (invariant 5).

        // Invariant 5: close any open coverage for this wing+shift
        let now_str = now.to_string();
        let closed_rows: Vec<CoverageRow> = unit_shift_coverages::table
            .filter(unit_shift_coverages::wing_id.eq(&input.wing_id))
            .filter(unit_shift_coverages::shift_key.eq(&input.shift_key))
            .filter(unit_shift_coverages::valid_to.is_null())
            .select(CoverageRow::as_select())
            .load(connection)
            .map_err(CoberturaError::database)?;

        diesel::update(
            unit_shift_coverages::table
                .filter(unit_shift_coverages::wing_id.eq(&input.wing_id))
                .filter(unit_shift_coverages::shift_key.eq(&input.shift_key))
                .filter(unit_shift_coverages::valid_to.is_null()),
        )
        .set(unit_shift_coverages::valid_to.eq(&now_str))
        .execute(connection)
        .map_err(CoberturaError::database)?;

        let closed_previous = closed_rows
            .into_iter()
            .next()
            .map(WingCoverage::try_from)
            .transpose()?;

        // Invariant 6: if staff_group_id provided, verify same facility as wing
        // (checked at mana-app level via ports)

        let id = super::new_coverage_id();
        let created_at = now.to_string();
        let created_by = by.as_ref().map(ToString::to_string);
        diesel::insert_into(unit_shift_coverages::table)
            .values(NewCoverageRow {
                id: id.as_str(),
                wing_id: &input.wing_id,
                staff_group_id: input.staff_group_id.as_deref(),
                shift_key: &input.shift_key,
                valid_from: &now_str,
                valid_to: None,
                created_at: &created_at,
                created_by: created_by.as_deref(),
            })
            .execute(connection)
            .map_err(CoberturaError::database)?;

        let coverage = WingCoverage {
            id,
            wing_id: input.wing_id,
            staff_group_id: input.staff_group_id,
            shift_key: input.shift_key,
            valid_from: now,
            valid_to: None,
            created_at: now,
            created_by: by,
        };

        Ok(CoverageResult {
            coverage,
            closed_previous,
        })
    }

    fn clear_coverage_in_transaction(
        connection: &mut SqliteConnection,
        wing_id: &str,
        shift_key: &str,
        now: Instante,
    ) -> Result<WingCoverage, CoberturaError> {
        let now_str = now.to_string();
        let row: Option<CoverageRow> = unit_shift_coverages::table
            .filter(unit_shift_coverages::wing_id.eq(wing_id))
            .filter(unit_shift_coverages::shift_key.eq(shift_key))
            .filter(unit_shift_coverages::valid_to.is_null())
            .select(CoverageRow::as_select())
            .first(connection)
            .optional()
            .map_err(CoberturaError::database)?;

        let row = row.ok_or(CoberturaError::NotFound)?;

        diesel::update(
            unit_shift_coverages::table
                .filter(unit_shift_coverages::wing_id.eq(wing_id))
                .filter(unit_shift_coverages::shift_key.eq(shift_key))
                .filter(unit_shift_coverages::valid_to.is_null()),
        )
        .set(unit_shift_coverages::valid_to.eq(&now_str))
        .execute(connection)
        .map_err(CoberturaError::database)?;

        let mut coverage = WingCoverage::try_from(row)?;
        coverage.valid_to = Some(now);
        Ok(coverage)
    }

    fn get_coverage(
        connection: &mut SqliteConnection,
        wing_id: &str,
        at: &Instante,
    ) -> Result<Vec<WingCoverage>, CoberturaError> {
        let at_str = at.to_string();
        unit_shift_coverages::table
            .filter(unit_shift_coverages::wing_id.eq(wing_id))
            .filter(unit_shift_coverages::valid_from.le(&at_str))
            .filter(
                unit_shift_coverages::valid_to
                    .is_null()
                    .or(unit_shift_coverages::valid_to.gt(&at_str)),
            )
            .select(CoverageRow::as_select())
            .order((
                unit_shift_coverages::shift_key.asc(),
                unit_shift_coverages::id.asc(),
            ))
            .load::<CoverageRow>(connection)
            .map_err(CoberturaError::database)?
            .into_iter()
            .map(WingCoverage::try_from)
            .collect()
    }

    fn list_coverages(
        connection: &mut SqliteConnection,
        wing_id: &str,
    ) -> Result<Vec<WingCoverage>, CoberturaError> {
        unit_shift_coverages::table
            .filter(unit_shift_coverages::wing_id.eq(wing_id))
            .filter(unit_shift_coverages::valid_to.is_null())
            .select(CoverageRow::as_select())
            .order((
                unit_shift_coverages::shift_key.asc(),
                unit_shift_coverages::id.asc(),
            ))
            .load::<CoverageRow>(connection)
            .map_err(CoberturaError::database)?
            .into_iter()
            .map(WingCoverage::try_from)
            .collect()
    }
}

impl TryFrom<CoverageRow> for WingCoverage {
    type Error = CoberturaError;

    fn try_from(row: CoverageRow) -> Result<Self, CoberturaError> {
        let valid_from = parse_instant("valid_from", row.valid_from)?;
        let valid_to = row
            .valid_to
            .map(|v| parse_instant("valid_to", v))
            .transpose()?;
        let created_at = parse_instant("created_at", row.created_at)?;
        Ok(Self {
            id: crate::cobertura::CoverageId::new(row.id),
            wing_id: row.wing_id,
            staff_group_id: row.staff_group_id,
            shift_key: row.shift_key,
            valid_from,
            valid_to,
            created_at,
            created_by: row.created_by.map(Id::<Actor>::new),
        })
    }
}
