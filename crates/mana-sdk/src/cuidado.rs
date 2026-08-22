use reqwest::Method;
use serde::{Deserialize, Serialize};

use crate::poblacion::path;
use crate::transport::{ApiResponse, ManaClient, ManaError};

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct Round {
    pub id: String,
    pub wing_id: String,
    pub status: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub scheduled_for: Option<String>,
    pub started_at: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub completed_at: Option<String>,
    pub started_by: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub completed_by: Option<String>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct RoundTask {
    pub id: String,
    pub round_id: String,
    pub resident_id: String,
    pub bed_id: String,
    pub status: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub note: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub completed_at: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub completed_by: Option<String>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct CareNote {
    pub id: String,
    pub resident_id: String,
    pub author_id: String,
    pub kind: String,
    pub body: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub duration_min: Option<i32>,
    pub created_at: String,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct RoundResponse {
    pub round: Option<Round>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct RoundsResponse {
    pub rounds: Vec<Round>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct TaskResponse {
    pub task: RoundTask,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct NotesResponse {
    pub notes: Vec<CareNote>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct NoteResponse {
    pub note: CareNote,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct CreateRoundRequest {
    pub wing_id: String,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct UpdateRoundRequest {
    pub status: String,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct UpdateTaskRequest {
    #[serde(skip_serializing_if = "Option::is_none")]
    pub status: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub note: Option<Option<String>>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct CreateNoteRequest {
    pub body: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub kind: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub duration_min: Option<i32>,
}

impl ManaClient {
    // -- Rounds --

    pub async fn current_round(
        &self,
        wing_id: &str,
    ) -> Result<ApiResponse<RoundResponse>, ManaError> {
        self.request(
            Method::GET,
            &format!(
                "/api/v1/rounds/current?wing_id={}",
                url::form_urlencoded::byte_serialize(wing_id.as_bytes()).collect::<String>()
            ),
        )
        .await
    }

    pub async fn list_rounds(
        &self,
        wing_id: &str,
        limit: Option<i64>,
    ) -> Result<ApiResponse<RoundsResponse>, ManaError> {
        let limit_str = limit.map(|l| l.to_string());
        let mut params = vec![("wing_id", wing_id)];
        if let Some(ref l) = limit_str {
            params.push(("limit", l));
        }
        let query: String = params
            .iter()
            .map(|(k, v)| {
                format!(
                    "{}={}",
                    k,
                    url::form_urlencoded::byte_serialize(v.as_bytes()).collect::<String>()
                )
            })
            .collect::<Vec<_>>()
            .join("&");
        self.request(Method::GET, &format!("/api/v1/rounds?{query}"))
            .await
    }

    pub async fn create_round(
        &self,
        request: CreateRoundRequest,
    ) -> Result<ApiResponse<RoundResponse>, ManaError> {
        self.request_json(Method::POST, "/api/v1/rounds", request)
            .await
    }

    pub async fn complete_round(
        &self,
        round_id: &str,
    ) -> Result<ApiResponse<RoundResponse>, ManaError> {
        self.request_json(
            Method::PATCH,
            &path("/api/v1/rounds", round_id)?,
            UpdateRoundRequest {
                status: "completed".to_owned(),
            },
        )
        .await
    }

    pub async fn cancel_round(
        &self,
        round_id: &str,
    ) -> Result<ApiResponse<RoundResponse>, ManaError> {
        self.request_json(
            Method::PATCH,
            &path("/api/v1/rounds", round_id)?,
            UpdateRoundRequest {
                status: "cancelled".to_owned(),
            },
        )
        .await
    }

    // -- Tasks --

    pub async fn update_task(
        &self,
        task_id: &str,
        request: UpdateTaskRequest,
    ) -> Result<ApiResponse<TaskResponse>, ManaError> {
        self.request_json(
            Method::PATCH,
            &path("/api/v1/round-tasks", task_id)?,
            request,
        )
        .await
    }

    // -- Notes --

    pub async fn list_notes(
        &self,
        resident_id: &str,
        limit: Option<i64>,
    ) -> Result<ApiResponse<NotesResponse>, ManaError> {
        let limit_str = limit.map(|l| l.to_string());
        let base = path("/api/v1/residents", resident_id)? + "/notes";
        let url = match limit_str {
            Some(ref l) => format!("{}?limit={}", base, l),
            None => base,
        };
        self.request(Method::GET, &url).await
    }

    pub async fn create_note(
        &self,
        resident_id: &str,
        request: CreateNoteRequest,
    ) -> Result<ApiResponse<NoteResponse>, ManaError> {
        self.request_json(
            Method::POST,
            &(path("/api/v1/residents", resident_id)? + "/notes"),
            request,
        )
        .await
    }
}
