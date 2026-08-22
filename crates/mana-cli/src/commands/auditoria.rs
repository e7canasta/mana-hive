use mana_sdk::{AuditQuery, AuditResponse};

use crate::{cli::CliError, output};

use super::super::cli::Options;

pub async fn dispatch(options: &Options) -> Result<(), CliError> {
    match options.verb() {
        "log" => {
            let query = AuditQuery {
                limit: options
                    .get("limit")
                    .and_then(|value| value.parse::<usize>().ok())
                    .map(|limit| limit.clamp(1, 500)),
                entity_type: options.get("entity-type").map(str::to_owned),
                entity_id: options.get("entity-id").map(str::to_owned),
                action: options.get("action").map(str::to_owned),
            };
            let client = crate::authenticated_client(options)?;
            let response = client.list_audit(query).await?;
            print_log(&crate::response_data(response)?);
            Ok(())
        }
        _ => Err(CliError::Usage(format!(
            "verbo desconocido para auditoria: {}\n\n{}",
            options.verb(),
            crate::cli::usage()
        ))),
    }
}

fn print_log(response: &AuditResponse) {
    let rows = response
        .audit
        .iter()
        .map(|entry| {
            vec![
                entry.created_at.clone(),
                entry.action.clone(),
                entry.entity_type.clone(),
                entry.entity_id.clone(),
                entry.actor_name.clone().unwrap_or_else(|| "-".to_owned()),
            ]
        })
        .collect::<Vec<_>>();
    output::print_table(
        &["created_at", "action", "entity_type", "entity_id", "actor"],
        &rows,
    );
}
