use diesel::prelude::*;
use diesel::{OptionalExtension, SqliteConnection};
use mana_kernel::{Actor, Id, Instante};

use crate::common::{parse_instant, stored_domain};
use crate::schema::{beds, facilities, rooms, wings};
use crate::ResidenceError;

use super::repo::EstructuraRepo;
use super::{
    Bed, BedId, BedInput, BedUpdate, Facility, FacilityId, FacilityInput, FacilityTree,
    FacilityUpdate, MonitorKey, Room, RoomId, RoomInput, RoomUpdate, StreamKey, Wing, WingId,
    WingInput, WingUpdate,
};

#[derive(Queryable, Selectable)]
#[diesel(table_name = facilities)]
#[diesel(check_for_backend(diesel::sqlite::Sqlite))]
pub(crate) struct FacilityRow {
    pub(crate) id: String,
    pub(crate) name: String,
    pub(crate) timezone: String,
    pub(crate) retired_at: Option<String>,
    pub(crate) retired_by: Option<String>,
    pub(crate) created_at: String,
    pub(crate) updated_at: String,
}

#[derive(Queryable, Selectable)]
#[diesel(table_name = wings)]
#[diesel(check_for_backend(diesel::sqlite::Sqlite))]
pub(crate) struct WingRow {
    pub(crate) id: String,
    pub(crate) facility_id: String,
    pub(crate) name: String,
    pub(crate) floor: String,
    pub(crate) sort_order: i32,
    pub(crate) retired_at: Option<String>,
    pub(crate) retired_by: Option<String>,
    pub(crate) created_at: String,
    pub(crate) updated_at: String,
}

#[derive(Queryable, Selectable)]
#[diesel(table_name = rooms)]
#[diesel(check_for_backend(diesel::sqlite::Sqlite))]
pub(crate) struct RoomRow {
    pub(crate) id: String,
    pub(crate) wing_id: String,
    pub(crate) number: String,
    pub(crate) room_type: String,
    pub(crate) stream_key: Option<String>,
    pub(crate) retired_at: Option<String>,
    pub(crate) retired_by: Option<String>,
    pub(crate) created_at: String,
    pub(crate) updated_at: String,
}

#[derive(Queryable, Selectable)]
#[diesel(table_name = beds)]
#[diesel(check_for_backend(diesel::sqlite::Sqlite))]
pub(crate) struct BedRow {
    pub(crate) id: String,
    pub(crate) room_id: String,
    pub(crate) label: String,
    pub(crate) monitor_key: Option<String>,
    pub(crate) retired_at: Option<String>,
    pub(crate) retired_by: Option<String>,
    pub(crate) created_at: String,
    pub(crate) updated_at: String,
}

#[derive(Insertable)]
#[diesel(table_name = facilities)]
struct NewFacilityRow<'a> {
    id: &'a str,
    name: &'a str,
    timezone: &'a str,
    retired_at: Option<&'a str>,
    retired_by: Option<&'a str>,
    created_at: &'a str,
    updated_at: &'a str,
}

#[derive(AsChangeset)]
#[diesel(table_name = facilities)]
#[diesel(treat_none_as_null = true)]
struct FacilityChangeset<'a> {
    name: &'a str,
    timezone: &'a str,
    retired_at: Option<&'a str>,
    retired_by: Option<&'a str>,
    updated_at: &'a str,
}

#[derive(Insertable)]
#[diesel(table_name = wings)]
struct NewWingRow<'a> {
    id: &'a str,
    facility_id: &'a str,
    name: &'a str,
    floor: &'a str,
    sort_order: i32,
    retired_at: Option<&'a str>,
    retired_by: Option<&'a str>,
    created_at: &'a str,
    updated_at: &'a str,
}

#[derive(AsChangeset)]
#[diesel(table_name = wings)]
#[diesel(treat_none_as_null = true)]
struct WingChangeset<'a> {
    name: &'a str,
    floor: &'a str,
    sort_order: i32,
    retired_at: Option<&'a str>,
    retired_by: Option<&'a str>,
    updated_at: &'a str,
}

#[derive(Insertable)]
#[diesel(table_name = rooms)]
struct NewRoomRow<'a> {
    id: &'a str,
    wing_id: &'a str,
    number: &'a str,
    room_type: &'a str,
    stream_key: Option<&'a str>,
    retired_at: Option<&'a str>,
    retired_by: Option<&'a str>,
    created_at: &'a str,
    updated_at: &'a str,
}

#[derive(AsChangeset)]
#[diesel(table_name = rooms)]
#[diesel(treat_none_as_null = true)]
struct RoomChangeset<'a> {
    number: &'a str,
    room_type: &'a str,
    stream_key: Option<&'a str>,
    retired_at: Option<&'a str>,
    retired_by: Option<&'a str>,
    updated_at: &'a str,
}

#[derive(Insertable)]
#[diesel(table_name = beds)]
struct NewBedRow<'a> {
    id: &'a str,
    room_id: &'a str,
    label: &'a str,
    monitor_key: Option<&'a str>,
    retired_at: Option<&'a str>,
    retired_by: Option<&'a str>,
    created_at: &'a str,
    updated_at: &'a str,
}

#[derive(AsChangeset)]
#[diesel(table_name = beds)]
#[diesel(treat_none_as_null = true)]
struct BedChangeset<'a> {
    label: &'a str,
    monitor_key: Option<&'a str>,
    retired_at: Option<&'a str>,
    retired_by: Option<&'a str>,
    updated_at: &'a str,
}

impl EstructuraRepo for SqliteConnection {
    fn create_facility_in_transaction(
        connection: &mut SqliteConnection,
        id: FacilityId,
        input: FacilityInput,
        now: Instante,
    ) -> Result<Facility, ResidenceError> {
        let facility = Facility::create(id, input, now)?;
        let created_at = facility.created_at.to_string();
        let row = NewFacilityRow {
            id: facility.id.as_str(),
            name: &facility.name,
            timezone: &facility.timezone,
            retired_at: None,
            retired_by: None,
            created_at: &created_at,
            updated_at: &created_at,
        };
        diesel::insert_into(facilities::table)
            .values(row)
            .execute(connection)
            .map_err(ResidenceError::database)?;
        Ok(facility)
    }

    fn list_facilities(connection: &mut SqliteConnection) -> Result<Vec<Facility>, ResidenceError> {
        facilities::table
            .filter(facilities::retired_at.is_null())
            .select(FacilityRow::as_select())
            .order((facilities::name.asc(), facilities::id.asc()))
            .load::<FacilityRow>(connection)
            .map_err(ResidenceError::database)?
            .into_iter()
            .map(Facility::try_from)
            .collect()
    }

    fn get_facility(
        connection: &mut SqliteConnection,
        id: &FacilityId,
    ) -> Result<Facility, ResidenceError> {
        facilities::table
            .filter(facilities::id.eq(id.as_str()))
            .filter(facilities::retired_at.is_null())
            .select(FacilityRow::as_select())
            .first(connection)
            .optional()
            .map_err(ResidenceError::database)?
            .map(Facility::try_from)
            .transpose()?
            .ok_or(ResidenceError::NotFound)
    }

    fn facility_tree(
        connection: &mut SqliteConnection,
        id: &FacilityId,
    ) -> Result<FacilityTree, ResidenceError> {
        let facility = <Self as EstructuraRepo>::get_facility(connection, id)?;

        let wing_rows: Vec<WingRow> = wings::table
            .filter(wings::facility_id.eq(id.as_str()))
            .filter(wings::retired_at.is_null())
            .select(WingRow::as_select())
            .order((wings::sort_order.asc(), wings::id.asc()))
            .load(connection)
            .map_err(ResidenceError::database)?;

        let room_rows: Vec<RoomRow> = rooms::table
            .filter(rooms::wing_id.eq_any(wing_rows.iter().map(|w| w.id.as_str())))
            .filter(rooms::retired_at.is_null())
            .select(RoomRow::as_select())
            .order((rooms::number.asc(), rooms::id.asc()))
            .load(connection)
            .map_err(ResidenceError::database)?;

        let bed_rows: Vec<BedRow> = beds::table
            .filter(beds::room_id.eq_any(room_rows.iter().map(|r| r.id.as_str())))
            .filter(beds::retired_at.is_null())
            .select(BedRow::as_select())
            .order((beds::label.asc(), beds::id.asc()))
            .load(connection)
            .map_err(ResidenceError::database)?;

        use std::collections::HashMap;
        let mut beds_by_room: HashMap<String, Vec<super::TreeBed>> = HashMap::new();
        for b in &bed_rows {
            beds_by_room
                .entry(b.room_id.clone())
                .or_default()
                .push(super::TreeBed {
                    id: b.id.clone(),
                    label: b.label.clone(),
                    monitor_key: b.monitor_key.clone(),
                });
        }

        let mut rooms_by_wing: HashMap<String, Vec<super::TreeRoom>> = HashMap::new();
        for r in &room_rows {
            rooms_by_wing
                .entry(r.wing_id.clone())
                .or_default()
                .push(super::TreeRoom {
                    id: r.id.clone(),
                    number: r.number.clone(),
                    room_type: r.room_type.clone(),
                    stream_key: r.stream_key.clone(),
                    beds: beds_by_room.remove(&r.id).unwrap_or_default(),
                });
        }

        let wings = wing_rows
            .into_iter()
            .map(|w| super::TreeWing {
                id: w.id.clone(),
                name: w.name.clone(),
                floor: w.floor.clone(),
                sort_order: w.sort_order,
                rooms: rooms_by_wing.remove(&w.id).unwrap_or_default(),
            })
            .collect();

        Ok(super::FacilityTree {
            id: facility.id.into_string(),
            name: facility.name,
            timezone: facility.timezone,
            wings,
        })
    }

    fn update_facility_in_transaction(
        connection: &mut SqliteConnection,
        id: &FacilityId,
        input: FacilityUpdate,
        now: Instante,
    ) -> Result<Facility, ResidenceError> {
        let mut facility = <Self as EstructuraRepo>::get_facility(connection, id)?;
        facility.apply_update(input, now)?;
        let updated_at = facility.updated_at.to_string();
        let retired_at = facility.retired_at.as_ref().map(ToString::to_string);
        let retired_by = facility.retired_by.as_ref().map(ToString::to_string);
        diesel::update(facilities::table.find(id.as_str()))
            .set(FacilityChangeset {
                name: &facility.name,
                timezone: &facility.timezone,
                retired_at: retired_at.as_deref(),
                retired_by: retired_by.as_deref(),
                updated_at: &updated_at,
            })
            .execute(connection)
            .map_err(ResidenceError::database)?;
        Ok(facility)
    }

    fn create_wing_in_transaction(
        connection: &mut SqliteConnection,
        id: WingId,
        input: WingInput,
        now: Instante,
    ) -> Result<Wing, ResidenceError> {
        ensure_facility_active(connection, &input.facility_id)?;
        let wing = Wing::create(input, id, now)?;
        let created_at = wing.created_at.to_string();
        diesel::insert_into(wings::table)
            .values(NewWingRow {
                id: wing.id.as_str(),
                facility_id: wing.facility_id.as_str(),
                name: &wing.name,
                floor: &wing.floor,
                sort_order: wing.sort_order,
                retired_at: None,
                retired_by: None,
                created_at: &created_at,
                updated_at: &created_at,
            })
            .execute(connection)
            .map_err(ResidenceError::database)?;
        Ok(wing)
    }

    fn list_wings(
        connection: &mut SqliteConnection,
        facility_id: &FacilityId,
    ) -> Result<Vec<Wing>, ResidenceError> {
        ensure_facility_active(connection, facility_id)?;
        wings::table
            .filter(wings::facility_id.eq(facility_id.as_str()))
            .filter(wings::retired_at.is_null())
            .select(WingRow::as_select())
            .order((wings::sort_order.asc(), wings::id.asc()))
            .load::<WingRow>(connection)
            .map_err(ResidenceError::database)?
            .into_iter()
            .map(Wing::try_from)
            .collect()
    }

    fn list_wings_all(connection: &mut SqliteConnection) -> Result<Vec<Wing>, ResidenceError> {
        wings::table
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
            .collect()
    }

    fn get_wing(connection: &mut SqliteConnection, id: &WingId) -> Result<Wing, ResidenceError> {
        ensure_wing_active(connection, id)?;
        wings::table
            .filter(wings::id.eq(id.as_str()))
            .filter(wings::retired_at.is_null())
            .select(WingRow::as_select())
            .first(connection)
            .optional()
            .map_err(ResidenceError::database)?
            .map(Wing::try_from)
            .transpose()?
            .ok_or(ResidenceError::NotFound)
    }

    fn update_wing_in_transaction(
        connection: &mut SqliteConnection,
        id: &WingId,
        input: WingUpdate,
        now: Instante,
    ) -> Result<Wing, ResidenceError> {
        let mut wing = <Self as EstructuraRepo>::get_wing(connection, id)?;
        wing.apply_update(input, now)?;
        let updated_at = wing.updated_at.to_string();
        let retired_at = wing.retired_at.as_ref().map(ToString::to_string);
        let retired_by = wing.retired_by.as_ref().map(ToString::to_string);
        diesel::update(wings::table.find(id.as_str()))
            .set(WingChangeset {
                name: &wing.name,
                floor: &wing.floor,
                sort_order: wing.sort_order,
                retired_at: retired_at.as_deref(),
                retired_by: retired_by.as_deref(),
                updated_at: &updated_at,
            })
            .execute(connection)
            .map_err(ResidenceError::database)?;
        Ok(wing)
    }

    fn create_room_in_transaction(
        connection: &mut SqliteConnection,
        id: RoomId,
        input: RoomInput,
        now: Instante,
    ) -> Result<Room, ResidenceError> {
        ensure_wing_active(connection, &input.wing_id)?;
        let room = Room::create(input, id, now)?;
        let created_at = room.created_at.to_string();
        let stream_key = room.stream_key.as_ref().map(StreamKey::as_str);
        diesel::insert_into(rooms::table)
            .values(NewRoomRow {
                id: room.id.as_str(),
                wing_id: room.wing_id.as_str(),
                number: &room.number,
                room_type: &room.room_type,
                stream_key,
                retired_at: None,
                retired_by: None,
                created_at: &created_at,
                updated_at: &created_at,
            })
            .execute(connection)
            .map_err(ResidenceError::database)?;
        Ok(room)
    }

    fn list_rooms(
        connection: &mut SqliteConnection,
        wing_id: &WingId,
    ) -> Result<Vec<Room>, ResidenceError> {
        ensure_wing_active(connection, wing_id)?;
        rooms::table
            .filter(rooms::wing_id.eq(wing_id.as_str()))
            .filter(rooms::retired_at.is_null())
            .select(RoomRow::as_select())
            .order((rooms::number.asc(), rooms::id.asc()))
            .load::<RoomRow>(connection)
            .map_err(ResidenceError::database)?
            .into_iter()
            .map(Room::try_from)
            .collect()
    }

    fn get_room(connection: &mut SqliteConnection, id: &RoomId) -> Result<Room, ResidenceError> {
        ensure_room_active(connection, id)?;
        rooms::table
            .filter(rooms::id.eq(id.as_str()))
            .filter(rooms::retired_at.is_null())
            .select(RoomRow::as_select())
            .first(connection)
            .optional()
            .map_err(ResidenceError::database)?
            .map(Room::try_from)
            .transpose()?
            .ok_or(ResidenceError::NotFound)
    }

    fn update_room_in_transaction(
        connection: &mut SqliteConnection,
        id: &RoomId,
        input: RoomUpdate,
        now: Instante,
    ) -> Result<Room, ResidenceError> {
        let mut room = <Self as EstructuraRepo>::get_room(connection, id)?;
        room.apply_update(input, now)?;
        let updated_at = room.updated_at.to_string();
        let retired_at = room.retired_at.as_ref().map(ToString::to_string);
        let retired_by = room.retired_by.as_ref().map(ToString::to_string);
        let stream_key = room.stream_key.as_ref().map(StreamKey::as_str);
        diesel::update(rooms::table.find(id.as_str()))
            .set(RoomChangeset {
                number: &room.number,
                room_type: &room.room_type,
                stream_key,
                retired_at: retired_at.as_deref(),
                retired_by: retired_by.as_deref(),
                updated_at: &updated_at,
            })
            .execute(connection)
            .map_err(ResidenceError::database)?;
        Ok(room)
    }

    fn create_bed_in_transaction(
        connection: &mut SqliteConnection,
        id: BedId,
        input: BedInput,
        now: Instante,
    ) -> Result<Bed, ResidenceError> {
        ensure_room_active(connection, &input.room_id)?;
        let bed = Bed::create(input, id, now)?;
        let created_at = bed.created_at.to_string();
        let monitor_key = bed.monitor_key.as_ref().map(MonitorKey::as_str);
        diesel::insert_into(beds::table)
            .values(NewBedRow {
                id: bed.id.as_str(),
                room_id: bed.room_id.as_str(),
                label: &bed.label,
                monitor_key,
                retired_at: None,
                retired_by: None,
                created_at: &created_at,
                updated_at: &created_at,
            })
            .execute(connection)
            .map_err(ResidenceError::database)?;
        Ok(bed)
    }

    fn list_beds(
        connection: &mut SqliteConnection,
        room_id: &RoomId,
    ) -> Result<Vec<Bed>, ResidenceError> {
        ensure_room_active(connection, room_id)?;
        beds::table
            .filter(beds::room_id.eq(room_id.as_str()))
            .filter(beds::retired_at.is_null())
            .select(BedRow::as_select())
            .order((beds::label.asc(), beds::id.asc()))
            .load::<BedRow>(connection)
            .map_err(ResidenceError::database)?
            .into_iter()
            .map(Bed::try_from)
            .collect()
    }

    fn get_bed(connection: &mut SqliteConnection, id: &BedId) -> Result<Bed, ResidenceError> {
        ensure_bed_active(connection, id)?;
        beds::table
            .filter(beds::id.eq(id.as_str()))
            .filter(beds::retired_at.is_null())
            .select(BedRow::as_select())
            .first(connection)
            .optional()
            .map_err(ResidenceError::database)?
            .map(Bed::try_from)
            .transpose()?
            .ok_or(ResidenceError::NotFound)
    }

    fn find_bed_by_monitor_key(
        connection: &mut SqliteConnection,
        monitor_key: &str,
    ) -> Result<Option<Bed>, ResidenceError> {
        beds::table
            .filter(beds::monitor_key.eq(monitor_key))
            .filter(beds::retired_at.is_null())
            .select(BedRow::as_select())
            .first(connection)
            .optional()
            .map_err(ResidenceError::database)?
            .map(Bed::try_from)
            .transpose()
    }

    fn update_bed_in_transaction(
        connection: &mut SqliteConnection,
        id: &BedId,
        input: BedUpdate,
        now: Instante,
    ) -> Result<Bed, ResidenceError> {
        let mut bed = <Self as EstructuraRepo>::get_bed(connection, id)?;
        bed.apply_update(input, now)?;
        let updated_at = bed.updated_at.to_string();
        let retired_at = bed.retired_at.as_ref().map(ToString::to_string);
        let retired_by = bed.retired_by.as_ref().map(ToString::to_string);
        let monitor_key = bed.monitor_key.as_ref().map(MonitorKey::as_str);
        diesel::update(beds::table.find(id.as_str()))
            .set(BedChangeset {
                label: &bed.label,
                monitor_key,
                retired_at: retired_at.as_deref(),
                retired_by: retired_by.as_deref(),
                updated_at: &updated_at,
            })
            .execute(connection)
            .map_err(ResidenceError::database)?;
        Ok(bed)
    }
}

pub(crate) fn ensure_facility_active(
    connection: &mut SqliteConnection,
    id: &FacilityId,
) -> Result<(), ResidenceError> {
    let exists = facilities::table
        .filter(facilities::id.eq(id.as_str()))
        .filter(facilities::retired_at.is_null())
        .select(facilities::id)
        .first::<String>(connection)
        .optional()
        .map_err(ResidenceError::database)?;
    exists.map(|_| ()).ok_or(ResidenceError::NotFound)
}

pub(crate) fn ensure_wing_active(
    connection: &mut SqliteConnection,
    id: &WingId,
) -> Result<(), ResidenceError> {
    let facility_id = wings::table
        .filter(wings::id.eq(id.as_str()))
        .filter(wings::retired_at.is_null())
        .select(wings::facility_id)
        .first::<String>(connection)
        .optional()
        .map_err(ResidenceError::database)?;
    let Some(facility_id) = facility_id else {
        return Err(ResidenceError::NotFound);
    };
    ensure_facility_active(connection, &FacilityId::new(facility_id))
}

pub(crate) fn ensure_room_active(
    connection: &mut SqliteConnection,
    id: &RoomId,
) -> Result<(), ResidenceError> {
    let wing_id = rooms::table
        .filter(rooms::id.eq(id.as_str()))
        .filter(rooms::retired_at.is_null())
        .select(rooms::wing_id)
        .first::<String>(connection)
        .optional()
        .map_err(ResidenceError::database)?;
    let Some(wing_id) = wing_id else {
        return Err(ResidenceError::NotFound);
    };
    ensure_wing_active(connection, &WingId::new(wing_id))
}

pub(crate) fn ensure_bed_active(
    connection: &mut SqliteConnection,
    id: &BedId,
) -> Result<(), ResidenceError> {
    let room_id = beds::table
        .filter(beds::id.eq(id.as_str()))
        .filter(beds::retired_at.is_null())
        .select(beds::room_id)
        .first::<String>(connection)
        .optional()
        .map_err(ResidenceError::database)?;
    let Some(room_id) = room_id else {
        return Err(ResidenceError::NotFound);
    };
    ensure_room_active(connection, &RoomId::new(room_id))
}

impl TryFrom<FacilityRow> for Facility {
    type Error = ResidenceError;

    fn try_from(row: FacilityRow) -> Result<Self, ResidenceError> {
        let created_at = parse_instant("created_at", row.created_at)?;
        let updated_at = parse_instant("updated_at", row.updated_at)?;
        let mut facility = Facility::create(
            FacilityId::new(row.id),
            FacilityInput {
                name: row.name,
                timezone: row.timezone,
            },
            created_at,
        )
        .map_err(|error| stored_domain("facility", error))?;
        facility.retired_at = row
            .retired_at
            .map(|value| parse_instant("retired_at", value))
            .transpose()?;
        facility.retired_by = row.retired_by.map(Id::<Actor>::new);
        facility.updated_at = updated_at;
        Ok(facility)
    }
}

impl TryFrom<WingRow> for Wing {
    type Error = ResidenceError;

    fn try_from(row: WingRow) -> Result<Self, ResidenceError> {
        let created_at = parse_instant("created_at", row.created_at)?;
        let updated_at = parse_instant("updated_at", row.updated_at)?;
        let mut wing = Wing::create(
            WingInput {
                facility_id: FacilityId::new(row.facility_id),
                name: row.name,
                floor: row.floor,
                sort_order: row.sort_order,
            },
            WingId::new(row.id),
            created_at,
        )
        .map_err(|error| stored_domain("wing", error))?;
        wing.retired_at = row
            .retired_at
            .map(|value| parse_instant("retired_at", value))
            .transpose()?;
        wing.retired_by = row.retired_by.map(Id::<Actor>::new);
        wing.updated_at = updated_at;
        Ok(wing)
    }
}

impl TryFrom<RoomRow> for Room {
    type Error = ResidenceError;

    fn try_from(row: RoomRow) -> Result<Self, ResidenceError> {
        let created_at = parse_instant("created_at", row.created_at)?;
        let updated_at = parse_instant("updated_at", row.updated_at)?;
        let mut room = Room::create(
            RoomInput {
                wing_id: WingId::new(row.wing_id),
                number: row.number,
                room_type: row.room_type,
                stream_key: row.stream_key,
            },
            RoomId::new(row.id),
            created_at,
        )
        .map_err(|error| stored_domain("room", error))?;
        room.retired_at = row
            .retired_at
            .map(|value| parse_instant("retired_at", value))
            .transpose()?;
        room.retired_by = row.retired_by.map(Id::<Actor>::new);
        room.updated_at = updated_at;
        Ok(room)
    }
}

impl TryFrom<BedRow> for Bed {
    type Error = ResidenceError;

    fn try_from(row: BedRow) -> Result<Self, ResidenceError> {
        let created_at = parse_instant("created_at", row.created_at)?;
        let updated_at = parse_instant("updated_at", row.updated_at)?;
        let mut bed = Bed::create(
            BedInput {
                room_id: RoomId::new(row.room_id),
                label: row.label,
                monitor_key: row.monitor_key,
            },
            BedId::new(row.id),
            created_at,
        )
        .map_err(|error| stored_domain("bed", error))?;
        bed.retired_at = row
            .retired_at
            .map(|value| parse_instant("retired_at", value))
            .transpose()?;
        bed.retired_by = row.retired_by.map(Id::<Actor>::new);
        bed.updated_at = updated_at;
        Ok(bed)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::testsupport::{instant, store};

    #[test]
    fn creates_and_lists_the_structure_hierarchy() {
        let store = store();
        let facility = store
            .create_facility(
                FacilityInput {
                    name: "Manantial".to_owned(),
                    timezone: "America/Argentina/Buenos_Aires".to_owned(),
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
                    sort_order: 1,
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
                    room_id: room.id.clone(),
                    label: "Cama 1".to_owned(),
                    monitor_key: Some("monitor-118-1".to_owned()),
                },
                instant(),
            )
            .unwrap();

        assert_eq!(store.list_facilities().unwrap().len(), 1);
        assert_eq!(store.list_wings(&facility.id).unwrap().len(), 1);
        assert_eq!(store.list_rooms(&wing.id).unwrap().len(), 1);
        assert_eq!(store.list_beds(&room.id).unwrap().len(), 1);
        assert_eq!(bed.monitor_key.as_ref().unwrap().as_str(), "monitor-118-1");
    }

    #[test]
    fn active_device_keys_and_room_numbers_are_unique() {
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
        store
            .create_room(
                RoomInput {
                    wing_id: wing.id.clone(),
                    number: "118".to_owned(),
                    room_type: "single".to_owned(),
                    stream_key: Some("stream-1".to_owned()),
                },
                instant(),
            )
            .unwrap();
        assert!(matches!(
            store.create_room(
                RoomInput {
                    wing_id: wing.id,
                    number: "118".to_owned(),
                    room_type: "single".to_owned(),
                    stream_key: Some("stream-2".to_owned()),
                },
                instant(),
            ),
            Err(ResidenceError::Conflict)
        ));
    }

    #[test]
    fn rejects_missing_parents_and_invalid_updates() {
        let store = store();
        assert!(matches!(
            store.create_wing(
                WingInput {
                    facility_id: FacilityId::new("facility-missing"),
                    name: "Ala".to_owned(),
                    floor: "1".to_owned(),
                    sort_order: 0,
                },
                instant(),
            ),
            Err(ResidenceError::NotFound)
        ));
        assert!(matches!(
            Facility::create(
                FacilityId::new("facility-1"),
                FacilityInput {
                    name: " ".to_owned(),
                    timezone: "UTC".to_owned(),
                },
                instant(),
            ),
            Err(crate::estructura::EstructuraError::EmptyField { field: "name" })
        ));
    }

    #[test]
    fn active_reads_hide_children_of_a_retired_parent() {
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
                    wing_id: wing.id,
                    number: "118".to_owned(),
                    room_type: "single".to_owned(),
                    stream_key: None,
                },
                instant(),
            )
            .unwrap();
        let mut connection = store.pool.get().unwrap();
        diesel::update(facilities::table.find(facility.id.as_str()))
            .set(facilities::retired_at.eq(instant().to_string()))
            .execute(&mut connection)
            .unwrap();
        drop(connection);

        assert!(store.list_wings_all().unwrap().is_empty());
        assert!(matches!(
            store.list_rooms(&room.wing_id),
            Err(ResidenceError::NotFound)
        ));
    }
}
