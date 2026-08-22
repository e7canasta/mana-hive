use std::sync::Arc;

use tracing::{info, error};

use ctx_vigilancia::{AlertInput, AlertLevel, EvidenceKind, VigilanciaStore};
use mana_nats::{NatsBroker, NotificationEvent, topics::topics, topics::consumers};

/// Vigilancia worker that subscribes to notifications and creates alerts
pub struct VigilanciaWorker {
    broker: Arc<NatsBroker>,
    store: VigilanciaStore,
}

impl VigilanciaWorker {
    /// Create a new VigilanciaWorker
    pub fn new(broker: Arc<NatsBroker>, store: VigilanciaStore) -> Self {
        Self { broker, store }
    }

    /// Run the vigilancia worker (subscribes to evt_notif, creates alerts)
    pub async fn run(&self) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
        info!("Vigilancia worker starting");
        
        let mut subscriber = self.broker
            .subscribe_notif(consumers::VIGILANCIA_NOTIF)
            .await?;

        info!("Subscribed to {}", topics::EVT_NOTIF);

        while let Some(notification) = subscriber.next().await {
            self.process_notification(notification).await;
        }

        info!("Vigilancia worker stopped");
        Ok(())
    }

    /// Process a single notification event
    async fn process_notification(&self, notification: NotificationEvent) {
        let bed_id = &notification.bed_id;
        
        info!(
            category = %notification.category,
            bed_id = %bed_id,
            "Processing notification"
        );

        // Only create alerts for Alarm category
        if notification.category == "alarm" {
            let input = AlertInput {
                resident_id: notification.resident_id.clone(),
                bed_id: bed_id.clone(),
                evidence_kind: EvidenceKind::SensorEvent,
                evidence_ref: notification.rule_id.clone(),
                rule_id: notification.rule_id.clone().unwrap_or_else(|| "unknown".to_string()),
                level: AlertLevel::High,
                title: format!("{} event detected", notification.event_type),
                detail: Some(format!(
                    "Category: {}, Rule: {:?}",
                    notification.category,
                    notification.rule_id
                )),
                occurred_at: mana_kernel::Instante::now(),
            };

            match self.store.create_alert(input) {
                Ok(alert) => {
                    info!(
                        alert_id = %alert.id,
                        bed_id = %bed_id,
                        "Alert created"
                    );
                }
                Err(e) => {
                    error!(
                        error = %e,
                        bed_id = %bed_id,
                        "Failed to create alert"
                    );
                }
            }
        } else {
            info!(
                category = %notification.category,
                bed_id = %bed_id,
                "Non-alarm notification, skipping alert creation"
            );
        }
    }
}

/// Run the vigilancia worker in a spawned task
pub fn spawn_vigilancia_worker(
    broker: Arc<NatsBroker>,
    store: VigilanciaStore,
) -> tokio::task::JoinHandle<Result<(), Box<dyn std::error::Error + Send + Sync>>> {
    tokio::spawn(async move {
        let worker = VigilanciaWorker::new(broker, store);
        worker.run().await
    })
}
