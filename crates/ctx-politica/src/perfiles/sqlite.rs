use diesel::prelude::*;
use diesel::sqlite::SqliteConnection;
use mana_kernel::{Actor, Id, Instante};

use super::{new_profile_id, AlarmProfileVersion, Mode, Overrides, PerfilesRepo, ProfileInput};
use crate::catalogo::{MobilityAid, RiskLevel};
use crate::error::PoliticaError;
use crate::schema::alarm_profile_versions;

#[derive(Queryable)]
struct ProfileRow {
    id: String,
    resident_id: String,
    valid_from: String,
    valid_to: Option<String>,
    mobility_aid: String,
    autopilot: i32,
    mode: String,
    template_id: String,
    overrides_json: String,
    catalog_version: String,
    updated_by: Option<String>,
    created_at: String,
    risk_level: String,
}

#[derive(Insertable)]
#[diesel(table_name = alarm_profile_versions)]
struct NewProfile {
    id: String,
    resident_id: String,
    valid_from: String,
    valid_to: Option<String>,
    mobility_aid: String,
    autopilot: i32,
    mode: String,
    template_id: String,
    overrides_json: String,
    catalog_version: String,
    updated_by: Option<String>,
    created_at: String,
    risk_level: String,
}

fn row_to_profile(row: ProfileRow) -> Result<AlarmProfileVersion, PoliticaError> {
    let overrides: Overrides = row.overrides_json.parse().unwrap_or_default();
    Ok(AlarmProfileVersion {
        id: mana_kernel::Id::new(&row.id),
        resident_id: row.resident_id,
        valid_from: row.valid_from.parse()?,
        valid_to: row.valid_to.map(|v| v.parse()).transpose()?,
        risk_level: RiskLevel::parse(&row.risk_level)?,
        mobility_aid: MobilityAid::parse(&row.mobility_aid)?,
        autopilot: row.autopilot != 0,
        mode: Mode::parse(&row.mode)?,
        template_id: row.template_id,
        overrides,
        catalog_version: row.catalog_version,
        updated_by: row.updated_by,
        created_at: row.created_at.parse()?,
    })
}

fn row_to_profile_unchecked(row: ProfileRow) -> AlarmProfileVersion {
    let overrides: Overrides = row.overrides_json.parse().unwrap_or_default();
    AlarmProfileVersion {
        id: mana_kernel::Id::new(&row.id),
        resident_id: row.resident_id,
        valid_from: row.valid_from.parse().unwrap(),
        valid_to: row.valid_to.and_then(|v| v.parse().ok()),
        risk_level: RiskLevel::parse(&row.risk_level).unwrap_or(RiskLevel::Medium),
        mobility_aid: MobilityAid::parse(&row.mobility_aid).unwrap_or(MobilityAid::None),
        autopilot: row.autopilot != 0,
        mode: Mode::parse(&row.mode).unwrap_or(Mode::Preset),
        template_id: row.template_id,
        overrides,
        catalog_version: row.catalog_version,
        updated_by: row.updated_by,
        created_at: row.created_at.parse().unwrap(),
    }
}

impl PerfilesRepo for SqliteConnection {
    fn get_current(
        &mut self,
        resident_id: &str,
    ) -> Result<Option<AlarmProfileVersion>, PoliticaError> {
        let row: Option<ProfileRow> = alarm_profile_versions::table
            .filter(alarm_profile_versions::resident_id.eq(resident_id))
            .filter(alarm_profile_versions::valid_to.is_null())
            .first::<ProfileRow>(self)
            .optional()?;

        Ok(row.map(row_to_profile_unchecked))
    }

    fn get_at(
        &mut self,
        resident_id: &str,
        at: &Instante,
    ) -> Result<Option<AlarmProfileVersion>, PoliticaError> {
        let at_str = at.to_string();
        let row: Option<ProfileRow> = alarm_profile_versions::table
            .filter(alarm_profile_versions::resident_id.eq(resident_id))
            .filter(alarm_profile_versions::valid_from.le(&at_str))
            .filter(
                alarm_profile_versions::valid_to
                    .is_null()
                    .or(alarm_profile_versions::valid_to.gt(&at_str)),
            )
            .order(alarm_profile_versions::valid_from.desc())
            .first::<ProfileRow>(self)
            .optional()?;

        Ok(row.map(row_to_profile_unchecked))
    }

    fn list_history(
        &mut self,
        resident_id: &str,
    ) -> Result<Vec<AlarmProfileVersion>, PoliticaError> {
        let rows: Vec<ProfileRow> = alarm_profile_versions::table
            .filter(alarm_profile_versions::resident_id.eq(resident_id))
            .order(alarm_profile_versions::valid_from.asc())
            .load::<ProfileRow>(self)?;

        Ok(rows.into_iter().map(row_to_profile_unchecked).collect())
    }

    fn apply_in_transaction(
        &mut self,
        resident_id: &str,
        input: ProfileInput,
        actor_id: Id<Actor>,
        now: Instante,
    ) -> Result<AlarmProfileVersion, PoliticaError> {
        self.transaction::<_, PoliticaError, _>(|conn| {
            let current: Option<ProfileRow> = alarm_profile_versions::table
                .filter(alarm_profile_versions::resident_id.eq(resident_id))
                .filter(alarm_profile_versions::valid_to.is_null())
                .first::<ProfileRow>(conn)
                .optional()?;

            if let Some(current_row) = current {
                let now_str = now.to_string();
                diesel::update(
                    alarm_profile_versions::table
                        .filter(alarm_profile_versions::id.eq(&current_row.id)),
                )
                .set(alarm_profile_versions::valid_to.eq(&now_str))
                .execute(conn)?;
            }

            let id = new_profile_id();
            let new_profile = NewProfile {
                id: id.to_string(),
                resident_id: resident_id.to_owned(),
                valid_from: now.to_string(),
                valid_to: None,
                mobility_aid: input.mobility_aid.as_str().to_owned(),
                autopilot: if input.autopilot { 1 } else { 0 },
                mode: input.mode.as_str().to_owned(),
                template_id: input.template_id,
                overrides_json: input.overrides.to_string(),
                catalog_version: input.catalog_version,
                updated_by: Some(actor_id.to_string()),
                created_at: now.to_string(),
                risk_level: input.risk_level.as_str().to_owned(),
            };

            diesel::insert_into(alarm_profile_versions::table)
                .values(&new_profile)
                .execute(conn)?;

            let row: ProfileRow = alarm_profile_versions::table
                .filter(alarm_profile_versions::id.eq(id.to_string()))
                .first::<ProfileRow>(conn)?;

            row_to_profile(row)
        })
    }
}
