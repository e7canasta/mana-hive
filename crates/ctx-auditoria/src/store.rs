use diesel::{prelude::*, sql_types::BigInt, Connection, SqliteConnection};
use diesel_migrations::{embed_migrations, EmbeddedMigrations};
use mana_kernel::{Actor, Id, Instante};
use mana_storage::{connection as get_connection, DbConnection, DbPool};

use crate::{
    domain::{new_audit_id, AuditEntry, AuditFilter, AuditRecord},
    schema::audit_log,
    AuditError,
};

pub const MIGRATIONS: EmbeddedMigrations = embed_migrations!();

#[derive(Clone)]
pub struct AuditStore {
    pool: DbPool,
}

#[derive(Queryable, Selectable)]
#[diesel(table_name = audit_log)]
#[diesel(check_for_backend(diesel::sqlite::Sqlite))]
struct AuditRow {
    id: String,
    actor_id: Option<String>,
    action: String,
    entity_type: String,
    entity_id: String,
    metadata_json: String,
    created_at: String,
}

#[derive(Insertable)]
#[diesel(table_name = audit_log)]
struct NewAuditRow<'a> {
    id: &'a str,
    actor_id: Option<&'a str>,
    action: &'a str,
    entity_type: &'a str,
    entity_id: &'a str,
    metadata_json: &'a str,
    created_at: &'a str,
}

pub fn run_migrations(pool: &DbPool) -> Result<(), AuditError> {
    mana_storage::run_migrations(pool, MIGRATIONS).map_err(AuditError::from)
}

impl AuditStore {
    pub fn new(pool: DbPool) -> Self {
        Self { pool }
    }

    pub fn record(&self, record: AuditRecord) -> Result<AuditEntry, AuditError> {
        let mut connection = self.connection()?;
        connection.transaction(|connection| self.record_in_transaction(connection, record))
    }

    /// Escribe sobre la conexion que ya contiene la transaccion del caso de
    /// uso. El contexto no abre una conexion propia en este camino.
    pub fn record_in_transaction(
        &self,
        connection: &mut SqliteConnection,
        record: AuditRecord,
    ) -> Result<AuditEntry, AuditError> {
        let id = new_audit_id()?;
        let metadata_json = serde_json::to_string(&record.metadata)
            .map_err(|_| crate::domain::AuditDomainError::InvalidMetadata)?;
        let created_at = Instante::now();
        let created_at_text = created_at.to_string();
        let row = NewAuditRow {
            id: id.as_str(),
            actor_id: record.actor_id.as_ref().map(Id::as_str),
            action: &record.action,
            entity_type: &record.entity_type,
            entity_id: &record.entity_id,
            metadata_json: &metadata_json,
            created_at: &created_at_text,
        };
        diesel::insert_into(audit_log::table)
            .values(row)
            .execute(connection)?;
        Ok(AuditEntry {
            id,
            actor_id: record.actor_id,
            action: record.action,
            entity_type: record.entity_type,
            entity_id: record.entity_id,
            metadata: record.metadata,
            created_at,
        })
    }

    pub fn list(&self, filter: &AuditFilter) -> Result<Vec<AuditEntry>, AuditError> {
        let mut connection = self.connection()?;
        let mut query = audit_log::table
            .select(AuditRow::as_select())
            .order((
                audit_log::created_at.desc(),
                diesel::dsl::sql::<BigInt>("rowid").desc(),
            ))
            .limit(filter.effective_limit() as i64)
            .into_boxed();
        if let Some(entity_type) = filter.entity_type.as_deref() {
            query = query.filter(audit_log::entity_type.eq(entity_type));
        }
        if let Some(entity_id) = filter.entity_id.as_deref() {
            query = query.filter(audit_log::entity_id.eq(entity_id));
        }
        if let Some(action) = filter.action.as_deref() {
            query = query.filter(audit_log::action.eq(action));
        }
        query
            .load::<AuditRow>(&mut connection)?
            .into_iter()
            .map(entry_from_row)
            .collect()
    }

    fn connection(&self) -> Result<DbConnection, AuditError> {
        get_connection(&self.pool).map_err(AuditError::from)
    }
}

fn entry_from_row(row: AuditRow) -> Result<AuditEntry, AuditError> {
    let created_at = row
        .created_at
        .parse::<Instante>()
        .map_err(|error| AuditError::InvalidStoredData(format!("created_at: {error}")))?;
    let metadata = serde_json::from_str(&row.metadata_json)
        .map_err(|error| AuditError::InvalidStoredData(format!("metadata_json: {error}")))?;
    Ok(AuditEntry {
        id: row.id.into(),
        actor_id: row.actor_id.map(Id::<Actor>::new),
        action: row.action,
        entity_type: row.entity_type,
        entity_id: row.entity_id,
        metadata,
        created_at,
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use diesel::Connection;
    use mana_storage::build_pool;
    use serde_json::json;

    fn store() -> AuditStore {
        let pool = build_pool(":memory:").unwrap();
        run_migrations(&pool).unwrap();
        AuditStore::new(pool)
    }

    fn record(action: &str) -> AuditRecord {
        AuditRecord::new(
            Some(Id::new("user-gaston")),
            action,
            "user",
            "user-staff",
            json!({"role": "staff"}),
        )
        .unwrap()
    }

    #[test]
    fn records_and_filters_append_only_entries() {
        let store = store();
        store.record(record("user.created")).unwrap();
        store.record(record("user.updated")).unwrap();

        let entries = store
            .list(&AuditFilter {
                entity_type: Some("user".to_owned()),
                entity_id: Some("user-staff".to_owned()),
                limit: Some(1),
                ..AuditFilter::default()
            })
            .unwrap();
        assert_eq!(entries.len(), 1);
        assert_eq!(entries[0].action, "user.updated");
        assert_eq!(
            entries[0].actor_id.as_ref().unwrap().as_str(),
            "user-gaston"
        );
    }

    #[test]
    fn a_rolled_back_transaction_does_not_leave_an_audit_entry() {
        let store = store();
        let mut connection = store.pool.get().unwrap();
        let result = connection.transaction::<(), AuditError, _>(|connection| {
            store.record_in_transaction(connection, record("user.created"))?;
            Err(AuditError::Domain(
                crate::domain::AuditDomainError::InvalidMetadata,
            ))
        });
        drop(connection);
        assert!(result.is_err());
        assert!(store.list(&AuditFilter::default()).unwrap().is_empty());
    }

    #[test]
    fn rejects_empty_labels_and_oversized_metadata() {
        assert!(matches!(
            AuditRecord::new(None, " ", "user", "user-1", json!({})),
            Err(crate::domain::AuditDomainError::EmptyAction)
        ));
        assert!(matches!(
            AuditRecord::new(
                None,
                "user.created",
                "user",
                "user-1",
                json!({"value": "x".repeat(17 * 1024)})
            ),
            Err(crate::domain::AuditDomainError::MetadataTooLarge)
        ));
        assert!(matches!(
            AuditRecord::new(None, "user.created", "user", "user-1", json!([])),
            Err(crate::domain::AuditDomainError::InvalidMetadata)
        ));
    }
}
