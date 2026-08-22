//! Subdominio de estructura fisica: facilities, wings, rooms y beds.
//!
//! Posee la jerarquia, los keys de dispositivos, la unicidad activa y el
//! retiro logico en cascada (los hijos de un padre retirado dejan de verse).

use mana_kernel::{define_kinds, Actor, Id, Instante};
use thiserror::Error;

use crate::common::random_id;

define_kinds!(FacilityKind, WingKind, RoomKind, BedKind);

pub type FacilityId = Id<FacilityKind>;
pub type WingId = Id<WingKind>;
pub type RoomId = Id<RoomKind>;
pub type BedId = Id<BedKind>;

const MAX_FACILITY_NAME: usize = 120;
const MAX_TIMEZONE: usize = 80;
const MAX_WING_NAME: usize = 120;
const MAX_FLOOR: usize = 40;
const MAX_ROOM_NUMBER: usize = 40;
const MAX_ROOM_TYPE: usize = 40;
const MAX_BED_LABEL: usize = 80;
const MAX_DEVICE_KEY: usize = 160;

pub mod repo;
pub mod sqlite;

#[derive(Clone, Debug, Eq, PartialEq, Error)]
pub enum EstructuraError {
    #[error("{field} no puede estar vacio")]
    EmptyField { field: &'static str },
    #[error("{field} excede la longitud maxima de {max} caracteres")]
    FieldTooLong { field: &'static str, max: usize },
    #[error("sort_order no puede ser negativo")]
    NegativeSortOrder,
    #[error("no hay campos para actualizar")]
    EmptyUpdate,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct StreamKey(String);

impl StreamKey {
    pub fn parse(value: impl AsRef<str>) -> Result<Self, EstructuraError> {
        Ok(Self(text(value.as_ref(), "stream_key", MAX_DEVICE_KEY)?))
    }

    pub fn as_str(&self) -> &str {
        &self.0
    }
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct MonitorKey(String);

impl MonitorKey {
    pub fn parse(value: impl AsRef<str>) -> Result<Self, EstructuraError> {
        Ok(Self(text(value.as_ref(), "monitor_key", MAX_DEVICE_KEY)?))
    }

    pub fn as_str(&self) -> &str {
        &self.0
    }
}

#[derive(Clone, Debug)]
pub struct FacilityInput {
    pub name: String,
    pub timezone: String,
}

#[derive(Clone, Debug, Default)]
pub struct FacilityUpdate {
    pub name: Option<String>,
    pub timezone: Option<String>,
}

#[derive(Clone, Debug)]
pub struct WingInput {
    pub facility_id: FacilityId,
    pub name: String,
    pub floor: String,
    pub sort_order: i32,
}

#[derive(Clone, Debug, Default)]
pub struct WingUpdate {
    pub name: Option<String>,
    pub floor: Option<String>,
    pub sort_order: Option<i32>,
}

#[derive(Clone, Debug)]
pub struct RoomInput {
    pub wing_id: WingId,
    pub number: String,
    pub room_type: String,
    pub stream_key: Option<String>,
}

#[derive(Clone, Debug, Default)]
pub struct RoomUpdate {
    pub number: Option<String>,
    pub room_type: Option<String>,
    pub stream_key: Option<Option<String>>,
}

#[derive(Clone, Debug)]
pub struct BedInput {
    pub room_id: RoomId,
    pub label: String,
    pub monitor_key: Option<String>,
}

#[derive(Clone, Debug, Default)]
pub struct BedUpdate {
    pub label: Option<String>,
    pub monitor_key: Option<Option<String>>,
}

#[derive(Clone, Debug)]
pub struct Facility {
    pub id: FacilityId,
    pub name: String,
    pub timezone: String,
    pub retired_at: Option<Instante>,
    pub retired_by: Option<Id<Actor>>,
    pub created_at: Instante,
    pub updated_at: Instante,
}

impl Facility {
    pub fn create(
        id: FacilityId,
        input: FacilityInput,
        now: Instante,
    ) -> Result<Self, EstructuraError> {
        Ok(Self {
            id,
            name: text(&input.name, "name", MAX_FACILITY_NAME)?,
            timezone: text(&input.timezone, "timezone", MAX_TIMEZONE)?,
            retired_at: None,
            retired_by: None,
            created_at: now,
            updated_at: now,
        })
    }

    pub fn apply_update(
        &mut self,
        input: FacilityUpdate,
        now: Instante,
    ) -> Result<(), EstructuraError> {
        if input.name.is_none() && input.timezone.is_none() {
            return Err(EstructuraError::EmptyUpdate);
        }
        if let Some(name) = input.name {
            self.name = text(&name, "name", MAX_FACILITY_NAME)?;
        }
        if let Some(timezone) = input.timezone {
            self.timezone = text(&timezone, "timezone", MAX_TIMEZONE)?;
        }
        self.updated_at = now;
        Ok(())
    }
}

#[derive(Clone, Debug)]
pub struct Wing {
    pub id: WingId,
    pub facility_id: FacilityId,
    pub name: String,
    pub floor: String,
    pub sort_order: i32,
    pub retired_at: Option<Instante>,
    pub retired_by: Option<Id<Actor>>,
    pub created_at: Instante,
    pub updated_at: Instante,
}

impl Wing {
    pub fn create(input: WingInput, id: WingId, now: Instante) -> Result<Self, EstructuraError> {
        if input.sort_order < 0 {
            return Err(EstructuraError::NegativeSortOrder);
        }
        Ok(Self {
            id,
            facility_id: input.facility_id,
            name: text(&input.name, "name", MAX_WING_NAME)?,
            floor: text(&input.floor, "floor", MAX_FLOOR)?,
            sort_order: input.sort_order,
            retired_at: None,
            retired_by: None,
            created_at: now,
            updated_at: now,
        })
    }

    pub fn apply_update(
        &mut self,
        input: WingUpdate,
        now: Instante,
    ) -> Result<(), EstructuraError> {
        if input.name.is_none() && input.floor.is_none() && input.sort_order.is_none() {
            return Err(EstructuraError::EmptyUpdate);
        }
        if let Some(name) = input.name {
            self.name = text(&name, "name", MAX_WING_NAME)?;
        }
        if let Some(floor) = input.floor {
            self.floor = text(&floor, "floor", MAX_FLOOR)?;
        }
        if let Some(sort_order) = input.sort_order {
            if sort_order < 0 {
                return Err(EstructuraError::NegativeSortOrder);
            }
            self.sort_order = sort_order;
        }
        self.updated_at = now;
        Ok(())
    }
}

#[derive(Clone, Debug)]
pub struct Room {
    pub id: RoomId,
    pub wing_id: WingId,
    pub number: String,
    pub room_type: String,
    pub stream_key: Option<StreamKey>,
    pub retired_at: Option<Instante>,
    pub retired_by: Option<Id<Actor>>,
    pub created_at: Instante,
    pub updated_at: Instante,
}

impl Room {
    pub fn create(input: RoomInput, id: RoomId, now: Instante) -> Result<Self, EstructuraError> {
        Ok(Self {
            id,
            wing_id: input.wing_id,
            number: text(&input.number, "number", MAX_ROOM_NUMBER)?,
            room_type: text(&input.room_type, "type", MAX_ROOM_TYPE)?,
            stream_key: input.stream_key.map(StreamKey::parse).transpose()?,
            retired_at: None,
            retired_by: None,
            created_at: now,
            updated_at: now,
        })
    }

    pub fn apply_update(
        &mut self,
        input: RoomUpdate,
        now: Instante,
    ) -> Result<(), EstructuraError> {
        if input.number.is_none() && input.room_type.is_none() && input.stream_key.is_none() {
            return Err(EstructuraError::EmptyUpdate);
        }
        if let Some(number) = input.number {
            self.number = text(&number, "number", MAX_ROOM_NUMBER)?;
        }
        if let Some(room_type) = input.room_type {
            self.room_type = text(&room_type, "type", MAX_ROOM_TYPE)?;
        }
        if let Some(stream_key) = input.stream_key {
            self.stream_key = stream_key.map(StreamKey::parse).transpose()?;
        }
        self.updated_at = now;
        Ok(())
    }
}

#[derive(Clone, Debug)]
pub struct Bed {
    pub id: BedId,
    pub room_id: RoomId,
    pub label: String,
    pub monitor_key: Option<MonitorKey>,
    pub retired_at: Option<Instante>,
    pub retired_by: Option<Id<Actor>>,
    pub created_at: Instante,
    pub updated_at: Instante,
}

impl Bed {
    pub fn create(input: BedInput, id: BedId, now: Instante) -> Result<Self, EstructuraError> {
        Ok(Self {
            id,
            room_id: input.room_id,
            label: text(&input.label, "label", MAX_BED_LABEL)?,
            monitor_key: input.monitor_key.map(MonitorKey::parse).transpose()?,
            retired_at: None,
            retired_by: None,
            created_at: now,
            updated_at: now,
        })
    }

    pub fn apply_update(&mut self, input: BedUpdate, now: Instante) -> Result<(), EstructuraError> {
        if input.label.is_none() && input.monitor_key.is_none() {
            return Err(EstructuraError::EmptyUpdate);
        }
        if let Some(label) = input.label {
            self.label = text(&label, "label", MAX_BED_LABEL)?;
        }
        if let Some(monitor_key) = input.monitor_key {
            self.monitor_key = monitor_key.map(MonitorKey::parse).transpose()?;
        }
        self.updated_at = now;
        Ok(())
    }
}

#[derive(Clone, Debug)]
pub struct TreeBed {
    pub id: String,
    pub label: String,
    pub monitor_key: Option<String>,
}

#[derive(Clone, Debug)]
pub struct TreeRoom {
    pub id: String,
    pub number: String,
    pub room_type: String,
    pub stream_key: Option<String>,
    pub beds: Vec<TreeBed>,
}

#[derive(Clone, Debug)]
pub struct TreeWing {
    pub id: String,
    pub name: String,
    pub floor: String,
    pub sort_order: i32,
    pub rooms: Vec<TreeRoom>,
}

#[derive(Clone, Debug)]
pub struct FacilityTree {
    pub id: String,
    pub name: String,
    pub timezone: String,
    pub wings: Vec<TreeWing>,
}

pub fn new_facility_id() -> FacilityId {
    Id::new(random_id("facility"))
}

pub fn new_wing_id() -> WingId {
    Id::new(random_id("wing"))
}

pub fn new_room_id() -> RoomId {
    Id::new(random_id("room"))
}

pub fn new_bed_id() -> BedId {
    Id::new(random_id("bed"))
}

fn text(value: &str, field: &'static str, max: usize) -> Result<String, EstructuraError> {
    let value = value.trim();
    if value.is_empty() {
        return Err(EstructuraError::EmptyField { field });
    }
    if value.chars().count() > max {
        return Err(EstructuraError::FieldTooLong { field, max });
    }
    Ok(value.to_owned())
}
