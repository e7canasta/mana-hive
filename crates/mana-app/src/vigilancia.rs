use ctx_vigilancia::{
    Alert, AlertInput, AlertLevel, AlertStatus, AlertTransition, Channel, DeliveryEventInput,
    DeliveryEventKind, DeliverySummary, DeliveryWithEvents, EvidenceKind, RecipientKind,
    TransitionInput,
};

use crate::{error::AppFailure, identidad::required_token, state::AppState};

#[derive(Clone, Debug)]
pub struct CreateAlertCommand {
    pub resident_id: Option<String>,
    pub bed_id: String,
    pub evidence_kind: String,
    pub evidence_ref: Option<String>,
    pub rule_id: String,
    pub level: String,
    pub title: String,
    pub detail: Option<String>,
    pub occurred_at: String,
}

#[derive(Clone, Debug)]
pub struct TransitionAlertCommand {
    pub to_status: String,
    pub actor_id: Option<String>,
}

#[derive(Clone, Debug)]
pub struct CreateDeliveryCommand {
    pub recipient_kind: String,
    pub recipient_id: String,
    pub channel: String,
    pub escalation_level: i32,
}

#[derive(Clone, Debug)]
pub struct AddDeliveryEventCommand {
    pub kind: String,
    pub reason: Option<String>,
}

#[derive(Clone, Debug, serde::Serialize)]
pub struct AlertView {
    pub id: String,
    pub resident_id: Option<String>,
    pub bed_id: String,
    pub evidence_kind: String,
    pub evidence_ref: Option<String>,
    pub rule_id: String,
    pub level: String,
    pub status: String,
    pub status_actor_id: Option<String>,
    pub status_at: Option<String>,
    pub title: String,
    pub detail: Option<String>,
    pub occurred_at: String,
    pub escalation: EscalationView,
    pub delivery_summary: DeliverySummaryView,
    pub created_at: String,
    pub updated_at: String,
}

#[derive(Clone, Debug, serde::Serialize)]
pub struct EscalationView {
    pub level: i32,
    pub escalated_at: Option<String>,
    pub escalated_to: Option<String>,
}

#[derive(Clone, Debug, serde::Serialize)]
pub struct DeliverySummaryView {
    pub sent: i64,
    pub acked: i64,
    pub failed: i64,
}

#[derive(Clone, Debug, serde::Serialize)]
pub struct TransitionView {
    pub id: String,
    pub alert_id: String,
    pub from_status: Option<String>,
    pub to_status: String,
    pub actor_id: Option<String>,
    pub occurred_at: String,
    pub sequence: i32,
}

#[derive(Clone, Debug, serde::Serialize)]
pub struct DeliveryView {
    pub id: String,
    pub alert_id: String,
    pub recipient_kind: String,
    pub recipient_id: String,
    pub channel: String,
    pub escalation_level: i32,
    pub created_at: String,
    pub events: Vec<DeliveryEventView>,
    pub sent_at: Option<String>,
    pub acked_at: Option<String>,
    pub failed_reason: Option<String>,
}

#[derive(Clone, Debug, serde::Serialize)]
pub struct DeliveryEventView {
    pub id: String,
    pub delivery_id: String,
    pub kind: String,
    pub reason: Option<String>,
    pub occurred_at: String,
}

#[derive(Clone, Debug, serde::Serialize)]
pub struct AlertsListView {
    pub alerts: Vec<AlertView>,
}

#[derive(Clone, Debug, serde::Serialize)]
pub struct DeliveriesListView {
    pub deliveries: Vec<DeliveryView>,
}

fn alert_view(alert: Alert, summary: DeliverySummary) -> AlertView {
    AlertView {
        id: alert.id,
        resident_id: alert.resident_id,
        bed_id: alert.bed_id,
        evidence_kind: alert.evidence_kind.as_str().to_owned(),
        evidence_ref: alert.evidence_ref,
        rule_id: alert.rule_id,
        level: alert.level.as_str().to_owned(),
        status: alert.status.as_str().to_owned(),
        status_actor_id: alert.status_actor_id,
        status_at: alert.status_at.map(|t| t.to_string()),
        title: alert.title,
        detail: alert.detail,
        occurred_at: alert.occurred_at.to_string(),
        escalation: EscalationView {
            level: alert.escalation_level,
            escalated_at: alert.escalated_at.map(|t| t.to_string()),
            escalated_to: alert.escalated_to,
        },
        delivery_summary: DeliverySummaryView {
            sent: summary.sent,
            acked: summary.acked,
            failed: summary.failed,
        },
        created_at: alert.created_at.to_string(),
        updated_at: alert.updated_at.to_string(),
    }
}

#[allow(dead_code)]
fn transition_view(t: AlertTransition) -> TransitionView {
    TransitionView {
        id: t.id,
        alert_id: t.alert_id,
        from_status: t.from_status.map(|s| s.as_str().to_owned()),
        to_status: t.to_status.as_str().to_owned(),
        actor_id: t.actor_id,
        occurred_at: t.occurred_at.to_string(),
        sequence: t.sequence,
    }
}

fn delivery_view(d: DeliveryWithEvents) -> DeliveryView {
    DeliveryView {
        id: d.delivery.id,
        alert_id: d.delivery.alert_id,
        recipient_kind: d.delivery.recipient_kind.as_str().to_owned(),
        recipient_id: d.delivery.recipient_id,
        channel: d.delivery.channel.as_str().to_owned(),
        escalation_level: d.delivery.escalation_level,
        created_at: d.delivery.created_at.to_string(),
        events: d
            .events
            .into_iter()
            .map(|e| DeliveryEventView {
                id: e.id,
                delivery_id: e.delivery_id,
                kind: e.kind.as_str().to_owned(),
                reason: e.reason,
                occurred_at: e.occurred_at.to_string(),
            })
            .collect(),
        sent_at: d.sent_at.map(|t| t.to_string()),
        acked_at: d.acked_at.map(|t| t.to_string()),
        failed_reason: d.failed_reason,
    }
}

impl AppState {
    pub async fn create_alert(
        &self,
        token: &str,
        command: CreateAlertCommand,
    ) -> Result<AlertView, AppFailure> {
        let _ = required_token(token)?;

        let evidence_kind = EvidenceKind::parse(&command.evidence_kind)
            .map_err(|e| AppFailure::validation(e.to_string(), Some("evidence_kind")))?;
        let level = AlertLevel::parse(&command.level)
            .map_err(|e| AppFailure::validation(e.to_string(), Some("level")))?;
        let occurred_at = command
            .occurred_at
            .parse()
            .map_err(|_| AppFailure::validation("invalid occurred_at", Some("occurred_at")))?;

        let alert = self.vigilancia.create_alert(AlertInput {
            resident_id: command.resident_id,
            bed_id: command.bed_id,
            evidence_kind,
            evidence_ref: command.evidence_ref,
            rule_id: command.rule_id,
            level,
            title: command.title,
            detail: command.detail,
            occurred_at,
        })?;

        let summary = self.vigilancia.delivery_summary(&alert.id)?;
        Ok(alert_view(alert, summary))
    }

    pub async fn get_alert(&self, token: &str, id: &str) -> Result<AlertView, AppFailure> {
        let _ = required_token(token)?;
        let alert = self.vigilancia.get_alert(id)?;
        let summary = self.vigilancia.delivery_summary(&alert.id)?;
        Ok(alert_view(alert, summary))
    }

    pub async fn list_alerts(
        &self,
        token: &str,
        status: Option<&str>,
        bed_id: Option<&str>,
        resident_id: Option<&str>,
    ) -> Result<AlertsListView, AppFailure> {
        let _ = required_token(token)?;
        let alerts = self.vigilancia.list_alerts(status, bed_id, resident_id)?;

        let mut views = Vec::with_capacity(alerts.len());
        for alert in alerts {
            let summary = self.vigilancia.delivery_summary(&alert.id)?;
            views.push(alert_view(alert, summary));
        }

        Ok(AlertsListView { alerts: views })
    }

    pub async fn transition_alert(
        &self,
        token: &str,
        alert_id: &str,
        command: TransitionAlertCommand,
    ) -> Result<AlertView, AppFailure> {
        let actor = required_token(token)?;

        let current = self.vigilancia.get_alert(alert_id)?;
        let from_status = current.status;
        let to_status = AlertStatus::parse(&command.to_status)
            .map_err(|e| AppFailure::validation(e.to_string(), Some("to_status")))?;

        let actor_id = command.actor_id.or(Some(actor.clone()));

        let alert = self.vigilancia.transition_alert(
            alert_id,
            TransitionInput {
                from_status: Some(from_status),
                to_status,
                actor_id,
                occurred_at: mana_kernel::Instante::now(),
            },
        )?;

        let summary = self.vigilancia.delivery_summary(&alert.id)?;
        Ok(alert_view(alert, summary))
    }

    pub async fn view_alert(&self, token: &str, alert_id: &str) -> Result<AlertView, AppFailure> {
        let _ = required_token(token)?;
        let alert = self.vigilancia.get_alert(alert_id)?;
        let summary = self.vigilancia.delivery_summary(&alert.id)?;
        Ok(alert_view(alert, summary))
    }

    pub async fn create_delivery(
        &self,
        token: &str,
        alert_id: &str,
        command: CreateDeliveryCommand,
    ) -> Result<DeliveryView, AppFailure> {
        let _ = required_token(token)?;

        let recipient_kind = RecipientKind::parse(&command.recipient_kind)
            .map_err(|e| AppFailure::validation(e.to_string(), Some("recipient_kind")))?;
        let channel = Channel::parse(&command.channel)
            .map_err(|e| AppFailure::validation(e.to_string(), Some("channel")))?;

        let delivery = self.vigilancia.create_delivery(
            alert_id,
            ctx_vigilancia::DeliveryInput {
                recipient_kind,
                recipient_id: command.recipient_id,
                channel,
                escalation_level: command.escalation_level,
            },
        )?;

        Ok(DeliveryView {
            id: delivery.id,
            alert_id: delivery.alert_id,
            recipient_kind: delivery.recipient_kind.as_str().to_owned(),
            recipient_id: delivery.recipient_id,
            channel: delivery.channel.as_str().to_owned(),
            escalation_level: delivery.escalation_level,
            created_at: delivery.created_at.to_string(),
            events: vec![],
            sent_at: None,
            acked_at: None,
            failed_reason: None,
        })
    }

    pub async fn add_delivery_event(
        &self,
        token: &str,
        delivery_id: &str,
        command: AddDeliveryEventCommand,
    ) -> Result<DeliveryView, AppFailure> {
        let _ = required_token(token)?;

        let kind = DeliveryEventKind::parse(&command.kind)
            .map_err(|e| AppFailure::validation(e.to_string(), Some("kind")))?;

        self.vigilancia.add_delivery_event(
            delivery_id,
            DeliveryEventInput {
                kind,
                reason: command.reason,
                occurred_at: mana_kernel::Instante::now(),
            },
        )?;

        let delivery = self.vigilancia.get_delivery(delivery_id)?;
        let deliveries = self.vigilancia.list_deliveries(&delivery.alert_id)?;
        Ok(deliveries
            .into_iter()
            .find(|d| d.delivery.id == delivery_id)
            .map(delivery_view)
            .unwrap())
    }

    pub async fn list_deliveries(
        &self,
        token: &str,
        alert_id: &str,
    ) -> Result<DeliveriesListView, AppFailure> {
        let _ = required_token(token)?;
        let deliveries = self.vigilancia.list_deliveries(alert_id)?;
        Ok(DeliveriesListView {
            deliveries: deliveries.into_iter().map(delivery_view).collect(),
        })
    }
}
