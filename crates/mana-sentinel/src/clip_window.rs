use serde::{Deserialize, Serialize};

use crate::error::SentinelError;

/// Estado de la ventana de clip.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub enum WindowState {
    Open,
    Closed,
    Expired,
}

/// Condición de cierre de la ventana.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum CloseCondition {
    Timeout { minutes: i32 },
    SequencePattern { before: i32, after: i32 },
    Manual,
}

/// Clip window en memoria.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ClipWindow {
    pub window_id: String,
    pub bed_id: String,
    pub resident_id: Option<String>,
    pub started_at: String,
    pub state: WindowState,
    pub events: Vec<serde_json::Value>,
}

/// Gestor de clip windows.
#[derive(Debug, Clone)]
pub struct ClipWindowManager {
    windows: Vec<ClipWindow>,
}

impl ClipWindowManager {
    pub fn new() -> Self {
        Self {
            windows: Vec::new(),
        }
    }

    /// Abre una nueva clip window.
    pub fn open_window(
        &mut self,
        bed_id: &str,
        resident_id: Option<&str>,
        started_at: &str,
    ) -> ClipWindow {
        let window = ClipWindow {
            window_id: uuid::Uuid::new_v4().to_string(),
            bed_id: bed_id.to_string(),
            resident_id: resident_id.map(|s| s.to_string()),
            started_at: started_at.to_string(),
            state: WindowState::Open,
            events: Vec::new(),
        };
        
        self.windows.push(window.clone());
        window
    }

    /// Agrega evento a una ventana abierta.
    pub fn add_event(
        &mut self,
        window_id: &str,
        event: serde_json::Value,
    ) -> Result<(), SentinelError> {
        if let Some(window) = self.windows.iter_mut().find(|w| w.window_id == window_id) {
            if window.state == WindowState::Open {
                window.events.push(event);
                Ok(())
            } else {
                Err(SentinelError::ClipWindow("Window is not open".to_string()))
            }
        } else {
            Err(SentinelError::ClipWindow("Window not found".to_string()))
        }
    }

    /// Cierra una ventana.
    pub fn close_window(&mut self, window_id: &str) -> Result<ClipWindow, SentinelError> {
        if let Some(window) = self.windows.iter_mut().find(|w| w.window_id == window_id) {
            window.state = WindowState::Closed;
            Ok(window.clone())
        } else {
            Err(SentinelError::ClipWindow("Window not found".to_string()))
        }
    }

    /// Obtiene ventanas abiertas para una cama.
    pub fn open_windows_for_bed(&self, bed_id: &str) -> Vec<&ClipWindow> {
        self.windows
            .iter()
            .filter(|w| w.bed_id == bed_id && w.state == WindowState::Open)
            .collect()
    }
}

impl Default for ClipWindowManager {
    fn default() -> Self {
        Self::new()
    }
}
