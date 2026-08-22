use crate::{cli::CliError, output, render};

use super::super::cli::Options;

pub async fn dispatch(options: &Options) -> Result<(), CliError> {
    match options.verb() {
        "facilities" => {
            let client = crate::authenticated_client(options)?;
            let response = client.list_facilities().await?;
            let data = crate::response_data(response)?;
            let rows = data
                .facilities
                .iter()
                .map(|facility| {
                    vec![
                        facility.id.clone(),
                        facility.name.clone(),
                        facility.timezone.clone(),
                    ]
                })
                .collect::<Vec<_>>();
            output::print_table(&["id", "name", "timezone"], &rows);
            Ok(())
        }
        "facility" => {
            let client = crate::authenticated_client(options)?;
            let response = client.facility(options.required("facility-id")?).await?;
            output::print_json(&crate::response_data(response)?)?;
            Ok(())
        }
        "tree" => {
            let client = crate::authenticated_client(options)?;
            let response = client.facility_tree(options.required("facility-id")?).await?;
            let tree = crate::response_data(response)?;
            let mut lines: Vec<String> = Vec::new();
            lines.push(format!("{} ({})", tree.name, tree.id));
            lines.push(format!("  timezone: {}", tree.timezone));
            for wing in &tree.wings {
                lines.push(format!(
                    "  {} [{}] piso {}",
                    wing.name, wing.id, wing.floor
                ));
                for room in &wing.rooms {
                    lines.push(format!(
                        "    {} [{}] tipo={} camas={}",
                        room.number,
                        room.id,
                        room.room_type,
                        room.beds.len()
                    ));
                    for bed in &room.beds {
                        lines.push(format!("      {} [{}]", bed.label, bed.id));
                        if let Some(ref resident) = bed.resident {
                            lines.push(format!("        └── {} [{}]", resident.name, resident.id));
                        }
                    }
                    for stream in &room.streams {
                        lines.push(format!(
                            "      stream {} [{}] \"{}\"",
                            stream.id,
                            stream.stream_key,
                            stream.name.as_deref().unwrap_or("")
                        ));
                        for region in &stream.regions {
                            lines.push(format!(
                                "        └── {} [{}] pts={} {}",
                                region.region_type,
                                region.id,
                                region.points.len(),
                                if region.is_static { "static" } else { "dynamic" }
                            ));
                        }
                    }
                }
            }
            println!("{}", lines.join("\n"));
            Ok(())
        }
        "wings" => {
            let client = crate::authenticated_client(options)?;
            let response = client.list_wings().await?;
            let data = crate::response_data(response)?;
            let rows = data
                .wings
                .iter()
                .map(|wing| {
                    vec![
                        wing.id.clone(),
                        wing.name.clone(),
                        wing.floor.clone(),
                        wing.sort_order.to_string(),
                        wing.bed_count
                            .map(|count| count.to_string())
                            .unwrap_or_else(|| "-".to_owned()),
                    ]
                })
                .collect::<Vec<_>>();
            output::print_table(&["id", "name", "floor", "sort_order", "beds"], &rows);
            Ok(())
        }
        "rooms" => {
            let client = crate::authenticated_client(options)?;
            let response = client.list_rooms(options.required("wing-id")?).await?;
            let data = crate::response_data(response)?;
            let rows = data
                .rooms
                .iter()
                .map(|room| {
                    vec![
                        room.id.clone(),
                        room.number.clone(),
                        room.room_type.clone(),
                        room.stream_key.clone().unwrap_or_else(|| "-".to_owned()),
                    ]
                })
                .collect::<Vec<_>>();
            output::print_table(&["id", "number", "type", "stream_key"], &rows);
            Ok(())
        }
        "beds" => {
            let client = crate::authenticated_client(options)?;
            let response = client.list_beds(options.required("room-id")?).await?;
            let data = crate::response_data(response)?;
            let rows = data
                .beds
                .iter()
                .map(|bed| {
                    vec![
                        bed.id.clone(),
                        bed.label.clone(),
                        bed.monitor_key.clone().unwrap_or_else(|| "-".to_owned()),
                    ]
                })
                .collect::<Vec<_>>();
            output::print_table(&["id", "label", "monitor_key"], &rows);
            Ok(())
        }
        "camas" => {
            let client = crate::authenticated_client(options)?;
            let response = client.list_residence_beds().await?;
            let data = crate::response_data(response)?;
            let rows = data
                .beds
                .iter()
                .map(|bed| {
                    vec![
                        bed.id.clone(),
                        bed.label.clone(),
                        bed.room_number.clone(),
                        bed.wing_name.clone(),
                        bed.monitor_key.clone().unwrap_or_else(|| "-".to_owned()),
                    ]
                })
                .collect::<Vec<_>>();
            output::print_table(&["id", "label", "habitacion", "ala", "monitor"], &rows);
            Ok(())
        }
        "planograma" => {
            let client = crate::authenticated_client(options)?;
            let response = client.planogram(options.required("wing-id")?).await?;
            let data = crate::response_data(response)?;
            if options.has("json") {
                output::print_json(&data)?;
            } else {
                print!("{}", render::planogram::render(&data.placements));
            }
            Ok(())
        }
        "privacidad" => {
            let client = crate::authenticated_client(options)?;
            let response = client.privacy_regions(options.required("room-id")?).await?;
            let data = crate::response_data(response)?;
            let rows = data
                .regions
                .iter()
                .map(|region| {
                    vec![
                        region.x.to_string(),
                        region.y.to_string(),
                        region.w.to_string(),
                        region.h.to_string(),
                    ]
                })
                .collect::<Vec<_>>();
            output::print_table(&["x", "y", "w", "h"], &rows);
            Ok(())
        }
        "create-facility" => {
            let client = crate::authenticated_client(options)?;
            let response = client
                .create_facility(mana_sdk::CreateFacilityRequest {
                    name: options.required("name")?.to_owned(),
                    timezone: options.required("timezone")?.to_owned(),
                })
                .await?;
            output::print_json(&crate::response_data(response)?)?;
            Ok(())
        }
        "update-facility" => {
            let client = crate::authenticated_client(options)?;
            let response = client
                .update_facility(
                    options.required("facility-id")?,
                    mana_sdk::UpdateFacilityRequest {
                        name: options.get("name").map(str::to_owned),
                        timezone: options.get("timezone").map(str::to_owned),
                    },
                )
                .await?;
            output::print_json(&crate::response_data(response)?)?;
            Ok(())
        }
        "create-wing" => {
            let client = crate::authenticated_client(options)?;
            let response = client
                .create_wing(
                    options.required("facility-id")?,
                    mana_sdk::CreateWingRequest {
                        name: options.required("name")?.to_owned(),
                        floor: options.required("floor")?.to_owned(),
                        sort_order: options.get("sort-order").and_then(|v| v.parse().ok()),
                    },
                )
                .await?;
            output::print_json(&crate::response_data(response)?)?;
            Ok(())
        }
        "update-wing" => {
            let wing_id = options.required("wing-id")?;
            let client = crate::authenticated_client(options)?;
            let response = client
                .update_wing(
                    wing_id,
                    mana_sdk::UpdateWingRequest {
                        name: options.get("name").map(str::to_owned),
                        floor: options.get("floor").map(str::to_owned),
                        sort_order: options.get("sort-order").and_then(|v| v.parse().ok()),
                    },
                )
                .await?;
            output::print_json(&crate::response_data(response)?)?;
            Ok(())
        }
        "create-room" => {
            let client = crate::authenticated_client(options)?;
            let response = client
                .create_room(
                    options.required("wing-id")?,
                    mana_sdk::CreateRoomRequest {
                        number: options.required("number")?.to_owned(),
                        room_type: options.get("type").map(str::to_owned),
                        stream_key: options.get("stream-key").map(str::to_owned),
                    },
                )
                .await?;
            output::print_json(&crate::response_data(response)?)?;
            Ok(())
        }
        "update-room" => {
            let client = crate::authenticated_client(options)?;
            let response = client
                .update_room(
                    options.required("room-id")?,
                    mana_sdk::UpdateRoomRequest {
                        number: options.get("number").map(str::to_owned),
                        room_type: options.get("type").map(str::to_owned),
                        stream_key: options
                            .get("stream-key")
                            .map(|v| Some(v.to_owned())),
                    },
                )
                .await?;
            output::print_json(&crate::response_data(response)?)?;
            Ok(())
        }
        "create-bed" => {
            let client = crate::authenticated_client(options)?;
            let response = client
                .create_bed(
                    options.required("room-id")?,
                    mana_sdk::CreateBedRequest {
                        label: options.required("label")?.to_owned(),
                        monitor_key: options.get("monitor-key").map(str::to_owned),
                    },
                )
                .await?;
            output::print_json(&crate::response_data(response)?)?;
            Ok(())
        }
        "update-bed" => {
            let client = crate::authenticated_client(options)?;
            let response = client
                .update_bed(
                    options.required("bed-id")?,
                    mana_sdk::UpdateBedRequest {
                        label: options.get("label").map(str::to_owned),
                        monitor_key: options
                            .get("monitor-key")
                            .map(|v| Some(v.to_owned())),
                    },
                )
                .await?;
            output::print_json(&crate::response_data(response)?)?;
            Ok(())
        }
        "save-planogram" => {
            let client = crate::authenticated_client(options)?;
            let request: mana_sdk::SavePlanogramRequest = parse_body(options)?;
            let response = client
                .save_planogram(options.required("wing-id")?, request)
                .await?;
            output::print_json(&crate::response_data(response)?)?;
            Ok(())
        }
        "save-privacy" => {
            let client = crate::authenticated_client(options)?;
            let request: mana_sdk::SavePrivacyRegionsRequest = parse_body(options)?;
            let response = client
                .save_privacy_regions(options.required("room-id")?, request)
                .await?;
            output::print_json(&crate::response_data(response)?)?;
            Ok(())
        }
        _ => Err(CliError::Usage(format!(
            "verbo desconocido para residencia: {}\n\n{}",
            options.verb(),
            crate::cli::usage()
        ))),
    }
}

fn parse_body<T: serde::de::DeserializeOwned>(options: &Options) -> Result<T, CliError> {
    let raw = options.required("body")?;
    serde_json::from_str(raw).map_err(|error| CliError::Usage(format!("body JSON invalido: {error}")))
}
