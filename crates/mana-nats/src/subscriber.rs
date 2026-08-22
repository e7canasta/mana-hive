use async_nats::Subscriber;
use futures::StreamExt;
use tracing::info;

use crate::broker::NatsBroker;
use crate::error::NatsError;
use crate::topics::topics;

/// Wrapper for NATS subscriber with deserialization
pub struct EventSubscriber<T> {
    inner: Subscriber,
    _phantom: std::marker::PhantomData<T>,
}

impl<T: serde::de::DeserializeOwned> EventSubscriber<T> {
    /// Create a new EventSubscriber
    pub fn new(inner: Subscriber) -> Self {
        Self {
            inner,
            _phantom: std::marker::PhantomData,
        }
    }

    /// Get the next message
    pub async fn next(&mut self) -> Option<T> {
        match self.inner.next().await {
            Some(message) => {
                match serde_json::from_slice(&message.payload) {
                    Ok(event) => Some(event),
                    Err(e) => {
                        tracing::error!(error = %e, "Failed to deserialize event");
                        None
                    }
                }
            }
            None => None,
        }
    }
}

impl NatsBroker {
    /// Subscribe to perception events with durable consumer
    pub async fn subscribe_perception(
        &self,
        consumer_name: &str,
    ) -> Result<EventSubscriber<mana_engine_v2::PerceptionEvent>, NatsError> {
        let subject = topics::EVT_PERCEPTION.to_string();
        
        let subscriber = self
            .client()
            .subscribe(subject.clone())
            .await
            .map_err(|e| NatsError::SubscribeFailed(e.to_string()))?;

        info!(
            subject = %subject,
            consumer = %consumer_name,
            "Subscribed to perception events"
        );

        Ok(EventSubscriber::new(subscriber))
    }

    /// Subscribe to scene events with durable consumer
    pub async fn subscribe_scene(
        &self,
        consumer_name: &str,
    ) -> Result<EventSubscriber<mana_engine_v2::SceneEvent>, NatsError> {
        let subject = topics::EVT_SCENE.to_string();
        
        let subscriber = self
            .client()
            .subscribe(subject.clone())
            .await
            .map_err(|e| NatsError::SubscribeFailed(e.to_string()))?;

        info!(
            subject = %subject,
            consumer = %consumer_name,
            "Subscribed to scene events"
        );

        Ok(EventSubscriber::new(subscriber))
    }

    /// Subscribe to notification events with durable consumer
    pub async fn subscribe_notif(
        &self,
        consumer_name: &str,
    ) -> Result<EventSubscriber<crate::publisher::NotificationEvent>, NatsError> {
        let subject = topics::EVT_NOTIF.to_string();
        
        let subscriber = self
            .client()
            .subscribe(subject.clone())
            .await
            .map_err(|e| NatsError::SubscribeFailed(e.to_string()))?;

        info!(
            subject = %subject,
            consumer = %consumer_name,
            "Subscribed to notification events"
        );

        Ok(EventSubscriber::new(subscriber))
    }

    /// Subscribe to policy events with durable consumer
    pub async fn subscribe_policy(
        &self,
        consumer_name: &str,
    ) -> Result<EventSubscriber<crate::publisher::PolicyEvent>, NatsError> {
        let subject = topics::EVT_POLICY.to_string();
        
        let subscriber = self
            .client()
            .subscribe(subject.clone())
            .await
            .map_err(|e| NatsError::SubscribeFailed(e.to_string()))?;

        info!(
            subject = %subject,
            consumer = %consumer_name,
            "Subscribed to policy events"
        );

        Ok(EventSubscriber::new(subscriber))
    }
}
