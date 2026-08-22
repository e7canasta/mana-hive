use diesel::SqliteConnection;
use mana_kernel::{Actor, Id, Instante};

use super::{
    MembershipId, StaffGroup, StaffGroupId, StaffGroupInput, StaffGroupMembership, StaffGroupUpdate,
};
use crate::CoberturaError;

pub trait GruposRepo {
    fn create_group_in_transaction(
        connection: &mut SqliteConnection,
        id: StaffGroupId,
        input: StaffGroupInput,
        now: Instante,
    ) -> Result<StaffGroup, CoberturaError>;

    fn update_group_in_transaction(
        connection: &mut SqliteConnection,
        id: &StaffGroupId,
        input: StaffGroupUpdate,
        now: Instante,
    ) -> Result<StaffGroup, CoberturaError>;

    fn retire_group_in_transaction(
        connection: &mut SqliteConnection,
        id: &StaffGroupId,
        by: Id<Actor>,
        now: Instante,
    ) -> Result<StaffGroup, CoberturaError>;

    fn get_group(
        connection: &mut SqliteConnection,
        id: &StaffGroupId,
    ) -> Result<StaffGroup, CoberturaError>;

    fn list_groups(
        connection: &mut SqliteConnection,
        facility_id: &str,
    ) -> Result<Vec<StaffGroup>, CoberturaError>;

    fn replace_members_in_transaction(
        connection: &mut SqliteConnection,
        group_id: &StaffGroupId,
        members: Vec<super::MembershipInput>,
        now: Instante,
    ) -> Result<Vec<StaffGroupMembership>, CoberturaError>;

    fn list_members(
        connection: &mut SqliteConnection,
        group_id: &StaffGroupId,
        at: Option<&Instante>,
    ) -> Result<Vec<StaffGroupMembership>, CoberturaError>;

    fn get_member(
        connection: &mut SqliteConnection,
        id: &MembershipId,
    ) -> Result<StaffGroupMembership, CoberturaError>;

    fn ensure_group_facility(
        connection: &mut SqliteConnection,
        group_id: &StaffGroupId,
        expected_facility: &str,
    ) -> Result<(), CoberturaError>;
}
