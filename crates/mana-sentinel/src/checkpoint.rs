use serde::{Deserialize, Serialize};

/// Checkpoint para el polling loop.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Checkpoint {
    pub service: String,
    pub last_timestamp: String,
    pub last_event_id: String,
}

impl Default for Checkpoint {
    fn default() -> Self {
        Self {
            service: "sensor-events".to_string(),
            last_timestamp: String::new(),
            last_event_id: String::new(),
        }
    }
}
