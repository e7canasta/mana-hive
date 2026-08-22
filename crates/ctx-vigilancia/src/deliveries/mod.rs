pub mod repo;
pub mod sqlite;

use crate::error::VigilanciaError;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum DeliveriesError {
    InvalidRecipientKind,
    InvalidChannel,
    InvalidEventKind,
}

impl std::fmt::Display for DeliveriesError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::InvalidRecipientKind => write!(f, "tipo de destinatario invalido"),
            Self::InvalidChannel => write!(f, "canal de notificacion invalido"),
            Self::InvalidEventKind => write!(f, "tipo de evento de entrega invalido"),
        }
    }
}

impl From<DeliveriesError> for VigilanciaError {
    fn from(error: DeliveriesError) -> Self {
        VigilanciaError::validation(error.to_string())
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum RecipientKind {
    User,
    StaffGroup,
    Service,
}

impl RecipientKind {
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::User => "user",
            Self::StaffGroup => "staff_group",
            Self::Service => "service",
        }
    }

    pub fn parse(value: &str) -> Result<Self, DeliveriesError> {
        match value {
            "user" => Ok(Self::User),
            "staff_group" => Ok(Self::StaffGroup),
            "service" => Ok(Self::Service),
            _ => Err(DeliveriesError::InvalidRecipientKind),
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Channel {
    Push,
    Tablet,
    Sms,
    Other,
}

impl Channel {
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::Push => "push",
            Self::Tablet => "tablet",
            Self::Sms => "sms",
            Self::Other => "other",
        }
    }

    pub fn parse(value: &str) -> Result<Self, DeliveriesError> {
        match value {
            "push" => Ok(Self::Push),
            "tablet" => Ok(Self::Tablet),
            "sms" => Ok(Self::Sms),
            "other" => Ok(Self::Other),
            _ => Err(DeliveriesError::InvalidChannel),
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum DeliveryEventKind {
    Sent,
    Acknowledged,
    Failed,
}

impl DeliveryEventKind {
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::Sent => "sent",
            Self::Acknowledged => "acknowledged",
            Self::Failed => "failed",
        }
    }

    pub fn parse(value: &str) -> Result<Self, DeliveriesError> {
        match value {
            "sent" => Ok(Self::Sent),
            "acknowledged" => Ok(Self::Acknowledged),
            "failed" => Ok(Self::Failed),
            _ => Err(DeliveriesError::InvalidEventKind),
        }
    }
}

#[derive(Debug, Clone)]
pub struct DeliveryInput {
    pub recipient_kind: RecipientKind,
    pub recipient_id: String,
    pub channel: Channel,
    pub escalation_level: i32,
}

#[derive(Debug, Clone)]
pub struct NotificationDelivery {
    pub id: String,
    pub alert_id: String,
    pub recipient_kind: RecipientKind,
    pub recipient_id: String,
    pub channel: Channel,
    pub escalation_level: i32,
    pub created_at: mana_kernel::Instante,
}

#[derive(Debug, Clone)]
pub struct DeliveryEventInput {
    pub kind: DeliveryEventKind,
    pub reason: Option<String>,
    pub occurred_at: mana_kernel::Instante,
}

#[derive(Debug, Clone)]
pub struct NotificationDeliveryEvent {
    pub id: String,
    pub delivery_id: String,
    pub kind: DeliveryEventKind,
    pub reason: Option<String>,
    pub occurred_at: mana_kernel::Instante,
}

#[derive(Debug, Clone)]
pub struct DeliverySummary {
    pub sent: i64,
    pub acked: i64,
    pub failed: i64,
}

#[derive(Debug, Clone)]
pub struct DeliveryWithEvents {
    pub delivery: NotificationDelivery,
    pub events: Vec<NotificationDeliveryEvent>,
    pub sent_at: Option<mana_kernel::Instante>,
    pub acked_at: Option<mana_kernel::Instante>,
    pub failed_reason: Option<String>,
}

pub fn new_delivery_id() -> String {
    use base64::Engine;
    let bytes: [u8; 16] = rand::random();
    base64::engine::general_purpose::URL_SAFE_NO_PAD.encode(bytes)
}
