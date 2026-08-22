use std::collections::HashSet;

use diesel::prelude::*;
use diesel::SqliteConnection;
use mana_kernel::Instante;

use crate::common::random_id;
use crate::estructura::sqlite::ensure_wing_active;
use crate::estructura::{RoomId, WingId};
use crate::schema::{planogram_placements, rooms};
use crate::ResidenceError;

use super::repo::PlanogramaRepo;
use super::{PlanogramEntry, PlanogramPlacementInput};

#[derive(Queryable, Selectable)]
#[diesel(table_name = planogram_placements)]
#[diesel(check_for_backend(diesel::sqlite::Sqlite))]
#[allow(dead_code)]
struct PlanogramRow {
    id: String,
    wing_id: String,
    room_id: String,
    x: f64,
    y: f64,
    sort_order: i32,
    active: i32,
    created_at: String,
    updated_at: String,
}

#[derive(Insertable)]
#[diesel(table_name = planogram_placements)]
struct NewPlanogramRow<'a> {
    id: &'a str,
    wing_id: &'a str,
    room_id: &'a str,
    x: f64,
    y: f64,
    sort_order: i32,
    active: i32,
    created_at: &'a str,
    updated_at: &'a str,
}

impl PlanogramaRepo for SqliteConnection {
    fn planogram(
        connection: &mut SqliteConnection,
        wing_id: &WingId,
    ) -> Result<Vec<PlanogramEntry>, ResidenceError> {
        ensure_wing_active(connection, wing_id)?;
        planogram_query(connection, wing_id)
    }

    fn save_planogram_in_transaction(
        connection: &mut SqliteConnection,
        wing_id: &WingId,
        inputs: Vec<PlanogramPlacementInput>,
        now: Instante,
    ) -> Result<Vec<PlanogramEntry>, ResidenceError> {
        ensure_wing_active(connection, wing_id)?;
        let mut seen: HashSet<String> = HashSet::new();
        for input in &inputs {
            input.validate()?;
            let room_id = input.room_id.as_str().to_owned();
            if !seen.insert(room_id.clone()) {
                return Err(ResidenceError::DuplicatePlanogramRoom { room_id });
            }
        }
        if !inputs.is_empty() {
            let room_ids: Vec<String> = inputs
                .iter()
                .map(|input| input.room_id.as_str().to_owned())
                .collect();
            let found: HashSet<String> = rooms::table
                .filter(rooms::wing_id.eq(wing_id.as_str()))
                .filter(rooms::retired_at.is_null())
                .filter(rooms::id.eq_any(room_ids))
                .select(rooms::id)
                .load::<String>(connection)
                .map_err(ResidenceError::database)?
                .into_iter()
                .collect();
            for input in &inputs {
                let room_id = input.room_id.as_str().to_owned();
                if !found.contains(&room_id) {
                    return Err(ResidenceError::RoomNotFound { room_id });
                }
            }
        }
        diesel::update(
            planogram_placements::table.filter(planogram_placements::wing_id.eq(wing_id.as_str())),
        )
        .set(planogram_placements::active.eq(0))
        .execute(connection)
        .map_err(ResidenceError::database)?;
        let created_at = now.to_string();
        for input in inputs {
            diesel::insert_into(planogram_placements::table)
                .values(NewPlanogramRow {
                    id: &random_id("placement"),
                    wing_id: wing_id.as_str(),
                    room_id: input.room_id.as_str(),
                    x: input.x,
                    y: input.y,
                    sort_order: input.sort_order,
                    active: 1,
                    created_at: &created_at,
                    updated_at: &created_at,
                })
                .execute(connection)
                .map_err(ResidenceError::database)?;
        }
        planogram_query(connection, wing_id)
    }
}

fn planogram_query(
    connection: &mut SqliteConnection,
    wing_id: &WingId,
) -> Result<Vec<PlanogramEntry>, ResidenceError> {
    let rows: Vec<(PlanogramRow, String, String, Option<String>)> = planogram_placements::table
        .inner_join(rooms::table.on(rooms::id.eq(planogram_placements::room_id)))
        .filter(planogram_placements::wing_id.eq(wing_id.as_str()))
        .filter(planogram_placements::active.eq(1))
        .select((
            PlanogramRow::as_select(),
            rooms::number,
            rooms::room_type,
            rooms::stream_key,
        ))
        .order((
            planogram_placements::sort_order.asc(),
            rooms::number.asc(),
            planogram_placements::id.asc(),
        ))
        .load::<(PlanogramRow, String, String, Option<String>)>(connection)
        .map_err(ResidenceError::database)?;
    Ok(rows
        .into_iter()
        .map(|(row, room_number, room_type, stream_key)| PlanogramEntry {
            id: row.id,
            wing_id: WingId::new(row.wing_id),
            room_id: RoomId::new(row.room_id),
            x: row.x,
            y: row.y,
            sort_order: row.sort_order,
            room_number,
            room_type,
            stream_key,
        })
        .collect())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::estructura::{FacilityInput, RoomInput, WingInput};
    use crate::testsupport::{instant, store};
    use crate::ResidenceError;

    #[test]
    fn saves_and_reads_the_active_planogram_version() {
        let store = store();
        let facility = store
            .create_facility(
                FacilityInput {
                    name: "Manantial".to_owned(),
                    timezone: "UTC".to_owned(),
                },
                instant(),
            )
            .unwrap();
        let wing = store
            .create_wing(
                WingInput {
                    facility_id: facility.id,
                    name: "Ala Norte".to_owned(),
                    floor: "1".to_owned(),
                    sort_order: 0,
                },
                instant(),
            )
            .unwrap();
        let room = store
            .create_room(
                RoomInput {
                    wing_id: wing.id.clone(),
                    number: "118".to_owned(),
                    room_type: "single".to_owned(),
                    stream_key: Some("stream-118".to_owned()),
                },
                instant(),
            )
            .unwrap();

        let saved = store
            .save_planogram(
                &wing.id,
                vec![PlanogramPlacementInput {
                    room_id: room.id.clone(),
                    x: 0.5,
                    y: 0.25,
                    sort_order: 0,
                }],
                instant(),
            )
            .unwrap();
        assert_eq!(saved.len(), 1);
        assert_eq!(saved[0].room_number, "118");
        assert_eq!(saved[0].x, 0.5);
        assert_eq!(store.planogram(&wing.id).unwrap().len(), 1);

        let replaced = store
            .save_planogram(
                &wing.id,
                vec![PlanogramPlacementInput {
                    room_id: room.id,
                    x: 0.75,
                    y: 0.5,
                    sort_order: 1,
                }],
                instant(),
            )
            .unwrap();
        assert_eq!(replaced.len(), 1);
        assert_eq!(replaced[0].x, 0.75);
    }

    #[test]
    fn rejects_duplicate_and_foreign_rooms() {
        let store = store();
        let facility = store
            .create_facility(
                FacilityInput {
                    name: "Manantial".to_owned(),
                    timezone: "UTC".to_owned(),
                },
                instant(),
            )
            .unwrap();
        let wing = store
            .create_wing(
                WingInput {
                    facility_id: facility.id.clone(),
                    name: "Ala Norte".to_owned(),
                    floor: "1".to_owned(),
                    sort_order: 0,
                },
                instant(),
            )
            .unwrap();
        let other_wing = store
            .create_wing(
                WingInput {
                    facility_id: facility.id,
                    name: "Ala Sur".to_owned(),
                    floor: "1".to_owned(),
                    sort_order: 1,
                },
                instant(),
            )
            .unwrap();
        let other_room = store
            .create_room(
                RoomInput {
                    wing_id: other_wing.id,
                    number: "201".to_owned(),
                    room_type: "single".to_owned(),
                    stream_key: None,
                },
                instant(),
            )
            .unwrap();

        assert!(matches!(
            store.save_planogram(
                &wing.id,
                vec![
                    PlanogramPlacementInput {
                        room_id: RoomId::new("room-a"),
                        x: 0.1,
                        y: 0.1,
                        sort_order: 0,
                    },
                    PlanogramPlacementInput {
                        room_id: RoomId::new("room-a"),
                        x: 0.2,
                        y: 0.2,
                        sort_order: 1,
                    },
                ],
                instant(),
            ),
            Err(ResidenceError::DuplicatePlanogramRoom { .. })
        ));
        assert!(matches!(
            store.save_planogram(
                &wing.id,
                vec![PlanogramPlacementInput {
                    room_id: other_room.id,
                    x: 0.1,
                    y: 0.1,
                    sort_order: 0,
                }],
                instant(),
            ),
            Err(ResidenceError::RoomNotFound { .. })
        ));
    }

    #[test]
    fn rejects_invalid_coordinates() {
        let store = store();
        let facility = store
            .create_facility(
                FacilityInput {
                    name: "Manantial".to_owned(),
                    timezone: "UTC".to_owned(),
                },
                instant(),
            )
            .unwrap();
        let wing = store
            .create_wing(
                WingInput {
                    facility_id: facility.id,
                    name: "Ala Norte".to_owned(),
                    floor: "1".to_owned(),
                    sort_order: 0,
                },
                instant(),
            )
            .unwrap();
        let room = store
            .create_room(
                RoomInput {
                    wing_id: wing.id.clone(),
                    number: "118".to_owned(),
                    room_type: "single".to_owned(),
                    stream_key: None,
                },
                instant(),
            )
            .unwrap();
        assert!(matches!(
            store.save_planogram(
                &wing.id,
                vec![PlanogramPlacementInput {
                    room_id: room.id,
                    x: f64::NAN,
                    y: 0.1,
                    sort_order: 0,
                }],
                instant(),
            ),
            Err(ResidenceError::Planograma(
                crate::planograma::PlanogramaError::InvalidPlanogramCoordinate
            ))
        ));
    }

    #[test]
    fn facade_composes_planogram_with_an_open_transaction() {
        let store = store();
        let facility = store
            .create_facility(
                FacilityInput {
                    name: "Manantial".to_owned(),
                    timezone: "UTC".to_owned(),
                },
                instant(),
            )
            .unwrap();
        let wing = store
            .create_wing(
                WingInput {
                    facility_id: facility.id,
                    name: "Ala Norte".to_owned(),
                    floor: "1".to_owned(),
                    sort_order: 0,
                },
                instant(),
            )
            .unwrap();
        let mut connection = store.pool.get().unwrap();
        let room = store
            .create_room_in_transaction(
                &mut connection,
                crate::estructura::new_room_id(),
                RoomInput {
                    wing_id: wing.id.clone(),
                    number: "118".to_owned(),
                    room_type: "single".to_owned(),
                    stream_key: None,
                },
                instant(),
            )
            .unwrap();
        let saved = store
            .save_planogram_in_transaction(
                &mut connection,
                &wing.id,
                vec![PlanogramPlacementInput {
                    room_id: room.id,
                    x: 0.5,
                    y: 0.5,
                    sort_order: 0,
                }],
                instant(),
            )
            .unwrap();
        drop(connection);
        assert_eq!(saved.len(), 1);
        assert_eq!(store.planogram(&wing.id).unwrap()[0].room_number, "118");
    }
}
