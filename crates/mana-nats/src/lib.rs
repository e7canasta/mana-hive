//! mana-nats: NATS JetStream integration for MANA Hub
//!
//! This crate provides the event-driven communication layer between components:
//! - Hub (Event Store)
//! - Engine (Digital Twin)
//! - Sentinel (Event Processor)
//! - Vigilancia (Alert Manager)
//!
//! # Topics
//!
//! - `evt_perception`: Perception events from IA-Edge
//! - `evt_scene`: Scene events from Engine
//! - `evt_notif`: Notification events from Sentinel
//!
//! # Usage
//!
//! ```rust,ignore
//! use mana_nats::{NatsBroker, topics::topics};
//!
//! #[tokio::main]
//! async fn main() {
//!     let broker = NatsBroker::new("nats://localhost:4222").await.unwrap();
//!
//!     // Publish perception event
//!     broker.publish_perception(&perception_event).await.unwrap();
//!
//!     // Subscribe to scene events
//!     let mut sub = broker.subscribe_scene("my-consumer").unwrap();
//!     while let Some(event) = sub.next().await {
//!         println!("Scene event: {:?}", event);
//!     }
//! }
//! ```

pub mod broker;
pub mod error;
pub mod publisher;
pub mod subscriber;
pub mod topics;
pub mod worker_loop;

// Re-exports
pub use broker::{NatsBroker, SharedBroker, create_broker};
pub use error::NatsError;
pub use publisher::NotificationEvent;
pub use subscriber::EventSubscriber;
pub use worker_loop::{init_tracing, shutdown_signal, connect_nats};
