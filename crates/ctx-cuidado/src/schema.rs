diesel::table! {
    rounds (id) {
        id -> Text,
        wing_id -> Text,
        status -> Text,
        scheduled_for -> Nullable<Text>,
        started_at -> Text,
        completed_at -> Nullable<Text>,
        started_by -> Text,
        completed_by -> Nullable<Text>,
        created_at -> Text,
        updated_at -> Text,
    }
}

diesel::table! {
    round_tasks (id) {
        id -> Text,
        round_id -> Text,
        resident_id -> Text,
        bed_id -> Text,
        status -> Text,
        note -> Nullable<Text>,
        completed_at -> Nullable<Text>,
        completed_by -> Nullable<Text>,
        created_at -> Text,
        updated_at -> Text,
    }
}

diesel::table! {
    care_notes (id) {
        id -> Text,
        resident_id -> Text,
        author_id -> Text,
        kind -> Text,
        body -> Text,
        duration_min -> Nullable<Integer>,
        created_at -> Text,
        updated_at -> Text,
    }
}

diesel::joinable!(round_tasks -> rounds (round_id));

diesel::allow_tables_to_appear_in_same_query!(rounds, round_tasks, care_notes,);
