//! Infraestructura SQLite compartida por los contextos persistentes.

use diesel::{
    connection::SimpleConnection,
    r2d2::{ConnectionManager, CustomizeConnection, Pool, PooledConnection},
    SqliteConnection,
};
use diesel_migrations::{EmbeddedMigrations, MigrationHarness};
use thiserror::Error;

pub type DbPool = Pool<ConnectionManager<SqliteConnection>>;
pub type DbConnection = PooledConnection<ConnectionManager<SqliteConnection>>;

const DEFAULT_POOL_SIZE: u32 = 8;
const IN_MEMORY_POOL_SIZE: u32 = 1;

#[derive(Debug, Error)]
pub enum StorageError {
    #[error("no se pudo obtener una conexion SQLite: {0}")]
    Pool(String),
    #[error("error de migracion: {0}")]
    Migration(String),
}

#[derive(Debug)]
struct SqliteConnectionCustomizer;

impl CustomizeConnection<SqliteConnection, diesel::r2d2::Error> for SqliteConnectionCustomizer {
    fn on_acquire(&self, connection: &mut SqliteConnection) -> Result<(), diesel::r2d2::Error> {
        // `busy_timeout` va **primero**, y no es cosmetico. r2d2 abre las ocho
        // conexiones del pool en paralelo al construirlo, y cambiar el
        // `journal_mode` pide el lock exclusivo de la base: con el timeout
        // todavia en cero, siete de las ocho se llevan un `database is locked`
        // instantaneo y el hub no arranca contra una base de archivo.
        //
        // No aparecia en los tests porque usan `:memory:`, que tiene pool de
        // una sola conexion y no puede correr la carrera.
        connection
            .batch_execute(
                "PRAGMA busy_timeout = 5000; PRAGMA foreign_keys = ON; PRAGMA journal_mode = WAL; PRAGMA synchronous = NORMAL;",
            )
            .map_err(diesel::r2d2::Error::QueryError)
    }
}

pub fn build_pool(database_url: &str) -> Result<DbPool, StorageError> {
    let manager = ConnectionManager::<SqliteConnection>::new(database_url);
    let max_size = if database_url == ":memory:" {
        IN_MEMORY_POOL_SIZE
    } else {
        DEFAULT_POOL_SIZE
    };
    Pool::builder()
        .max_size(max_size)
        .connection_customizer(Box::new(SqliteConnectionCustomizer))
        .build(manager)
        .map_err(|error| StorageError::Pool(error.to_string()))
}

pub fn connection(pool: &DbPool) -> Result<DbConnection, StorageError> {
    pool.get()
        .map_err(|error| StorageError::Pool(error.to_string()))
}

pub fn run_migrations(pool: &DbPool, migrations: EmbeddedMigrations) -> Result<(), StorageError> {
    let mut connection = connection(pool)?;
    connection
        .run_pending_migrations(migrations)
        .map(|_| ())
        .map_err(|error| StorageError::Migration(error.to_string()))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn in_memory_pool_is_single_connection_and_usable() {
        let pool = build_pool(":memory:").unwrap();
        assert_eq!(pool.max_size(), IN_MEMORY_POOL_SIZE);

        let mut connection = connection(&pool).unwrap();
        connection
            .batch_execute("CREATE TABLE storage_probe (id INTEGER NOT NULL);")
            .unwrap();
    }
}
