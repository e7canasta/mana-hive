pub mod schema;

mod clip_window;
mod common;
mod error;
mod evidence;
mod timeline;

pub use clip_window::{
    CategorizedEvent, ClipWindow, ClipWindowCloseInput, ClipWindowFilter, ClipWindowInput,
    CloseCondition, WindowState,
};
pub use error::EvidenceError;
pub use evidence::{Evidence, EvidenceFilter, EvidenceInput, EvidenceType, EventCategory};
pub use mana_storage::DbPool;
pub use timeline::{Timeline, TimelineCloseInput, TimelineFilter, TimelineInput, TimelineEvent};

use diesel::prelude::*;
use diesel_migrations::{embed_migrations, EmbeddedMigrations};

pub const MIGRATIONS: EmbeddedMigrations = embed_migrations!();

#[derive(Clone)]
pub struct EvidenceStore {
    pub(crate) pool: DbPool,
}

pub fn run_migrations(pool: &DbPool) -> Result<(), EvidenceError> {
    mana_storage::run_migrations(pool, MIGRATIONS).map_err(EvidenceError::from)
}

impl EvidenceStore {
    pub fn new(pool: DbPool) -> Self {
        Self { pool }
    }

    // ============================================================
    // EVIDENCE
    // ============================================================

    pub fn create_evidence(&self, input: EvidenceInput) -> Result<Evidence, EvidenceError> {
        let mut conn = self.pool.get().map_err(|e| EvidenceError::Pool(e.to_string()))?;
        let id = common::random_id("ev");
        let created_at = common::now_rfc3339();

        diesel::insert_into(schema::evidence::table)
            .values((
                schema::evidence::id.eq(&id),
                schema::evidence::bed_id.eq(&input.bed_id),
                schema::evidence::resident_id.eq(&input.resident_id),
                schema::evidence::evidence_type.eq(serde_json::to_string(&input.evidence_type).unwrap()),
                schema::evidence::category.eq(serde_json::to_string(&input.category).unwrap()),
                schema::evidence::scene_event_id.eq(&input.scene_event_id),
                schema::evidence::scene_event_json.eq(&input.scene_event_json),
                schema::evidence::rule_id.eq(&input.rule_id),
                schema::evidence::shift.eq(&input.shift),
                schema::evidence::risk_level.eq(&input.risk_level),
                schema::evidence::timestamp.eq(&input.timestamp),
                schema::evidence::created_at.eq(&created_at),
            ))
            .execute(&mut conn)?;

        Ok(Evidence {
            id,
            bed_id: input.bed_id,
            resident_id: input.resident_id,
            evidence_type: input.evidence_type,
            category: input.category,
            scene_event_id: input.scene_event_id,
            scene_event_json: input.scene_event_json,
            rule_id: input.rule_id,
            shift: input.shift,
            risk_level: input.risk_level,
            timestamp: input.timestamp,
            created_at,
        })
    }

    pub fn get_evidence(&self, evidence_id: &str) -> Result<Evidence, EvidenceError> {
        let mut conn = self.pool.get().map_err(|e| EvidenceError::Pool(e.to_string()))?;

        let row = schema::evidence::table
            .find(evidence_id)
            .select((
                schema::evidence::id,
                schema::evidence::bed_id,
                schema::evidence::resident_id,
                schema::evidence::evidence_type,
                schema::evidence::category,
                schema::evidence::scene_event_id,
                schema::evidence::scene_event_json,
                schema::evidence::rule_id,
                schema::evidence::shift,
                schema::evidence::risk_level,
                schema::evidence::timestamp,
                schema::evidence::created_at,
            ))
            .first::<(String, String, Option<String>, String, String, String, String, Option<String>, Option<String>, Option<String>, String, String)>(&mut conn)?;

        Ok(Evidence {
            id: row.0,
            bed_id: row.1,
            resident_id: row.2,
            evidence_type: serde_json::from_str(&row.3).unwrap_or(EvidenceType::Custom),
            category: serde_json::from_str(&row.4).unwrap_or(EventCategory::Off),
            scene_event_id: row.5,
            scene_event_json: row.6,
            rule_id: row.7,
            shift: row.8,
            risk_level: row.9,
            timestamp: row.10,
            created_at: row.11,
        })
    }

    pub fn list_evidence(&self, filter: EvidenceFilter) -> Result<Vec<Evidence>, EvidenceError> {
        let mut conn = self.pool.get().map_err(|e| EvidenceError::Pool(e.to_string()))?;

        let mut query = schema::evidence::table
            .into_boxed()
            .order(schema::evidence::timestamp.desc());

        if let Some(bed_id_filter) = &filter.bed_id {
            query = query.filter(schema::evidence::bed_id.eq(bed_id_filter));
        }
        if let Some(resident_id_filter) = &filter.resident_id {
            query = query.filter(schema::evidence::resident_id.eq(resident_id_filter));
        }
        if let Some(since_filter) = &filter.since {
            query = query.filter(schema::evidence::timestamp.ge(since_filter));
        }
        if let Some(until_filter) = &filter.until {
            query = query.filter(schema::evidence::timestamp.le(until_filter));
        }

        let limit_val = filter.limit.unwrap_or(100);
        let rows = query.limit(limit_val).load::<(String, String, Option<String>, String, String, String, String, Option<String>, Option<String>, Option<String>, String, String)>(&mut conn)?;

        Ok(rows
            .into_iter()
            .map(|row| Evidence {
                id: row.0,
                bed_id: row.1,
                resident_id: row.2,
                evidence_type: serde_json::from_str(&row.3).unwrap_or(EvidenceType::Custom),
                category: serde_json::from_str(&row.4).unwrap_or(EventCategory::Off),
                scene_event_id: row.5,
                scene_event_json: row.6,
                rule_id: row.7,
                shift: row.8,
                risk_level: row.9,
                timestamp: row.10,
                created_at: row.11,
            })
            .collect())
    }

    // ============================================================
    // TIMELINES
    // ============================================================

    pub fn create_timeline(&self, input: TimelineInput) -> Result<Timeline, EvidenceError> {
        let mut conn = self.pool.get().map_err(|e| EvidenceError::Pool(e.to_string()))?;
        let id = common::random_id("tl");
        let created_at = common::now_rfc3339();

        let before_json = serde_json::to_string(&input.before_events).unwrap();

        diesel::insert_into(schema::timelines::table)
            .values((
                schema::timelines::id.eq(&id),
                schema::timelines::bed_id.eq(&input.bed_id),
                schema::timelines::resident_id.eq(&input.resident_id),
                schema::timelines::anchor_event_id.eq(&input.anchor_event_id),
                schema::timelines::anchor_event_json.eq(&input.anchor_event_json),
                schema::timelines::before_events_json.eq(&before_json),
                schema::timelines::after_events_json.eq("[]"),
                schema::timelines::window_start.eq(&input.window_start),
                schema::timelines::window_end.eq(&input.window_end),
                schema::timelines::created_at.eq(&created_at),
                schema::timelines::closed_at.eq(Option::<String>::None),
            ))
            .execute(&mut conn)?;

        Ok(Timeline {
            id,
            bed_id: input.bed_id,
            resident_id: input.resident_id,
            anchor_event_id: input.anchor_event_id,
            anchor_event_json: input.anchor_event_json,
            before_events: input.before_events,
            after_events: vec![],
            window_start: input.window_start,
            window_end: input.window_end,
            created_at,
            closed_at: None,
        })
    }

    pub fn close_timeline(
        &self,
        timeline_id: &str,
        input: TimelineCloseInput,
    ) -> Result<Timeline, EvidenceError> {
        let mut conn = self.pool.get().map_err(|e| EvidenceError::Pool(e.to_string()))?;
        let closed_at = common::now_rfc3339();
        let after_json = serde_json::to_string(&input.after_events).unwrap();

        diesel::update(schema::timelines::table.find(timeline_id))
            .set((
                schema::timelines::after_events_json.eq(&after_json),
                schema::timelines::closed_at.eq(&closed_at),
            ))
            .execute(&mut conn)?;

        self.get_timeline(timeline_id)
    }

    pub fn get_timeline(&self, timeline_id: &str) -> Result<Timeline, EvidenceError> {
        let mut conn = self.pool.get().map_err(|e| EvidenceError::Pool(e.to_string()))?;

        let row = schema::timelines::table
            .find(timeline_id)
            .select((
                schema::timelines::id,
                schema::timelines::bed_id,
                schema::timelines::resident_id,
                schema::timelines::anchor_event_id,
                schema::timelines::anchor_event_json,
                schema::timelines::before_events_json,
                schema::timelines::after_events_json,
                schema::timelines::window_start,
                schema::timelines::window_end,
                schema::timelines::created_at,
                schema::timelines::closed_at,
            ))
            .first::<(String, String, Option<String>, String, String, String, String, String, String, String, Option<String>)>(&mut conn)?;

        Ok(Timeline {
            id: row.0,
            bed_id: row.1,
            resident_id: row.2,
            anchor_event_id: row.3,
            anchor_event_json: row.4,
            before_events: serde_json::from_str(&row.5).unwrap_or_default(),
            after_events: serde_json::from_str(&row.6).unwrap_or_default(),
            window_start: row.7,
            window_end: row.8,
            created_at: row.9,
            closed_at: row.10,
        })
    }

    // ============================================================
    // CLIP WINDOWS
    // ============================================================

    pub fn create_clip_window(&self, input: ClipWindowInput) -> Result<ClipWindow, EvidenceError> {
        let mut conn = self.pool.get().map_err(|e| EvidenceError::Pool(e.to_string()))?;
        let window_id = common::random_id("cw");
        let created_at = common::now_rfc3339();

        let condition_json = serde_json::to_string(&input.close_condition).unwrap();

        diesel::insert_into(schema::clip_windows::table)
            .values((
                schema::clip_windows::window_id.eq(&window_id),
                schema::clip_windows::bed_id.eq(&input.bed_id),
                schema::clip_windows::resident_id.eq(&input.resident_id),
                schema::clip_windows::started_at.eq(&input.started_at),
                schema::clip_windows::ended_at.eq(Option::<String>::None),
                schema::clip_windows::timeout_minutes.eq(input.timeout_minutes),
                schema::clip_windows::events_json.eq("[]"),
                schema::clip_windows::state.eq("open"),
                schema::clip_windows::close_condition_json.eq(&condition_json),
                schema::clip_windows::created_at.eq(&created_at),
                schema::clip_windows::closed_at.eq(Option::<String>::None),
            ))
            .execute(&mut conn)?;

        Ok(ClipWindow {
            window_id,
            bed_id: input.bed_id,
            resident_id: input.resident_id,
            started_at: input.started_at,
            ended_at: None,
            timeout_minutes: input.timeout_minutes,
            events: vec![],
            state: WindowState::Open,
            close_condition: input.close_condition,
            created_at,
            closed_at: None,
        })
    }

    pub fn close_clip_window(
        &self,
        window_id: &str,
        input: ClipWindowCloseInput,
    ) -> Result<ClipWindow, EvidenceError> {
        let mut conn = self.pool.get().map_err(|e| EvidenceError::Pool(e.to_string()))?;

        let events_json = serde_json::to_string(&input.events).unwrap();
        let state_str = serde_json::to_string(&input.state).unwrap();

        diesel::update(schema::clip_windows::table.find(window_id))
            .set((
                schema::clip_windows::ended_at.eq(&input.ended_at),
                schema::clip_windows::events_json.eq(&events_json),
                schema::clip_windows::state.eq(&state_str),
                schema::clip_windows::closed_at.eq(&input.ended_at),
            ))
            .execute(&mut conn)?;

        self.get_clip_window(window_id)
    }

    pub fn get_clip_window(&self, window_id: &str) -> Result<ClipWindow, EvidenceError> {
        let mut conn = self.pool.get().map_err(|e| EvidenceError::Pool(e.to_string()))?;

        let row = schema::clip_windows::table
            .find(window_id)
            .select((
                schema::clip_windows::window_id,
                schema::clip_windows::bed_id,
                schema::clip_windows::resident_id,
                schema::clip_windows::started_at,
                schema::clip_windows::ended_at,
                schema::clip_windows::timeout_minutes,
                schema::clip_windows::events_json,
                schema::clip_windows::state,
                schema::clip_windows::close_condition_json,
                schema::clip_windows::created_at,
                schema::clip_windows::closed_at,
            ))
            .first::<(String, String, Option<String>, String, Option<String>, i32, String, String, String, String, Option<String>)>(&mut conn)?;

        Ok(ClipWindow {
            window_id: row.0,
            bed_id: row.1,
            resident_id: row.2,
            started_at: row.3,
            ended_at: row.4,
            timeout_minutes: row.5,
            events: serde_json::from_str(&row.6).unwrap_or_default(),
            state: serde_json::from_str(&row.7).unwrap_or(WindowState::Open),
            close_condition: serde_json::from_str(&row.8).unwrap_or(CloseCondition::Timeout { minutes: 30 }),
            created_at: row.9,
            closed_at: row.10,
        })
    }

    pub fn list_open_clip_windows(&self, bed_id_filter: &str) -> Result<Vec<ClipWindow>, EvidenceError> {
        let mut conn = self.pool.get().map_err(|e| EvidenceError::Pool(e.to_string()))?;

        let rows = schema::clip_windows::table
            .filter(schema::clip_windows::bed_id.eq(bed_id_filter))
            .filter(schema::clip_windows::state.eq("open"))
            .order(schema::clip_windows::started_at.desc())
            .select((
                schema::clip_windows::window_id,
                schema::clip_windows::bed_id,
                schema::clip_windows::resident_id,
                schema::clip_windows::started_at,
                schema::clip_windows::ended_at,
                schema::clip_windows::timeout_minutes,
                schema::clip_windows::events_json,
                schema::clip_windows::state,
                schema::clip_windows::close_condition_json,
                schema::clip_windows::created_at,
                schema::clip_windows::closed_at,
            ))
            .load::<(String, String, Option<String>, String, Option<String>, i32, String, String, String, String, Option<String>)>(&mut conn)?;

        Ok(rows
            .into_iter()
            .map(|row| ClipWindow {
                window_id: row.0,
                bed_id: row.1,
                resident_id: row.2,
                started_at: row.3,
                ended_at: row.4,
                timeout_minutes: row.5,
                events: serde_json::from_str(&row.6).unwrap_or_default(),
                state: serde_json::from_str(&row.7).unwrap_or(WindowState::Open),
                close_condition: serde_json::from_str(&row.8).unwrap_or(CloseCondition::Timeout { minutes: 30 }),
                created_at: row.9,
                closed_at: row.10,
            })
            .collect())
    }
}
