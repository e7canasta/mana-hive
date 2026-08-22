use std::collections::HashMap;

use diesel::prelude::*;
use diesel::SqliteConnection;

use crate::estructura::sqlite::{BedRow, WingRow};
use crate::estructura::{Bed, Wing, WingId};
use crate::schema::{beds, facilities, rooms, wings};
use crate::ResidenceError;

use super::{ResidenceBed, WingOverview};

type BedWithLocationRow = (
    BedRow,
    String,
    String,
    Option<String>,
    String,
    String,
    String,
);

pub(crate) fn list_wings_overview(
    connection: &mut SqliteConnection,
) -> Result<Vec<WingOverview>, ResidenceError> {
    let wings: Vec<Wing> = wings::table
        .filter(wings::retired_at.is_null())
        .filter(
            wings::facility_id.eq_any(
                facilities::table
                    .filter(facilities::retired_at.is_null())
                    .select(facilities::id),
            ),
        )
        .select(WingRow::as_select())
        .order((wings::sort_order.asc(), wings::id.asc()))
        .load::<WingRow>(connection)
        .map_err(ResidenceError::database)?
        .into_iter()
        .map(Wing::try_from)
        .collect::<Result<Vec<_>, _>>()?;
    let counts: HashMap<String, i64> = beds::table
        .inner_join(rooms::table.on(rooms::id.eq(beds::room_id)))
        .filter(beds::retired_at.is_null())
        .filter(rooms::retired_at.is_null())
        .group_by(rooms::wing_id)
        .select((rooms::wing_id, diesel::dsl::count(beds::id)))
        .load::<(String, i64)>(connection)
        .map_err(ResidenceError::database)?
        .into_iter()
        .collect();
    Ok(wings
        .into_iter()
        .map(|wing| {
            let bed_count = counts.get(wing.id.as_str()).copied().unwrap_or(0);
            (wing, bed_count)
        })
        .collect())
}

pub(crate) fn list_beds_all(
    connection: &mut SqliteConnection,
) -> Result<Vec<ResidenceBed>, ResidenceError> {
    let rows: Vec<BedWithLocationRow> = beds::table
        .inner_join(rooms::table.on(rooms::id.eq(beds::room_id)))
        .inner_join(wings::table.on(wings::id.eq(rooms::wing_id)))
        .filter(beds::retired_at.is_null())
        .filter(rooms::retired_at.is_null())
        .filter(wings::retired_at.is_null())
        .filter(
            wings::facility_id.eq_any(
                facilities::table
                    .filter(facilities::retired_at.is_null())
                    .select(facilities::id),
            ),
        )
        .select((
            BedRow::as_select(),
            rooms::number,
            rooms::room_type,
            rooms::stream_key,
            wings::id,
            wings::name,
            wings::floor,
        ))
        .order((
            wings::sort_order.asc(),
            wings::id.asc(),
            rooms::number.asc(),
            beds::label.asc(),
            beds::id.asc(),
        ))
        .load::<BedWithLocationRow>(connection)
        .map_err(ResidenceError::database)?;
    rows.into_iter()
        .map(
            |(row, number, room_type, stream_key, wing_id, wing_name, wing_floor)| {
                Bed::try_from(row).map(|bed| ResidenceBed {
                    bed,
                    room_number: number,
                    room_type,
                    stream_key,
                    wing_id: WingId::new(wing_id),
                    wing_name,
                    wing_floor,
                })
            },
        )
        .collect()
}

#[cfg(test)]
mod tests {
    use crate::estructura::{BedInput, FacilityInput, RoomInput, WingInput};
    use crate::testsupport::{instant, store};

    #[test]
    fn overviews_carry_room_and_wing_location() {
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
        let bed = store
            .create_bed(
                BedInput {
                    room_id: room.id,
                    label: "Cama 1".to_owned(),
                    monitor_key: None,
                },
                instant(),
            )
            .unwrap();

        let overview = store.list_wings_overview().unwrap();
        assert_eq!(overview.len(), 1);
        assert_eq!(overview[0].0.id, wing.id);
        assert_eq!(overview[0].1, 1);

        let beds = store.list_beds_all().unwrap();
        assert_eq!(beds.len(), 1);
        assert_eq!(beds[0].bed.id, bed.id);
        assert_eq!(beds[0].room_number, "118");
        assert_eq!(beds[0].wing_name, "Ala Norte");
        assert_eq!(beds[0].wing_floor, "1");
        assert_eq!(beds[0].stream_key.as_deref(), Some("stream-118"));
    }
}
