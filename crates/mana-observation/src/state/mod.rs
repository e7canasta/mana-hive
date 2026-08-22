//! Proyeccion del ultimo evento por cama. Reemplazable y reconstruible.

use mana_kernel::Instante;

/// Cuan vieja es la ultima observacion de una cama.
///
/// **No se persiste.** Es una funcion del reloj, y una columna `freshness`
/// queda vieja sola: diria `live` sobre una cama que dejo de informar hace una
/// hora. Se deriva en cada lectura y por eso no puede mentir.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum Freshness {
    /// Nunca se observo esta cama. Distinto de `Offline`: no es que se cayo, es
    /// que nunca hablo.
    NotObserved,
    Live,
    Stale,
    Offline,
}

/// Umbrales de frescura. Vienen de parametros de plataforma, no del codigo.
#[derive(Clone, Copy, Debug)]
pub struct FreshnessThresholds {
    pub live_within_seconds: i64,
    pub stale_within_seconds: i64,
}

impl Default for FreshnessThresholds {
    fn default() -> Self {
        Self {
            live_within_seconds: 90,
            stale_within_seconds: 600,
        }
    }
}

impl Freshness {
    pub fn derive(
        last_seen: Option<Instante>,
        now: Instante,
        thresholds: FreshnessThresholds,
    ) -> Self {
        let Some(last_seen) = last_seen else {
            return Self::NotObserved;
        };
        let seconds = now
            .as_datetime()
            .signed_duration_since(*last_seen.as_datetime())
            .num_seconds();
        if seconds <= thresholds.live_within_seconds {
            Self::Live
        } else if seconds <= thresholds.stale_within_seconds {
            Self::Stale
        } else {
            Self::Offline
        }
    }

    pub fn as_str(&self) -> &'static str {
        match self {
            Self::NotObserved => "not_observed",
            Self::Live => "live",
            Self::Stale => "stale",
            Self::Offline => "offline",
        }
    }
}

/// El estado proyectado de una cama.
///
/// Sin `alert_level`: eso es un veredicto de politica y no vive en una tabla de
/// observacion (invariante 8).
#[derive(Clone, Debug)]
pub struct BedState {
    pub bed_id: String,
    pub resident_id: Option<String>,
    pub room_state: Option<String>,
    pub state: String,
    pub substate: Option<String>,
    /// `None` es "el detector no informo", nunca `false`.
    pub sleeping: Option<bool>,
    pub state_since: Option<Instante>,
    pub updated_at: Instante,
    pub source: String,
    pub source_event_id: Option<String>,
}

impl BedState {
    pub fn freshness(&self, now: Instante, thresholds: FreshnessThresholds) -> Freshness {
        Freshness::derive(Some(self.updated_at), now, thresholds)
    }
}
