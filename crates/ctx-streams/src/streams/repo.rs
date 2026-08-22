use diesel::SqliteConnection;

use super::{Stream, StreamInput, StreamRegion, StreamRegionInput};
use crate::StreamsError;

pub trait StreamsRepo {
    fn create_stream_in_transaction(
        connection: &mut SqliteConnection,
        id: &str,
        input: StreamInput,
        now: &str,
    ) -> Result<Stream, StreamsError>;

    fn list_streams(
        connection: &mut SqliteConnection,
        room_id: &str,
    ) -> Result<Vec<Stream>, StreamsError>;

    fn get_stream(
        connection: &mut SqliteConnection,
        id: &str,
    ) -> Result<Stream, StreamsError>;

    fn list_regions(
        connection: &mut SqliteConnection,
        stream_id: &str,
    ) -> Result<Vec<StreamRegion>, StreamsError>;

    fn replace_regions_in_transaction(
        connection: &mut SqliteConnection,
        stream_id: &str,
        inputs: Vec<(String, StreamRegionInput)>,
        now: &str,
    ) -> Result<Vec<StreamRegion>, StreamsError>;

    fn update_region_in_transaction(
        connection: &mut SqliteConnection,
        stream_id: &str,
        region_id: &str,
        points: &[(f64, f64)],
        updated_by: Option<&str>,
        now: &str,
    ) -> Result<StreamRegion, StreamsError>;
}
