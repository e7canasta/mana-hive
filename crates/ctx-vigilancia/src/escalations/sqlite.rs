use diesel::prelude::*;
use diesel::sqlite::SqliteConnection;

use super::repo::EscalationsRepo;
use super::AlertEscalation;
use crate::error::VigilanciaError;
use crate::schema::alert_escalations;

#[derive(Queryable)]
struct EscalationRow {
    id: String,
    alert_id: String,
    level: i32,
    target_id: String,
    occurred_at: String,
    created_at: String,
}

fn row_to_escalation(row: EscalationRow) -> AlertEscalation {
    AlertEscalation {
        id: row.id,
        alert_id: row.alert_id,
        level: row.level,
        target_id: row.target_id,
        occurred_at: row.occurred_at.parse().unwrap(),
        created_at: row.created_at.parse().unwrap(),
    }
}

impl EscalationsRepo for SqliteConnection {
    fn list_by_alert(&mut self, alert_id: &str) -> Result<Vec<AlertEscalation>, VigilanciaError> {
        let rows: Vec<EscalationRow> = alert_escalations::table
            .filter(alert_escalations::alert_id.eq(alert_id))
            .order(alert_escalations::level.asc())
            .load::<EscalationRow>(self)?;

        Ok(rows.into_iter().map(row_to_escalation).collect())
    }
}
