diesel::table! {
    facilities (id) {
        id -> Text,
        name -> Text,
        timezone -> Text,
        retired_at -> Nullable<Text>,
        retired_by -> Nullable<Text>,
        created_at -> Text,
        updated_at -> Text,
    }
}

diesel::table! {
    wings (id) {
        id -> Text,
        facility_id -> Text,
        name -> Text,
        floor -> Text,
        sort_order -> Integer,
        retired_at -> Nullable<Text>,
        retired_by -> Nullable<Text>,
        created_at -> Text,
        updated_at -> Text,
    }
}

diesel::table! {
    rooms (id) {
        id -> Text,
        wing_id -> Text,
        number -> Text,
        room_type -> Text,
        stream_key -> Nullable<Text>,
        retired_at -> Nullable<Text>,
        retired_by -> Nullable<Text>,
        created_at -> Text,
        updated_at -> Text,
    }
}

diesel::table! {
    beds (id) {
        id -> Text,
        room_id -> Text,
        label -> Text,
        monitor_key -> Nullable<Text>,
        retired_at -> Nullable<Text>,
        retired_by -> Nullable<Text>,
        created_at -> Text,
        updated_at -> Text,
    }
}

diesel::table! {
    planogram_placements (id) {
        id -> Text,
        wing_id -> Text,
        room_id -> Text,
        x -> Double,
        y -> Double,
        sort_order -> Integer,
        active -> Integer,
        created_at -> Text,
        updated_at -> Text,
    }
}

diesel::table! {
    room_privacy_regions (id) {
        id -> Text,
        room_id -> Text,
        x -> Double,
        y -> Double,
        w -> Double,
        h -> Double,
        active -> Integer,
        created_at -> Text,
        updated_at -> Text,
    }
}

diesel::joinable!(wings -> facilities (facility_id));
diesel::joinable!(rooms -> wings (wing_id));
diesel::joinable!(beds -> rooms (room_id));
diesel::joinable!(planogram_placements -> wings (wing_id));
diesel::joinable!(planogram_placements -> rooms (room_id));
diesel::joinable!(room_privacy_regions -> rooms (room_id));

diesel::allow_tables_to_appear_in_same_query!(
    facilities,
    wings,
    rooms,
    beds,
    planogram_placements,
    room_privacy_regions,
);
