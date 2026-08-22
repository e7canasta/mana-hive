//! mana-sentinel: event processor para el sistema de observación.
//!
//! Sentinel consume scene events de Engine, evalúa reglas de alarma,
//! gestiona incidentes y dispatcha notificaciones.
//!
//! ## Flujo
//!
//! 1. Recibe scene events de Engine via NATS
//! 2. Evalúa reglas contra alarm catalog
//! 3. Resuelve perfil efectivo del residente
//! 4. Deduplica alertas (in-memory)
//! 5. Crea alertas via Hub API
//! 6. Gestiona incidentes
//! 7. Dispatcha notificaciones

pub mod client;
pub mod checkpoint;
pub mod clip_window;
pub mod dispatch;
pub mod error;
pub mod evaluator;
pub mod incident;
pub mod rule_engine;

pub use client::{EngineClient, HubClient};
pub use checkpoint::Checkpoint;
pub use clip_window::ClipWindowManager;
pub use error::SentinelError;
pub use evaluator::{CategorizedEvent, EventCategory};
pub use incident::{
    Incident, IncidentEvent, IncidentEventType, IncidentManager,
    IncidentPreset, IncidentStatus,
};
pub use rule_engine::RuleEngine;
