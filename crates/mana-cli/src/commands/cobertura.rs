use crate::{cli::CliError, output};

use super::super::cli::Options;

pub async fn dispatch(options: &Options) -> Result<(), CliError> {
    match options.verb() {
        "grilla" => {
            let client = crate::authenticated_client(options)?;
            let facility_id = options.required("facility-id")?;
            let response = client.get_shift_grid(facility_id).await?;
            output::print_json(&crate::response_data(response)?)?;
            Ok(())
        }
        "reemplazar-grilla" => {
            let client = crate::authenticated_client(options)?;
            let facility_id = options.required("facility-id")?;
            let shifts_json = options.required("shifts")?;
            let shifts: Vec<mana_sdk::ShiftEntry> = serde_json::from_str(shifts_json)?;
            let response = client
                .replace_shift_grid(facility_id, mana_sdk::ReplaceShiftGridRequest { shifts })
                .await?;
            output::print_json(&crate::response_data(response)?)?;
            Ok(())
        }
        "grupos" => {
            let client = crate::authenticated_client(options)?;
            let facility_id = options.required("facility-id")?;
            let response = client.list_staff_groups(facility_id).await?;
            let data = crate::response_data(response)?;
            let rows: Vec<Vec<String>> = data
                .groups
                .iter()
                .map(|g| {
                    vec![
                        g.id.clone(),
                        g.name.clone(),
                        g.facility_id.clone(),
                        g.retired_at.clone().unwrap_or_else(|| "-".to_owned()),
                    ]
                })
                .collect();
            output::print_table(&["id", "nombre", "facility", "retirado"], &rows);
            Ok(())
        }
        "grupo" => {
            let client = crate::authenticated_client(options)?;
            let group_id = options.required("group-id")?;
            let response = client.staff_group(group_id).await?;
            output::print_json(&crate::response_data(response)?)?;
            Ok(())
        }
        "crear-grupo" => {
            let client = crate::authenticated_client(options)?;
            let facility_id = options.required("facility-id")?;
            let name = options.required("name")?;
            let response = client
                .create_staff_group(mana_sdk::CreateGroupRequest {
                    facility_id: facility_id.to_owned(),
                    name: name.to_owned(),
                })
                .await?;
            output::print_json(&crate::response_data(response)?)?;
            Ok(())
        }
        "miembros" => {
            let client = crate::authenticated_client(options)?;
            let group_id = options.required("group-id")?;
            let users_json = options.required("users")?;
            let users: Vec<String> = serde_json::from_str(users_json)?;
            let members = users
                .into_iter()
                .map(|user_id| mana_sdk::MemberEntry {
                    user_id,
                    valid_from: None,
                })
                .collect();
            let response = client
                .replace_members(group_id, mana_sdk::ReplaceMembersRequest { members })
                .await?;
            output::print_json(&crate::response_data(response)?)?;
            Ok(())
        }
        "cobertura" => {
            let client = crate::authenticated_client(options)?;
            let wing_id = options.required("wing-id")?;
            let at = options.get("at");
            let response = client.get_wing_coverage(wing_id, at).await?;
            let data = crate::response_data(response)?;
            let rows: Vec<Vec<String>> = data
                .coverages
                .iter()
                .map(|c| {
                    vec![
                        c.shift_key.clone(),
                        c.staff_group_id.clone().unwrap_or_else(|| "-".to_owned()),
                        c.valid_from.clone(),
                        c.valid_to.clone().unwrap_or_else(|| "-".to_owned()),
                    ]
                })
                .collect();
            output::print_table(&["turno", "grupo", "desde", "hasta"], &rows);
            Ok(())
        }
        "asignar-cobertura" => {
            let client = crate::authenticated_client(options)?;
            let wing_id = options.required("wing-id")?;
            let shift_key = options.required("shift-key")?;
            let staff_group_id = options.get("group-id").map(str::to_owned);
            let response = client
                .assign_wing_coverage(
                    wing_id,
                    mana_sdk::AssignCoverageRequest {
                        staff_group_id,
                        shift_key: shift_key.to_owned(),
                    },
                )
                .await?;
            output::print_json(&crate::response_data(response)?)?;
            Ok(())
        }
        "update-group" => {
            let client = crate::authenticated_client(options)?;
            let group_id = options.required("group-id")?;
            let response = client
                .update_staff_group(
                    group_id,
                    mana_sdk::UpdateGroupRequest {
                        name: options.get("name").map(str::to_owned),
                    },
                )
                .await?;
            output::print_json(&crate::response_data(response)?)?;
            Ok(())
        }
        "clear-coverage" => {
            let client = crate::authenticated_client(options)?;
            let wing_id = options.required("wing-id")?;
            let shift_key = options.required("shift-key")?;
            let response = client
                .clear_wing_coverage(wing_id, shift_key)
                .await?;
            output::print_json(&crate::response_data(response)?)?;
            Ok(())
        }
        _ => Err(CliError::Usage(format!(
            "verbo desconocido para cobertura: {}",
            options.verb()
        ))),
    }
}
