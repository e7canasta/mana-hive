use crate::{cli::CliError, output};

use super::super::cli::Options;

pub async fn dispatch(options: &Options) -> Result<(), CliError> {
    match options.verb() {
        "list" => {
            let client = crate::authenticated_client(options)?;
            let response = client.list_streams(options.required("room-id")?).await?;
            let data = crate::response_data(response)?;
            let rows = data
                .streams
                .iter()
                .map(|stream| {
                    vec![
                        stream.id.clone(),
                        stream.stream_key.clone(),
                        stream.name.clone().unwrap_or_default(),
                    ]
                })
                .collect::<Vec<_>>();
            output::print_table(&["id", "stream_key", "name"], &rows);
            Ok(())
        }
        "create" => {
            let client = crate::authenticated_client(options)?;
            let request = mana_sdk::CreateStreamRequest {
                stream_key: options.get("stream-key").map(|s| s.to_owned()),
                name: options.get("name").map(|s| s.to_owned()),
            };
            let response = client
                .create_stream(options.required("room-id")?, request)
                .await?;
            output::print_json(&crate::response_data(response)?)?;
            Ok(())
        }
        "get" => {
            let client = crate::authenticated_client(options)?;
            let response = client.get_stream(options.required("stream-id")?).await?;
            output::print_json(&crate::response_data(response)?)?;
            Ok(())
        }
        "regions" => {
            let client = crate::authenticated_client(options)?;
            let response = client.list_regions(options.required("stream-id")?).await?;
            let data = crate::response_data(response)?;
            let rows = data
                .regions
                .iter()
                .map(|region| {
                    vec![
                        region.id.clone(),
                        region.region_type.clone(),
                        format!("{}", region.points.len()),
                        region.label.clone().unwrap_or_default(),
                        if region.is_static { "static" } else { "dynamic" }.to_owned(),
                    ]
                })
                .collect::<Vec<_>>();
            output::print_table(&["id", "type", "points", "label", "kind"], &rows);
            Ok(())
        }
        "set-regions" => {
            let client = crate::authenticated_client(options)?;
            let body = options.required("body")?;
            let regions: Vec<mana_sdk::RegionRequest> = serde_json::from_str(body)
                .map_err(|e| CliError::Usage(format!("JSON invalido: {e}")))?;
            let request = mana_sdk::ReplaceRegionsRequest { regions };
            let response = client
                .replace_regions(options.required("stream-id")?, request)
                .await?;
            output::print_json(&crate::response_data(response)?)?;
            Ok(())
        }
        "update-region" => {
            let client = crate::authenticated_client(options)?;
            let points_str = options.required("points")?;
            let points: Vec<(f64, f64)> = serde_json::from_str(points_str)
                .map_err(|e| CliError::Usage(format!("JSON invalido en points: {e}")))?;
            let request = mana_sdk::UpdateRegionRequest { points };
            let response = client
                .update_region(
                    options.required("stream-id")?,
                    options.required("region-id")?,
                    request,
                )
                .await?;
            output::print_json(&crate::response_data(response)?)?;
            Ok(())
        }
        other => Err(CliError::Usage(format!(
            "verb desconocido: {other}\n\n{}",
            crate::cli::usage()
        ))),
    }
}
