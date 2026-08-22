use diesel::prelude::*;
use diesel::sqlite::SqliteConnection;

use super::repo::AlertasRepo;
use super::{
    new_alert_id, Alert, AlertInput, AlertLevel, AlertStatus, AlertTransition, EscalationInput,
    EvidenceKind, TransitionInput,
};
use crate::error::VigilanciaError;
use crate::schema::{alert_escalations, alert_transitions, alerts};

#[derive(Queryable)]
struct AlertRow {
    id: String,
    resident_id: Option<String>,
    bed_id: String,
    evidence_kind: String,
    evidence_ref: Option<String>,
    rule_id: String,
    level: String,
    status: String,
    status_actor_id: Option<String>,
    status_at: Option<String>,
    title: String,
    detail: Option<String>,
    occurred_at: String,
    escalation_level: i32,
    escalated_at: Option<String>,
    escalated_to: Option<String>,
    created_at: String,
    updated_at: String,
}

#[derive(Insertable)]
#[diesel(table_name = alerts)]
struct NewAlert {
    id: String,
    resident_id: Option<String>,
    bed_id: String,
    evidence_kind: String,
    evidence_ref: Option<String>,
    rule_id: String,
    level: String,
    status: String,
    status_actor_id: Option<String>,
    status_at: Option<String>,
    title: String,
    detail: Option<String>,
    occurred_at: String,
    escalation_level: i32,
    escalated_at: Option<String>,
    escalated_to: Option<String>,
    created_at: String,
    updated_at: String,
}

#[derive(Queryable)]
struct TransitionRow {
    id: String,
    alert_id: String,
    from_status: Option<String>,
    to_status: String,
    actor_id: Option<String>,
    occurred_at: String,
    sequence: i32,
}

#[derive(Insertable)]
#[diesel(table_name = alert_transitions)]
struct NewTransition {
    id: String,
    alert_id: String,
    from_status: Option<String>,
    to_status: String,
    actor_id: Option<String>,
    occurred_at: String,
    sequence: i32,
}

#[derive(Insertable)]
#[diesel(table_name = alert_escalations)]
struct NewEscalation {
    id: String,
    alert_id: String,
    level: i32,
    target_id: String,
    occurred_at: String,
    created_at: String,
}

fn row_to_alert(row: AlertRow) -> Alert {
    Alert {
        id: row.id,
        resident_id: row.resident_id,
        bed_id: row.bed_id,
        evidence_kind: EvidenceKind::parse(&row.evidence_kind).unwrap_or(EvidenceKind::Manual),
        evidence_ref: row.evidence_ref,
        rule_id: row.rule_id,
        level: AlertLevel::parse(&row.level).unwrap_or(AlertLevel::Low),
        status: AlertStatus::parse(&row.status).unwrap_or(AlertStatus::Open),
        status_actor_id: row.status_actor_id,
        status_at: row.status_at.and_then(|v| v.parse().ok()),
        title: row.title,
        detail: row.detail,
        occurred_at: row.occurred_at.parse().unwrap(),
        escalation_level: row.escalation_level,
        escalated_at: row.escalated_at.and_then(|v| v.parse().ok()),
        escalated_to: row.escalated_to,
        created_at: row.created_at.parse().unwrap(),
        updated_at: row.updated_at.parse().unwrap(),
    }
}

fn row_to_transition(row: TransitionRow) -> AlertTransition {
    AlertTransition {
        id: row.id,
        alert_id: row.alert_id,
        from_status: row.from_status.and_then(|v| AlertStatus::parse(&v).ok()),
        to_status: AlertStatus::parse(&row.to_status).unwrap_or(AlertStatus::Open),
        actor_id: row.actor_id,
        occurred_at: row.occurred_at.parse().unwrap(),
        sequence: row.sequence,
    }
}

impl AlertasRepo for SqliteConnection {
    fn get(&mut self, id: &str) -> Result<Alert, VigilanciaError> {
        let row: AlertRow =
            alerts::table
                .find(id)
                .first::<AlertRow>(self)
                .map_err(|e| match e {
                    diesel::result::Error::NotFound => {
                        VigilanciaError::not_found(format!("alerta {id}"))
                    }
                    other => VigilanciaError::from(other),
                })?;
        Ok(row_to_alert(row))
    }

    fn list(
        &mut self,
        status: Option<&str>,
        bed_id: Option<&str>,
        resident_id: Option<&str>,
    ) -> Result<Vec<Alert>, VigilanciaError> {
        let mut query = alerts::table.into_boxed();

        if let Some(s) = status {
            query = query.filter(alerts::status.eq(s));
        }
        if let Some(b) = bed_id {
            query = query.filter(alerts::bed_id.eq(b));
        }
        if let Some(r) = resident_id {
            query = query.filter(alerts::resident_id.eq(r));
        }

        let rows: Vec<AlertRow> = query
            .order(alerts::created_at.desc())
            .load::<AlertRow>(self)?;

        Ok(rows.into_iter().map(row_to_alert).collect())
    }

    fn create_in_transaction(&mut self, input: AlertInput) -> Result<Alert, VigilanciaError> {
        self.transaction::<_, VigilanciaError, _>(|conn| {
            let now = mana_kernel::Instante::now();
            let id = new_alert_id();

            let new_alert = NewAlert {
                id: id.clone(),
                resident_id: input.resident_id,
                bed_id: input.bed_id,
                evidence_kind: input.evidence_kind.as_str().to_owned(),
                evidence_ref: input.evidence_ref,
                rule_id: input.rule_id,
                level: input.level.as_str().to_owned(),
                status: AlertStatus::Open.as_str().to_owned(),
                status_actor_id: None,
                status_at: None,
                title: input.title,
                detail: input.detail,
                occurred_at: input.occurred_at.to_string(),
                escalation_level: 0,
                escalated_at: None,
                escalated_to: None,
                created_at: now.to_string(),
                updated_at: now.to_string(),
            };

            diesel::insert_into(alerts::table)
                .values(&new_alert)
                .execute(conn)?;

            let transition_id = new_alert_id();
            let new_transition = NewTransition {
                id: transition_id,
                alert_id: id.clone(),
                from_status: None,
                to_status: AlertStatus::Open.as_str().to_owned(),
                actor_id: None,
                occurred_at: now.to_string(),
                sequence: 1,
            };

            diesel::insert_into(alert_transitions::table)
                .values(&new_transition)
                .execute(conn)?;

            let row: AlertRow = alerts::table.find(&id).first::<AlertRow>(conn)?;
            Ok(row_to_alert(row))
        })
    }

    fn transition_in_transaction(
        &mut self,
        alert_id: &str,
        input: TransitionInput,
    ) -> Result<Alert, VigilanciaError> {
        self.transaction::<_, VigilanciaError, _>(|conn| {
            let current_row: AlertRow = alerts::table
                .find(alert_id)
                .first::<AlertRow>(conn)
                .map_err(|e| match e {
                    diesel::result::Error::NotFound => {
                        VigilanciaError::not_found(format!("alerta {alert_id}"))
                    }
                    other => VigilanciaError::from(other),
                })?;

            let current_status = AlertStatus::parse(&current_row.status)?;

            if !current_status.can_transition_to(&input.to_status) {
                return Err(VigilanciaError::from(
                    super::AlertasError::InvalidTransition,
                ));
            }

            if input.to_status != AlertStatus::Open && input.actor_id.is_none() {
                return Err(VigilanciaError::from(super::AlertasError::MissingActor));
            }

            let now = mana_kernel::Instante::now();

            diesel::update(alerts::table.filter(alerts::id.eq(alert_id)))
                .set((
                    alerts::status.eq(input.to_status.as_str()),
                    alerts::status_actor_id.eq(&input.actor_id),
                    alerts::status_at.eq(input.occurred_at.to_string()),
                    alerts::updated_at.eq(now.to_string()),
                ))
                .execute(conn)?;

            let max_seq: i32 = alert_transitions::table
                .filter(alert_transitions::alert_id.eq(alert_id))
                .select(diesel::dsl::max(alert_transitions::sequence))
                .first::<Option<i32>>(conn)?
                .unwrap_or(0);

            let transition_id = new_alert_id();
            let new_transition = NewTransition {
                id: transition_id,
                alert_id: alert_id.to_owned(),
                from_status: Some(current_status.as_str().to_owned()),
                to_status: input.to_status.as_str().to_owned(),
                actor_id: input.actor_id,
                occurred_at: input.occurred_at.to_string(),
                sequence: max_seq + 1,
            };

            diesel::insert_into(alert_transitions::table)
                .values(&new_transition)
                .execute(conn)?;

            let row: AlertRow = alerts::table.find(alert_id).first::<AlertRow>(conn)?;
            Ok(row_to_alert(row))
        })
    }

    fn escalate_in_transaction(
        &mut self,
        alert_id: &str,
        input: EscalationInput,
    ) -> Result<Alert, VigilanciaError> {
        self.transaction::<_, VigilanciaError, _>(|conn| {
            let now = mana_kernel::Instante::now();

            diesel::update(alerts::table.filter(alerts::id.eq(alert_id)))
                .set((
                    alerts::escalation_level.eq(input.level),
                    alerts::escalated_at.eq(input.occurred_at.to_string()),
                    alerts::escalated_to.eq(&input.target_id),
                    alerts::updated_at.eq(now.to_string()),
                ))
                .execute(conn)?;

            let escalation_id = new_alert_id();
            let new_escalation = NewEscalation {
                id: escalation_id,
                alert_id: alert_id.to_owned(),
                level: input.level,
                target_id: input.target_id,
                occurred_at: input.occurred_at.to_string(),
                created_at: now.to_string(),
            };

            diesel::insert_into(alert_escalations::table)
                .values(&new_escalation)
                .execute(conn)?;

            let row: AlertRow = alerts::table.find(alert_id).first::<AlertRow>(conn)?;
            Ok(row_to_alert(row))
        })
    }

    fn list_transitions(
        &mut self,
        alert_id: &str,
    ) -> Result<Vec<AlertTransition>, VigilanciaError> {
        let rows: Vec<TransitionRow> = alert_transitions::table
            .filter(alert_transitions::alert_id.eq(alert_id))
            .order(alert_transitions::sequence.asc())
            .load::<TransitionRow>(self)?;

        Ok(rows.into_iter().map(row_to_transition).collect())
    }
}
