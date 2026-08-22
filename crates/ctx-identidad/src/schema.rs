diesel::table! {
    auth_sessions (token_hash) {
        token_hash -> Binary,
        user_id -> Text,
        expires_at -> Text,
        created_at -> Text,
        last_seen_at -> Nullable<Text>,
    }
}

diesel::table! {
    users (id) {
        id -> Text,
        username -> Text,
        display_name -> Text,
        role -> Text,
        job_title -> Nullable<Text>,
        password_hash -> Text,
        retired_at -> Nullable<Text>,
        retired_by -> Nullable<Text>,
        created_at -> Text,
        updated_at -> Text,
    }
}

diesel::joinable!(auth_sessions -> users (user_id));
diesel::allow_tables_to_appear_in_same_query!(auth_sessions, users);
