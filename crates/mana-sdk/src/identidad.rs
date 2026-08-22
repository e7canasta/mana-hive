use serde::{Deserialize, Serialize};
use serde_json::Value;

use crate::transport::{ApiResponse, ManaClient, ManaError};

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct LoginRequest {
    pub username: String,
    pub password: String,
}

#[derive(Clone, Debug, Default, Deserialize, Serialize)]
pub struct AuthUser {
    #[serde(default)]
    pub id: String,
    #[serde(default)]
    pub username: String,
    #[serde(default)]
    pub display_name: String,
    #[serde(default)]
    pub role: String,
    #[serde(default)]
    pub features: Vec<String>,
    #[serde(default)]
    pub permissions: Vec<String>,
    #[serde(default)]
    pub capabilities: Vec<String>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct LoginResponse {
    pub token: String,
    #[serde(default)]
    pub expires_at: String,
    pub user: AuthUser,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct CurrentUserResponse {
    pub user: AuthUser,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct AdminUser {
    #[serde(default)]
    pub id: String,
    #[serde(default)]
    pub username: String,
    #[serde(default)]
    pub display_name: String,
    #[serde(default)]
    pub role: String,
    #[serde(default)]
    pub job_title: Option<String>,
    #[serde(default)]
    pub active: i32,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct UsersResponse {
    #[serde(default)]
    pub users: Vec<AdminUser>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct UserResponse {
    pub user: AdminUser,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct CreateUserRequest {
    pub username: String,
    pub display_name: String,
    pub role: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub job_title: Option<String>,
    pub password: String,
}

#[derive(Clone, Debug, Default, Deserialize, Serialize)]
pub struct UpdateUserRequest {
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub display_name: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub role: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub job_title: Option<Option<String>>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub active: Option<bool>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub password: Option<String>,
}

impl ManaClient {
    pub async fn login(
        &mut self,
        username: impl AsRef<str>,
        password: impl AsRef<str>,
    ) -> Result<ApiResponse<LoginResponse>, ManaError> {
        self.login_request(LoginRequest {
            username: username.as_ref().to_owned(),
            password: password.as_ref().to_owned(),
        })
        .await
    }

    pub async fn login_with_credentials(
        &mut self,
        username: impl AsRef<str>,
        password: impl AsRef<str>,
    ) -> Result<ApiResponse<LoginResponse>, ManaError> {
        self.login(username, password).await
    }

    pub async fn login_request(
        &mut self,
        request: LoginRequest,
    ) -> Result<ApiResponse<LoginResponse>, ManaError> {
        let response: ApiResponse<LoginResponse> = self
            .request_json(reqwest::Method::POST, "/api/v1/auth/login", &request)
            .await?;
        let token = response
            .data
            .as_ref()
            .ok_or(ManaError::MissingResponseBody {
                status: response.status,
            })?
            .token
            .clone();
        self.set_token(token)?;
        Ok(response)
    }

    pub async fn current_user(&self) -> Result<ApiResponse<CurrentUserResponse>, ManaError> {
        self.request(reqwest::Method::GET, "/api/v1/auth/me").await
    }

    pub async fn me(&self) -> Result<ApiResponse<CurrentUserResponse>, ManaError> {
        self.current_user().await
    }

    pub async fn logout(&mut self) -> Result<ApiResponse<()>, ManaError> {
        let response: ApiResponse<Value> = self
            .request(reqwest::Method::POST, "/api/v1/auth/logout")
            .await?;
        self.clear_bearer_token();
        Ok(response.map(|_| ()))
    }

    pub async fn list_users(&self) -> Result<ApiResponse<UsersResponse>, ManaError> {
        self.list_users_with_inactive(false).await
    }

    pub async fn list_users_with_inactive(
        &self,
        include_inactive: bool,
    ) -> Result<ApiResponse<UsersResponse>, ManaError> {
        let path = if include_inactive {
            "/api/v1/users?include_inactive=1"
        } else {
            "/api/v1/users"
        };
        self.request(reqwest::Method::GET, path).await
    }

    pub async fn create(
        &self,
        request: CreateUserRequest,
    ) -> Result<ApiResponse<UserResponse>, ManaError> {
        self.request_json(reqwest::Method::POST, "/api/v1/users", &request)
            .await
    }

    pub async fn create_user(
        &self,
        request: CreateUserRequest,
    ) -> Result<ApiResponse<UserResponse>, ManaError> {
        self.create(request).await
    }

    pub async fn update(
        &self,
        user_id: &str,
        request: UpdateUserRequest,
    ) -> Result<ApiResponse<UserResponse>, ManaError> {
        if user_id.trim().is_empty() || user_id.contains(['/', '?', '#']) {
            return Err(ManaError::InvalidPath(user_id.to_owned()));
        }
        let path = format!("/api/v1/users/{user_id}");
        self.request_json(reqwest::Method::PATCH, &path, &request)
            .await
    }

    pub async fn update_user(
        &self,
        user_id: &str,
        request: UpdateUserRequest,
    ) -> Result<ApiResponse<UserResponse>, ManaError> {
        self.update(user_id, request).await
    }
}
