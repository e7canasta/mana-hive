use crate::{cli::CliError, output};

use super::super::cli::Options;

pub async fn dispatch(options: &Options) -> Result<(), CliError> {
    match options.verb() {
        "listar" => {
            let client = crate::authenticated_client(options)?;
            let response = client.list_alerts().await?;
            let data = crate::response_data(response)?;
            let rows: Vec<Vec<String>> = data
                .alerts
                .iter()
                .map(|a| {
                    vec![
                        a.id.clone(),
                        a.rule_id.clone(),
                        a.level.clone(),
                        a.status.clone(),
                        a.title.clone(),
                        a.bed_id.clone(),
                    ]
                })
                .collect();
            output::print_table(&["id", "regla", "nivel", "estado", "titulo", "cama"], &rows);
            Ok(())
        }
        "crear" => {
            let client = crate::authenticated_client(options)?;
            let bed_id = options.required("bed-id")?.to_owned();
            let rule_id = options.required("rule-id")?.to_owned();
            let level = options.get("level").unwrap_or("medium").to_owned();
            let title = options.required("title")?.to_owned();
            let evidence_kind = options.get("evidence-kind").unwrap_or("manual").to_owned();
            let resident_id = options.get("resident-id").map(str::to_owned);
            let detail = options.get("detail").map(str::to_owned);
            let occurred_at = options.get("occurred-at").unwrap_or("").to_owned();

            let request = mana_sdk::CreateAlertRequest {
                resident_id,
                bed_id,
                evidence_kind,
                evidence_ref: None,
                rule_id,
                level,
                title,
                detail,
                occurred_at,
            };
            let response = client.create_alert(request).await?;
            let data = crate::response_data(response)?;
            println!("Alerta creada: {}", data.alert.id);
            println!("  Estado: {}", data.alert.status);
            println!("  Nivel: {}", data.alert.level);
            Ok(())
        }
        "detalle" => {
            let client = crate::authenticated_client(options)?;
            let alert_id = options.required("alert-id")?;
            let response = client.get_alert(alert_id).await?;
            let data = crate::response_data(response)?;
            let a = &data.alert;
            println!("Alerta: {}", a.id);
            println!("  Regla: {}", a.rule_id);
            println!("  Nivel: {}", a.level);
            println!("  Estado: {}", a.status);
            println!("  Titulo: {}", a.title);
            if let Some(detail) = &a.detail {
                println!("  Detalle: {detail}");
            }
            println!("  Cama: {}", a.bed_id);
            if let Some(resident) = &a.resident_id {
                println!("  Residente: {resident}");
            }
            println!(
                "  Escalamiento: nivel {}, {:?}, {:?}",
                a.escalation.level, a.escalation.escalated_at, a.escalation.escalated_to
            );
            println!(
                "  Entregas: enviados={}, acusados={}, fallidos={}",
                a.delivery_summary.sent, a.delivery_summary.acked, a.delivery_summary.failed
            );
            Ok(())
        }
        "transicion" => {
            let client = crate::authenticated_client(options)?;
            let alert_id = options.required("alert-id")?.to_owned();
            let to_status = options.required("to-status")?.to_owned();
            let actor_id = options.get("actor-id").map(str::to_owned);

            let request = mana_sdk::TransitionAlertRequest {
                to_status,
                actor_id,
            };
            let response = client.transition_alert(&alert_id, request).await?;
            let data = crate::response_data(response)?;
            println!("Alerta {} -> {}", data.alert.id, data.alert.status);
            Ok(())
        }
        "entregas" => {
            let client = crate::authenticated_client(options)?;
            let alert_id = options.required("alert-id")?;
            let response = client.list_deliveries(alert_id).await?;
            let data = crate::response_data(response)?;
            let rows: Vec<Vec<String>> = data
                .deliveries
                .iter()
                .map(|d| {
                    vec![
                        d.id.clone(),
                        d.recipient_kind.clone(),
                        d.recipient_id.clone(),
                        d.channel.clone(),
                        d.events
                            .iter()
                            .map(|e| e.kind.clone())
                            .collect::<Vec<_>>()
                            .join(", "),
                    ]
                })
                .collect();
            output::print_table(&["id", "tipo", "destinatario", "canal", "eventos"], &rows);
            Ok(())
        }
        "view" => {
            let client = crate::authenticated_client(options)?;
            let alert_id = options.required("alert-id")?;
            let response = client.view_alert(alert_id).await?;
            let data = crate::response_data(response)?;
            println!("Alerta vista: {}", data.alert.id);
            println!("  Estado: {}", data.alert.status);
            Ok(())
        }
        "create-delivery" => {
            let client = crate::authenticated_client(options)?;
            let alert_id = options.required("alert-id")?;
            let request = mana_sdk::CreateDeliveryRequest {
                recipient_kind: options.required("recipient-kind")?.to_owned(),
                recipient_id: options.required("recipient-id")?.to_owned(),
                channel: options.required("channel")?.to_owned(),
                escalation_level: options
                    .get("escalation-level")
                    .and_then(|v| v.parse::<i32>().ok())
                    .unwrap_or(0),
            };
            let response = client
                .create_delivery(alert_id, request)
                .await?;
            let data = crate::response_data(response)?;
            println!("Entrega creada: {}", data.id);
            println!("  Canal: {}", data.channel);
            Ok(())
        }
        "delivery-event" => {
            let client = crate::authenticated_client(options)?;
            let delivery_id = options.required("delivery-id")?;
            let request = mana_sdk::AddDeliveryEventRequest {
                kind: options.required("kind")?.to_owned(),
                reason: options.get("reason").map(str::to_owned),
            };
            let response = client
                .add_delivery_event(delivery_id, request)
                .await?;
            let data = crate::response_data(response)?;
            println!("Evento registrado en entrega: {}", data.id);
            Ok(())
        }
        _ => Err(CliError::Usage(format!(
            "verbo desconocido para vigilancia: {}",
            options.verb()
        ))),
    }
}
