use diesel::prelude::*;
use diesel::SqliteConnection;

use crate::schema::{stream_regions, streams};
use crate::StreamsError;

use super::repo::StreamsRepo;
use super::{Stream, StreamRegion, StreamRegionInput, StreamRegionType};

#[derive(Queryable, Selectable)]
#[diesel(table_name = streams)]
#[diesel(check_for_backend(diesel::sqlite::Sqlite))]
struct StreamRow {
    id: String,
    room_id: String,
    stream_key: String,
    name: Option<String>,
    #[allow(dead_code)]
    active: i32,
    #[allow(dead_code)]
    created_at: String,
    #[allow(dead_code)]
    updated_at: String,
}

#[derive(Insertable)]
#[diesel(table_name = streams)]
struct NewStreamRow<'a> {
    id: &'a str,
    room_id: &'a str,
    stream_key: &'a str,
    name: Option<&'a str>,
    active: i32,
    created_at: &'a str,
    updated_at: &'a str,
}

#[derive(Queryable, Selectable)]
#[diesel(table_name = stream_regions)]
#[diesel(check_for_backend(diesel::sqlite::Sqlite))]
struct StreamRegionRow {
    id: String,
    stream_id: String,
    region_type: String,
    points: String,
    label: Option<String>,
    is_static: i32,
    updated_by: Option<String>,
    #[allow(dead_code)]
    created_at: String,
    #[allow(dead_code)]
    updated_at: String,
}

#[derive(Insertable)]
#[diesel(table_name = stream_regions)]
struct NewStreamRegionRow<'a> {
    id: &'a str,
    stream_id: &'a str,
    region_type: &'a str,
    points: &'a str,
    label: Option<&'a str>,
    is_static: i32,
    updated_by: Option<&'a str>,
    created_at: &'a str,
    updated_at: &'a str,
}

impl From<StreamRow> for Stream {
    fn from(row: StreamRow) -> Self {
        Stream {
            id: row.id.into(),
            room_id: row.room_id,
            stream_key: row.stream_key,
            name: row.name,
        }
    }
}

fn parse_points(json: &str) -> Result<Vec<(f64, f64)>, StreamsError> {
    serde_json::from_str(json).map_err(|e| StreamsError::InvalidStoredData(e.to_string()))
}

fn serialize_points(points: &[(f64, f64)]) -> Result<String, StreamsError> {
    serde_json::to_string(points).map_err(|e| StreamsError::InvalidStoredData(e.to_string()))
}

impl From<StreamRegionRow> for StreamRegion {
    fn from(row: StreamRegionRow) -> Self {
        let region_type = StreamRegionType::parse(&row.region_type)
            .unwrap_or(StreamRegionType::Object);
        StreamRegion {
            id: row.id.into(),
            stream_id: row.stream_id.into(),
            region_type,
            points: parse_points(&row.points).unwrap_or_default(),
            label: row.label,
            is_static: row.is_static != 0,
            updated_by: row.updated_by,
        }
    }
}

impl StreamsRepo for SqliteConnection {
    fn create_stream_in_transaction(
        connection: &mut SqliteConnection,
        id: &str,
        input: super::StreamInput,
        now: &str,
    ) -> Result<Stream, StreamsError> {
        let new = NewStreamRow {
            id,
            room_id: &input.room_id,
            stream_key: &input.stream_key,
            name: input.name.as_deref(),
            active: 1,
            created_at: now,
            updated_at: now,
        };
        diesel::insert_into(streams::table)
            .values(&new)
            .execute(connection)
            .map_err(StreamsError::database)?;

        streams::table
            .find(id)
            .select(StreamRow::as_select())
            .first(connection)
            .map(Stream::from)
            .map_err(StreamsError::database)
    }

    fn list_streams(
        connection: &mut SqliteConnection,
        room_id: &str,
    ) -> Result<Vec<Stream>, StreamsError> {
        streams::table
            .filter(streams::room_id.eq(room_id))
            .filter(streams::active.eq(1))
            .select(StreamRow::as_select())
            .load(connection)
            .map(|rows| rows.into_iter().map(Stream::from).collect())
            .map_err(StreamsError::database)
    }

    fn get_stream(connection: &mut SqliteConnection, id: &str) -> Result<Stream, StreamsError> {
        streams::table
            .find(id)
            .filter(streams::active.eq(1))
            .select(StreamRow::as_select())
            .first(connection)
            .map(Stream::from)
            .map_err(StreamsError::database)
    }

    fn list_regions(
        connection: &mut SqliteConnection,
        stream_id: &str,
    ) -> Result<Vec<StreamRegion>, StreamsError> {
        stream_regions::table
            .filter(stream_regions::stream_id.eq(stream_id))
            .select(StreamRegionRow::as_select())
            .load(connection)
            .map(|rows| rows.into_iter().map(StreamRegion::from).collect())
            .map_err(StreamsError::database)
    }

    fn replace_regions_in_transaction(
        connection: &mut SqliteConnection,
        stream_id: &str,
        inputs: Vec<(String, StreamRegionInput)>,
        now: &str,
    ) -> Result<Vec<StreamRegion>, StreamsError> {
        diesel::delete(
            stream_regions::table.filter(stream_regions::stream_id.eq(stream_id)),
        )
        .execute(connection)
        .map_err(StreamsError::database)?;

        let mut result = Vec::new();
        for (id, input) in inputs {
            let points_json = serialize_points(&input.points)?;
            let new = NewStreamRegionRow {
                id: &id,
                stream_id,
                region_type: input.region_type.as_str(),
                points: &points_json,
                label: input.label.as_deref(),
                is_static: input.region_type.is_static() as i32,
                updated_by: None,
                created_at: now,
                updated_at: now,
            };
            diesel::insert_into(stream_regions::table)
                .values(&new)
                .execute(connection)
                .map_err(StreamsError::database)?;

            let row = stream_regions::table
                .filter(stream_regions::id.eq(id.as_str()))
                .select(StreamRegionRow::as_select())
                .first(connection)
                .map_err(StreamsError::database)?;
            result.push(StreamRegion::from(row));
        }
        Ok(result)
    }

    fn update_region_in_transaction(
        connection: &mut SqliteConnection,
        stream_id: &str,
        region_id: &str,
        points: &[(f64, f64)],
        updated_by: Option<&str>,
        now: &str,
    ) -> Result<StreamRegion, StreamsError> {
        let points_json = serialize_points(points)?;
        diesel::update(
            stream_regions::table
                .filter(stream_regions::id.eq(region_id))
                .filter(stream_regions::stream_id.eq(stream_id)),
        )
        .set((
            stream_regions::points.eq(&points_json),
            stream_regions::updated_by.eq(updated_by),
            stream_regions::updated_at.eq(now),
        ))
        .execute(connection)
        .map_err(StreamsError::database)?;

        stream_regions::table
            .find(region_id)
            .select(StreamRegionRow::as_select())
            .first(connection)
            .map(StreamRegion::from)
            .map_err(StreamsError::database)
    }
}
