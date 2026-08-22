use reqwest::Method;
use serde::{Deserialize, Serialize};

use crate::transport::{ApiResponse, ManaClient, ManaError};

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct AlertResponse {
    pub alert: AlertItem,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct AlertsResponse {
    pub alerts: Vec<AlertItem>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct AlertItem {
    pub id: String,
    #[serde(default)]
    pub resident_id: Option<String>,
    pub bed_id: String,
    pub evidence_kind: String,
    #[serde(default)]
    pub evidence_ref: Option<String>,
    pub rule_id: String,
    pub level: String,
    pub status: String,
    #[serde(default)]
    pub status_actor_id: Option<String>,
    #[serde(default)]
    pub status_at: Option<String>,
    pub title: String,
    #[serde(default)]
    pub detail: Option<String>,
    pub occurred_at: String,
    pub escalation: EscalationItem,
    pub delivery_summary: DeliverySummaryItem,
    pub created_at: String,
    pub updated_at: String,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct EscalationItem {
    pub level: i32,
    #[serde(default)]
    pub escalated_at: Option<String>,
    #[serde(default)]
    pub escalated_to: Option<String>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct DeliverySummaryItem {
    pub sent: i64,
    pub acked: i64,
    pub failed: i64,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct DeliveryItem {
    pub id: String,
    pub alert_id: String,
    pub recipient_kind: String,
    pub recipient_id: String,
    pub channel: String,
    pub escalation_level: i32,
    pub created_at: String,
    #[serde(default)]
    pub events: Vec<DeliveryEventItem>,
    #[serde(default)]
    pub sent_at: Option<String>,
    #[serde(default)]
    pub acked_at: Option<String>,
    #[serde(default)]
    pub failed_reason: Option<String>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct DeliveryEventItem {
    pub id: String,
    pub delivery_id: String,
    pub kind: String,
    #[serde(default)]
    pub reason: Option<String>,
    pub occurred_at: String,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct DeliveriesResponse {
    pub deliveries: Vec<DeliveryItem>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct CreateAlertRequest {
    #[serde(skip_serializing_if = "Option::is_none")]
    pub resident_id: Option<String>,
    pub bed_id: String,
    pub evidence_kind: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub evidence_ref: Option<String>,
    pub rule_id: String,
    pub level: String,
    pub title: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub detail: Option<String>,
    pub occurred_at: String,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct TransitionAlertRequest {
    pub to_status: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub actor_id: Option<String>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct CreateDeliveryRequest {
    pub recipient_kind: String,
    pub recipient_id: String,
    pub channel: String,
    #[serde(default)]
    pub escalation_level: i32,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct AddDeliveryEventRequest {
    pub kind: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub reason: Option<String>,
}

impl ManaClient {
    pub async fn list_alerts(&self) -> Result<ApiResponse<AlertsResponse>, ManaError> {
        self.request(Method::GET, "/api/v1/alerts").await
    }

    pub async fn create_alert(
        &self,
        request: CreateAlertRequest,
    ) -> Result<ApiResponse<AlertResponse>, ManaError> {
        self.request_json(Method::POST, "/api/v1/alerts", request)
            .await
    }

    pub async fn get_alert(&self, alert_id: &str) -> Result<ApiResponse<AlertResponse>, ManaError> {
        let url = format!("/api/v1/alerts/{alert_id}");
        self.request(Method::GET, &url).await
    }

    pub async fn transition_alert(
        &self,
        alert_id: &str,
        request: TransitionAlertRequest,
    ) -> Result<ApiResponse<AlertResponse>, ManaError> {
        let url = format!("/api/v1/alerts/{alert_id}");
        self.request_json(Method::PATCH, &url, request).await
    }

    pub async fn view_alert(
        &self,
        alert_id: &str,
    ) -> Result<ApiResponse<AlertResponse>, ManaError> {
        let url = format!("/api/v1/alerts/{alert_id}/view");
        self.request(Method::POST, &url).await
    }

    pub async fn list_deliveries(
        &self,
        alert_id: &str,
    ) -> Result<ApiResponse<DeliveriesResponse>, ManaError> {
        let url = format!("/api/v1/alerts/{alert_id}/deliveries");
        self.request(Method::GET, &url).await
    }

    pub async fn create_delivery(
        &self,
        alert_id: &str,
        request: CreateDeliveryRequest,
    ) -> Result<ApiResponse<DeliveryItem>, ManaError> {
        let url = format!("/api/v1/alerts/{alert_id}/deliveries");
        self.request_json(Method::POST, &url, request).await
    }

    pub async fn add_delivery_event(
        &self,
        delivery_id: &str,
        request: AddDeliveryEventRequest,
    ) -> Result<ApiResponse<DeliveryItem>, ManaError> {
        let url = format!("/api/v1/deliveries/{delivery_id}/events");
        self.request_json(Method::POST, &url, request).await
    }
}
