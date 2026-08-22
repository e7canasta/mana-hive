diesel::table! {
    sensor_events (id) {
        id -> Text,
        source_event_id -> Text,
        monitor_key -> Text,
        bed_id -> Nullable<Text>,
        resident_id -> Nullable<Text>,
        kind -> Text,
        room_state -> Nullable<Text>,
        substate -> Nullable<Text>,
        zone -> Nullable<Text>,
        state -> Nullable<Text>,
        sleeping -> Nullable<Integer>,
        occurred_at -> Text,
        received_at -> Text,
        payload_json -> Text,
    }
}

diesel::table! {
    current_bed_states (bed_id) {
        bed_id -> Text,
        resident_id -> Nullable<Text>,
        room_state -> Nullable<Text>,
        state -> Text,
        substate -> Nullable<Text>,
        sleeping -> Nullable<Integer>,
        state_since -> Nullable<Text>,
        updated_at -> Text,
        source -> Text,
        source_event_id -> Nullable<Text>,
    }
}

diesel::allow_tables_to_appear_in_same_query!(sensor_events, current_bed_states);

diesel::table! {
    scene_events (id) {
        id -> Text,
        event_id -> Text,
        bed_id -> Text,
        resident_id -> Nullable<Text>,
        event_type -> Text,
        from_state -> Nullable<Text>,
        to_state -> Nullable<Text>,
        trigger_type -> Nullable<Text>,
        timestamp -> Text,
        payload_json -> Text,
        received_at -> Text,
    }
}

diesel::table! {
    notification_events (id) {
        id -> Text,
        category -> Text,
        bed_id -> Text,
        resident_id -> Nullable<Text>,
        event_type -> Text,
        timestamp -> Text,
        rule_id -> Nullable<Text>,
        risk_level -> Nullable<Text>,
        payload_json -> Text,
        received_at -> Text,
    }
}

diesel::table! {
    sleep_summaries (id) {
        id -> Text,
        source_record_id -> Text,
        resident_id -> Text,
        observed_on -> Text,
        calm_minutes -> Integer,
        restless_minutes -> Integer,
        awake_minutes -> Integer,
        out_of_bed_minutes -> Integer,
        bed_exit_count -> Integer,
        wake_count -> Integer,
        source -> Text,
        model_version -> Text,
        confidence -> Nullable<Double>,
        provenance_json -> Text,
        created_at -> Text,
        updated_at -> Text,
    }
}

diesel::table! {
    mobility_summaries (id) {
        id -> Text,
        source_record_id -> Text,
        resident_id -> Text,
        observed_on -> Text,
        in_bed_minutes -> Integer,
        out_of_bed_minutes -> Integer,
        out_of_sight_minutes -> Integer,
        walking_minutes -> Integer,
        distance_meters -> Nullable<Double>,
        transfer_count -> Integer,
        source -> Text,
        model_version -> Text,
        confidence -> Nullable<Double>,
        provenance_json -> Text,
        created_at -> Text,
        updated_at -> Text,
    }
}

diesel::table! {
    bathroom_summaries (id) {
        id -> Text,
        source_record_id -> Text,
        resident_id -> Text,
        observed_on -> Text,
        visit_count -> Integer,
        night_visit_count -> Integer,
        assisted_count -> Integer,
        total_minutes -> Integer,
        longest_visit_minutes -> Integer,
        source -> Text,
        model_version -> Text,
        confidence -> Nullable<Double>,
        provenance_json -> Text,
        created_at -> Text,
        updated_at -> Text,
    }
}
