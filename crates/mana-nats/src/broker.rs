use std::sync::Arc;

use async_nats::{Client, ConnectOptions};
use tracing::info;

use crate::error::NatsError;

/// NATS JetStream broker for event-driven communication
#[derive(Debug, Clone)]
pub struct NatsBroker {
    client: Client,
}

impl NatsBroker {
    /// Create a new NatsBroker connection
    pub async fn new(url: &str) -> Result<Self, NatsError> {
        let options = ConnectOptions::new();
        
        let client = options
            .connect(url)
            .await
            .map_err(|e| NatsError::ConnectionFailed(e.to_string()))?;

        info!("Connected to NATS at {}", url);

        Ok(Self { client })
    }

    /// Get the underlying NATS client
    pub fn client(&self) -> &Client {
        &self.client
    }

    /// Check if connection is active
    pub fn is_connected(&self) -> bool {
        self.client.connection_state() == async_nats::connection::State::Connected
    }
}

/// Shared broker instance
pub type SharedBroker = Arc<NatsBroker>;

/// Create a shared broker
pub async fn create_broker(url: &str) -> Result<SharedBroker, NatsError> {
    let broker = NatsBroker::new(url).await?;
    Ok(Arc::new(broker))
}
