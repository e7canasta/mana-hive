use base64::{engine::general_purpose::URL_SAFE_NO_PAD, Engine as _};
use chrono::NaiveDate;
use mana_kernel::Instante;
use rand::RngExt;

use crate::PoblacionError;

pub(crate) fn random_id(prefix: &str) -> String {
    let mut bytes = [0_u8; 16];
    rand::rng().fill(&mut bytes);
    format!("{prefix}-{}", URL_SAFE_NO_PAD.encode(bytes))
}

pub(crate) fn parse_instant(label: &str, value: String) -> Result<Instante, PoblacionError> {
    value
        .parse::<Instante>()
        .map_err(|error| PoblacionError::InvalidStoredData(format!("{label}: {error}")))
}

pub(crate) fn parse_date(label: &str, value: String) -> Result<NaiveDate, PoblacionError> {
    NaiveDate::parse_from_str(&value, "%Y-%m-%d")
        .map_err(|error| PoblacionError::InvalidStoredData(format!("{label}: {error}")))
}

pub(crate) fn stored_domain(label: &str, error: impl std::fmt::Display) -> PoblacionError {
    PoblacionError::InvalidStoredData(format!("{label}: {error}"))
}
