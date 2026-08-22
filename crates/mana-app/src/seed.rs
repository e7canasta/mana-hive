//! Siembra demo del hub.
//!
//! Hasta F8 la residencia demo la sembraba `scripts/seed.mjs` en el SQLite de
//! Node. Con Node retirado esa base no alimenta a nadie, asi que la siembra
//! tiene que vivir aca o `pnpm dev:all` levanta un hub con dos usuarios y sin
//! residencia.
//!
//! Es idempotente por diseño: arrancar dos veces sobre la misma base no
//! duplica, porque `MANA_HUB_SEED_DEMO` es una variable de entorno y nadie
//! recuerda si ya corrio.

use ctx_cobertura::{CoverageInput, MembershipInput, ShiftInput, StaffGroupInput};
use ctx_poblacion::{BedRef, ResidentInput};
use ctx_residencia::{BedInput, FacilityInput, RoomInput, WingInput};
use mana_kernel::Instante;

use crate::{error::AppFailure, state::AppState};

struct BedSpec {
    label: &'static str,
    monitor_key: &'static str,
    resident: Option<&'static str>,
}

struct RoomSpec {
    number: &'static str,
    stream_key: &'static str,
    beds: &'static [BedSpec],
}

struct WingSpec {
    name: &'static str,
    floor: &'static str,
    rooms: &'static [RoomSpec],
}

/// Una residencia chica pero completa: dos alas, habitaciones simples y
/// compartidas, y una cama **sin ocupante a proposito** para que el board tenga
/// el caso que mas se olvida al probar.
const WINGS: &[WingSpec] = &[
    WingSpec {
        name: "Ala Norte",
        floor: "1",
        rooms: &[
            RoomSpec {
                number: "101",
                stream_key: "home-101",
                beds: &[BedSpec {
                    label: "101-A",
                    monitor_key: "mana-camera-101",
                    resident: Some("Elena Ferrari"),
                }],
            },
            RoomSpec {
                number: "102",
                stream_key: "home-102",
                beds: &[
                    BedSpec {
                        label: "102-A",
                        monitor_key: "mana-camera-102a",
                        resident: Some("Hector Mendez"),
                    },
                    BedSpec {
                        label: "102-B",
                        monitor_key: "mana-camera-102b",
                        resident: None,
                    },
                ],
            },
        ],
    },
    WingSpec {
        name: "Ala Sur",
        floor: "1",
        rooms: &[RoomSpec {
            number: "118",
            stream_key: "home-118",
            beds: &[BedSpec {
                label: "118-A",
                monitor_key: "mana-camera-118",
                resident: Some("Rosa Silva"),
            }],
        }],
    },
];

impl AppState {
    /// Siembra la residencia demo. No hace nada si ya hay una.
    pub fn seed_demo_residence(&self) -> Result<(), AppFailure> {
        if !self.residence.list_facilities()?.is_empty() {
            return Ok(());
        }
        let now = Instante::now();
        let mut wing_ids = Vec::new();

        let facility = self.residence.create_facility(
            FacilityInput {
                name: "Residencia Demo".to_owned(),
                timezone: "America/Argentina/Buenos_Aires".to_owned(),
            },
            now,
        )?;

        for (index, wing_spec) in WINGS.iter().enumerate() {
            let wing = self.residence.create_wing(
                WingInput {
                    facility_id: facility.id.clone(),
                    name: wing_spec.name.to_owned(),
                    floor: wing_spec.floor.to_owned(),
                    sort_order: index as i32,
                },
                now,
            )?;

            for room_spec in wing_spec.rooms {
                let room = self.residence.create_room(
                    RoomInput {
                        wing_id: wing.id.clone(),
                        number: room_spec.number.to_owned(),
                        room_type: "standard".to_owned(),
                        stream_key: Some(room_spec.stream_key.to_owned()),
                    },
                    now,
                )?;

                for bed_spec in room_spec.beds {
                    let bed = self.residence.create_bed(
                        BedInput {
                            room_id: room.id.clone(),
                            label: bed_spec.label.to_owned(),
                            monitor_key: Some(bed_spec.monitor_key.to_owned()),
                        },
                        now,
                    )?;

                    let Some(full_name) = bed_spec.resident else {
                        continue;
                    };
                    let resident = self.poblacion.create_resident(
                        ResidentInput {
                            full_name: full_name.to_owned(),
                            external_id: None,
                            birth_date: None,
                            admission_date: Some("2025-01-15".to_owned()),
                        },
                        now,
                    )?;
                    let bed_ref = BedRef::new(bed.id.as_str())
                        .map_err(|error| AppFailure::validation(error.to_string(), None))?;
                    self.poblacion.assign(&resident.id, &bed_ref, now, None)?;
                }
            }

            wing_ids.push(wing.id.as_str().to_owned());
        }

        // La grilla laboral es de la residencia. `day`/`night` de la politica
        // clinica son otro eje y no se mezclan con estos turnos.
        self.cobertura.replace_grid(
            facility.id.as_str(),
            vec![
                ShiftInput {
                    key: "day".to_owned(),
                    label: "Mañana".to_owned(),
                    start_minute: 7 * 60,
                },
                ShiftInput {
                    key: "afternoon".to_owned(),
                    label: "Tarde".to_owned(),
                    start_minute: 15 * 60,
                },
                ShiftInput {
                    key: "night".to_owned(),
                    label: "Noche".to_owned(),
                    start_minute: 23 * 60,
                },
            ],
            now,
        )?;

        let group = self.cobertura.create_group(
            StaffGroupInput {
                facility_id: facility.id.as_str().to_owned(),
                name: "Equipo Demo".to_owned(),
            },
            now,
        )?;
        self.cobertura.replace_members(
            &group.id,
            vec![
                MembershipInput {
                    user_id: "user-gaston".to_owned(),
                    valid_from: now,
                },
                MembershipInput {
                    user_id: "user-staff".to_owned(),
                    valid_from: now,
                },
            ],
            now,
        )?;

        for wing_id in &wing_ids {
            for shift in ["day", "afternoon", "night"] {
                self.cobertura.assign_coverage(
                    CoverageInput {
                        wing_id: wing_id.clone(),
                        staff_group_id: Some(group.id.as_str().to_owned()),
                        shift_key: shift.to_owned(),
                    },
                    now,
                    None,
                )?;
            }
        }

        Ok(())
    }
}
