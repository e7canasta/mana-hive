use tracing::info;

use crate::error::SentinelError;
use crate::evaluator::{CategorizedEvent, EventCategory};

/// Dispatch de notificaciones y alertas.
#[derive(Debug, Clone)]
pub struct Dispatch;

impl Dispatch {
    pub fn new() -> Self {
        Self
    }

    /// Despacha un evento categorizado.
    pub async fn dispatch(&self, event: &CategorizedEvent) -> Result<(), SentinelError> {
        match event.category {
            EventCategory::Off => {
                // No hacer nada
            }
            EventCategory::Notify => {
                info!(
                    bed_id = %event.scene_event.bed_id,
                    reason = %event.reason,
                    "Notificación enviada"
                );
                // TODO: enviar notificación real
            }
            EventCategory::Alarm => {
                info!(
                    bed_id = %event.scene_event.bed_id,
                    reason = %event.reason,
                    "Alarma generada"
                );
                // TODO: crear alerta en vigilancia
            }
            EventCategory::Mark => {
                info!(
                    bed_id = %event.scene_event.bed_id,
                    reason = %event.reason,
                    "Evento marcado para revisión"
                );
                // TODO: marcar evento
            }
        }
        
        Ok(())
    }
}

impl Default for Dispatch {
    fn default() -> Self {
        Self::new()
    }
}
