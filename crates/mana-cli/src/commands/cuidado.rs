use crate::{cli::CliError, output};

use super::super::cli::Options;

pub async fn dispatch(options: &Options) -> Result<(), CliError> {
    match options.verb() {
        "ronda-actual" => {
            let client = crate::authenticated_client(options)?;
            let wing_id = options.required("wing-id")?;
            let response = client.current_round(wing_id).await?;
            output::print_json(&crate::response_data(response)?)?;
            Ok(())
        }
        "rondas" => {
            let client = crate::authenticated_client(options)?;
            let wing_id = options.required("wing-id")?;
            let limit = options.get("limit").and_then(|v| v.parse::<i64>().ok());
            let response = client.list_rounds(wing_id, limit).await?;
            let data = crate::response_data(response)?;
            let rows: Vec<Vec<String>> = data
                .rounds
                .iter()
                .map(|r| {
                    vec![
                        r.id.clone(),
                        r.wing_id.clone(),
                        r.status.clone(),
                        r.started_at.clone(),
                        r.completed_at.clone().unwrap_or_else(|| "-".to_owned()),
                    ]
                })
                .collect();
            output::print_table(&["id", "ala", "estado", "inicio", "fin"], &rows);
            Ok(())
        }
        "crear-ronda" => {
            let client = crate::authenticated_client(options)?;
            let wing_id = options.required("wing-id")?;
            let response = client
                .create_round(mana_sdk::CreateRoundRequest {
                    wing_id: wing_id.to_owned(),
                })
                .await?;
            output::print_json(&crate::response_data(response)?)?;
            Ok(())
        }
        "completar-ronda" => {
            let client = crate::authenticated_client(options)?;
            let round_id = options.required("round-id")?;
            let response = client.complete_round(round_id).await?;
            output::print_json(&crate::response_data(response)?)?;
            Ok(())
        }
        "tarea" => {
            let client = crate::authenticated_client(options)?;
            let task_id = options.required("task-id")?;
            let status = options.get("status").map(str::to_owned);
            let note = options.get("note").map(|n| Some(n.to_owned()));
            let response = client
                .update_task(task_id, mana_sdk::UpdateTaskRequest { status, note })
                .await?;
            output::print_json(&crate::response_data(response)?)?;
            Ok(())
        }
        "notas" => {
            let client = crate::authenticated_client(options)?;
            let resident_id = options.required("resident-id")?;
            let limit = options.get("limit").and_then(|v| v.parse::<i64>().ok());
            let response = client.list_notes(resident_id, limit).await?;
            let data = crate::response_data(response)?;
            let rows: Vec<Vec<String>> = data
                .notes
                .iter()
                .map(|n| {
                    vec![
                        n.id.clone(),
                        n.resident_id.clone(),
                        n.kind.clone(),
                        n.body.clone(),
                        n.duration_min
                            .map(|d| format!("{d}min"))
                            .unwrap_or_else(|| "-".to_owned()),
                        n.created_at.clone(),
                    ]
                })
                .collect();
            output::print_table(
                &["id", "residente", "tipo", "cuerpo", "duracion", "creada"],
                &rows,
            );
            Ok(())
        }
        "nota" => {
            let client = crate::authenticated_client(options)?;
            let resident_id = options.required("resident-id")?;
            let body = options.required("body")?;
            let kind = options.get("kind").map(str::to_owned);
            let duration_min = options
                .get("duration-min")
                .and_then(|v| v.parse::<i32>().ok());
            let response = client
                .create_note(
                    resident_id,
                    mana_sdk::CreateNoteRequest {
                        body: body.to_owned(),
                        kind,
                        duration_min,
                    },
                )
                .await?;
            output::print_json(&crate::response_data(response)?)?;
            Ok(())
        }
        "cancel-round" => {
            let client = crate::authenticated_client(options)?;
            let round_id = options.required("round-id")?;
            let response = client.cancel_round(round_id).await?;
            output::print_json(&crate::response_data(response)?)?;
            Ok(())
        }
        _ => Err(CliError::Usage(format!(
            "verbo desconocido para cuidado: {}",
            options.verb()
        ))),
    }
}
