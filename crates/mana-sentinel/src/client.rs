use mana_engine_v2::PerceptionEvent;
use serde::{Deserialize, Serialize};

use crate::error::SentinelError;

/// Respuesta del hub al listar sensor events.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SensorEventsResponse {
    pub events: Vec<SensorEvent>,
    pub has_more: bool,
    pub last_timestamp: String,
}

/// Sensor event crudo del hub.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SensorEvent {
    pub id: String,
    pub monitor_key: String,
    pub bed_id: Option<String>,
    pub resident_id: Option<String>,
    pub state: Option<String>,
    pub sleeping: Option<bool>,
    pub zone: Option<String>,
    pub objects: Option<serde_json::Value>,
    pub room: Option<String>,
    pub confidence: f64,
    pub received_at: String,
}

/// Respuesta del engine al enviar perception event o tick.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct EngineResponse {
    pub scene_events: Vec<mana_engine_v2::SceneEvent>,
    pub scene_events_count: usize,
}

/// Cliente para Hub API.
#[derive(Debug, Clone)]
pub struct HubClient {
    base_url: String,
    server_token: String,
}

impl HubClient {
    pub fn new(base_url: &str, server_token: &str) -> Self {
        Self {
            base_url: base_url.to_string(),
            server_token: server_token.to_string(),
        }
    }

    /// Obtiene sensor events desde un timestamp.
    pub async fn get_sensor_events(
        &self,
        since: &str,
        limit: usize,
        bed_id: Option<&str>,
    ) -> Result<SensorEventsResponse, SentinelError> {
        let client = reqwest::Client::new();
        let mut url = format!(
            "{}/api/v1/internal/sensor-events?since={}&limit={}",
            self.base_url, since, limit
        );
        
        if let Some(bed_id) = bed_id {
            url.push_str(&format!("&bed_id={}", bed_id));
        }
        
        let response = client
            .get(&url)
            .header("Authorization", format!("Bearer {}", self.server_token))
            .send()
            .await?
            .json()
            .await?;
        
        Ok(response)
    }

    /// Crea evidence en el hub.
    pub async fn create_evidence(
        &self,
        input: serde_json::Value,
    ) -> Result<serde_json::Value, SentinelError> {
        let client = reqwest::Client::new();
        let url = format!("{}/api/v1/internal/evidence", self.base_url);
        
        let response = client
            .post(&url)
            .header("Authorization", format!("Bearer {}", self.server_token))
            .json(&input)
            .send()
            .await?
            .json()
            .await?;
        
        Ok(response)
    }

    /// Crea clip window en el hub.
    pub async fn create_clip_window(
        &self,
        input: serde_json::Value,
    ) -> Result<serde_json::Value, SentinelError> {
        let client = reqwest::Client::new();
        let url = format!("{}/api/v1/internal/clip-windows", self.base_url);
        
        let response = client
            .post(&url)
            .header("Authorization", format!("Bearer {}", self.server_token))
            .json(&input)
            .send()
            .await?
            .json()
            .await?;
        
        Ok(response)
    }

    /// Cierra clip window en el hub.
    pub async fn close_clip_window(
        &self,
        window_id: &str,
        input: serde_json::Value,
    ) -> Result<serde_json::Value, SentinelError> {
        let client = reqwest::Client::new();
        let url = format!(
            "{}/api/v1/internal/clip-windows/{}/close",
            self.base_url, window_id
        );
        
        let response = client
            .post(&url)
            .header("Authorization", format!("Bearer {}", self.server_token))
            .json(&input)
            .send()
            .await?
            .json()
            .await?;
        
        Ok(response)
    }
}

/// Cliente para Engine API.
#[derive(Debug, Clone)]
pub struct EngineClient {
    base_url: String,
}

impl EngineClient {
    pub fn new(base_url: &str) -> Self {
        Self {
            base_url: base_url.to_string(),
        }
    }

    /// Envía perception event al engine.
    pub async fn send_perception_event(
        &self,
        event: PerceptionEvent,
    ) -> Result<EngineResponse, SentinelError> {
        let client = reqwest::Client::new();
        let url = format!("{}/internal/v1/engine/perception", self.base_url);

        let response = client
            .post(&url)
            .json(&event)
            .send()
            .await?
            .json()
            .await?;

        Ok(response)
    }

    /// Llama al tick del engine (scan loop de PLC).
    pub async fn tick(&self) -> Result<EngineResponse, SentinelError> {
        let client = reqwest::Client::new();
        let url = format!("{}/internal/v1/engine/tick", self.base_url);

        let response = client.post(&url).send().await?.json().await?;

        Ok(response)
    }

    /// Obtiene el estado de una cama.
    pub async fn get_bed_state(&self, bed_id: &str) -> Result<serde_json::Value, SentinelError> {
        let client = reqwest::Client::new();
        let url = format!("{}/internal/v1/engine/state/{}", self.base_url, bed_id);

        let response = client.get(&url).send().await?.json().await?;

        Ok(response)
    }
}
