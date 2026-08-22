use std::sync::Arc;
use std::time::Duration;

use thiserror::Error;
use tokio::sync::{broadcast, RwLock};
use tracing::info;

use mana_engine_v2::{DigitalTwin, PerceptionEvent};
use mana_nats::{NatsBroker, topics::{topics, consumers}};

const MAX_RETRIES: u32 = 3;
const BASE_DELAY_MS: u64 = 100;

#[derive(Debug, Error)]
pub enum EngineWorkerError {
    #[error("NATS subscribe failed: {0}")]
    Subscribe(String),
    #[error("Invalid perception event: {0}")]
    InvalidEvent(String),
    #[error("Publish failed after {retries} retries: {last_error}")]
    PublishFailed { retries: u32, last_error: String },
}

impl From<mana_nats::NatsError> for EngineWorkerError {
    fn from(e: mana_nats::NatsError) -> Self {
        Self::Subscribe(e.to_string())
    }
}

/// Engine worker that subscribes to perception events and publishes scene events.
///
/// The `DigitalTwin` is shared via `Arc<RwLock<...>>` so that:
/// - The worker gets exclusive write access when processing events.
/// - Any readers (e.g., AppState queries) get shared read access.
pub struct EngineWorker {
    broker: Arc<NatsBroker>,
    twin: Arc<RwLock<DigitalTwin>>,
}

impl EngineWorker {
    /// Create a new EngineWorker
    pub fn new(broker: Arc<NatsBroker>, twin: Arc<RwLock<DigitalTwin>>) -> Self {
        Self { broker, twin }
    }

    /// Run the engine worker with graceful shutdown
    pub async fn run(&self, shutdown: broadcast::Sender<()>) -> Result<(), EngineWorkerError> {
        info!("Engine worker starting");

        let mut subscriber = self.broker
            .subscribe_perception(consumers::ENGINE_PERCEPTION)
            .await?;

        let mut policy_subscriber = self.broker
            .subscribe_policy(consumers::ENGINE_POLICY)
            .await?;

        let mut rx = shutdown.subscribe();

        info!("Subscribed to {} and {}", topics::EVT_PERCEPTION, topics::EVT_POLICY);

        loop {
            tokio::select! {
                Some(perception) = subscriber.next() => {
                    if let Err(e) = self.process_perception(perception).await {
                        tracing::error!(err = %e, "Failed to process perception event");
                    }
                }
                Some(policy_event) = policy_subscriber.next() => {
                    self.process_policy_event(policy_event).await;
                }
                _ = rx.recv() => {
                    info!("Shutdown signal received, draining...");
                    break;
                }
            }
        }

        info!("Engine worker stopped");
        Ok(())
    }

    /// Process a single perception event.
    ///
    /// Acquires a write lock on the DigitalTwin, processes the event, and
    /// releases the lock. This ensures state accumulates correctly across events.
    async fn process_perception(&self, perception: PerceptionEvent) -> Result<(), EngineWorkerError> {
        let bed_id = perception.bed_id.clone().unwrap_or_default();
        let trace_id = perception.trace_id.clone();

        if perception.event_id.is_empty() {
            return Err(EngineWorkerError::InvalidEvent("event_id is required".into()));
        }
        if perception.monitor_key.is_empty() {
            return Err(EngineWorkerError::InvalidEvent("monitor_key is required".into()));
        }

        info!(
            event_id = %perception.event_id,
            bed_id = %bed_id,
            trace_id = trace_id.as_deref().unwrap_or("-"),
            "Processing perception event"
        );

        // Acquire write lock, process event, release lock.
        let scene_events = {
            let mut twin = self.twin.write().await;
            twin.on_perception_event(perception)
        };

        let broker = self.broker.clone();
        let bed_id_clone = bed_id.clone();
        tokio::spawn(async move {
            for scene_event in &scene_events {
                info!(
                    event_type = ?&scene_event.event_type,
                    bed_id = %scene_event.bed_id,
                    trace_id = scene_event.trace_id.as_deref().unwrap_or("-"),
                    "Publishing scene event"
                );

                let mut last_err = None;
                for attempt in 0..MAX_RETRIES {
                    match broker.publish_scene(scene_event).await {
                        Ok(()) => {
                            last_err = None;
                            break;
                        }
                        Err(e) => {
                            tracing::warn!(
                                error = %e,
                                bed_id = %bed_id_clone,
                                attempt = attempt + 1,
                                max_retries = MAX_RETRIES,
                                "Failed to publish scene event, retrying"
                            );
                            last_err = Some(e.to_string());
                            let delay = Duration::from_millis(BASE_DELAY_MS * 2u64.pow(attempt));
                            tokio::time::sleep(delay).await;
                        }
                    }
                }

                if let Some(e) = last_err {
                    tracing::error!(
                        err = %e,
                        bed_id = %bed_id_clone,
                        "Failed to publish scene event after max retries"
                    );
                }
            }
        });

        Ok(())
    }

    /// Process a policy event.
    ///
    /// Engine is pure state tracking (DigitalTwin FSM) and does not evaluate
    /// alarm rules — that is Sentinel's responsibility. For now we acknowledge
    /// the event and log it. Future use: update dwell thresholds or other
    /// state-tracking parameters from policy.
    async fn process_policy_event(&self, event: mana_nats::publisher::PolicyEvent) {
        info!(
            event_type = %event.event_type,
            resident_id = %event.resident_id,
            policy_type = %event.policy_type,
            "Policy event received (acknowledged, no action required)"
        );
    }
}

/// Run the engine worker in a spawned task with graceful shutdown
pub fn spawn_engine_worker(
    broker: Arc<NatsBroker>,
    twin: Arc<RwLock<DigitalTwin>>,
    shutdown: broadcast::Sender<()>,
) -> tokio::task::JoinHandle<Result<(), EngineWorkerError>> {
    tokio::spawn(async move {
        let worker = EngineWorker::new(broker, twin);
        worker.run(shutdown).await
    })
}
