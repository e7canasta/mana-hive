use super::AlertEscalation;
use crate::error::VigilanciaError;

pub trait EscalationsRepo {
    fn list_by_alert(&mut self, alert_id: &str) -> Result<Vec<AlertEscalation>, VigilanciaError>;
}
