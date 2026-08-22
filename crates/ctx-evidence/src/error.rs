use std::fmt;

#[derive(Debug)]
pub enum EvidenceError {
    Diesel(diesel::result::Error),
    Pool(String),
    Storage(String),
    NotFound,
    InvalidInput(String),
}

impl fmt::Display for EvidenceError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            EvidenceError::Diesel(e) => write!(f, "database error: {}", e),
            EvidenceError::Pool(e) => write!(f, "connection pool error: {}", e),
            EvidenceError::Storage(e) => write!(f, "storage error: {}", e),
            EvidenceError::NotFound => write!(f, "not found"),
            EvidenceError::InvalidInput(e) => write!(f, "invalid input: {}", e),
        }
    }
}

impl std::error::Error for EvidenceError {}

impl From<diesel::result::Error> for EvidenceError {
    fn from(e: diesel::result::Error) -> Self {
        EvidenceError::Diesel(e)
    }
}

impl From<String> for EvidenceError {
    fn from(e: String) -> Self {
        EvidenceError::Pool(e)
    }
}

impl From<mana_storage::StorageError> for EvidenceError {
    fn from(e: mana_storage::StorageError) -> Self {
        EvidenceError::Storage(e.to_string())
    }
}
