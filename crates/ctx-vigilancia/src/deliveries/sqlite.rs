use diesel::prelude::*;
use diesel::sqlite::SqliteConnection;

use super::repo::DeliveriesRepo;
use super::{
    new_delivery_id, Channel, DeliveryEventInput, DeliveryEventKind, DeliveryInput,
    DeliverySummary, DeliveryWithEvents, NotificationDelivery, NotificationDeliveryEvent,
    RecipientKind,
};
use crate::error::VigilanciaError;
use crate::schema::{notification_deliveries, notification_delivery_events};

#[derive(Queryable)]
struct DeliveryRow {
    id: String,
    alert_id: String,
    recipient_kind: String,
    recipient_id: String,
    channel: String,
    escalation_level: i32,
    created_at: String,
}

#[derive(Insertable)]
#[diesel(table_name = notification_deliveries)]
struct NewDelivery {
    id: String,
    alert_id: String,
    recipient_kind: String,
    recipient_id: String,
    channel: String,
    escalation_level: i32,
    created_at: String,
}

#[derive(Queryable)]
struct EventRow {
    id: String,
    delivery_id: String,
    kind: String,
    reason: Option<String>,
    occurred_at: String,
}

#[derive(Insertable)]
#[diesel(table_name = notification_delivery_events)]
struct NewDeliveryEvent {
    id: String,
    delivery_id: String,
    kind: String,
    reason: Option<String>,
    occurred_at: String,
}

fn row_to_delivery(row: DeliveryRow) -> NotificationDelivery {
    NotificationDelivery {
        id: row.id,
        alert_id: row.alert_id,
        recipient_kind: RecipientKind::parse(&row.recipient_kind).unwrap_or(RecipientKind::User),
        recipient_id: row.recipient_id,
        channel: Channel::parse(&row.channel).unwrap_or(Channel::Other),
        escalation_level: row.escalation_level,
        created_at: row.created_at.parse().unwrap(),
    }
}

fn row_to_event(row: EventRow) -> NotificationDeliveryEvent {
    NotificationDeliveryEvent {
        id: row.id,
        delivery_id: row.delivery_id,
        kind: DeliveryEventKind::parse(&row.kind).unwrap_or(DeliveryEventKind::Sent),
        reason: row.reason,
        occurred_at: row.occurred_at.parse().unwrap(),
    }
}

impl DeliveriesRepo for SqliteConnection {
    fn get(&mut self, id: &str) -> Result<NotificationDelivery, VigilanciaError> {
        let row: DeliveryRow = notification_deliveries::table
            .find(id)
            .first::<DeliveryRow>(self)
            .map_err(|e| match e {
                diesel::result::Error::NotFound => {
                    VigilanciaError::not_found(format!("delivery {id}"))
                }
                other => VigilanciaError::from(other),
            })?;
        Ok(row_to_delivery(row))
    }

    fn create_in_transaction(
        &mut self,
        alert_id: &str,
        input: DeliveryInput,
    ) -> Result<NotificationDelivery, VigilanciaError> {
        self.transaction::<_, VigilanciaError, _>(|conn| {
            let now = mana_kernel::Instante::now();
            let id = new_delivery_id();

            let new_delivery = NewDelivery {
                id: id.clone(),
                alert_id: alert_id.to_owned(),
                recipient_kind: input.recipient_kind.as_str().to_owned(),
                recipient_id: input.recipient_id,
                channel: input.channel.as_str().to_owned(),
                escalation_level: input.escalation_level,
                created_at: now.to_string(),
            };

            diesel::insert_into(notification_deliveries::table)
                .values(&new_delivery)
                .execute(conn)?;

            let row: DeliveryRow = notification_deliveries::table
                .find(&id)
                .first::<DeliveryRow>(conn)?;
            Ok(row_to_delivery(row))
        })
    }

    fn add_event_in_transaction(
        &mut self,
        delivery_id: &str,
        input: DeliveryEventInput,
    ) -> Result<NotificationDeliveryEvent, VigilanciaError> {
        self.transaction::<_, VigilanciaError, _>(|conn| {
            let id = new_delivery_id();

            let new_event = NewDeliveryEvent {
                id: id.clone(),
                delivery_id: delivery_id.to_owned(),
                kind: input.kind.as_str().to_owned(),
                reason: input.reason,
                occurred_at: input.occurred_at.to_string(),
            };

            diesel::insert_into(notification_delivery_events::table)
                .values(&new_event)
                .execute(conn)?;

            let row: EventRow = notification_delivery_events::table
                .find(&id)
                .first::<EventRow>(conn)?;
            Ok(row_to_event(row))
        })
    }

    fn list_by_alert(
        &mut self,
        alert_id: &str,
    ) -> Result<Vec<DeliveryWithEvents>, VigilanciaError> {
        let delivery_rows: Vec<DeliveryRow> = notification_deliveries::table
            .filter(notification_deliveries::alert_id.eq(alert_id))
            .order(notification_deliveries::created_at.asc())
            .load::<DeliveryRow>(self)?;

        let mut result = Vec::new();
        for delivery_row in delivery_rows {
            let delivery = row_to_delivery(delivery_row);

            let event_rows: Vec<EventRow> = notification_delivery_events::table
                .filter(notification_delivery_events::delivery_id.eq(&delivery.id))
                .order(notification_delivery_events::occurred_at.asc())
                .load::<EventRow>(self)?;

            let events: Vec<NotificationDeliveryEvent> =
                event_rows.into_iter().map(row_to_event).collect();

            let sent_at = events
                .iter()
                .find(|e| e.kind == DeliveryEventKind::Sent)
                .map(|e| e.occurred_at);
            let acked_at = events
                .iter()
                .find(|e| e.kind == DeliveryEventKind::Acknowledged)
                .map(|e| e.occurred_at);
            let failed_reason = events
                .iter()
                .find(|e| e.kind == DeliveryEventKind::Failed)
                .and_then(|e| e.reason.clone());

            result.push(DeliveryWithEvents {
                delivery,
                events,
                sent_at,
                acked_at,
                failed_reason,
            });
        }

        Ok(result)
    }

    fn summary_by_alert(&mut self, alert_id: &str) -> Result<DeliverySummary, VigilanciaError> {
        let deliveries: Vec<DeliveryRow> = notification_deliveries::table
            .filter(notification_deliveries::alert_id.eq(alert_id))
            .load::<DeliveryRow>(self)?;

        let mut sent = 0i64;
        let mut acked = 0i64;
        let mut failed = 0i64;

        for delivery_row in &deliveries {
            let event_rows: Vec<EventRow> = notification_delivery_events::table
                .filter(notification_delivery_events::delivery_id.eq(&delivery_row.id))
                .load::<EventRow>(self)?;

            for event_row in event_rows {
                match event_row.kind.as_str() {
                    "sent" => sent += 1,
                    "acknowledged" => acked += 1,
                    "failed" => failed += 1,
                    _ => {}
                }
            }
        }

        Ok(DeliverySummary {
            sent,
            acked,
            failed,
        })
    }
}
