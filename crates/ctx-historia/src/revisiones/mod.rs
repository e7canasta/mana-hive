pub mod repo;
pub mod sqlite;

use mana_kernel::{define_kinds, Actor, Id, Instante};
use thiserror::Error;

define_kinds!(ReviewKind);

pub type ReviewId = Id<ReviewKind>;

#[derive(Clone, Debug, Eq, PartialEq, Error)]
pub enum RevisionesError {
    #[error("dato persistido invalido: {0}")]
    InvalidStoredData(String),
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum ReviewStatus {
    Open,
    UnderReview,
    Closed,
}

impl ReviewStatus {
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::Open => "open",
            Self::UnderReview => "under_review",
            Self::Closed => "closed",
        }
    }

    pub fn parse(value: &str) -> Result<Self, RevisionesError> {
        match value {
            "open" => Ok(Self::Open),
            "under_review" => Ok(Self::UnderReview),
            "closed" => Ok(Self::Closed),
            other => Err(RevisionesError::InvalidStoredData(format!(
                "invalid review status: {other}"
            ))),
        }
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum DetectionVerdict {
    Fall,
    NotAFall,
    Uncertain,
    SafeToGround,
}

impl DetectionVerdict {
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::Fall => "fall",
            Self::NotAFall => "not_a_fall",
            Self::Uncertain => "uncertain",
            Self::SafeToGround => "safe_to_ground",
        }
    }

    pub fn parse(value: &str) -> Result<Self, RevisionesError> {
        match value {
            "fall" => Ok(Self::Fall),
            "not_a_fall" => Ok(Self::NotAFall),
            "uncertain" => Ok(Self::Uncertain),
            "safe_to_ground" => Ok(Self::SafeToGround),
            other => Err(RevisionesError::InvalidStoredData(format!(
                "invalid detection verdict: {other}"
            ))),
        }
    }
}

#[derive(Clone, Debug)]
pub struct IncidentReview {
    pub id: ReviewId,
    pub incident_id: String,
    pub status: ReviewStatus,
    pub detection_verdict: Option<DetectionVerdict>,
    pub review_note: Option<String>,
    pub resolved_at: Option<Instante>,
    pub actor_id: Id<Actor>,
    pub created_at: Instante,
}

#[derive(Clone, Debug)]
pub struct ReviewInput {
    pub status: ReviewStatus,
    pub detection_verdict: Option<DetectionVerdict>,
    pub review_note: Option<String>,
    pub resolved_at: Option<Instante>,
}

pub fn new_review_id() -> ReviewId {
    Id::new(crate::common::random_id("rev"))
}
