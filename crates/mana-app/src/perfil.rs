//! Resolución de perfil efectivo para UI y politics.
//!
//! Esta función traduce el perfil stored a reglas efectivas que el motor evalúa.
//! Delega en `mana_motores::perfil_efectivo` para la lógica centralizada.

use ctx_politica::{AlarmProfileVersion, Overrides, PerfilEfectivoInput};
use mana_motores::catalogo::AlarmCatalog;
use mana_motores::alarmas::PerfilEfectivo;
use serde_json::Value as JsonValue;

pub const PLANTILLA_POR_DEFECTO: &str = "balanced";

/// Defaults para cuando no hay perfil guardado.
fn default_input() -> PerfilEfectivoInput {
    use ctx_politica::{MobilityAid, RiskLevel};
    PerfilEfectivoInput {
        risk_level: RiskLevel::Medium,
        mobility_aid: MobilityAid::None,
        is_custom: false,
        template_id: PLANTILLA_POR_DEFECTO.to_owned(),
        overrides: Overrides::empty(),
    }
}

/// El perfil efectivo tal como lo consume UI, resuelto contra el perfil vigente.
pub fn perfil_efectivo(
    catalog: &AlarmCatalog,
    profile: Option<&AlarmProfileVersion>,
) -> PerfilEfectivo {
    let input = profile.map(|p| p.to_perfil_input()).unwrap_or_else(default_input);
    mana_motores::perfil_efectivo(catalog, input.risk_level, input.mobility_aid, input.is_custom, &input.template_id, input.overrides.as_value())
}

/// El perfil efectivo cuando las piezas ya vienen resueltas.
pub fn perfil_efectivo_con(
    catalog: &AlarmCatalog,
    level: ctx_politica::RiskLevel,
    aid: ctx_politica::MobilityAid,
    is_custom: bool,
    template_id: &str,
    overrides: &JsonValue,
) -> PerfilEfectivo {
    mana_motores::perfil_efectivo(catalog, level, aid, is_custom, template_id, overrides)
}
