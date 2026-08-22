pub mod repo;
pub mod sqlite;

pub use repo::PerfilesRepo;

use mana_kernel::{Id, Instante};

use crate::catalogo::{MobilityAid, RiskLevel};
use crate::error::PoliticaError;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum PerfilesError {
    OverlappingVersion,
    InvalidMobilityAid,
    InvalidMode,
}

impl std::fmt::Display for PerfilesError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::OverlappingVersion => {
                write!(f, "ya existe una version valida para este instante")
            }
            Self::InvalidMobilityAid => write!(f, "mobility aid invalido"),
            Self::InvalidMode => write!(f, "modo invalido"),
        }
    }
}

impl From<PerfilesError> for PoliticaError {
    fn from(error: PerfilesError) -> Self {
        PoliticaError::validation(error.to_string())
    }
}

/// De donde salen las reglas del residente: del preset de su nivel, o del
/// preset mas los ajustes que alguien le hizo encima.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Mode {
    Preset,
    Custom,
}

impl Mode {
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::Preset => "preset",
            Self::Custom => "custom",
        }
    }

    pub fn parse(value: &str) -> Result<Self, PerfilesError> {
        match value {
            "preset" => Ok(Self::Preset),
            "custom" => Ok(Self::Custom),
            _ => Err(PerfilesError::InvalidMode),
        }
    }

    pub fn is_custom(&self) -> bool {
        matches!(self, Self::Custom)
    }
}

/// Overrides de reglas para un perfil. Wrapper tipado sobre `serde_json::Value`.
///
/// Se almacena como JSON string en DB y se parsea una vez en la frontera (repo).
/// Los consumidores reciben `Overrides` ya parseado, evitando re-parseos en cada
/// evaluación del motor.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Overrides(serde_json::Value);

impl Overrides {
    pub fn empty() -> Self {
        Self(serde_json::Value::Object(Default::default()))
    }

    pub fn as_value(&self) -> &serde_json::Value {
        &self.0
    }

    pub fn into_value(self) -> serde_json::Value {
        self.0
    }
}

impl std::fmt::Display for Overrides {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "{}", self.0)
    }
}

impl std::str::FromStr for Overrides {
    type Err = serde_json::Error;

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        let value: serde_json::Value = serde_json::from_str(s)?;
        Ok(Self(value))
    }
}

impl Default for Overrides {
    fn default() -> Self {
        Self::empty()
    }
}

impl serde::Serialize for Overrides {
    fn serialize<S: serde::Serializer>(&self, serializer: S) -> Result<S::Ok, S::Error> {
        self.0.serialize(serializer)
    }
}

impl<'de> serde::Deserialize<'de> for Overrides {
    fn deserialize<D: serde::Deserializer<'de>>(deserializer: D) -> Result<Self, D::Error> {
        serde_json::Value::deserialize(deserializer).map(Self)
    }
}

#[derive(Debug, Clone)]
pub struct ProfileInput {
    pub risk_level: RiskLevel,
    pub mobility_aid: MobilityAid,
    pub autopilot: bool,
    pub mode: Mode,
    pub template_id: String,
    pub overrides: Overrides,
    pub catalog_version: String,
}

pub type ProfileId = Id<AlarmProfileVersion>;

pub fn new_profile_id() -> ProfileId {
    use base64::Engine;
    let bytes: [u8; 16] = rand::random();
    let id = base64::engine::general_purpose::URL_SAFE_NO_PAD.encode(bytes);
    ProfileId::new(&id)
}

#[derive(Debug, Clone)]
pub struct AlarmProfileVersion {
    pub id: ProfileId,
    pub resident_id: String,
    pub valid_from: Instante,
    pub valid_to: Option<Instante>,
    pub risk_level: RiskLevel,
    pub mobility_aid: MobilityAid,
    pub autopilot: bool,
    pub mode: Mode,
    pub template_id: String,
    pub overrides: Overrides,
    pub catalog_version: String,
    pub updated_by: Option<String>,
    pub created_at: Instante,
}

/// Valores extraidos de `AlarmProfileVersion` para resolver el perfil efectivo.
#[derive(Debug, Clone)]
pub struct PerfilEfectivoInput {
    pub risk_level: RiskLevel,
    pub mobility_aid: MobilityAid,
    pub is_custom: bool,
    pub template_id: String,
    pub overrides: Overrides,
}

impl AlarmProfileVersion {
    pub fn to_perfil_input(&self) -> PerfilEfectivoInput {
        PerfilEfectivoInput {
            risk_level: self.risk_level,
            mobility_aid: self.mobility_aid,
            is_custom: self.mode.is_custom(),
            template_id: self.template_id.clone(),
            overrides: self.overrides.clone(),
        }
    }
}
