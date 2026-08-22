//! Tipos transversales del SoR. Este crate no conoce ningun contexto de negocio.

use std::{fmt, marker::PhantomData, str::FromStr};

use chrono::{DateTime, SecondsFormat, Utc};
use serde::{de::Visitor, Deserialize, Deserializer, Serialize, Serializer};
use thiserror::Error;

/// Declara markers de tipos para que dos ids con el mismo texto no sean intercambiables.
#[macro_export]
macro_rules! define_kinds {
    ($($kind:ident),+ $(,)?) => {
        $(
            #[derive(Debug, Clone, Copy, Eq, PartialEq, Ord, PartialOrd, Hash)]
            pub enum $kind {}
        )+
    };
}

define_kinds!(Actor);

/// Identidad tipada por un marker `K`.
#[derive(Clone, Eq, PartialEq, Ord, PartialOrd, Hash)]
pub struct Id<K> {
    value: String,
    marker: PhantomData<fn() -> K>,
}

impl<K> Id<K> {
    pub fn new(value: impl Into<String>) -> Self {
        Self {
            value: value.into(),
            marker: PhantomData,
        }
    }

    pub fn as_str(&self) -> &str {
        &self.value
    }

    pub fn into_string(self) -> String {
        self.value
    }
}

impl<K> From<String> for Id<K> {
    fn from(value: String) -> Self {
        Self::new(value)
    }
}

impl<K> From<&str> for Id<K> {
    fn from(value: &str) -> Self {
        Self::new(value)
    }
}

impl<K> fmt::Debug for Id<K> {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter.debug_tuple("Id").field(&self.value).finish()
    }
}

impl<K> fmt::Display for Id<K> {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter.write_str(&self.value)
    }
}

impl<K> Serialize for Id<K> {
    fn serialize<S>(&self, serializer: S) -> Result<S::Ok, S::Error>
    where
        S: Serializer,
    {
        serializer.serialize_str(&self.value)
    }
}

impl<'de, K> Deserialize<'de> for Id<K> {
    fn deserialize<D>(deserializer: D) -> Result<Self, D::Error>
    where
        D: Deserializer<'de>,
    {
        String::deserialize(deserializer).map(Self::new)
    }
}

/// Instante normalizado a UTC y serializado como ISO-8601 terminado en `Z`.
#[derive(Clone, Copy, Debug, Eq, PartialEq, Ord, PartialOrd, Hash)]
pub struct Instante(DateTime<Utc>);

impl Instante {
    pub fn now() -> Self {
        Self(Utc::now())
    }

    pub fn new(value: DateTime<Utc>) -> Self {
        Self(value)
    }

    pub fn as_datetime(&self) -> &DateTime<Utc> {
        &self.0
    }

    pub fn into_datetime(self) -> DateTime<Utc> {
        self.0
    }
}

impl From<DateTime<Utc>> for Instante {
    fn from(value: DateTime<Utc>) -> Self {
        Self::new(value)
    }
}

impl From<Instante> for DateTime<Utc> {
    fn from(value: Instante) -> Self {
        value.into_datetime()
    }
}

impl fmt::Display for Instante {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter.write_str(&self.0.to_rfc3339_opts(SecondsFormat::Millis, true))
    }
}

impl FromStr for Instante {
    type Err = chrono::ParseError;

    fn from_str(value: &str) -> Result<Self, Self::Err> {
        DateTime::parse_from_rfc3339(value).map(|date| Self(date.with_timezone(&Utc)))
    }
}

impl Serialize for Instante {
    fn serialize<S>(&self, serializer: S) -> Result<S::Ok, S::Error>
    where
        S: Serializer,
    {
        serializer.serialize_str(&self.to_string())
    }
}

struct InstanteVisitor;

impl<'de> Visitor<'de> for InstanteVisitor {
    type Value = Instante;

    fn expecting(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter.write_str("an ISO-8601 UTC timestamp")
    }

    fn visit_str<E>(self, value: &str) -> Result<Self::Value, E>
    where
        E: serde::de::Error,
    {
        Instante::from_str(value).map_err(|error| E::custom(error.to_string()))
    }
}

impl<'de> Deserialize<'de> for Instante {
    fn deserialize<D>(deserializer: D) -> Result<Self, D::Error>
    where
        D: Deserializer<'de>,
    {
        deserializer.deserialize_str(InstanteVisitor)
    }
}

/// Codigos de error compartidos por los limites del sistema.
///
/// El vocabulario es el que ya emite la API Node: mientras dure la convivencia,
/// un handler Rust que invente un codigo rompe el contrato sin que nada lo note.
/// `VARIANTES` existe para que un test pueda cruzarlo contra el otro lado.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum Fallo {
    ValidationError,
    NotFound,
    Conflict,
    Forbidden,
    Unauthenticated,
    InvalidCredentials,
    RateLimited,
    InvalidJson,
    InvalidBody,
    PayloadTooLarge,
    InternalError,
    UpstreamUnavailable,
}

impl Fallo {
    /// Todas las variantes, para pruebas de exhaustividad y mensajes de error.
    pub const VARIANTES: [Self; 12] = [
        Self::ValidationError,
        Self::NotFound,
        Self::Conflict,
        Self::Forbidden,
        Self::Unauthenticated,
        Self::InvalidCredentials,
        Self::RateLimited,
        Self::InvalidJson,
        Self::InvalidBody,
        Self::PayloadTooLarge,
        Self::InternalError,
        Self::UpstreamUnavailable,
    ];

    pub const fn codigo(self) -> &'static str {
        match self {
            Self::ValidationError => "VALIDATION_ERROR",
            Self::NotFound => "NOT_FOUND",
            Self::Conflict => "CONFLICT",
            Self::Forbidden => "FORBIDDEN",
            Self::Unauthenticated => "UNAUTHENTICATED",
            Self::InvalidCredentials => "INVALID_CREDENTIALS",
            Self::RateLimited => "RATE_LIMITED",
            Self::InvalidJson => "INVALID_JSON",
            Self::InvalidBody => "INVALID_BODY",
            Self::PayloadTooLarge => "PAYLOAD_TOO_LARGE",
            Self::InternalError => "INTERNAL_ERROR",
            Self::UpstreamUnavailable => "UPSTREAM_UNAVAILABLE",
        }
    }

    pub const fn code(self) -> &'static str {
        self.codigo()
    }

    pub fn desde_codigo(codigo: &str) -> Option<Self> {
        Self::VARIANTES
            .into_iter()
            .find(|fallo| fallo.codigo() == codigo)
    }
}

impl fmt::Display for Fallo {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter.write_str(self.codigo())
    }
}

impl std::error::Error for Fallo {}

impl Serialize for Fallo {
    fn serialize<S>(&self, serializer: S) -> Result<S::Ok, S::Error>
    where
        S: Serializer,
    {
        serializer.serialize_str(self.codigo())
    }
}

impl<'de> Deserialize<'de> for Fallo {
    fn deserialize<D>(deserializer: D) -> Result<Self, D::Error>
    where
        D: Deserializer<'de>,
    {
        const CONOCIDOS: [&str; 12] = [
            "VALIDATION_ERROR",
            "NOT_FOUND",
            "CONFLICT",
            "FORBIDDEN",
            "UNAUTHENTICATED",
            "INVALID_CREDENTIALS",
            "RATE_LIMITED",
            "INVALID_JSON",
            "INVALID_BODY",
            "PAYLOAD_TOO_LARGE",
            "INTERNAL_ERROR",
            "UPSTREAM_UNAVAILABLE",
        ];
        let value = String::deserialize(deserializer)?;
        Fallo::desde_codigo(&value)
            .ok_or_else(|| serde::de::Error::unknown_variant(&value, &CONOCIDOS))
    }
}

/// Marca temporal y actor responsable de retirar un registro.
#[derive(Clone, Debug, Eq, PartialEq, Serialize, Deserialize)]
pub struct Retiro {
    pub retired_at: Instante,
    pub retired_by: Id<Actor>,
}

/// Contrato minimo para el borrado logico, sin conocer el agregado concreto.
pub trait Retirable {
    fn retiro(&self) -> Option<&Retiro>;
    fn retiro_mut(&mut self) -> &mut Option<Retiro>;

    fn retire(&mut self, retiro: Retiro) {
        *self.retiro_mut() = Some(retiro);
    }

    fn is_retired(&self) -> bool {
        self.retiro().is_some()
    }
}

/// Error de dominio minimo para parseos de instantes desde codigo no-JSON.
#[derive(Debug, Error)]
#[error("invalid instant: {0}")]
pub struct InvalidInstant(#[from] chrono::ParseError);

/// Error unificado para los binarios del workspace.
///
/// Cada variante envuelve un error de un crate distinto. Los bins solo necesitan
/// `type AppResult<T> = Result<T, AppError>` y un `?` por cualquier error del ecosistema.
#[derive(Debug, Error)]
pub enum AppError {
    #[error("catalog: {0}")]
    Catalog(String),

    #[error("database: {0}")]
    Database(String),

    #[error("nats: {0}")]
    Nats(String),

    #[error("http: {0}")]
    Http(String),

    #[error("seed: {0}")]
    Seed(String),

    #[error("persist: {0}")]
    Persist(String),

    #[error("config: {0}")]
    Config(String),

    #[error("io: {0}")]
    Io(#[from] std::io::Error),

    #[error("{0}")]
    Other(String),
}

#[cfg(test)]
mod tests {
    use super::*;

    #[derive(Default)]
    struct Record {
        retiro: Option<Retiro>,
    }

    impl Retirable for Record {
        fn retiro(&self) -> Option<&Retiro> {
            self.retiro.as_ref()
        }

        fn retiro_mut(&mut self) -> &mut Option<Retiro> {
            &mut self.retiro
        }
    }

    #[test]
    fn ids_are_typed_and_serialized_as_strings() {
        define_kinds!(Cama, Residente);
        let cama = Id::<Cama>::new("118-0");
        let residente = Id::<Residente>::new("resident-1");

        assert_eq!(cama.as_str(), "118-0");
        assert_eq!(serde_json::to_string(&cama).unwrap(), "\"118-0\"");
        assert_eq!(serde_json::from_str::<Id<Cama>>("\"118-0\"").unwrap(), cama);
        assert_ne!(cama.as_str(), residente.as_str());
    }

    #[test]
    fn instante_round_trips_as_utc_iso() {
        let instant: Instante = "2026-08-13T18:00:00.000Z".parse().unwrap();
        assert_eq!(instant.to_string(), "2026-08-13T18:00:00.000Z");
        let json = serde_json::to_string(&instant).unwrap();
        assert_eq!(json, "\"2026-08-13T18:00:00.000Z\"");
        assert_eq!(serde_json::from_str::<Instante>(&json).unwrap(), instant);
    }

    // Los codigos son los que ya emite Node en `api/server.js` y sus dominios.
    // Si esta lista y la de alla dejan de coincidir, un handler Rust responde
    // algo que ningun cliente sabe leer.
    #[test]
    fn fallo_exposes_the_contract_codes() {
        assert_eq!(Fallo::ValidationError.codigo(), "VALIDATION_ERROR");
        assert_eq!(Fallo::NotFound.codigo(), "NOT_FOUND");
        assert_eq!(Fallo::Conflict.codigo(), "CONFLICT");
        assert_eq!(Fallo::Forbidden.code(), "FORBIDDEN");
        assert_eq!(Fallo::Unauthenticated.codigo(), "UNAUTHENTICATED");
        assert_eq!(Fallo::InvalidCredentials.codigo(), "INVALID_CREDENTIALS");
        assert_eq!(Fallo::RateLimited.codigo(), "RATE_LIMITED");
        assert_eq!(Fallo::InvalidJson.codigo(), "INVALID_JSON");
        assert_eq!(Fallo::InvalidBody.codigo(), "INVALID_BODY");
        assert_eq!(Fallo::PayloadTooLarge.codigo(), "PAYLOAD_TOO_LARGE");
        assert_eq!(Fallo::InternalError.codigo(), "INTERNAL_ERROR");
        assert_eq!(Fallo::UpstreamUnavailable.codigo(), "UPSTREAM_UNAVAILABLE");
    }

    #[test]
    fn fallo_round_trips_through_its_code() {
        for fallo in Fallo::VARIANTES {
            assert_eq!(Fallo::desde_codigo(fallo.codigo()), Some(fallo));
            let json = serde_json::to_string(&fallo).unwrap();
            assert_eq!(serde_json::from_str::<Fallo>(&json).unwrap(), fallo);
        }
        assert_eq!(Fallo::desde_codigo("FOUND"), None);
    }

    #[test]
    fn retiro_is_explicit_and_actor_typed() {
        let mut record = Record::default();
        assert!(!record.is_retired());
        record.retire(Retiro {
            retired_at: Instante::now(),
            retired_by: Id::new("actor-1"),
        });
        assert!(record.is_retired());
        assert_eq!(record.retiro().unwrap().retired_by.as_str(), "actor-1");
    }
}
