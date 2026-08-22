use tracing::info;

use crate::broker::NatsBroker;
use crate::error::NatsError;
use crate::topics::topics;

impl NatsBroker {
    /// Publish a perception event to evt_perception
    pub async fn publish_perception(
        &self,
        event: &mana_engine_v2::PerceptionEvent,
    ) -> Result<(), NatsError> {
        let payload = serde_json::to_vec(event)
            .map_err(|e| NatsError::SerializationFailed(e.to_string()))?;

        self.client()
            .publish(topics::EVT_PERCEPTION, payload.into())
            .await
            .map_err(|e| NatsError::PublishFailed(e.to_string()))?;

        info!(
            event_id = %event.event_id,
            bed_id = %event.bed_id.as_deref().unwrap_or("unknown"),
            "Published perception event"
        );

        Ok(())
    }

    /// Publish a scene event to evt_scene
    pub async fn publish_scene(
        &self,
        event: &mana_engine_v2::SceneEvent,
    ) -> Result<(), NatsError> {
        let payload = serde_json::to_vec(event)
            .map_err(|e| NatsError::SerializationFailed(e.to_string()))?;

        self.client()
            .publish(topics::EVT_SCENE, payload.into())
            .await
            .map_err(|e| NatsError::PublishFailed(e.to_string()))?;

        info!(
            event_type = ?&event.event_type,
            bed_id = %event.bed_id,
            "Published scene event"
        );

        Ok(())
    }

    /// Publish a notification event to evt_notif
    pub async fn publish_notif(
        &self,
        category: &str,
        bed_id: &str,
        resident_id: Option<&str>,
        scene_event: &mana_engine_v2::SceneEvent,
    ) -> Result<(), NatsError> {
        let notif = NotificationEvent {
            category: category.to_string(),
            bed_id: bed_id.to_string(),
            resident_id: resident_id.map(|s| s.to_string()),
            event_type: scene_event.event_type.as_str().to_string(),
            timestamp: scene_event.timestamp.to_rfc3339(),
            rule_id: match &scene_event.trigger {
                mana_engine_v2::scene_event::TriggerInfo::DwellCompleted { rule_id, .. } => {
                    Some(rule_id.clone())
                }
                _ => None,
            },
            risk_level: match category {
                "alarm" => Some("high".to_string()),
                "notify" => Some("medium".to_string()),
                _ => None,
            },
            trace_id: scene_event.trace_id.clone(),
        };

        let payload = serde_json::to_vec(&notif)
            .map_err(|e| NatsError::SerializationFailed(e.to_string()))?;

        self.client()
            .publish(topics::EVT_NOTIF, payload.into())
            .await
            .map_err(|e| NatsError::PublishFailed(e.to_string()))?;

        info!(
            category = %category,
            bed_id = %bed_id,
            "Published notification event"
        );

        Ok(())
    }
}

/// Notification event structure
#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct NotificationEvent {
    pub category: String,
    pub bed_id: String,
    pub resident_id: Option<String>,
    pub event_type: String,
    pub timestamp: String,
    pub rule_id: Option<String>,
    pub risk_level: Option<String>,
    /// Trace ID para correlación cross-pipeline
    #[serde(default)]
    pub trace_id: Option<String>,
}

/// Policy event structure
#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct PolicyEvent {
    pub event_type: String,
    pub resident_id: String,
    pub policy_type: String,
    pub effective_at: String,
    pub payload: serde_json::Value,
}

impl NatsBroker {
    /// Publish a policy event to evt_policy
    pub async fn publish_policy(
        &self,
        event: &PolicyEvent,
    ) -> Result<(), NatsError> {
        let payload = serde_json::to_vec(event)
            .map_err(|e| NatsError::SerializationFailed(e.to_string()))?;

        self.client()
            .publish(topics::EVT_POLICY, payload.into())
            .await
            .map_err(|e| NatsError::PublishFailed(e.to_string()))?;

        info!(
            resident_id = %event.resident_id,
            policy_type = %event.policy_type,
            "Published policy event"
        );

        Ok(())
    }
}
