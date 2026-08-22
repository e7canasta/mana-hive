//! Cámaras/streams y regiones de interés poligonales.
//!
//! - [`streams`]: streams vinculados a rooms y sus regiones ROI.

mod common;
mod error;

pub mod schema;
pub mod streams;

pub use error::StreamsError;
pub use mana_storage::DbPool;
pub use streams::{
    new_stream_id, new_stream_region_id, validate_points, Points, Stream, StreamId, StreamInput,
    StreamRegion, StreamRegionId, StreamRegionInput, StreamRegionType,
};

use diesel::prelude::*;
use diesel_migrations::{embed_migrations, EmbeddedMigrations};
use mana_storage::{connection as get_connection, DbConnection};

use crate::streams::repo::StreamsRepo;

pub const MIGRATIONS: EmbeddedMigrations = embed_migrations!();

#[derive(Clone)]
pub struct StreamsStore {
    pub(crate) pool: DbPool,
}

pub fn run_migrations(pool: &DbPool) -> Result<(), StreamsError> {
    mana_storage::run_migrations(pool, MIGRATIONS).map_err(StreamsError::from)
}

impl StreamsStore {
    pub fn new(pool: DbPool) -> Self {
        Self { pool }
    }

    fn connection(&self) -> Result<DbConnection, StreamsError> {
        get_connection(&self.pool).map_err(StreamsError::from)
    }

    pub fn create_stream(
        &self,
        input: StreamInput,
        now: &str,
    ) -> Result<Stream, StreamsError> {
        let id = new_stream_id();
        let mut connection = self.connection()?;
        connection.transaction(|connection| {
            <SqliteConnection as StreamsRepo>::create_stream_in_transaction(
                connection, &id, input, now,
            )
        })
    }

    pub fn create_stream_in_transaction(
        &self,
        connection: &mut SqliteConnection,
        id: &str,
        input: StreamInput,
        now: &str,
    ) -> Result<Stream, StreamsError> {
        <SqliteConnection as StreamsRepo>::create_stream_in_transaction(
            connection, id, input, now,
        )
    }

    pub fn list_streams(&self, room_id: &str) -> Result<Vec<Stream>, StreamsError> {
        let mut connection = self.connection()?;
        <SqliteConnection as StreamsRepo>::list_streams(&mut connection, room_id)
    }

    pub fn list_streams_in_transaction(
        &self,
        connection: &mut SqliteConnection,
        room_id: &str,
    ) -> Result<Vec<Stream>, StreamsError> {
        <SqliteConnection as StreamsRepo>::list_streams(connection, room_id)
    }

    pub fn get_stream(&self, id: &str) -> Result<Stream, StreamsError> {
        let mut connection = self.connection()?;
        <SqliteConnection as StreamsRepo>::get_stream(&mut connection, id)
    }

    pub fn get_stream_in_transaction(
        &self,
        connection: &mut SqliteConnection,
        id: &str,
    ) -> Result<Stream, StreamsError> {
        <SqliteConnection as StreamsRepo>::get_stream(connection, id)
    }

    pub fn list_regions(&self, stream_id: &str) -> Result<Vec<StreamRegion>, StreamsError> {
        let mut connection = self.connection()?;
        <SqliteConnection as StreamsRepo>::list_regions(&mut connection, stream_id)
    }

    pub fn list_regions_in_transaction(
        &self,
        connection: &mut SqliteConnection,
        stream_id: &str,
    ) -> Result<Vec<StreamRegion>, StreamsError> {
        <SqliteConnection as StreamsRepo>::list_regions(connection, stream_id)
    }

    pub fn replace_regions(
        &self,
        stream_id: &str,
        inputs: Vec<StreamRegionInput>,
        now: &str,
    ) -> Result<Vec<StreamRegion>, StreamsError> {
        let mut connection = self.connection()?;
        let ids_and_inputs: Vec<_> = inputs
            .into_iter()
            .map(|input| (streams::new_stream_region_id(), input))
            .collect();
        connection.transaction(|connection| {
            <SqliteConnection as StreamsRepo>::replace_regions_in_transaction(
                connection,
                stream_id,
                ids_and_inputs,
                now,
            )
        })
    }

    pub fn replace_regions_in_transaction(
        &self,
        connection: &mut SqliteConnection,
        stream_id: &str,
        inputs: Vec<StreamRegionInput>,
        now: &str,
    ) -> Result<Vec<StreamRegion>, StreamsError> {
        let ids_and_inputs: Vec<_> = inputs
            .into_iter()
            .map(|input| (streams::new_stream_region_id(), input))
            .collect();
        <SqliteConnection as StreamsRepo>::replace_regions_in_transaction(
            connection,
            stream_id,
            ids_and_inputs,
            now,
        )
    }

    pub fn update_region(
        &self,
        stream_id: &str,
        region_id: &str,
        points: &[(f64, f64)],
        updated_by: Option<&str>,
        now: &str,
    ) -> Result<StreamRegion, StreamsError> {
        let mut connection = self.connection()?;
        connection.transaction(|connection| {
            <SqliteConnection as StreamsRepo>::update_region_in_transaction(
                connection,
                stream_id,
                region_id,
                points,
                updated_by,
                now,
            )
        })
    }

    pub fn update_region_in_transaction(
        &self,
        connection: &mut SqliteConnection,
        stream_id: &str,
        region_id: &str,
        points: &[(f64, f64)],
        updated_by: Option<&str>,
        now: &str,
    ) -> Result<StreamRegion, StreamsError> {
        <SqliteConnection as StreamsRepo>::update_region_in_transaction(
            connection,
            stream_id,
            region_id,
            points,
            updated_by,
            now,
        )
    }
}

#[cfg(test)]
pub(crate) mod testsupport {
    use mana_storage::build_pool;

    use super::{run_migrations, StreamsStore};

    pub(crate) fn store() -> StreamsStore {
        let pool = build_pool(":memory:").unwrap();
        run_migrations(&pool).unwrap();
        StreamsStore::new(pool)
    }
}
