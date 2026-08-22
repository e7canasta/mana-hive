diesel::table! {
    alarm_profile_versions (id) {
        id -> Text,
        resident_id -> Text,
        valid_from -> Text,
        valid_to -> Nullable<Text>,
        mobility_aid -> Text,
        autopilot -> Integer,
        mode -> Text,
        template_id -> Text,
        overrides_json -> Text,
        catalog_version -> Text,
        updated_by -> Nullable<Text>,
        created_at -> Text,
        risk_level -> Text,
    }
}
