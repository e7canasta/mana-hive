diesel::table! {
    audit_log (id) {
        id -> Text,
        actor_id -> Nullable<Text>,
        action -> Text,
        entity_type -> Text,
        entity_id -> Text,
        metadata_json -> Text,
        created_at -> Text,
    }
}
