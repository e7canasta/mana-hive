use crate::{cli::CliError, output, render::{self, OutputFormat}};

use super::super::cli::Options;

pub async fn dispatch(options: &Options) -> Result<(), CliError> {
    let format = OutputFormat::from_str(Some(options.format()));
    
    match options.verb() {
        "catalogo" => {
            let client = crate::unauthenticated_client(options)?;
            let response = client.get_catalog().await?;
            let data = crate::response_data(response)?;
            let rows: Vec<Vec<String>> = data
                .transitions
                .iter()
                .map(|r| {
                    vec![
                        r.id.clone(),
                        r.group.clone(),
                        r.label.clone(),
                        // `transition` la dispara el evento, `dwell` el reloj.
                        r.class.clone(),
                        if r.locked { "si" } else { "no" }.to_owned(),
                    ]
                })
                .collect();
            output::print_table(&["id", "grupo", "regla", "clase", "bloqueada"], &rows);
            Ok(())
        }
        "presets" => {
            let client = crate::authenticated_client(options)?;
            let response = client.get_catalog().await?;
            let data = crate::response_data(response)?;
            // La matriz es `nivel -> regla -> que hace`: se muestra cuantas
            // reglas calibra cada nivel, que es lo que hace util el listado.
            let rows: Vec<Vec<String>> = data
                .presets
                .iter()
                .map(|(level, rules)| {
                    vec![
                        level.clone(),
                        rules
                            .as_object()
                            .map(|r| r.len().to_string())
                            .unwrap_or_else(|| "0".to_owned()),
                    ]
                })
                .collect();
            output::print_table(&["nivel", "reglas calibradas"], &rows);
            Ok(())
        }
        "perfil" => {
            let client = crate::authenticated_client(options)?;
            let resident_id = options.required("resident-id")?;
            let response = if let Some(at) = options.get("at") {
                client.get_preset_at(resident_id, at).await?
            } else {
                client.get_preset(resident_id).await?
            };
            let data = crate::response_data(response)?;
            let value = serde_json::to_value(&data)?;
            render::print_or_render(format, &value, render::politica::render_profile);
            Ok(())
        }
        "historial" => {
            let client = crate::authenticated_client(options)?;
            let resident_id = options.required("resident-id")?;
            let response = client.get_preset_history(resident_id).await?;
            let data = crate::response_data(response)?;
            let rows: Vec<Vec<String>> = data
                .versions
                .iter()
                .map(|v| {
                    vec![
                        v.id.clone(),
                        v.risk_level.clone(),
                        v.mobility_aid.clone(),
                        v.mode.clone(),
                        v.autopilot.to_string(),
                        v.valid_from.clone(),
                        v.valid_to.clone().unwrap_or_default(),
                    ]
                })
                .collect();
            output::print_table(
                &[
                    "id",
                    "nivel",
                    "movilidad",
                    "modo",
                    "autopilot",
                    "desde",
                    "hasta",
                ],
                &rows,
            );
            Ok(())
        }
        "actualizar" => {
            let client = crate::authenticated_client(options)?;
            let resident_id = options.required("resident-id")?;
            
            // Soporte para --body con JSON completo o opciones individuales
            if let Some(body_str) = options.get("body") {
                let body: serde_json::Value = serde_json::from_str(body_str)
                    .map_err(|e| CliError::Usage(format!("JSON invalido en --body: {e}")))?;
                
                let response = client
                    .update_preset(
                        resident_id,
                        mana_sdk::UpdatePresetRequest {
                            risk_level: body.get("risk_level").and_then(|v| v.as_str()).map(str::to_owned),
                            mobility_aid: body.get("mobility_aid").and_then(|v| v.as_str()).map(str::to_owned),
                            autopilot: body.get("autopilot").and_then(|v| v.as_bool()),
                            mode: body.get("mode").and_then(|v| v.as_str()).map(str::to_owned),
                            template_id: body.get("template_id").and_then(|v| v.as_str()).map(str::to_owned),
                            overrides: body.get("overrides").cloned(),
                            catalog_version: body.get("catalog_version").and_then(|v| v.as_str()).map(str::to_owned),
                        },
                    )
                    .await?;
                let data = crate::response_data(response)?;
                let value = serde_json::to_value(&data)?;
                render::print_or_render(format, &value, render::politica::render_profile);
                return Ok(());
            }
            
            let risk_level = options.get("risk-level").map(str::to_owned);
            let mobility_aid = options.get("mobility-aid").map(str::to_owned);
            let autopilot = options.get("autopilot").map(|v| v == "true" || v == "1");
            let mode = options.get("mode").map(str::to_owned);
            let template_id = options.get("template-id").map(str::to_owned);
            let overrides_json = options.get("overrides").map(str::to_owned);
            let response = client
                .update_preset(
                    resident_id,
                    mana_sdk::UpdatePresetRequest {
                        risk_level,
                        mobility_aid,
                        autopilot,
                        mode,
                        template_id,
                        overrides: overrides_json.and_then(|s| serde_json::from_str(&s).ok()),
                        catalog_version: None,
                    },
                )
                .await?;
            let data = crate::response_data(response)?;
            let value = serde_json::to_value(&data)?;
            render::print_or_render(format, &value, render::politica::render_profile);
            Ok(())
        }
        "autopilot" => {
            let client = crate::authenticated_client(options)?;
            let response = client.autopilot().await?;
            let data = crate::response_data(response)?;
            let rows: Vec<Vec<String>> = data
                .presets
                .iter()
                .map(|p| {
                    vec![
                        p.resident_id.clone(),
                        p.mobility_aid.clone(),
                        p.mode.clone(),
                    ]
                })
                .collect();
            output::print_table(&["residente", "movilidad", "modo"], &rows);
            Ok(())
        }
        "apply-recommendation" => {
            let client = crate::authenticated_client(options)?;
            let resident_id = options.required("resident-id")?;
            let response = client
                .apply_recommendation(
                    resident_id,
                    mana_sdk::ApplyRecommendationRequest {
                        resident_id: resident_id.to_owned(),
                        template_id: options.get("template-id").map(str::to_owned),
                        overrides: options.get("overrides-json").map(str::to_owned),
                        catalog_version: options.get("catalog-version").map(str::to_owned),
                    },
                )
                .await?;
            let data = crate::response_data(response)?;
            let value = serde_json::to_value(&data)?;
            render::print_or_render(format, &value, render::politica::render_profile);
            Ok(())
        }
        _ => Err(CliError::Usage(format!(
            "verbo desconocido para politica: {}",
            options.verb()
        ))),
    }
}
