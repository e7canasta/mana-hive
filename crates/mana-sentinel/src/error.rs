use thiserror::Error;

#[derive(Debug, Error)]
pub enum SentinelError {
    #[error("HTTP client error: {0}")]
    Http(#[from] reqwest::Error),
    
    #[error("JSON error: {0}")]
    Json(#[from] serde_json::Error),
    
    #[error("Hub API error: {0}")]
    HubApi(String),
    
    #[error("Engine API error: {0}")]
    EngineApi(String),
    
    #[error("Checkpoint error: {0}")]
    Checkpoint(String),
    
    #[error("Clip window error: {0}")]
    ClipWindow(String),
    
    #[error("Dispatch error: {0}")]
    Dispatch(String),
    
    #[error("Internal error: {0}")]
    Internal(String),
}
