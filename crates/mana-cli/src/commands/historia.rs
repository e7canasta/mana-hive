use crate::{cli::CliError, output};

use super::super::cli::Options;

pub async fn dispatch(options: &Options) -> Result<(), CliError> {
    match options.verb() {
        "incidentes" => {
            let client = crate::authenticated_client(options)?;
            let resident_id = options.required("resident-id")?;
            let response = client.list_incidents(resident_id).await?;
            let data = crate::response_data(response)?;
            let rows: Vec<Vec<String>> = data
                .incidents
                .iter()
                .map(|i| {
                    vec![
                        i.id.clone(),
                        i.detection.kind.clone(),
                        i.detection.severity.clone(),
                        i.current.status.clone(),
                        i.occurred_at.clone(),
                    ]
                })
                .collect();
            output::print_table(&["id", "tipo", "severidad", "estado", "ocurrido"], &rows);
            Ok(())
        }
        "incidente" => {
            let client = crate::authenticated_client(options)?;
            let incident_id = options.required("incident-id")?;
            let response = client.get_incident(incident_id).await?;
            output::print_json(&crate::response_data(response)?)?;
            Ok(())
        }
        "revisar" => {
            let client = crate::authenticated_client(options)?;
            let incident_id = options.required("incident-id")?;
            let status = options.required("status")?;
            let verdict = options.get("verdict").map(str::to_owned);
            let note = options.get("note").map(str::to_owned);
            let response = client
                .create_review(
                    incident_id,
                    mana_sdk::CreateReviewRequest {
                        status: status.to_owned(),
                        detection_verdict: verdict,
                        review_note: note,
                        resolved_at: None,
                    },
                )
                .await?;
            output::print_json(&crate::response_data(response)?)?;
            Ok(())
        }
        "ingest-incident" => {
            let client = crate::authenticated_client(options)?;
            let request = mana_sdk::IngestRequest {
                source_record_id: options.required("source-record-id")?.to_owned(),
                resident_id: options.required("resident-id")?.to_owned(),
                bed_id: options.get("bed-id").map(str::to_owned),
                source_alert_id: options.get("source-alert-id").map(str::to_owned),
                kind: options.required("kind")?.to_owned(),
                severity: options.required("severity")?.to_owned(),
                occurred_at: options.required("occurred-at")?.to_owned(),
                location: options.get("location").map(str::to_owned),
                activity: options.get("activity").map(str::to_owned),
                injury_status: options
                    .get("injury-status")
                    .unwrap_or("unknown")
                    .to_owned(),
                self_recovery: options
                    .get("self-recovery")
                    .and_then(|v| v.parse::<bool>().ok()),
                response_seconds: options
                    .get("response-seconds")
                    .and_then(|v| v.parse::<i32>().ok()),
                narrative: options.get("narrative").map(str::to_owned),
                interventions_json: options.get("interventions-json").map(str::to_owned),
                source: options.get("source").unwrap_or("cli").to_owned(),
                model_version: options.get("model-version").unwrap_or("cli").to_owned(),
                confidence: options
                    .get("confidence")
                    .and_then(|v| v.parse::<f64>().ok()),
                provenance_json: options.get("provenance-json").map(str::to_owned),
            };
            let response = client.ingest_incident(request).await?;
            output::print_json(&crate::response_data(response)?)?;
            Ok(())
        }
        _ => Err(CliError::Usage(format!(
            "verbo desconocido para historia: {}",
            options.verb()
        ))),
    }
}
