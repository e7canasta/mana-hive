use crate::{cli::CliError, output};

use super::super::cli::Options;

pub async fn dispatch(options: &Options) -> Result<(), CliError> {
    match options.verb() {
        "residentes" => {
            let client = crate::authenticated_client(options)?;
            let query = options.get("q");
            let response = client.list_residents(query).await?;
            let data = crate::response_data(response)?;
            let rows = data
                .residents
                .iter()
                .map(|resident| {
                    let room = resident
                        .room
                        .as_ref()
                        .map(|room| format!("{} ({})", room.number, room.wing_name))
                        .unwrap_or_else(|| "-".to_owned());
                    vec![
                        resident.id.clone(),
                        resident.full_name.clone(),
                        resident.status.clone(),
                        room,
                        resident
                            .admission_date
                            .clone()
                            .unwrap_or_else(|| "-".to_owned()),
                        resident.bed_id.clone().unwrap_or_else(|| "-".to_owned()),
                    ]
                })
                .collect::<Vec<_>>();
            output::print_table(
                &["id", "nombre", "estado", "habitacion", "ingreso", "cama"],
                &rows,
            );
            Ok(())
        }
        "residente" => {
            let client = crate::authenticated_client(options)?;
            let response = client.resident(options.required("resident-id")?).await?;
            output::print_json(&crate::response_data(response)?)?;
            Ok(())
        }
        "alta" => {
            let client = crate::authenticated_client(options)?;
            let full_name = options.required("full-name")?;
            let response = client
                .create_resident(mana_sdk::CreateResidentRequest {
                    full_name: Some(full_name.to_owned()),
                    external_id: options.get("external-id").map(str::to_owned),
                    birth_date: options.get("birth-date").map(str::to_owned),
                    admission_date: options.get("admission-date").map(str::to_owned),
                })
                .await?;
            output::print_json(&crate::response_data(response)?)?;
            Ok(())
        }
        "asignar" => {
            let client = crate::authenticated_client(options)?;
            let resident_id = options.required("resident-id")?;
            let bed_id = options.required("bed-id")?;
            let response = client
                .assign_bed(
                    resident_id,
                    mana_sdk::AssignBedRequest {
                        bed_id: bed_id.to_owned(),
                    },
                )
                .await?;
            output::print_json(&crate::response_data(response)?)?;
            Ok(())
        }
        "liberar" => {
            let client = crate::authenticated_client(options)?;
            let bed_id = options.required("bed-id")?;
            let response = client.release_bed(bed_id).await?;
            output::print_json(&crate::response_data(response)?)?;
            Ok(())
        }
        "egreso" => {
            let client = crate::authenticated_client(options)?;
            let resident_id = options.required("resident-id")?;
            let response = client
                .discharge_resident(
                    resident_id,
                    mana_sdk::DischargeRequest {
                        discharged_at: options.get("discharged-at").map(str::to_owned),
                    },
                )
                .await?;
            output::print_json(&crate::response_data(response)?)?;
            Ok(())
        }
        "assignments" => {
            let client = crate::authenticated_client(options)?;
            let response = client
                .list_assignments(options.required("resident-id")?)
                .await?;
            let data = crate::response_data(response)?;
            let rows = data
                .assignments
                .iter()
                .map(|assignment| {
                    vec![
                        assignment.id.clone(),
                        assignment.resident_id.clone(),
                        assignment.bed_id.clone(),
                        assignment.starts_at.clone(),
                        assignment
                            .ends_at
                            .clone()
                            .unwrap_or_else(|| "-".to_owned()),
                    ]
                })
                .collect::<Vec<_>>();
            output::print_table(&["id", "residente", "cama", "inicio", "fin"], &rows);
            Ok(())
        }
        "update" => {
            let client = crate::authenticated_client(options)?;
            let resident_id = options.required("resident-id")?;
            let response = client
                .update_resident(
                    resident_id,
                    mana_sdk::UpdateResidentRequest {
                        full_name: options.get("full-name").map(str::to_owned),
                        external_id: options
                            .get("external-id")
                            .map(|value| Some(value.to_owned())),
                        birth_date: options
                            .get("birth-date")
                            .map(|value| Some(value.to_owned())),
                        admission_date: options
                            .get("admission-date")
                            .map(|value| Some(value.to_owned())),
                    },
                )
                .await?;
            output::print_json(&crate::response_data(response)?)?;
            Ok(())
        }
        _ => Err(CliError::Usage(format!(
            "verbo desconocido para poblacion: {}\n\n{}",
            options.verb(),
            crate::cli::usage()
        ))),
    }
}
