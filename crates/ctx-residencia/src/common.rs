use base64::{engine::general_purpose::URL_SAFE_NO_PAD, Engine as _};
use mana_kernel::Instante;
use rand::RngExt;

use crate::ResidenceError;

pub(crate) fn random_id(prefix: &str) -> String {
    let mut bytes = [0_u8; 16];
    rand::rng().fill(&mut bytes);
    format!("{prefix}-{}", URL_SAFE_NO_PAD.encode(bytes))
}

pub(crate) fn parse_instant(label: &str, value: String) -> Result<Instante, ResidenceError> {
    value
        .parse::<Instante>()
        .map_err(|error| ResidenceError::InvalidStoredData(format!("{label}: {error}")))
}

pub(crate) fn stored_domain(label: &str, error: impl std::fmt::Display) -> ResidenceError {
    ResidenceError::InvalidStoredData(format!("{label}: {error}"))
}
