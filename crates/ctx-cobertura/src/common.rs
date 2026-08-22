use base64::{engine::general_purpose::URL_SAFE_NO_PAD, Engine as _};
use mana_kernel::Instante;
use rand::RngExt;

use crate::CoberturaError;

pub(crate) fn random_id(prefix: &str) -> String {
    let mut bytes = [0_u8; 16];
    rand::rng().fill(&mut bytes);
    format!("{prefix}-{}", URL_SAFE_NO_PAD.encode(bytes))
}

pub(crate) fn parse_instant(label: &str, value: String) -> Result<Instante, CoberturaError> {
    value
        .parse::<Instante>()
        .map_err(|error| CoberturaError::InvalidStoredData(format!("{label}: {error}")))
}
