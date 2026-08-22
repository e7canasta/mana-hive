use thiserror::Error;

#[derive(Debug, Error)]
pub enum NatsError {
    #[error("NATS connection failed: {0}")]
    ConnectionFailed(String),

    #[error("NATS publish failed: {0}")]
    PublishFailed(String),

    #[error("NATS subscribe failed: {0}")]
    SubscribeFailed(String),

    #[error("NATS message serialization failed: {0}")]
    SerializationFailed(String),

    #[error("NATS message deserialization failed: {0}")]
    DeserializationFailed(String),

    #[error("NATS connection closed")]
    ConnectionClosed,
}
