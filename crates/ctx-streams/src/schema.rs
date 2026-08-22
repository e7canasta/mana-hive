diesel::table! {
    streams (id) {
        id -> Text,
        room_id -> Text,
        stream_key -> Text,
        name -> Nullable<Text>,
        active -> Integer,
        created_at -> Text,
        updated_at -> Text,
    }
}

diesel::table! {
    stream_regions (id) {
        id -> Text,
        stream_id -> Text,
        region_type -> Text,
        points -> Text,
        label -> Nullable<Text>,
        is_static -> Integer,
        updated_by -> Nullable<Text>,
        created_at -> Text,
        updated_at -> Text,
    }
}

diesel::joinable!(stream_regions -> streams (stream_id));

diesel::allow_tables_to_appear_in_same_query!(streams, stream_regions,);
