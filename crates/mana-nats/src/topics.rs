/// NATS topic constants
pub mod topics {
    /// Perception events from IA-Edge
    pub const EVT_PERCEPTION: &str = "evt_perception";

    /// Scene events from Engine
    pub const EVT_SCENE: &str = "evt_scene";

    /// Notification events from Sentinel
    pub const EVT_NOTIF: &str = "evt_notif";

    /// Policy changes from Hub
    pub const EVT_POLICY: &str = "evt_policy";
}

/// Durable consumer names
pub mod consumers {
    /// Hub persists perception events
    pub const HUB_PERCEPTION: &str = "hub-persistence";

    /// Hub persists scene events
    pub const HUB_SCENE: &str = "hub-scene";

    /// Hub persists notification events
    pub const HUB_NOTIF: &str = "hub-notif";

    /// Engine processes perception events
    pub const ENGINE_PERCEPTION: &str = "engine-processor";

    /// Engine processes policy changes
    pub const ENGINE_POLICY: &str = "engine-policy";

    /// Sentinel evaluates scene events
    pub const SENTINEL_SCENE: &str = "sentinel-evaluator";

    /// Sentinel processes policy changes
    pub const SENTINEL_POLICY: &str = "sentinel-policy";

    /// Vigilancia handles notification events
    pub const VIGILANCIA_NOTIF: &str = "vigilancia-handler";
}
