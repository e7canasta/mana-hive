use super::{
    DeliveryEventInput, DeliveryInput, DeliverySummary, DeliveryWithEvents, NotificationDelivery,
    NotificationDeliveryEvent,
};
use crate::error::VigilanciaError;

pub trait DeliveriesRepo {
    fn get(&mut self, id: &str) -> Result<NotificationDelivery, VigilanciaError>;

    fn create_in_transaction(
        &mut self,
        alert_id: &str,
        input: DeliveryInput,
    ) -> Result<NotificationDelivery, VigilanciaError>;

    fn add_event_in_transaction(
        &mut self,
        delivery_id: &str,
        input: DeliveryEventInput,
    ) -> Result<NotificationDeliveryEvent, VigilanciaError>;

    fn list_by_alert(&mut self, alert_id: &str)
        -> Result<Vec<DeliveryWithEvents>, VigilanciaError>;

    fn summary_by_alert(&mut self, alert_id: &str) -> Result<DeliverySummary, VigilanciaError>;
}
