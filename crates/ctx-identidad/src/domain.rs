use std::{collections::BTreeSet, fmt, str::FromStr};

use argon2::{
    password_hash::{
        PasswordHash as ParsedPasswordHash, PasswordHasher, PasswordVerifier, SaltString,
    },
    Argon2,
};
use base64::{engine::general_purpose::URL_SAFE_NO_PAD, Engine as _};
use chrono::Duration;
use mana_kernel::{define_kinds, Actor, Id, Instante};
use rand::RngExt;
use sha2::{Digest, Sha256};
use thiserror::Error;

define_kinds!(UserKind, SessionKind);

pub type UserId = Id<UserKind>;
pub type SessionId = Id<SessionKind>;

#[derive(Clone, Debug, Eq, PartialEq, Error)]
pub enum DomainError {
    #[error("username debe tener entre 1 y 64 caracteres")]
    InvalidUsername,
    #[error("username contiene caracteres invalidos")]
    InvalidUsernameCharacters,
    #[error("display_name debe ser un texto no vacio de hasta 120 caracteres")]
    InvalidDisplayName,
    #[error("job_title debe ser texto de hasta 80 caracteres")]
    InvalidJobTitle,
    #[error("password debe tener al menos 6 caracteres")]
    ShortPassword,
    #[error("password es demasiado larga")]
    LongPassword,
    #[error("role debe ser owner, supervisor o staff")]
    InvalidRole,
    #[error("password hash invalido")]
    InvalidPasswordHash,
    #[error("no se pudo generar un token seguro")]
    Randomness,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub enum Role {
    Owner,
    Supervisor,
    Staff,
}

impl Role {
    pub const fn as_str(&self) -> &'static str {
        match self {
            Self::Owner => "owner",
            Self::Supervisor => "supervisor",
            Self::Staff => "staff",
        }
    }

    pub fn features(&self) -> &'static [Feature] {
        match self {
            Self::Owner => &[
                Feature::Nursing,
                Feature::Residents,
                Feature::Alerts,
                Feature::Reports,
                Feature::Configuration,
            ],
            Self::Supervisor => &[
                Feature::Nursing,
                Feature::Residents,
                Feature::Alerts,
                Feature::Reports,
                Feature::Configuration,
            ],
            Self::Staff => &[Feature::Nursing],
        }
    }

    pub fn capabilities(&self) -> &'static [Capability] {
        match self {
            Self::Owner => &[
                Capability::MasterStructureRead,
                Capability::MasterStructureWrite,
                Capability::MonitoringBoardRead,
                Capability::MonitoringLiveRead,
                Capability::ResidentsListRead,
                Capability::ResidentsSnapshotRead,
                Capability::ResidentsLiveRead,
                Capability::ResidentsWrite,
                Capability::ResidentsNotesRead,
                Capability::ResidentsNotesWrite,
                Capability::SleepRead,
                Capability::MobilityRead,
                Capability::BathroomRead,
                Capability::CareRead,
                Capability::IncidentsRead,
                Capability::IncidentsManage,
                Capability::RoundsRead,
                Capability::RoundsManage,
                Capability::AlertsRead,
                Capability::AlertsManage,
                Capability::AnalyticsRead,
                Capability::AuditRead,
                Capability::ConfigAlarmsRead,
                Capability::ConfigAlarmsManage,
                Capability::StreamsRead,
                Capability::StreamsWrite,
            ],
            Self::Supervisor => &[
                Capability::MasterStructureRead,
                Capability::MasterStructureWrite,
                Capability::MonitoringBoardRead,
                Capability::MonitoringLiveRead,
                Capability::ResidentsListRead,
                Capability::ResidentsSnapshotRead,
                Capability::ResidentsLiveRead,
                Capability::ResidentsWrite,
                Capability::ResidentsNotesRead,
                Capability::ResidentsNotesWrite,
                Capability::SleepRead,
                Capability::MobilityRead,
                Capability::BathroomRead,
                Capability::CareRead,
                Capability::IncidentsRead,
                Capability::IncidentsManage,
                Capability::RoundsRead,
                Capability::RoundsManage,
                Capability::AlertsRead,
                Capability::AlertsManage,
                Capability::AnalyticsRead,
                Capability::AuditRead,
                Capability::ConfigAlarmsRead,
                Capability::ConfigAlarmsManage,
                Capability::StreamsRead,
                Capability::StreamsWrite,
            ],
            Self::Staff => &[
                Capability::MasterStructureRead,
                Capability::MonitoringBoardRead,
                Capability::MonitoringLiveRead,
                Capability::ResidentsSnapshotRead,
                Capability::ResidentsLiveRead,
                Capability::SleepRead,
                Capability::MobilityRead,
                Capability::BathroomRead,
                Capability::CareRead,
                Capability::RoundsRead,
                Capability::RoundsManage,
                Capability::AlertsRead,
                Capability::AlertsManage,
            ],
        }
    }
}

impl FromStr for Role {
    type Err = DomainError;

    fn from_str(value: &str) -> Result<Self, Self::Err> {
        match value {
            "owner" => Ok(Self::Owner),
            "supervisor" => Ok(Self::Supervisor),
            "staff" => Ok(Self::Staff),
            _ => Err(DomainError::InvalidRole),
        }
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum Feature {
    Nursing,
    Residents,
    Alerts,
    Reports,
    Configuration,
}

impl Feature {
    pub const fn as_str(self) -> &'static str {
        match self {
            Self::Nursing => "nursing",
            Self::Residents => "residents",
            Self::Alerts => "alerts",
            Self::Reports => "reports",
            Self::Configuration => "configuration",
        }
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum Capability {
    MasterStructureRead,
    MasterStructureWrite,
    MonitoringBoardRead,
    MonitoringLiveRead,
    ResidentsListRead,
    ResidentsSnapshotRead,
    ResidentsLiveRead,
    ResidentsWrite,
    ResidentsNotesRead,
    ResidentsNotesWrite,
    SleepRead,
    MobilityRead,
    BathroomRead,
    CareRead,
    IncidentsRead,
    IncidentsManage,
    RoundsRead,
    RoundsManage,
    AlertsRead,
    AlertsManage,
    AnalyticsRead,
    AuditRead,
    ConfigAlarmsRead,
    ConfigAlarmsManage,
    StreamsRead,
    StreamsWrite,
}

impl Capability {
    pub const fn as_str(self) -> &'static str {
        match self {
            Self::MasterStructureRead => "master.structure.read",
            Self::MasterStructureWrite => "master.structure.write",
            Self::MonitoringBoardRead => "monitoring.board.read",
            Self::MonitoringLiveRead => "monitoring.live.read",
            Self::ResidentsListRead => "residents.list.read",
            Self::ResidentsSnapshotRead => "residents.snapshot.read",
            Self::ResidentsLiveRead => "residents.live.read",
            Self::ResidentsWrite => "residents.write",
            Self::ResidentsNotesRead => "residents.notes.read",
            Self::ResidentsNotesWrite => "residents.notes.write",
            Self::SleepRead => "sleep.read",
            Self::MobilityRead => "mobility.read",
            Self::BathroomRead => "bathroom.read",
            Self::CareRead => "care.read",
            Self::IncidentsRead => "incidents.read",
            Self::IncidentsManage => "incidents.manage",
            Self::RoundsRead => "rounds.read",
            Self::RoundsManage => "rounds.manage",
            Self::AlertsRead => "alerts.read",
            Self::AlertsManage => "alerts.manage",
            Self::AnalyticsRead => "analytics.read",
            Self::AuditRead => "audit.read",
            Self::ConfigAlarmsRead => "config.alarms.read",
            Self::ConfigAlarmsManage => "config.alarms.manage",
            Self::StreamsRead => "streams.read",
            Self::StreamsWrite => "streams.write",
        }
    }

    pub const fn all() -> &'static [Self] {
        &[
            Self::MasterStructureRead,
            Self::MasterStructureWrite,
            Self::MonitoringBoardRead,
            Self::MonitoringLiveRead,
            Self::ResidentsListRead,
            Self::ResidentsSnapshotRead,
            Self::ResidentsLiveRead,
            Self::ResidentsWrite,
            Self::ResidentsNotesRead,
            Self::ResidentsNotesWrite,
            Self::SleepRead,
            Self::MobilityRead,
            Self::BathroomRead,
            Self::CareRead,
            Self::IncidentsRead,
            Self::IncidentsManage,
            Self::RoundsRead,
            Self::RoundsManage,
            Self::AlertsRead,
            Self::AlertsManage,
            Self::AnalyticsRead,
            Self::AuditRead,
            Self::ConfigAlarmsRead,
            Self::ConfigAlarmsManage,
            Self::StreamsRead,
            Self::StreamsWrite,
        ]
    }
}

#[derive(Clone, Eq, PartialEq)]
pub struct Username(String);

impl Username {
    pub fn parse(value: impl AsRef<str>) -> Result<Self, DomainError> {
        let normalized = value.as_ref().trim().to_ascii_lowercase();
        let length = normalized.chars().count();
        if !(1..=64).contains(&length) {
            return Err(DomainError::InvalidUsername);
        }
        if normalized
            .chars()
            .any(|character| character.is_whitespace() || character.is_control())
        {
            return Err(DomainError::InvalidUsernameCharacters);
        }
        Ok(Self(normalized))
    }

    pub fn as_str(&self) -> &str {
        &self.0
    }
}

impl fmt::Debug for Username {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter.debug_tuple("Username").field(&self.0).finish()
    }
}

#[derive(Clone, Eq, PartialEq)]
pub struct DisplayName(String);

impl DisplayName {
    pub fn parse(value: impl AsRef<str>) -> Result<Self, DomainError> {
        let normalized = value.as_ref().trim().to_owned();
        if normalized.is_empty() || normalized.chars().count() > 120 {
            return Err(DomainError::InvalidDisplayName);
        }
        Ok(Self(normalized))
    }

    pub fn as_str(&self) -> &str {
        &self.0
    }
}

impl fmt::Debug for DisplayName {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter.debug_tuple("DisplayName").field(&self.0).finish()
    }
}

#[derive(Clone, Eq, PartialEq)]
pub struct JobTitle(String);

impl JobTitle {
    pub fn parse(value: impl AsRef<str>) -> Result<Self, DomainError> {
        let normalized = value.as_ref().trim().to_owned();
        if normalized.is_empty() || normalized.chars().count() > 80 {
            return Err(DomainError::InvalidJobTitle);
        }
        Ok(Self(normalized))
    }

    pub fn as_str(&self) -> &str {
        &self.0
    }
}

impl fmt::Debug for JobTitle {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter.debug_tuple("JobTitle").field(&self.0).finish()
    }
}

#[derive(Clone)]
pub struct PasswordHash(String);

impl PasswordHash {
    pub fn hash(password: &str) -> Result<Self, DomainError> {
        validate_password(password)?;
        let mut salt_bytes = [0_u8; 16];
        rand::rng().fill(&mut salt_bytes);
        let salt = SaltString::encode_b64(&salt_bytes).map_err(|_| DomainError::Randomness)?;
        Argon2::default()
            .hash_password(password.as_bytes(), &salt)
            .map(|hash| Self(hash.to_string()))
            .map_err(|_| DomainError::InvalidPasswordHash)
    }

    pub fn from_stored(value: impl Into<String>) -> Result<Self, DomainError> {
        let value = value.into();
        ParsedPasswordHash::new(&value).map_err(|_| DomainError::InvalidPasswordHash)?;
        Ok(Self(value))
    }

    pub fn verify(&self, password: &str) -> bool {
        let Ok(parsed) = ParsedPasswordHash::new(&self.0) else {
            return false;
        };
        Argon2::default()
            .verify_password(password.as_bytes(), &parsed)
            .is_ok()
    }

    pub(crate) fn as_stored(&self) -> &str {
        &self.0
    }
}

impl fmt::Debug for PasswordHash {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter.write_str("PasswordHash(<redacted>)")
    }
}

pub fn validate_password(password: &str) -> Result<(), DomainError> {
    let length = password.chars().count();
    if length < 6 {
        return Err(DomainError::ShortPassword);
    }
    if length > 256 {
        return Err(DomainError::LongPassword);
    }
    Ok(())
}

#[derive(Clone, Eq, PartialEq)]
pub struct ClearSessionToken(String);

impl ClearSessionToken {
    pub fn generate() -> Result<Self, DomainError> {
        let mut bytes = [0_u8; 32];
        rand::rng().fill(&mut bytes);
        Ok(Self(URL_SAFE_NO_PAD.encode(bytes)))
    }

    pub fn as_str(&self) -> &str {
        &self.0
    }
}

impl fmt::Debug for ClearSessionToken {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter.write_str("ClearSessionToken(<redacted>)")
    }
}

#[derive(Clone, Eq, PartialEq)]
pub struct TokenHash([u8; 32]);

impl TokenHash {
    pub fn from_token(token: &str) -> Self {
        let digest = Sha256::digest(token.as_bytes());
        let mut bytes = [0_u8; 32];
        bytes.copy_from_slice(&digest);
        Self(bytes)
    }

    pub fn as_bytes(&self) -> &[u8; 32] {
        &self.0
    }
}

impl fmt::Debug for TokenHash {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter.write_str("TokenHash(<redacted>)")
    }
}

#[derive(Clone)]
pub struct User {
    pub id: UserId,
    pub username: Username,
    pub display_name: DisplayName,
    pub role: Role,
    pub job_title: Option<JobTitle>,
    pub retired_at: Option<Instante>,
    pub retired_by: Option<Id<Actor>>,
    pub created_at: Instante,
    pub updated_at: Instante,
    password_hash: PasswordHash,
}

impl User {
    pub fn is_active(&self) -> bool {
        self.retired_at.is_none()
    }

    pub fn verify_password(&self, password: &str) -> bool {
        self.password_hash.verify(password)
    }

    pub fn public(&self, enabled_capabilities: &BTreeSet<String>) -> AuthenticatedUser {
        let capabilities = self
            .role
            .capabilities()
            .iter()
            .copied()
            .filter(|capability| enabled_capabilities.contains(capability.as_str()))
            .collect();
        AuthenticatedUser {
            id: self.id.clone(),
            username: self.username.clone(),
            display_name: self.display_name.clone(),
            role: self.role.clone(),
            features: self.role.features().to_vec(),
            capabilities,
        }
    }

    pub(crate) fn from_persisted(parts: PersistedUser) -> Self {
        Self {
            id: parts.id,
            username: parts.username,
            display_name: parts.display_name,
            role: parts.role,
            job_title: parts.job_title,
            retired_at: parts.retired_at,
            retired_by: parts.retired_by,
            created_at: parts.created_at,
            updated_at: parts.updated_at,
            password_hash: parts.password_hash,
        }
    }

    pub(crate) fn password_hash(&self) -> &PasswordHash {
        &self.password_hash
    }
}

pub(crate) struct PersistedUser {
    pub id: UserId,
    pub username: Username,
    pub display_name: DisplayName,
    pub role: Role,
    pub job_title: Option<JobTitle>,
    pub retired_at: Option<Instante>,
    pub retired_by: Option<Id<Actor>>,
    pub created_at: Instante,
    pub updated_at: Instante,
    pub password_hash: PasswordHash,
}

impl fmt::Debug for User {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter
            .debug_struct("User")
            .field("id", &self.id)
            .field("username", &self.username)
            .field("display_name", &self.display_name)
            .field("role", &self.role)
            .field("job_title", &self.job_title)
            .field("retired_at", &self.retired_at)
            .field("retired_by", &self.retired_by)
            .field("created_at", &self.created_at)
            .field("updated_at", &self.updated_at)
            .finish_non_exhaustive()
    }
}

#[derive(Clone, Debug)]
pub struct AuthenticatedUser {
    pub id: UserId,
    pub username: Username,
    pub display_name: DisplayName,
    pub role: Role,
    pub features: Vec<Feature>,
    pub capabilities: Vec<Capability>,
}

#[derive(Clone)]
pub struct LoginSession {
    pub token: ClearSessionToken,
    pub expires_at: Instante,
    pub user: User,
}

#[derive(Clone, Debug)]
pub struct UpdateUserInput {
    pub display_name: Option<String>,
    pub role: Option<Role>,
    pub job_title: Option<Option<String>>,
    pub active: Option<bool>,
    pub password: Option<String>,
}

#[derive(Clone, Debug)]
pub struct CreateUserInput {
    pub username: String,
    pub display_name: String,
    pub role: Role,
    pub job_title: Option<String>,
    pub password: String,
}

pub fn new_user_id() -> Result<UserId, DomainError> {
    let token = ClearSessionToken::generate()?;
    Ok(Id::new(format!("user-{}", &token.as_str()[..16])))
}

pub fn session_expiration(now: Instante) -> Instante {
    Instante::new(*now.as_datetime() + Duration::hours(8))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn normalizes_usernames_and_rejects_whitespace() {
        assert_eq!(Username::parse(" Gaston ").unwrap().as_str(), "gaston");
        assert_eq!(
            Username::parse(" ").unwrap_err(),
            DomainError::InvalidUsername
        );
        assert_eq!(
            Username::parse("gas ton").unwrap_err(),
            DomainError::InvalidUsernameCharacters
        );
    }

    #[test]
    fn password_hash_verifies_without_exposing_the_secret() {
        let hash = PasswordHash::hash("gaston-demo").unwrap();
        assert!(hash.verify("gaston-demo"));
        assert!(!hash.verify("wrong-password"));
        assert!(!format!("{hash:?}").contains("gaston"));
    }

    #[test]
    fn token_hash_is_always_32_bytes_and_token_is_not_debuggable() {
        let token = ClearSessionToken::generate().unwrap();
        let hash = TokenHash::from_token(token.as_str());
        assert_eq!(hash.as_bytes().len(), 32);
        assert!(!format!("{token:?}").contains(token.as_str()));
    }

    #[test]
    fn role_capabilities_are_intersected_with_enabled_configuration() {
        let enabled = ["master.structure.read".to_owned()]
            .into_iter()
            .collect::<BTreeSet<_>>();
        let user = User {
            id: Id::new("user-1"),
            username: Username::parse("gaston").unwrap(),
            display_name: DisplayName::parse("Gaston").unwrap(),
            role: Role::Supervisor,
            job_title: None,
            retired_at: None,
            retired_by: None,
            created_at: "2026-08-18T00:00:00.000Z".parse().unwrap(),
            updated_at: "2026-08-18T00:00:00.000Z".parse().unwrap(),
            password_hash: PasswordHash::hash("gaston-demo").unwrap(),
        };
        let public = user.public(&enabled);
        assert_eq!(public.capabilities, vec![Capability::MasterStructureRead]);
    }
}
