use diesel::prelude::*;
use diesel::SqliteConnection;
use mana_kernel::Instante;

use crate::common::{parse_instant, random_id};
use crate::estructura::sqlite::ensure_room_active;
use crate::estructura::RoomId;
use crate::schema::room_privacy_regions;
use crate::ResidenceError;

use super::repo::PrivacidadRepo;
use super::{PrivacidadError, PrivacyRegion, PrivacyRegionInput, MAX_PRIVACY_REGIONS};

#[derive(Queryable, Selectable)]
#[diesel(table_name = room_privacy_regions)]
#[diesel(check_for_backend(diesel::sqlite::Sqlite))]
#[allow(dead_code)]
struct PrivacyRegionRow {
    id: String,
    room_id: String,
    x: f64,
    y: f64,
    w: f64,
    h: f64,
    active: i32,
    created_at: String,
    updated_at: String,
}

#[derive(Insertable)]
#[diesel(table_name = room_privacy_regions)]
struct NewPrivacyRegionRow<'a> {
    id: &'a str,
    room_id: &'a str,
    x: f64,
    y: f64,
    w: f64,
    h: f64,
    active: i32,
    created_at: &'a str,
    updated_at: &'a str,
}

impl PrivacidadRepo for SqliteConnection {
    fn privacy_regions(
        connection: &mut SqliteConnection,
        room_id: &RoomId,
    ) -> Result<Vec<PrivacyRegion>, ResidenceError> {
        ensure_room_active(connection, room_id)?;
        privacy_regions_query(connection, room_id)
    }

    fn save_privacy_regions_in_transaction(
        connection: &mut SqliteConnection,
        room_id: &RoomId,
        inputs: Vec<PrivacyRegionInput>,
        now: Instante,
    ) -> Result<Vec<PrivacyRegion>, ResidenceError> {
        ensure_room_active(connection, room_id)?;
        if inputs.len() > MAX_PRIVACY_REGIONS {
            return Err(ResidenceError::Privacidad(
                PrivacidadError::TooManyPrivacyRegions {
                    max: MAX_PRIVACY_REGIONS,
                },
            ));
        }
        for input in &inputs {
            input.validate()?;
        }
        diesel::update(
            room_privacy_regions::table.filter(room_privacy_regions::room_id.eq(room_id.as_str())),
        )
        .set(room_privacy_regions::active.eq(0))
        .execute(connection)
        .map_err(ResidenceError::database)?;
        let created_at = now.to_string();
        for input in inputs {
            let region = PrivacyRegion::create(random_id("privacy"), room_id.clone(), input, now)?;
            diesel::insert_into(room_privacy_regions::table)
                .values(NewPrivacyRegionRow {
                    id: &region.id,
                    room_id: region.room_id.as_str(),
                    x: region.x,
                    y: region.y,
                    w: region.w,
                    h: region.h,
                    active: 1,
                    created_at: &created_at,
                    updated_at: &created_at,
                })
                .execute(connection)
                .map_err(ResidenceError::database)?;
        }
        privacy_regions_query(connection, room_id)
    }
}

fn privacy_regions_query(
    connection: &mut SqliteConnection,
    room_id: &RoomId,
) -> Result<Vec<PrivacyRegion>, ResidenceError> {
    let rows = room_privacy_regions::table
        .filter(room_privacy_regions::room_id.eq(room_id.as_str()))
        .filter(room_privacy_regions::active.eq(1))
        .select(PrivacyRegionRow::as_select())
        .order(room_privacy_regions::id.asc())
        .load::<PrivacyRegionRow>(connection)
        .map_err(ResidenceError::database)?;
    rows.into_iter()
        .map(|row| {
            Ok(PrivacyRegion {
                id: row.id,
                room_id: RoomId::new(row.room_id),
                x: row.x,
                y: row.y,
                w: row.w,
                h: row.h,
                created_at: parse_instant("created_at", row.created_at)?,
                updated_at: parse_instant("updated_at", row.updated_at)?,
            })
        })
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::estructura::{FacilityInput, RoomInput, WingInput};
    use crate::testsupport::{instant, store};
    use crate::ResidenceError;

    #[test]
    fn saves_and_reads_privacy_regions_for_a_room() {
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

        let regions = store
            .save_privacy_regions(
                &room.id,
                vec![PrivacyRegionInput {
                    x: 0.35,
                    y: 0.2,
                    w: 0.3,
                    h: 0.6,
                }],
                instant(),
            )
            .unwrap();
        assert_eq!(regions.len(), 1);
        assert_eq!(regions[0].w, 0.3);
        assert_eq!(store.privacy_regions(&room.id).unwrap().len(), 1);

        let replaced = store
            .save_privacy_regions(
                &room.id,
                vec![PrivacyRegionInput {
                    x: 0.1,
                    y: 0.1,
                    w: 0.2,
                    h: 0.2,
                }],
                instant(),
            )
            .unwrap();
        assert_eq!(replaced.len(), 1);
        assert_eq!(replaced[0].x, 0.1);
    }

    #[test]
    fn rejects_unknown_rooms_invalid_regions_and_too_many() {
        let store = store();
        assert!(matches!(
            store.save_privacy_regions(
                &RoomId::new("room-unknown"),
                vec![PrivacyRegionInput {
                    x: 0.1,
                    y: 0.1,
                    w: 0.2,
                    h: 0.2,
                }],
                instant(),
            ),
            Err(ResidenceError::NotFound)
        ));
        assert!(matches!(
            store.save_privacy_regions(
                &RoomId::new("room-unknown"),
                vec![PrivacyRegionInput {
                    x: 0.9,
                    y: 0.9,
                    w: 0.5,
                    h: 0.5,
                }],
                instant(),
            ),
            Err(ResidenceError::NotFound)
        ));

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
                    wing_id: wing.id,
                    number: "118".to_owned(),
                    room_type: "single".to_owned(),
                    stream_key: None,
                },
                instant(),
            )
            .unwrap();

        assert!(matches!(
            store.save_privacy_regions(
                &room.id,
                vec![PrivacyRegionInput {
                    x: 0.9,
                    y: 0.9,
                    w: 0.5,
                    h: 0.5,
                }],
                instant(),
            ),
            Err(ResidenceError::Privacidad(
                PrivacidadError::InvalidPrivacyRegion
            ))
        ));
        assert!(matches!(
            store.save_privacy_regions(
                &room.id,
                (0..9)
                    .map(|index| PrivacyRegionInput {
                        x: 0.1 * index as f64,
                        y: 0.1,
                        w: 0.05,
                        h: 0.05,
                    })
                    .collect(),
                instant(),
            ),
            Err(ResidenceError::Privacidad(
                PrivacidadError::TooManyPrivacyRegions { max: 8 }
            ))
        ));
    }
}
