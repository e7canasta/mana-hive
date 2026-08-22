use crate::{cli::CliError, output};

use super::super::cli::Options;

pub async fn dispatch(options: &Options) -> Result<(), CliError> {
    match options.verb() {
        "board" => {
            let client = crate::authenticated_client(options)?;
            let wing_id = options.required("wing-id")?;
            let data = crate::response_data(client.wing_board(wing_id).await?)?;
            let rows: Vec<Vec<String>> = data
                .rooms
                .iter()
                .flat_map(|room| {
                    room.beds.iter().map(move |bed| {
                        vec![
                            room.number.clone(),
                            bed.label.clone(),
                            bed.resident_name.clone().unwrap_or_else(|| "-".to_owned()),
                            bed.current_state
                                .as_ref()
                                .map(|state| state.state.clone())
                                .unwrap_or_else(|| "-".to_owned()),
                            bed.current_state
                                .as_ref()
                                .map(|state| state.freshness.clone())
                                .unwrap_or_else(|| "not_observed".to_owned()),
                            // Una cama sin monitor_key no suena nunca. Se muestra.
                            bed.monitor_key
                                .clone()
                                .unwrap_or_else(|| "SIN VINCULAR".to_owned()),
                        ]
                    })
                })
                .collect();
            output::print_table(
                &[
                    "habitacion",
                    "cama",
                    "residente",
                    "estado",
                    "frescura",
                    "monitor",
                ],
                &rows,
            );
            println!("\neventos sin resolver: {}", data.unresolved_events);
            Ok(())
        }
        "estado" => {
            let client = crate::authenticated_client(options)?;
            let resident_id = options.required("resident-id")?;
            let data = crate::response_data(client.resident_current_state(resident_id).await?)?;
            output::print_json(&data)?;
            Ok(())
        }
        "eventos" => {
            let client = crate::authenticated_client(options)?;
            let resident_id = options.required("resident-id")?;
            let data = crate::response_data(client.resident_events(resident_id).await?)?;
            let rows: Vec<Vec<String>> = data
                .events
                .iter()
                .map(|event| {
                    vec![
                        event.occurred_at.clone(),
                        event.kind.clone(),
                        event.state.clone().unwrap_or_else(|| "-".to_owned()),
                        event.monitor_key.clone(),
                    ]
                })
                .collect();
            output::print_table(&["ocurrido", "tipo", "estado", "monitor"], &rows);
            Ok(())
        }
        "timeline" => {
            let client = crate::authenticated_client(options)?;
            let resident_id = options.required("resident-id")?;
            let data = crate::response_data(client.resident_timeline(resident_id).await?)?;
            output::print_json(&data)?;
            Ok(())
        }
        "sueno" => {
            let client = crate::authenticated_client(options)?;
            let resident_id = options.required("resident-id")?;
            let data = crate::response_data(client.resident_sleep(resident_id).await?)?;
            let rows: Vec<Vec<String>> = data
                .summaries
                .iter()
                .map(|s| {
                    vec![
                        s.observed_on.clone(),
                        s.in_bed_minutes.to_string(),
                        s.calm_minutes.to_string(),
                        s.wake_count.to_string(),
                        s.bed_exit_count.to_string(),
                        s.efficiency
                            .map(|value| format!("{:.0}%", value * 100.0))
                            .unwrap_or_else(|| "-".to_owned()),
                    ]
                })
                .collect();
            output::print_table(
                &[
                    "dia",
                    "en cama",
                    "tranquilo",
                    "despertares",
                    "salidas",
                    "eficiencia",
                ],
                &rows,
            );
            Ok(())
        }
        "movilidad" => {
            let client = crate::authenticated_client(options)?;
            let resident_id = options.required("resident-id")?;
            let data = crate::response_data(client.resident_mobility(resident_id).await?)?;
            let rows: Vec<Vec<String>> = data
                .summaries
                .iter()
                .map(|s| {
                    vec![
                        s.observed_on.clone(),
                        s.in_bed_minutes.to_string(),
                        s.out_of_bed_minutes.to_string(),
                        s.walking_minutes.to_string(),
                        s.transfer_count.to_string(),
                    ]
                })
                .collect();
            output::print_table(
                &["dia", "en cama", "fuera", "caminando", "transferencias"],
                &rows,
            );
            Ok(())
        }
        "bano" => {
            let client = crate::authenticated_client(options)?;
            let resident_id = options.required("resident-id")?;
            let data = crate::response_data(client.resident_bathroom(resident_id).await?)?;
            let rows: Vec<Vec<String>> = data
                .summaries
                .iter()
                .map(|s| {
                    vec![
                        s.observed_on.clone(),
                        s.visit_count.to_string(),
                        s.night_visit_count.to_string(),
                        s.assisted_count.to_string(),
                        s.average_visit_minutes
                            .map(|value| format!("{value:.1}"))
                            .unwrap_or_else(|| "-".to_owned()),
                    ]
                })
                .collect();
            output::print_table(
                &["dia", "visitas", "nocturnas", "asistidas", "promedio min"],
                &rows,
            );
            Ok(())
        }
        "habitaciones" => {
            let client = crate::authenticated_client(options)?;
            let data = crate::response_data(client.companion_rooms().await?)?;
            let rows: Vec<Vec<String>> = data
                .rooms
                .iter()
                .map(|room| {
                    vec![
                        room.room_number.clone(),
                        room.stream_key.clone().unwrap_or_else(|| "-".to_owned()),
                        room.occupants.join(", "),
                    ]
                })
                .collect();
            output::print_table(&["habitacion", "stream", "ocupantes"], &rows);
            Ok(())
        }
        "mirar" => {
            let client = crate::authenticated_client(options)?;
            let room_id = options.required("room-id")?;
            let data = crate::response_data(client.peek_room(room_id).await?)?;
            output::print_json(&data)?;
            Ok(())
        }
        "reporte" => {
            let client = crate::authenticated_client(options)?;
            let data = crate::response_data(client.reports_summary().await?)?;
            output::print_json(&data)?;
            Ok(())
        }
        "ingerir" => {
            // La ingesta no usa sesion: autentica al bridge con el secreto.
            let client = crate::unauthenticated_client(options)?;
            let secret = options
                .get("secret")
                .unwrap_or("clinical-dev-secret")
                .to_owned();
            let response = client
                .ingest_event(
                    &secret,
                    mana_sdk::IngestEventRequest {
                        source_event_id: options.required("source-event-id")?.to_owned(),
                        monitor_key: options.required("monitor-key")?.to_owned(),
                        kind: options.get("kind").unwrap_or("room_state").to_owned(),
                        state: options.get("state").map(str::to_owned),
                        occurred_at: options
                            .get("occurred-at")
                            .map(str::to_owned)
                            .unwrap_or_else(chrono_now),
                        ..Default::default()
                    },
                )
                .await?;
            let data = crate::response_data(response)?;
            if !data.resolved {
                println!("aviso: monitor_key sin vincular, el evento quedo sin cama");
            }
            output::print_json(&data)?;
            Ok(())
        }
        "ingest-sleep" => ingest_summary(options, SummaryKind::Sleep).await,
        "ingest-mobility" => ingest_summary(options, SummaryKind::Mobility).await,
        "ingest-bathroom" => ingest_summary(options, SummaryKind::Bathroom).await,
        _ => Err(CliError::Usage(format!(
            "verbo desconocido para observacion: {}",
            options.verb()
        ))),
    }
}

enum SummaryKind {
    Sleep,
    Mobility,
    Bathroom,
}

async fn ingest_summary(options: &Options, kind: SummaryKind) -> Result<(), CliError> {
    let client = crate::unauthenticated_client(options)?;
    let secret = options
        .get("secret")
        .unwrap_or("clinical-dev-secret")
        .to_owned();
    let body: serde_json::Value = {
        let raw = options.required("body")?;
        serde_json::from_str(raw)
            .map_err(|error| CliError::Usage(format!("body JSON invalido: {error}")))?
    };
    let response = match kind {
        SummaryKind::Sleep => client.ingest_sleep_summary(&secret, body).await?,
        SummaryKind::Mobility => client.ingest_mobility_summary(&secret, body).await?,
        SummaryKind::Bathroom => client.ingest_bathroom_summary(&secret, body).await?,
    };
    output::print_json(&crate::response_data(response)?)?;
    Ok(())
}

/// El instante de ahora en el formato que la API espera. El CLI lo completa
/// cuando el operador no lo pasa, que es el caso normal al probar a mano.
fn chrono_now() -> String {
    mana_sdk::now_rfc3339()
}
