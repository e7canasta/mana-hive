pub mod schema;

mod alertas;
mod deliveries;
mod error;
mod escalations;

pub use alertas::{
    Alert, AlertInput, AlertLevel, AlertStatus, AlertTransition, AlertasError, EvidenceKind,
    TransitionInput,
};
pub use deliveries::{
    Channel, DeliveriesError, DeliveryEventInput, DeliveryEventKind, DeliveryInput,
    DeliverySummary, DeliveryWithEvents, NotificationDelivery, NotificationDeliveryEvent,
    RecipientKind,
};
pub use error::VigilanciaError;
pub use escalations::AlertEscalation;
pub use mana_storage::DbPool;

use diesel::prelude::*;
use diesel_migrations::{embed_migrations, EmbeddedMigrations};
use mana_kernel::Instante;
use mana_storage::{connection as get_connection, DbConnection};

use crate::alertas::repo::AlertasRepo;
use crate::deliveries::repo::DeliveriesRepo;
use crate::escalations::repo::EscalationsRepo;

pub const MIGRATIONS: EmbeddedMigrations = embed_migrations!();

/// Crea una alerta dentro de una transaccion en curso.
///
/// La usa el motor: la alerta que nace de un evento se escribe en la **misma**
/// transaccion que la evidencia que la produjo. Si se partiera en dos, un fallo
/// entre medio dejaria evidencia sin su alerta, que es la falla silenciosa.
pub fn create_alert_in_transaction(
    connection: &mut SqliteConnection,
    input: AlertInput,
) -> Result<Alert, VigilanciaError> {
    <SqliteConnection as AlertasRepo>::create_in_transaction(connection, input)
}

/// Las reglas que ya avisaron en una cama desde un instante.
///
/// Es la deduplicacion del motor, y se hace **por episodio** —desde que la cama
/// entro en su estado actual— y no por "hay una alerta abierta". La diferencia
/// importa: que alguien resuelva la alerta no puede hacerla sonar de nuevo
/// mientras el residente sigue fuera de la cama.
///
/// Se expone suelta porque quien la llama es `mana-app`, que evalua dentro de
/// la misma transaccion que ingiere el evento.
pub fn rules_alerted_since_in_transaction(
    connection: &mut SqliteConnection,
    bed_id: &str,
    since: &Instante,
) -> Result<std::collections::BTreeSet<String>, VigilanciaError> {
    use crate::schema::alerts;
    let rows: Vec<String> = alerts::table
        .filter(alerts::bed_id.eq(bed_id))
        .filter(alerts::occurred_at.ge(since.to_string()))
        .select(alerts::rule_id)
        .load(connection)?;
    Ok(rows.into_iter().collect())
}

#[derive(Clone)]
pub struct VigilanciaStore {
    pub(crate) pool: DbPool,
}

pub fn run_migrations(pool: &DbPool) -> Result<(), VigilanciaError> {
    mana_storage::run_migrations(pool, MIGRATIONS).map_err(VigilanciaError::from)
}

impl VigilanciaStore {
    pub fn new(pool: DbPool) -> Self {
        Self { pool }
    }

    pub fn pool(&self) -> &DbPool {
        &self.pool
    }

    fn connection(&self) -> Result<DbConnection, VigilanciaError> {
        get_connection(&self.pool).map_err(VigilanciaError::from)
    }

    pub fn create_alert(&self, input: AlertInput) -> Result<Alert, VigilanciaError> {
        let mut connection = self.connection()?;
        <SqliteConnection as AlertasRepo>::create_in_transaction(&mut connection, input)
    }

    pub fn get_alert(&self, id: &str) -> Result<Alert, VigilanciaError> {
        let mut connection = self.connection()?;
        <SqliteConnection as AlertasRepo>::get(&mut connection, id)
    }

    pub fn list_alerts(
        &self,
        status: Option<&str>,
        bed_id: Option<&str>,
        resident_id: Option<&str>,
    ) -> Result<Vec<Alert>, VigilanciaError> {
        let mut connection = self.connection()?;
        <SqliteConnection as AlertasRepo>::list(&mut connection, status, bed_id, resident_id)
    }

    pub fn transition_alert(
        &self,
        alert_id: &str,
        input: TransitionInput,
    ) -> Result<Alert, VigilanciaError> {
        let mut connection = self.connection()?;
        <SqliteConnection as AlertasRepo>::transition_in_transaction(
            &mut connection,
            alert_id,
            input,
        )
    }

    pub fn escalate_alert(
        &self,
        alert_id: &str,
        level: i32,
        target_id: &str,
        occurred_at: mana_kernel::Instante,
    ) -> Result<Alert, VigilanciaError> {
        let mut connection = self.connection()?;
        <SqliteConnection as AlertasRepo>::escalate_in_transaction(
            &mut connection,
            alert_id,
            alertas::EscalationInput {
                level,
                target_id: target_id.to_owned(),
                occurred_at,
            },
        )
    }

    pub fn list_transitions(
        &self,
        alert_id: &str,
    ) -> Result<Vec<AlertTransition>, VigilanciaError> {
        let mut connection = self.connection()?;
        <SqliteConnection as AlertasRepo>::list_transitions(&mut connection, alert_id)
    }

    pub fn create_delivery(
        &self,
        alert_id: &str,
        input: deliveries::DeliveryInput,
    ) -> Result<NotificationDelivery, VigilanciaError> {
        let mut connection = self.connection()?;
        <SqliteConnection as DeliveriesRepo>::create_in_transaction(
            &mut connection,
            alert_id,
            input,
        )
    }

    pub fn get_delivery(&self, id: &str) -> Result<NotificationDelivery, VigilanciaError> {
        let mut connection = self.connection()?;
        <SqliteConnection as DeliveriesRepo>::get(&mut connection, id)
    }

    pub fn add_delivery_event(
        &self,
        delivery_id: &str,
        input: DeliveryEventInput,
    ) -> Result<NotificationDeliveryEvent, VigilanciaError> {
        let mut connection = self.connection()?;
        <SqliteConnection as DeliveriesRepo>::add_event_in_transaction(
            &mut connection,
            delivery_id,
            input,
        )
    }

    pub fn list_deliveries(
        &self,
        alert_id: &str,
    ) -> Result<Vec<DeliveryWithEvents>, VigilanciaError> {
        let mut connection = self.connection()?;
        <SqliteConnection as DeliveriesRepo>::list_by_alert(&mut connection, alert_id)
    }

    pub fn delivery_summary(&self, alert_id: &str) -> Result<DeliverySummary, VigilanciaError> {
        let mut connection = self.connection()?;
        <SqliteConnection as DeliveriesRepo>::summary_by_alert(&mut connection, alert_id)
    }

    pub fn list_escalations(
        &self,
        alert_id: &str,
    ) -> Result<Vec<AlertEscalation>, VigilanciaError> {
        let mut connection = self.connection()?;
        <SqliteConnection as EscalationsRepo>::list_by_alert(&mut connection, alert_id)
    }
}

#[cfg(test)]
pub(crate) mod testsupport {
    use mana_storage::build_pool;

    use super::{run_migrations, VigilanciaStore};

    pub(crate) fn store() -> VigilanciaStore {
        let pool = build_pool(":memory:").unwrap();
        run_migrations(&pool).unwrap();
        VigilanciaStore::new(pool)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::testsupport::store;

    fn now() -> mana_kernel::Instante {
        mana_kernel::Instante::now()
    }

    fn alert_input() -> AlertInput {
        AlertInput {
            resident_id: Some("resident-1".to_owned()),
            bed_id: "bed-1".to_owned(),
            evidence_kind: EvidenceKind::SensorEvent,
            evidence_ref: Some("event-1".to_owned()),
            rule_id: "bed_exit".to_owned(),
            level: AlertLevel::High,
            title: "Bed exit detected".to_owned(),
            detail: Some("Resident left bed without assistance".to_owned()),
            occurred_at: now(),
        }
    }

    #[test]
    fn create_alert_starts_open() {
        let store = store();
        let alert = store.create_alert(alert_input()).unwrap();
        assert_eq!(alert.status, AlertStatus::Open);
        assert_eq!(alert.level, AlertLevel::High);
        assert_eq!(alert.rule_id, "bed_exit");
        assert_eq!(alert.escalation_level, 0);
    }

    #[test]
    fn create_alert_records_first_transition() {
        let store = store();
        let alert = store.create_alert(alert_input()).unwrap();
        let transitions = store.list_transitions(&alert.id).unwrap();
        assert_eq!(transitions.len(), 1);
        assert_eq!(transitions[0].to_status, AlertStatus::Open);
        assert!(transitions[0].from_status.is_none());
    }

    #[test]
    fn transition_open_to_acknowledged() {
        let store = store();
        let alert = store.create_alert(alert_input()).unwrap();
        let updated = store
            .transition_alert(
                &alert.id,
                TransitionInput {
                    from_status: Some(AlertStatus::Open),
                    to_status: AlertStatus::Acknowledged,
                    actor_id: Some("user-1".to_owned()),
                    occurred_at: now(),
                },
            )
            .unwrap();
        assert_eq!(updated.status, AlertStatus::Acknowledged);
        assert_eq!(updated.status_actor_id.as_deref(), Some("user-1"));
    }

    #[test]
    fn transition_acknowledged_to_attending() {
        let store = store();
        let alert = store.create_alert(alert_input()).unwrap();
        store
            .transition_alert(
                &alert.id,
                TransitionInput {
                    from_status: Some(AlertStatus::Open),
                    to_status: AlertStatus::Acknowledged,
                    actor_id: Some("user-1".to_owned()),
                    occurred_at: now(),
                },
            )
            .unwrap();
        let updated = store
            .transition_alert(
                &alert.id,
                TransitionInput {
                    from_status: Some(AlertStatus::Acknowledged),
                    to_status: AlertStatus::Attending,
                    actor_id: Some("user-1".to_owned()),
                    occurred_at: now(),
                },
            )
            .unwrap();
        assert_eq!(updated.status, AlertStatus::Attending);
    }

    #[test]
    fn transition_attending_to_resolved() {
        let store = store();
        let alert = store.create_alert(alert_input()).unwrap();
        store
            .transition_alert(
                &alert.id,
                TransitionInput {
                    from_status: Some(AlertStatus::Open),
                    to_status: AlertStatus::Acknowledged,
                    actor_id: Some("user-1".to_owned()),
                    occurred_at: now(),
                },
            )
            .unwrap();
        store
            .transition_alert(
                &alert.id,
                TransitionInput {
                    from_status: Some(AlertStatus::Acknowledged),
                    to_status: AlertStatus::Attending,
                    actor_id: Some("user-1".to_owned()),
                    occurred_at: now(),
                },
            )
            .unwrap();
        let resolved = store
            .transition_alert(
                &alert.id,
                TransitionInput {
                    from_status: Some(AlertStatus::Attending),
                    to_status: AlertStatus::Resolved,
                    actor_id: Some("user-2".to_owned()),
                    occurred_at: now(),
                },
            )
            .unwrap();
        assert_eq!(resolved.status, AlertStatus::Resolved);
        assert_eq!(resolved.status_actor_id.as_deref(), Some("user-2"));
    }

    #[test]
    fn invalid_transition_open_to_resolved() {
        let store = store();
        let alert = store.create_alert(alert_input()).unwrap();
        let result = store.transition_alert(
            &alert.id,
            TransitionInput {
                from_status: Some(AlertStatus::Open),
                to_status: AlertStatus::Resolved,
                actor_id: Some("user-1".to_owned()),
                occurred_at: now(),
            },
        );
        assert!(result.is_err());
    }

    #[test]
    fn acknowledged_requires_actor() {
        let store = store();
        let alert = store.create_alert(alert_input()).unwrap();
        let result = store.transition_alert(
            &alert.id,
            TransitionInput {
                from_status: Some(AlertStatus::Open),
                to_status: AlertStatus::Acknowledged,
                actor_id: None,
                occurred_at: now(),
            },
        );
        assert!(result.is_err());
    }

    #[test]
    fn transition_history_is_append_only() {
        let store = store();
        let alert = store.create_alert(alert_input()).unwrap();
        store
            .transition_alert(
                &alert.id,
                TransitionInput {
                    from_status: Some(AlertStatus::Open),
                    to_status: AlertStatus::Acknowledged,
                    actor_id: Some("user-1".to_owned()),
                    occurred_at: now(),
                },
            )
            .unwrap();
        store
            .transition_alert(
                &alert.id,
                TransitionInput {
                    from_status: Some(AlertStatus::Acknowledged),
                    to_status: AlertStatus::Attending,
                    actor_id: Some("user-1".to_owned()),
                    occurred_at: now(),
                },
            )
            .unwrap();
        let transitions = store.list_transitions(&alert.id).unwrap();
        assert_eq!(transitions.len(), 3); // open, ack, attending
        assert_eq!(transitions[0].to_status, AlertStatus::Open);
        assert_eq!(transitions[1].to_status, AlertStatus::Acknowledged);
        assert_eq!(transitions[2].to_status, AlertStatus::Attending);
    }

    #[test]
    fn escalate_alert() {
        let store = store();
        let alert = store.create_alert(alert_input()).unwrap();
        let escalated = store
            .escalate_alert(&alert.id, 1, "supervisor-1", now())
            .unwrap();
        assert_eq!(escalated.escalation_level, 1);
        assert_eq!(escalated.escalated_to.as_deref(), Some("supervisor-1"));
    }

    #[test]
    fn escalate_survives_restart() {
        let store = store();
        let alert = store.create_alert(alert_input()).unwrap();
        store
            .escalate_alert(&alert.id, 1, "supervisor-1", now())
            .unwrap();

        let escalations = store.list_escalations(&alert.id).unwrap();
        assert_eq!(escalations.len(), 1);
        assert_eq!(escalations[0].level, 1);
    }

    #[test]
    fn create_delivery() {
        let store = store();
        let alert = store.create_alert(alert_input()).unwrap();
        let delivery = store
            .create_delivery(
                &alert.id,
                deliveries::DeliveryInput {
                    recipient_kind: RecipientKind::User,
                    recipient_id: "user-1".to_owned(),
                    channel: Channel::Push,
                    escalation_level: 0,
                },
            )
            .unwrap();
        assert_eq!(delivery.alert_id, alert.id);
        assert_eq!(delivery.recipient_kind, RecipientKind::User);
        assert_eq!(delivery.channel, Channel::Push);
    }

    #[test]
    fn delivery_retry_creates_new_delivery() {
        let store = store();
        let alert = store.create_alert(alert_input()).unwrap();
        store
            .create_delivery(
                &alert.id,
                deliveries::DeliveryInput {
                    recipient_kind: RecipientKind::User,
                    recipient_id: "user-1".to_owned(),
                    channel: Channel::Push,
                    escalation_level: 0,
                },
            )
            .unwrap();
        store
            .create_delivery(
                &alert.id,
                deliveries::DeliveryInput {
                    recipient_kind: RecipientKind::User,
                    recipient_id: "user-1".to_owned(),
                    channel: Channel::Sms,
                    escalation_level: 0,
                },
            )
            .unwrap();

        let deliveries = store.list_deliveries(&alert.id).unwrap();
        assert_eq!(deliveries.len(), 2);
    }

    #[test]
    fn delivery_event_tracking() {
        let store = store();
        let alert = store.create_alert(alert_input()).unwrap();
        let delivery = store
            .create_delivery(
                &alert.id,
                deliveries::DeliveryInput {
                    recipient_kind: RecipientKind::User,
                    recipient_id: "user-1".to_owned(),
                    channel: Channel::Push,
                    escalation_level: 0,
                },
            )
            .unwrap();

        store
            .add_delivery_event(
                &delivery.id,
                DeliveryEventInput {
                    kind: DeliveryEventKind::Sent,
                    reason: None,
                    occurred_at: now(),
                },
            )
            .unwrap();

        store
            .add_delivery_event(
                &delivery.id,
                DeliveryEventInput {
                    kind: DeliveryEventKind::Acknowledged,
                    reason: None,
                    occurred_at: now(),
                },
            )
            .unwrap();

        let deliveries = store.list_deliveries(&alert.id).unwrap();
        assert_eq!(deliveries.len(), 1);
        assert!(deliveries[0].sent_at.is_some());
        assert!(deliveries[0].acked_at.is_some());
    }

    #[test]
    fn delivery_summary_counts() {
        let store = store();
        let alert = store.create_alert(alert_input()).unwrap();

        let d1 = store
            .create_delivery(
                &alert.id,
                deliveries::DeliveryInput {
                    recipient_kind: RecipientKind::User,
                    recipient_id: "user-1".to_owned(),
                    channel: Channel::Push,
                    escalation_level: 0,
                },
            )
            .unwrap();
        store
            .add_delivery_event(
                &d1.id,
                DeliveryEventInput {
                    kind: DeliveryEventKind::Sent,
                    reason: None,
                    occurred_at: now(),
                },
            )
            .unwrap();
        store
            .add_delivery_event(
                &d1.id,
                DeliveryEventInput {
                    kind: DeliveryEventKind::Acknowledged,
                    reason: None,
                    occurred_at: now(),
                },
            )
            .unwrap();

        let d2 = store
            .create_delivery(
                &alert.id,
                deliveries::DeliveryInput {
                    recipient_kind: RecipientKind::User,
                    recipient_id: "user-2".to_owned(),
                    channel: Channel::Sms,
                    escalation_level: 0,
                },
            )
            .unwrap();
        store
            .add_delivery_event(
                &d2.id,
                DeliveryEventInput {
                    kind: DeliveryEventKind::Failed,
                    reason: Some("timeout".to_owned()),
                    occurred_at: now(),
                },
            )
            .unwrap();

        let summary = store.delivery_summary(&alert.id).unwrap();
        assert_eq!(summary.sent, 1);
        assert_eq!(summary.acked, 1);
        assert_eq!(summary.failed, 1);
    }

    #[test]
    fn list_alerts_filter_by_status() {
        let store = store();
        store.create_alert(alert_input()).unwrap();

        let mut input2 = alert_input();
        input2.title = "Another alert".to_owned();
        let alert2 = store.create_alert(input2).unwrap();
        store
            .transition_alert(
                &alert2.id,
                TransitionInput {
                    from_status: Some(AlertStatus::Open),
                    to_status: AlertStatus::Acknowledged,
                    actor_id: Some("user-1".to_owned()),
                    occurred_at: now(),
                },
            )
            .unwrap();

        let open = store.list_alerts(Some("open"), None, None).unwrap();
        assert_eq!(open.len(), 1);

        let acked = store.list_alerts(Some("acknowledged"), None, None).unwrap();
        assert_eq!(acked.len(), 1);

        let all = store.list_alerts(None, None, None).unwrap();
        assert_eq!(all.len(), 2);
    }

    #[test]
    fn alert_not_found() {
        let store = store();
        let result = store.get_alert("nonexistent");
        assert!(result.is_err());
    }
}
