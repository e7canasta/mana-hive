use ctx_streams::{
    Points, Stream as CtxStream, StreamInput, StreamRegion as CtxStreamRegion,
    StreamRegionInput, StreamRegionType,
};

use crate::{
    error::AppFailure,
    identidad::{
        actor_id, authenticated_actor_in_transaction, require_capability, required_token,
    },
    state::{AppState, Stores},
};

#[derive(Clone, Debug)]
pub struct CreateStreamCommand {
    pub room_id: String,
    pub stream_key: String,
    pub name: Option<String>,
}

#[derive(Clone, Debug)]
pub struct StreamView {
    pub id: String,
    pub room_id: String,
    pub stream_key: String,
    pub name: Option<String>,
}

#[derive(Clone, Debug)]
pub struct StreamRegionView {
    pub id: String,
    pub stream_id: String,
    pub region_type: String,
    pub points: Points,
    pub label: Option<String>,
    pub is_static: bool,
    pub updated_by: Option<String>,
}

#[derive(Clone, Debug)]
pub struct RegionCommand {
    pub region_type: String,
    pub points: Points,
    pub label: Option<String>,
}

#[derive(Clone, Debug)]
pub struct ReplaceRegionsCommand {
    pub regions: Vec<RegionCommand>,
}

#[derive(Clone, Debug)]
pub struct UpdateRegionCommand {
    pub points: Points,
}

impl From<CtxStream> for StreamView {
    fn from(s: CtxStream) -> Self {
        StreamView {
            id: s.id.to_string(),
            room_id: s.room_id,
            stream_key: s.stream_key,
            name: s.name,
        }
    }
}

impl From<CtxStreamRegion> for StreamRegionView {
    fn from(r: CtxStreamRegion) -> Self {
        StreamRegionView {
            id: r.id.to_string(),
            stream_id: r.stream_id.to_string(),
            region_type: r.region_type.to_string(),
            points: r.points,
            label: r.label,
            is_static: r.is_static,
            updated_by: r.updated_by,
        }
    }
}

impl AppState {
    pub async fn create_stream(
        &self,
        token: &str,
        command: CreateStreamCommand,
    ) -> Result<StreamView, AppFailure> {
        let token = required_token(token)?;
        let enabled = self.enabled_capabilities.clone();
        self.transaction(move |connection, stores| {
            let Stores {
                identity,
                audit,
                streams,
                ..
            } = stores;
            let actor = authenticated_actor_in_transaction(identity, connection, &token, &enabled)?;
            require_capability(&actor, "streams.write")?;
            let input = StreamInput {
                room_id: command.room_id,
                stream_key: command.stream_key,
                name: command.name,
            };
            let now = chrono::Utc::now().to_rfc3339();
            let id = ctx_streams::new_stream_id();
            let stream = streams.create_stream_in_transaction(connection, &id, input, &now)?;
            let view = StreamView::from(stream);
            let record = ctx_auditoria::AuditRecord::new(
                Some(actor_id(&actor)),
                "stream.created",
                "stream",
                &view.id,
                serde_json::json!({"room_id": &view.room_id, "stream_key": &view.stream_key}),
            )?;
            audit.record_in_transaction(connection, record)?;
            Ok(view)
        })
        .await
    }

    pub async fn list_streams(
        &self,
        token: &str,
        room_id: &str,
    ) -> Result<Vec<StreamView>, AppFailure> {
        let token = required_token(token)?;
        let enabled = self.enabled_capabilities.clone();
        let room_id = room_id.to_owned();
        self.transaction(move |connection, stores| {
            let Stores { identity, streams, .. } = stores;
            let _actor = authenticated_actor_in_transaction(identity, connection, &token, &enabled)?;
            let list = streams.list_streams_in_transaction(connection, &room_id)?;
            Ok(list.into_iter().map(StreamView::from).collect())
        })
        .await
    }

    pub async fn get_stream(
        &self,
        token: &str,
        stream_id: &str,
    ) -> Result<StreamView, AppFailure> {
        let token = required_token(token)?;
        let enabled = self.enabled_capabilities.clone();
        let stream_id = stream_id.to_owned();
        self.transaction(move |connection, stores| {
            let Stores { identity, streams, .. } = stores;
            let _actor = authenticated_actor_in_transaction(identity, connection, &token, &enabled)?;
            let stream = streams.get_stream_in_transaction(connection, &stream_id)?;
            Ok(StreamView::from(stream))
        })
        .await
    }

    pub async fn list_regions(
        &self,
        token: &str,
        stream_id: &str,
    ) -> Result<Vec<StreamRegionView>, AppFailure> {
        let token = required_token(token)?;
        let enabled = self.enabled_capabilities.clone();
        let stream_id = stream_id.to_owned();
        self.transaction(move |connection, stores| {
            let Stores { identity, streams, .. } = stores;
            let _actor = authenticated_actor_in_transaction(identity, connection, &token, &enabled)?;
            let list = streams.list_regions_in_transaction(connection, &stream_id)?;
            Ok(list.into_iter().map(StreamRegionView::from).collect())
        })
        .await
    }

    pub async fn replace_regions(
        &self,
        token: &str,
        stream_id: &str,
        command: ReplaceRegionsCommand,
    ) -> Result<Vec<StreamRegionView>, AppFailure> {
        let token = required_token(token)?;
        let enabled = self.enabled_capabilities.clone();
        let stream_id = stream_id.to_owned();
        self.transaction(move |connection, stores| {
            let Stores {
                identity,
                audit,
                streams,
                ..
            } = stores;
            let actor = authenticated_actor_in_transaction(identity, connection, &token, &enabled)?;
            require_capability(&actor, "streams.write")?;
            let inputs: Vec<StreamRegionInput> = command
                .regions
                .into_iter()
                .map(|r| {
                    let region_type = StreamRegionType::parse(&r.region_type)
                        .map_err(|e| AppFailure::validation(e.to_string(), None))?;
                    ctx_streams::validate_points(&r.points)
                        .map_err(|e| AppFailure::validation(e.to_string(), None))?;
                    Ok(StreamRegionInput {
                        region_type,
                        points: r.points,
                        label: r.label,
                    })
                })
                .collect::<Result<Vec<_>, AppFailure>>()?;
            let now = chrono::Utc::now().to_rfc3339();
            let regions = streams.replace_regions_in_transaction(connection, &stream_id, inputs, &now)?;
            let record = ctx_auditoria::AuditRecord::new(
                Some(actor_id(&actor)),
                "stream.regions.replaced",
                "stream",
                &stream_id,
                serde_json::json!({"count": regions.len()}),
            )?;
            audit.record_in_transaction(connection, record)?;
            Ok(regions.into_iter().map(StreamRegionView::from).collect())
        })
        .await
    }

    pub async fn update_region(
        &self,
        token: &str,
        stream_id: &str,
        region_id: &str,
        command: UpdateRegionCommand,
    ) -> Result<StreamRegionView, AppFailure> {
        let token = required_token(token)?;
        let enabled = self.enabled_capabilities.clone();
        let stream_id = stream_id.to_owned();
        let region_id = region_id.to_owned();
        self.transaction(move |connection, stores| {
            let Stores {
                identity,
                audit,
                streams,
                ..
            } = stores;
            let actor = authenticated_actor_in_transaction(identity, connection, &token, &enabled)?;
            require_capability(&actor, "streams.write")?;
            let now = chrono::Utc::now().to_rfc3339();
            let actor_id_str = actor.id.to_string();
            let region = streams.update_region_in_transaction(
                connection,
                &stream_id,
                &region_id,
                &command.points,
                Some(&actor_id_str),
                &now,
            )?;
            let record = ctx_auditoria::AuditRecord::new(
                Some(actor_id(&actor)),
                "stream.region.updated",
                "stream",
                &stream_id,
                serde_json::json!({"region_id": &region_id}),
            )?;
            audit.record_in_transaction(connection, record)?;
            Ok(StreamRegionView::from(region))
        })
        .await
    }
}
