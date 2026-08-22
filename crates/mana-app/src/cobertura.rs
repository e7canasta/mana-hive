use ctx_cobertura::{
    CoverageInput, MembershipInput, ShiftInput, StaffGroupId, StaffGroupInput, StaffGroupUpdate,
};

use crate::{error::AppFailure, identidad::required_token, state::AppState};

#[derive(Clone, Debug)]
pub struct CreateGroupCommand {
    pub facility_id: String,
    pub name: String,
}

#[derive(Clone, Debug, Default)]
pub struct UpdateGroupCommand {
    pub name: Option<String>,
}

#[derive(Clone, Debug)]
pub struct ReplaceMembersCommand {
    pub members: Vec<MemberEntry>,
}

#[derive(Clone, Debug)]
pub struct MemberEntry {
    pub user_id: String,
    pub valid_from: Option<String>,
}

#[derive(Clone, Debug)]
pub struct ReplaceGridCommand {
    pub shifts: Vec<ShiftEntry>,
}

#[derive(Clone, Debug)]
pub struct ShiftEntry {
    pub key: String,
    pub label: String,
    pub start_minute: i32,
}

#[derive(Clone, Debug)]
pub struct AssignCoverageCommand {
    pub staff_group_id: Option<String>,
    pub shift_key: String,
}

#[derive(Clone, Debug, serde::Serialize)]
pub struct GroupView {
    pub id: String,
    pub facility_id: String,
    pub name: String,
    pub retired_at: Option<String>,
    pub created_at: String,
    pub updated_at: String,
}

#[derive(Clone, Debug, serde::Serialize)]
pub struct MemberView {
    /// **El id del usuario.** El contrato describe al miembro como persona, no
    /// como fila de membresia; esa fila viaja en `membership_id`.
    pub id: String,
    pub membership_id: String,
    pub staff_group_id: String,
    pub user_id: String,
    /// Identidad resuelta: el cliente pinta el nombre del turno sin pedir cada
    /// usuario por separado. Cruza Cobertura e Identidad, y por eso vive aca.
    pub username: String,
    pub display_name: String,
    pub role: String,
    pub valid_from: String,
    pub valid_to: Option<String>,
}

#[derive(Clone, Debug, serde::Serialize)]
pub struct ShiftView {
    pub id: String,
    pub facility_id: String,
    pub key: String,
    pub label: String,
    pub start_minute: i32,
    /// El contrato del cliente pide la hora; `start_minute` es la precision
    /// interna. Se derivan una de la otra y viajan las dos.
    pub start_hour: i32,
    pub sort_order: i32,
}

#[derive(Clone, Debug, serde::Serialize)]
pub struct GridView {
    pub facility_id: String,
    pub shifts: Vec<ShiftView>,
    pub coverages_cleared: i64,
}

#[derive(Clone, Debug, serde::Serialize)]
pub struct CoverageView {
    pub id: String,
    pub wing_id: String,
    pub staff_group_id: Option<String>,
    pub shift_key: String,
    pub valid_from: String,
    pub valid_to: Option<String>,
}

/// La cobertura **en un instante**: que turno rige segun la grilla de la
/// residencia y que grupo lo cubre.
///
/// El contrato del cliente pide esto y no la lista de coberturas: el panel
/// quiere saber a quien avisar ahora, no el historial. `shift` es la clave que
/// la residencia declaro, no un enum fijo — atarlo a `day`/`night` mezclaria la
/// planilla laboral con el corte de alarmas, que son ejes distintos.
#[derive(Clone, Debug, serde::Serialize)]
pub struct WingCoverageView {
    pub wing: CoverageWingView,
    pub at: String,
    pub shift: String,
    pub staff_group: Option<GroupView>,
    /// Todas las coberturas vigentes del ala. Se conserva para quien ya la
    /// consumia.
    pub coverages: Vec<CoverageView>,
}

#[derive(Clone, Debug, serde::Serialize)]
pub struct CoverageWingView {
    pub id: String,
    pub name: String,
    pub floor: String,
    pub facility_id: String,
}

impl AppState {
    /// `user_id -> (username, display_name, role)`. Una consulta por request,
    /// no una por miembro.
    fn identities(&self) -> Result<Identities, AppFailure> {
        Ok(self
            .identity
            .list_users(true)?
            .into_iter()
            .map(|user| {
                (
                    user.id.as_str().to_owned(),
                    (
                        user.username.as_str().to_owned(),
                        user.display_name.as_str().to_owned(),
                        user.role.as_str().to_owned(),
                    ),
                )
            })
            .collect())
    }

    // -- Shifts --

    pub async fn get_shift_grid(
        &self,
        token: &str,
        facility_id: &str,
    ) -> Result<GridView, AppFailure> {
        required_token(token)?;
        let grid = self.cobertura.get_grid(facility_id)?;
        Ok(GridView {
            facility_id: grid.facility_id,
            shifts: grid.shifts.into_iter().map(shift_view).collect(),
            coverages_cleared: 0,
        })
    }

    pub async fn replace_shift_grid(
        &self,
        token: &str,
        facility_id: &str,
        command: ReplaceGridCommand,
    ) -> Result<GridView, AppFailure> {
        let _ = required_token(token)?;
        let inputs: Vec<ShiftInput> = command
            .shifts
            .into_iter()
            .map(|s| ShiftInput {
                key: s.key,
                label: s.label,
                start_minute: s.start_minute,
            })
            .collect();
        let result =
            self.cobertura
                .replace_grid(facility_id, inputs, mana_kernel::Instante::now())?;
        Ok(GridView {
            facility_id: result.grid.facility_id,
            shifts: result.grid.shifts.into_iter().map(shift_view).collect(),
            coverages_cleared: result.coverages_cleared,
        })
    }

    // -- Groups --

    pub async fn list_staff_groups(
        &self,
        token: &str,
        facility_id: &str,
    ) -> Result<Vec<GroupView>, AppFailure> {
        required_token(token)?;
        let groups = self.cobertura.list_groups(facility_id)?;
        Ok(groups.into_iter().map(group_view).collect())
    }

    pub async fn get_staff_group(
        &self,
        token: &str,
        group_id: &str,
    ) -> Result<(GroupView, Vec<MemberView>), AppFailure> {
        required_token(token)?;
        let id = StaffGroupId::new(group_id);
        let group = self.cobertura.get_group(&id)?;
        let members = self.cobertura.list_members(&id, None)?;
        Ok((group_view(group), {
            let identities = self.identities()?;
            members
                .into_iter()
                .map(|member| member_view_resolved(member, &identities))
                .collect()
        }))
    }

    pub async fn create_staff_group(
        &self,
        token: &str,
        command: CreateGroupCommand,
    ) -> Result<GroupView, AppFailure> {
        let _ = required_token(token)?;
        let group = self.cobertura.create_group(
            StaffGroupInput {
                facility_id: command.facility_id,
                name: command.name,
            },
            mana_kernel::Instante::now(),
        )?;
        Ok(group_view(group))
    }

    pub async fn update_staff_group(
        &self,
        token: &str,
        group_id: &str,
        command: UpdateGroupCommand,
    ) -> Result<GroupView, AppFailure> {
        let _ = required_token(token)?;
        let id = StaffGroupId::new(group_id);
        let group = self.cobertura.update_group(
            &id,
            StaffGroupUpdate { name: command.name },
            mana_kernel::Instante::now(),
        )?;
        Ok(group_view(group))
    }

    pub async fn replace_members(
        &self,
        token: &str,
        group_id: &str,
        command: ReplaceMembersCommand,
    ) -> Result<Vec<MemberView>, AppFailure> {
        let _ = required_token(token)?;
        let id = StaffGroupId::new(group_id);
        let now = mana_kernel::Instante::now();
        let inputs: Vec<MembershipInput> = command
            .members
            .into_iter()
            .map(|m| MembershipInput {
                user_id: m.user_id,
                valid_from: m.valid_from.and_then(|v| v.parse().ok()).unwrap_or(now),
            })
            .collect();
        let members = self.cobertura.replace_members(&id, inputs, now)?;
        let identities = self.identities()?;
        Ok(members
            .into_iter()
            .map(|member| member_view_resolved(member, &identities))
            .collect())
    }

    // -- Coverage --

    pub async fn get_wing_coverage(
        &self,
        token: &str,
        wing_id: &str,
        at: Option<&str>,
    ) -> Result<WingCoverageView, AppFailure> {
        required_token(token)?;
        let instant: mana_kernel::Instante = at
            .and_then(|v| v.parse().ok())
            .unwrap_or(mana_kernel::Instante::now());

        let wing = self.residence.get_wing(&wing_id.into())?;
        let coverages: Vec<CoverageView> = self
            .cobertura
            .get_coverage(wing_id, &instant)?
            .into_iter()
            .map(coverage_view)
            .collect();

        // Que turno rige a esta hora: el ultimo de la grilla cuyo comienzo ya
        // paso, o el ultimo del dia si todavia no arranco ninguno.
        //
        // La hora es la **local de la residencia**. Se calculaba en UTC, y una
        // grilla que declara `morning` a las 8 y `afternoon` a las 14 mandaba
        // al turno equivocado durante las horas de corrimiento: en Buenos Aires
        // (-3), entre las 11 y las 14 locales.
        let facility = self.residence.get_facility(&wing.facility_id)?;
        let minute_of_day = crate::reloj::minuto_del_dia(&instant, Some(&facility.timezone));
        let mut shifts = self.cobertura.list_shifts(wing.facility_id.as_str())?;
        shifts.sort_by_key(|shift| shift.start_minute);
        let current = shifts
            .iter()
            .rev()
            .find(|shift| shift.start_minute <= minute_of_day)
            .or_else(|| shifts.last())
            .map(|shift| shift.key.clone())
            .unwrap_or_default();

        let staff_group = coverages
            .iter()
            .find(|coverage| coverage.shift_key == current)
            .and_then(|coverage| coverage.staff_group_id.clone())
            .map(|id| {
                self.cobertura
                    .get_group(&ctx_cobertura::StaffGroupId::new(id))
            })
            .transpose()?
            .map(group_view);

        Ok(WingCoverageView {
            wing: CoverageWingView {
                id: wing.id.as_str().to_owned(),
                name: wing.name.clone(),
                floor: wing.floor.clone(),
                facility_id: wing.facility_id.as_str().to_owned(),
            },
            at: instant.to_string(),
            shift: current,
            staff_group,
            coverages,
        })
    }

    pub async fn assign_wing_coverage(
        &self,
        token: &str,
        wing_id: &str,
        command: AssignCoverageCommand,
    ) -> Result<CoverageView, AppFailure> {
        let _ = required_token(token)?;
        let now = mana_kernel::Instante::now();
        let result = self.cobertura.assign_coverage(
            CoverageInput {
                wing_id: wing_id.to_owned(),
                staff_group_id: command.staff_group_id,
                shift_key: command.shift_key,
            },
            now,
            None,
        )?;
        Ok(coverage_view(result.coverage))
    }

    pub async fn clear_wing_coverage(
        &self,
        token: &str,
        wing_id: &str,
        shift_key: &str,
    ) -> Result<CoverageView, AppFailure> {
        let _ = required_token(token)?;
        let coverage =
            self.cobertura
                .clear_coverage(wing_id, shift_key, mana_kernel::Instante::now())?;
        Ok(coverage_view(coverage))
    }
}

fn group_view(group: ctx_cobertura::StaffGroup) -> GroupView {
    GroupView {
        id: group.id.into_string(),
        facility_id: group.facility_id,
        name: group.name,
        retired_at: group.retired_at.map(|t| t.to_string()),
        created_at: group.created_at.to_string(),
        updated_at: group.updated_at.to_string(),
    }
}

type Identities = std::collections::HashMap<String, (String, String, String)>;

fn member_view_resolved(
    member: ctx_cobertura::StaffGroupMembership,
    identities: &Identities,
) -> MemberView {
    let (username, display_name, role) = identities
        .get(&member.user_id)
        .cloned()
        .unwrap_or_else(|| (String::new(), String::new(), String::new()));
    MemberView {
        id: member.user_id.clone(),
        membership_id: member.id.into_string(),
        staff_group_id: member.staff_group_id.into_string(),
        user_id: member.user_id,
        username,
        display_name,
        role,
        valid_from: member.valid_from.to_string(),
        valid_to: member.valid_to.map(|t| t.to_string()),
    }
}

fn shift_view(shift: ctx_cobertura::FacilityShift) -> ShiftView {
    ShiftView {
        id: shift.id.into_string(),
        facility_id: shift.facility_id.clone(),
        key: shift.key,
        label: shift.label,
        start_hour: shift.start_minute / 60,
        start_minute: shift.start_minute,
        sort_order: shift.sort_order,
    }
}

fn coverage_view(coverage: ctx_cobertura::WingCoverage) -> CoverageView {
    CoverageView {
        id: coverage.id.into_string(),
        wing_id: coverage.wing_id,
        staff_group_id: coverage.staff_group_id,
        shift_key: coverage.shift_key,
        valid_from: coverage.valid_from.to_string(),
        valid_to: coverage.valid_to.map(|t| t.to_string()),
    }
}
