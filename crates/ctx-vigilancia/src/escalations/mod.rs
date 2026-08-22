pub mod repo;
pub mod sqlite;

#[derive(Debug, Clone)]
pub struct AlertEscalation {
    pub id: String,
    pub alert_id: String,
    pub level: i32,
    pub target_id: String,
    pub occurred_at: mana_kernel::Instante,
    pub created_at: mana_kernel::Instante,
}

#[allow(dead_code)]
pub fn new_escalation_id() -> String {
    use base64::Engine;
    let bytes: [u8; 16] = rand::random();
    base64::engine::general_purpose::URL_SAFE_NO_PAD.encode(bytes)
}
