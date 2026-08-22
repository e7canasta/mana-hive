use super::{Alert, AlertInput, AlertTransition, EscalationInput, TransitionInput};
use crate::error::VigilanciaError;

pub trait AlertasRepo {
    fn get(&mut self, id: &str) -> Result<Alert, VigilanciaError>;

    fn list(
        &mut self,
        status: Option<&str>,
        bed_id: Option<&str>,
        resident_id: Option<&str>,
    ) -> Result<Vec<Alert>, VigilanciaError>;

    fn create_in_transaction(&mut self, input: AlertInput) -> Result<Alert, VigilanciaError>;

    fn transition_in_transaction(
        &mut self,
        alert_id: &str,
        input: TransitionInput,
    ) -> Result<Alert, VigilanciaError>;

    fn escalate_in_transaction(
        &mut self,
        alert_id: &str,
        input: EscalationInput,
    ) -> Result<Alert, VigilanciaError>;

    fn list_transitions(&mut self, alert_id: &str)
        -> Result<Vec<AlertTransition>, VigilanciaError>;
}
