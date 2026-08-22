// @generated automatically by Diesel CLI.
// This file is manually maintained.

diesel::table! {
    evidence (id) {
        id -> Text,
        bed_id -> Text,
        resident_id -> Nullable<Text>,
        evidence_type -> Text,
        category -> Text,
        scene_event_id -> Text,
        scene_event_json -> Text,
        rule_id -> Nullable<Text>,
        shift -> Nullable<Text>,
        risk_level -> Nullable<Text>,
        timestamp -> Text,
        created_at -> Text,
    }
}

diesel::table! {
    timelines (id) {
        id -> Text,
        bed_id -> Text,
        resident_id -> Nullable<Text>,
        anchor_event_id -> Text,
        anchor_event_json -> Text,
        before_events_json -> Text,
        after_events_json -> Text,
        window_start -> Text,
        window_end -> Text,
        created_at -> Text,
        closed_at -> Nullable<Text>,
    }
}

diesel::table! {
    clip_windows (window_id) {
        window_id -> Text,
        bed_id -> Text,
        resident_id -> Nullable<Text>,
        started_at -> Text,
        ended_at -> Nullable<Text>,
        timeout_minutes -> Integer,
        events_json -> Text,
        state -> Text,
        close_condition_json -> Text,
        created_at -> Text,
        closed_at -> Nullable<Text>,
    }
}
