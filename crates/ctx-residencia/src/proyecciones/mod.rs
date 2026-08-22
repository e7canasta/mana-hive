//! Proyecciones de lectura que cruzan los agregados del contexto para
//! alimentar la interfaz (overview de alas y camas con ubicacion).

use crate::estructura::{Bed, Wing, WingId};

pub mod sqlite;

#[derive(Clone, Debug)]
pub struct ResidenceBed {
    pub bed: Bed,
    pub room_number: String,
    pub room_type: String,
    pub stream_key: Option<String>,
    pub wing_id: WingId,
    pub wing_name: String,
    pub wing_floor: String,
}

pub type WingOverview = (Wing, i64);
