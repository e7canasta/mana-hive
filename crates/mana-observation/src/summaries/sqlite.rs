//! Mapeo fila <-> dominio de los resumenes. Las `*Row` no salen del crate.

use diesel::prelude::*;
use mana_kernel::Instante;

use crate::{
    common::parse_instant,
    error::ObservationError,
    schema::{bathroom_summaries, mobility_summaries, sleep_summaries},
    summaries::{
        new_summary_id, BathroomSummary, BathroomSummaryInput, MobilitySummary,
        MobilitySummaryInput, Provenance, SleepSummary, SleepSummaryInput,
    },
};

/// Reingerir el mismo dia reemplaza los valores y conserva `created_at`: la
/// fuente puede recalcular, y cuando lo hace no queremos dos verdades.
pub struct Upsert<T> {
    pub summary: T,
    pub replaced: bool,
}

macro_rules! provenance_of {
    ($row:expr) => {
        Provenance {
            source: $row.source,
            model_version: $row.model_version,
            confidence: $row.confidence,
            provenance_json: $row.provenance_json,
        }
    };
}

#[derive(Queryable, Selectable)]
#[diesel(table_name = sleep_summaries)]
struct SleepRow {
    id: String,
    source_record_id: String,
    resident_id: String,
    observed_on: String,
    calm_minutes: i32,
    restless_minutes: i32,
    awake_minutes: i32,
    out_of_bed_minutes: i32,
    bed_exit_count: i32,
    wake_count: i32,
    source: String,
    model_version: String,
    confidence: Option<f64>,
    provenance_json: String,
    created_at: String,
    updated_at: String,
}

fn to_sleep(row: SleepRow) -> Result<SleepSummary, ObservationError> {
    Ok(SleepSummary {
        id: row.id.into(),
        source_record_id: row.source_record_id,
        resident_id: row.resident_id,
        observed_on: row.observed_on,
        calm_minutes: row.calm_minutes,
        restless_minutes: row.restless_minutes,
        awake_minutes: row.awake_minutes,
        out_of_bed_minutes: row.out_of_bed_minutes,
        bed_exit_count: row.bed_exit_count,
        wake_count: row.wake_count,
        created_at: parse_instant("created_at", row.created_at.clone())?,
        updated_at: parse_instant("updated_at", row.updated_at.clone())?,
        provenance: provenance_of!(row),
    })
}

pub(crate) fn upsert_sleep(
    connection: &mut SqliteConnection,
    input: SleepSummaryInput,
) -> Result<Upsert<SleepSummary>, ObservationError> {
    input.validate()?;
    connection.transaction(|connection| {
        let now = Instante::now();
        let existing: Option<SleepRow> = sleep_summaries::table
            .filter(sleep_summaries::resident_id.eq(&input.resident_id))
            .filter(sleep_summaries::observed_on.eq(&input.observed_on))
            .select(SleepRow::as_select())
            .first(connection)
            .optional()?;

        let (id, created_at, replaced) = match &existing {
            Some(row) => (row.id.clone(), row.created_at.clone(), true),
            None => (new_summary_id().into_string(), now.to_string(), false),
        };

        let values = (
            sleep_summaries::id.eq(&id),
            sleep_summaries::source_record_id.eq(&input.source_record_id),
            sleep_summaries::resident_id.eq(&input.resident_id),
            sleep_summaries::observed_on.eq(&input.observed_on),
            sleep_summaries::calm_minutes.eq(input.calm_minutes),
            sleep_summaries::restless_minutes.eq(input.restless_minutes),
            sleep_summaries::awake_minutes.eq(input.awake_minutes),
            sleep_summaries::out_of_bed_minutes.eq(input.out_of_bed_minutes),
            sleep_summaries::bed_exit_count.eq(input.bed_exit_count),
            sleep_summaries::wake_count.eq(input.wake_count),
            sleep_summaries::source.eq(&input.provenance.source),
            sleep_summaries::model_version.eq(&input.provenance.model_version),
            sleep_summaries::confidence.eq(input.provenance.confidence),
            sleep_summaries::provenance_json.eq(&input.provenance.provenance_json),
            sleep_summaries::created_at.eq(&created_at),
            sleep_summaries::updated_at.eq(now.to_string()),
        );

        diesel::insert_into(sleep_summaries::table)
            .values(values.clone())
            .on_conflict(sleep_summaries::id)
            .do_update()
            .set(values)
            .execute(connection)?;

        let row: SleepRow = sleep_summaries::table
            .find(&id)
            .select(SleepRow::as_select())
            .first(connection)?;
        Ok(Upsert {
            summary: to_sleep(row)?,
            replaced,
        })
    })
}

pub(crate) fn list_sleep(
    connection: &mut SqliteConnection,
    resident_id: &str,
    limit: i64,
) -> Result<Vec<SleepSummary>, ObservationError> {
    let rows: Vec<SleepRow> = sleep_summaries::table
        .filter(sleep_summaries::resident_id.eq(resident_id))
        .order(sleep_summaries::observed_on.desc())
        .limit(limit)
        .select(SleepRow::as_select())
        .load(connection)?;
    rows.into_iter().map(to_sleep).collect()
}

#[derive(Queryable, Selectable)]
#[diesel(table_name = mobility_summaries)]
struct MobilityRow {
    id: String,
    source_record_id: String,
    resident_id: String,
    observed_on: String,
    in_bed_minutes: i32,
    out_of_bed_minutes: i32,
    out_of_sight_minutes: i32,
    walking_minutes: i32,
    distance_meters: Option<f64>,
    transfer_count: i32,
    source: String,
    model_version: String,
    confidence: Option<f64>,
    provenance_json: String,
    created_at: String,
    updated_at: String,
}

fn to_mobility(row: MobilityRow) -> Result<MobilitySummary, ObservationError> {
    Ok(MobilitySummary {
        id: row.id.into(),
        source_record_id: row.source_record_id,
        resident_id: row.resident_id,
        observed_on: row.observed_on,
        in_bed_minutes: row.in_bed_minutes,
        out_of_bed_minutes: row.out_of_bed_minutes,
        out_of_sight_minutes: row.out_of_sight_minutes,
        walking_minutes: row.walking_minutes,
        distance_meters: row.distance_meters,
        transfer_count: row.transfer_count,
        created_at: parse_instant("created_at", row.created_at.clone())?,
        updated_at: parse_instant("updated_at", row.updated_at.clone())?,
        provenance: provenance_of!(row),
    })
}

pub(crate) fn upsert_mobility(
    connection: &mut SqliteConnection,
    input: MobilitySummaryInput,
) -> Result<Upsert<MobilitySummary>, ObservationError> {
    input.validate()?;
    connection.transaction(|connection| {
        let now = Instante::now();
        let existing: Option<MobilityRow> = mobility_summaries::table
            .filter(mobility_summaries::resident_id.eq(&input.resident_id))
            .filter(mobility_summaries::observed_on.eq(&input.observed_on))
            .select(MobilityRow::as_select())
            .first(connection)
            .optional()?;

        let (id, created_at, replaced) = match &existing {
            Some(row) => (row.id.clone(), row.created_at.clone(), true),
            None => (new_summary_id().into_string(), now.to_string(), false),
        };

        let values = (
            mobility_summaries::id.eq(&id),
            mobility_summaries::source_record_id.eq(&input.source_record_id),
            mobility_summaries::resident_id.eq(&input.resident_id),
            mobility_summaries::observed_on.eq(&input.observed_on),
            mobility_summaries::in_bed_minutes.eq(input.in_bed_minutes),
            mobility_summaries::out_of_bed_minutes.eq(input.out_of_bed_minutes),
            mobility_summaries::out_of_sight_minutes.eq(input.out_of_sight_minutes),
            mobility_summaries::walking_minutes.eq(input.walking_minutes),
            mobility_summaries::distance_meters.eq(input.distance_meters),
            mobility_summaries::transfer_count.eq(input.transfer_count),
            mobility_summaries::source.eq(&input.provenance.source),
            mobility_summaries::model_version.eq(&input.provenance.model_version),
            mobility_summaries::confidence.eq(input.provenance.confidence),
            mobility_summaries::provenance_json.eq(&input.provenance.provenance_json),
            mobility_summaries::created_at.eq(&created_at),
            mobility_summaries::updated_at.eq(now.to_string()),
        );

        diesel::insert_into(mobility_summaries::table)
            .values(values.clone())
            .on_conflict(mobility_summaries::id)
            .do_update()
            .set(values)
            .execute(connection)?;

        let row: MobilityRow = mobility_summaries::table
            .find(&id)
            .select(MobilityRow::as_select())
            .first(connection)?;
        Ok(Upsert {
            summary: to_mobility(row)?,
            replaced,
        })
    })
}

pub(crate) fn list_mobility(
    connection: &mut SqliteConnection,
    resident_id: &str,
    limit: i64,
) -> Result<Vec<MobilitySummary>, ObservationError> {
    let rows: Vec<MobilityRow> = mobility_summaries::table
        .filter(mobility_summaries::resident_id.eq(resident_id))
        .order(mobility_summaries::observed_on.desc())
        .limit(limit)
        .select(MobilityRow::as_select())
        .load(connection)?;
    rows.into_iter().map(to_mobility).collect()
}

#[derive(Queryable, Selectable)]
#[diesel(table_name = bathroom_summaries)]
struct BathroomRow {
    id: String,
    source_record_id: String,
    resident_id: String,
    observed_on: String,
    visit_count: i32,
    night_visit_count: i32,
    assisted_count: i32,
    total_minutes: i32,
    longest_visit_minutes: i32,
    source: String,
    model_version: String,
    confidence: Option<f64>,
    provenance_json: String,
    created_at: String,
    updated_at: String,
}

fn to_bathroom(row: BathroomRow) -> Result<BathroomSummary, ObservationError> {
    Ok(BathroomSummary {
        id: row.id.into(),
        source_record_id: row.source_record_id,
        resident_id: row.resident_id,
        observed_on: row.observed_on,
        visit_count: row.visit_count,
        night_visit_count: row.night_visit_count,
        assisted_count: row.assisted_count,
        total_minutes: row.total_minutes,
        longest_visit_minutes: row.longest_visit_minutes,
        created_at: parse_instant("created_at", row.created_at.clone())?,
        updated_at: parse_instant("updated_at", row.updated_at.clone())?,
        provenance: provenance_of!(row),
    })
}

pub(crate) fn upsert_bathroom(
    connection: &mut SqliteConnection,
    input: BathroomSummaryInput,
) -> Result<Upsert<BathroomSummary>, ObservationError> {
    input.validate()?;
    connection.transaction(|connection| {
        let now = Instante::now();
        let existing: Option<BathroomRow> = bathroom_summaries::table
            .filter(bathroom_summaries::resident_id.eq(&input.resident_id))
            .filter(bathroom_summaries::observed_on.eq(&input.observed_on))
            .select(BathroomRow::as_select())
            .first(connection)
            .optional()?;

        let (id, created_at, replaced) = match &existing {
            Some(row) => (row.id.clone(), row.created_at.clone(), true),
            None => (new_summary_id().into_string(), now.to_string(), false),
        };

        let values = (
            bathroom_summaries::id.eq(&id),
            bathroom_summaries::source_record_id.eq(&input.source_record_id),
            bathroom_summaries::resident_id.eq(&input.resident_id),
            bathroom_summaries::observed_on.eq(&input.observed_on),
            bathroom_summaries::visit_count.eq(input.visit_count),
            bathroom_summaries::night_visit_count.eq(input.night_visit_count),
            bathroom_summaries::assisted_count.eq(input.assisted_count),
            bathroom_summaries::total_minutes.eq(input.total_minutes),
            bathroom_summaries::longest_visit_minutes.eq(input.longest_visit_minutes),
            bathroom_summaries::source.eq(&input.provenance.source),
            bathroom_summaries::model_version.eq(&input.provenance.model_version),
            bathroom_summaries::confidence.eq(input.provenance.confidence),
            bathroom_summaries::provenance_json.eq(&input.provenance.provenance_json),
            bathroom_summaries::created_at.eq(&created_at),
            bathroom_summaries::updated_at.eq(now.to_string()),
        );

        diesel::insert_into(bathroom_summaries::table)
            .values(values.clone())
            .on_conflict(bathroom_summaries::id)
            .do_update()
            .set(values)
            .execute(connection)?;

        let row: BathroomRow = bathroom_summaries::table
            .find(&id)
            .select(BathroomRow::as_select())
            .first(connection)?;
        Ok(Upsert {
            summary: to_bathroom(row)?,
            replaced,
        })
    })
}

pub(crate) fn list_bathroom(
    connection: &mut SqliteConnection,
    resident_id: &str,
    limit: i64,
) -> Result<Vec<BathroomSummary>, ObservationError> {
    let rows: Vec<BathroomRow> = bathroom_summaries::table
        .filter(bathroom_summaries::resident_id.eq(resident_id))
        .order(bathroom_summaries::observed_on.desc())
        .limit(limit)
        .select(BathroomRow::as_select())
        .load(connection)?;
    rows.into_iter().map(to_bathroom).collect()
}
