use diesel::prelude::*;
use diesel::{OptionalExtension, SqliteConnection};
use mana_kernel::{Actor, Id, Instante};

use crate::common::parse_instant;
use crate::schema::{staff_group_members, staff_groups};
use crate::CoberturaError;

use super::repo::GruposRepo;
use super::{
    MembershipId, MembershipInput, StaffGroup, StaffGroupId, StaffGroupInput, StaffGroupMembership,
    StaffGroupUpdate,
};

#[derive(Queryable, Selectable)]
#[diesel(table_name = staff_groups)]
#[diesel(check_for_backend(diesel::sqlite::Sqlite))]
struct GroupRow {
    id: String,
    facility_id: String,
    name: String,
    retired_at: Option<String>,
    retired_by: Option<String>,
    created_at: String,
    updated_at: String,
}

#[derive(Insertable)]
#[diesel(table_name = staff_groups)]
struct NewGroupRow<'a> {
    id: &'a str,
    facility_id: &'a str,
    name: &'a str,
    retired_at: Option<&'a str>,
    retired_by: Option<&'a str>,
    created_at: &'a str,
    updated_at: &'a str,
}

#[derive(AsChangeset)]
#[diesel(table_name = staff_groups)]
#[diesel(treat_none_as_null = true)]
struct GroupChangeset<'a> {
    name: &'a str,
    retired_at: Option<&'a str>,
    retired_by: Option<&'a str>,
    updated_at: &'a str,
}

#[derive(Queryable, Selectable)]
#[diesel(table_name = staff_group_members)]
#[diesel(check_for_backend(diesel::sqlite::Sqlite))]
struct MembershipRow {
    id: String,
    staff_group_id: String,
    user_id: String,
    valid_from: String,
    valid_to: Option<String>,
    created_at: String,
}

#[derive(Insertable)]
#[diesel(table_name = staff_group_members)]
struct NewMembershipRow<'a> {
    id: &'a str,
    staff_group_id: &'a str,
    user_id: &'a str,
    valid_from: &'a str,
    valid_to: Option<&'a str>,
    created_at: &'a str,
}

impl GruposRepo for SqliteConnection {
    fn create_group_in_transaction(
        connection: &mut SqliteConnection,
        id: StaffGroupId,
        input: StaffGroupInput,
        now: Instante,
    ) -> Result<StaffGroup, CoberturaError> {
        let group = StaffGroup::create(id, input, now)?;
        let created_at = group.created_at.to_string();
        let updated_at = group.updated_at.to_string();
        diesel::insert_into(staff_groups::table)
            .values(NewGroupRow {
                id: group.id.as_str(),
                facility_id: &group.facility_id,
                name: &group.name,
                retired_at: None,
                retired_by: None,
                created_at: &created_at,
                updated_at: &updated_at,
            })
            .execute(connection)
            .map_err(CoberturaError::database)?;
        Ok(group)
    }

    fn update_group_in_transaction(
        connection: &mut SqliteConnection,
        id: &StaffGroupId,
        input: StaffGroupUpdate,
        now: Instante,
    ) -> Result<StaffGroup, CoberturaError> {
        let mut group = <Self as GruposRepo>::get_group(connection, id)?;
        group.apply_update(input, now)?;
        update_group_row(connection, &group)?;
        Ok(group)
    }

    fn retire_group_in_transaction(
        connection: &mut SqliteConnection,
        id: &StaffGroupId,
        by: Id<Actor>,
        now: Instante,
    ) -> Result<StaffGroup, CoberturaError> {
        let mut group = <Self as GruposRepo>::get_group(connection, id)?;
        group.retire(by, now)?;
        update_group_row(connection, &group)?;
        Ok(group)
    }

    fn get_group(
        connection: &mut SqliteConnection,
        id: &StaffGroupId,
    ) -> Result<StaffGroup, CoberturaError> {
        staff_groups::table
            .filter(staff_groups::id.eq(id.as_str()))
            .select(GroupRow::as_select())
            .first(connection)
            .optional()
            .map_err(CoberturaError::database)?
            .map(StaffGroup::try_from)
            .transpose()?
            .ok_or(CoberturaError::NotFound)
    }

    fn list_groups(
        connection: &mut SqliteConnection,
        facility_id: &str,
    ) -> Result<Vec<StaffGroup>, CoberturaError> {
        // Sin filtro, todos: un `facility_id` vacio es "no filtres", no "no
        // hay ninguno". Devolver la lista vacia hacia que el panel mostrara
        // cero grupos sin decir por que.
        let mut query = staff_groups::table
            .filter(staff_groups::retired_at.is_null())
            .into_boxed();
        if !facility_id.trim().is_empty() {
            query = query.filter(staff_groups::facility_id.eq(facility_id));
        }
        query
            .select(GroupRow::as_select())
            .order((staff_groups::name.asc(), staff_groups::id.asc()))
            .load::<GroupRow>(connection)
            .map_err(CoberturaError::database)?
            .into_iter()
            .map(StaffGroup::try_from)
            .collect()
    }

    fn replace_members_in_transaction(
        connection: &mut SqliteConnection,
        group_id: &StaffGroupId,
        members: Vec<MembershipInput>,
        now: Instante,
    ) -> Result<Vec<StaffGroupMembership>, CoberturaError> {
        use crate::schema::staff_group_members as m;
        let now_str = now.to_string();

        // Close all currently open memberships for this group
        diesel::update(
            m::table
                .filter(m::staff_group_id.eq(group_id.as_str()))
                .filter(m::valid_to.is_null()),
        )
        .set(m::valid_to.eq(&now_str))
        .execute(connection)
        .map_err(CoberturaError::database)?;

        let mut result = Vec::with_capacity(members.len());
        for input in members {
            let id = super::new_membership_id();
            let valid_from_str = input.valid_from.to_string();
            let created_at_str = now.to_string();
            diesel::insert_into(m::table)
                .values(NewMembershipRow {
                    id: id.as_str(),
                    staff_group_id: group_id.as_str(),
                    user_id: &input.user_id,
                    valid_from: &valid_from_str,
                    valid_to: None,
                    created_at: &created_at_str,
                })
                .execute(connection)
                .map_err(CoberturaError::database)?;
            result.push(StaffGroupMembership {
                id,
                staff_group_id: group_id.clone(),
                user_id: input.user_id,
                valid_from: input.valid_from,
                valid_to: None,
                created_at: now,
            });
        }
        Ok(result)
    }

    fn list_members(
        connection: &mut SqliteConnection,
        group_id: &StaffGroupId,
        at: Option<&Instante>,
    ) -> Result<Vec<StaffGroupMembership>, CoberturaError> {
        let at_str = at.map(|a| a.to_string());
        let mut filter = staff_group_members::table
            .filter(staff_group_members::staff_group_id.eq(group_id.as_str()))
            .into_boxed();
        if let Some(ref at_val) = at_str {
            filter = filter
                .filter(staff_group_members::valid_from.le(at_val))
                .filter(
                    staff_group_members::valid_to
                        .is_null()
                        .or(staff_group_members::valid_to.gt(at_val)),
                );
        } else {
            filter = filter.filter(staff_group_members::valid_to.is_null());
        }
        filter
            .select(MembershipRow::as_select())
            .order((
                staff_group_members::valid_from.asc(),
                staff_group_members::id.asc(),
            ))
            .load::<MembershipRow>(connection)
            .map_err(CoberturaError::database)?
            .into_iter()
            .map(StaffGroupMembership::try_from)
            .collect()
    }

    fn get_member(
        connection: &mut SqliteConnection,
        id: &MembershipId,
    ) -> Result<StaffGroupMembership, CoberturaError> {
        staff_group_members::table
            .filter(staff_group_members::id.eq(id.as_str()))
            .select(MembershipRow::as_select())
            .first(connection)
            .optional()
            .map_err(CoberturaError::database)?
            .map(StaffGroupMembership::try_from)
            .transpose()?
            .ok_or(CoberturaError::NotFound)
    }

    fn ensure_group_facility(
        connection: &mut SqliteConnection,
        group_id: &StaffGroupId,
        expected_facility: &str,
    ) -> Result<(), CoberturaError> {
        let facility = staff_groups::table
            .filter(staff_groups::id.eq(group_id.as_str()))
            .select(staff_groups::facility_id)
            .first::<String>(connection)
            .optional()
            .map_err(CoberturaError::database)?;
        match facility.as_deref() {
            Some(f) if f == expected_facility => Ok(()),
            _ => Err(CoberturaError::CrossFacility),
        }
    }
}

fn update_group_row(
    connection: &mut SqliteConnection,
    group: &StaffGroup,
) -> Result<(), CoberturaError> {
    let updated_at = group.updated_at.to_string();
    let retired_at = group.retired_at.map(|t| t.to_string());
    let retired_by = group.retired_by.as_ref().map(ToString::to_string);
    diesel::update(staff_groups::table.find(group.id.as_str()))
        .set(GroupChangeset {
            name: &group.name,
            retired_at: retired_at.as_deref(),
            retired_by: retired_by.as_deref(),
            updated_at: &updated_at,
        })
        .execute(connection)
        .map_err(CoberturaError::database)?;
    Ok(())
}

impl TryFrom<GroupRow> for StaffGroup {
    type Error = CoberturaError;

    fn try_from(row: GroupRow) -> Result<Self, CoberturaError> {
        let created_at = parse_instant("created_at", row.created_at)?;
        let updated_at = parse_instant("updated_at", row.updated_at)?;
        let retired_at = row
            .retired_at
            .map(|v| parse_instant("retired_at", v))
            .transpose()?;
        Ok(Self {
            id: StaffGroupId::new(row.id),
            facility_id: row.facility_id,
            name: row.name,
            retired_at,
            retired_by: row.retired_by.map(Id::<Actor>::new),
            created_at,
            updated_at,
        })
    }
}

impl TryFrom<MembershipRow> for StaffGroupMembership {
    type Error = CoberturaError;

    fn try_from(row: MembershipRow) -> Result<Self, CoberturaError> {
        let valid_from = parse_instant("valid_from", row.valid_from)?;
        let valid_to = row
            .valid_to
            .map(|v| parse_instant("valid_to", v))
            .transpose()?;
        let created_at = parse_instant("created_at", row.created_at)?;
        Ok(Self {
            id: MembershipId::new(row.id),
            staff_group_id: StaffGroupId::new(row.staff_group_id),
            user_id: row.user_id,
            valid_from,
            valid_to,
            created_at,
        })
    }
}
