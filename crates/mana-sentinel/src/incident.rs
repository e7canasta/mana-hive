use std::collections::HashMap;

use serde::{Deserialize, Serialize};
use chrono::{DateTime, Utc};

use mana_engine_v2::SceneEvent;

/// Estado de un incidente
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub enum IncidentStatus {
    /// Recién creado, recolectando evidencia
    CollectingEvidence,
    /// Esperando llegada del staff
    WaitingForStaff,
    /// Staff presente en la habitación
    StaffOnSite,
    /// Cerrando incidente (staff se fue)
    ClosingIncident,
    /// Incidente cerrado
    Closed,
}

/// Evento en la timeline del incidente
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct IncidentEvent {
    pub event_id: String,
    pub event_type: IncidentEventType,
    pub timestamp: DateTime<Utc>,
    pub data: serde_json::Value,
}

/// Tipos de eventos en un incidente
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub enum IncidentEventType {
    /// Evento primario que abre el incidente
    Primary,
    /// Llegada del staff
    StaffArrived,
    /// Salida del staff
    StaffDeparted,
    /// Acción tomada
    ActionTaken,
    /// Resolución
    Resolution,
    /// Escalamiento
    Escalation,
    /// Evento de escena durante el incidente
    SceneEvent,
}

/// Preset que define un tipo de incidente
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct IncidentPreset {
    pub id: String,
    pub name: String,
    pub description: String,
    /// ¿Requiere presencia de staff?
    pub requires_staff: bool,
    /// Duración del clip window en minutos
    pub clip_window_minutes: i32,
    /// Tiempo máximo para acknowledge (minutos)
    pub acknowledge_within_minutes: Option<i32>,
    /// Tiempo máximo para que staff llegue (minutos)
    pub arrive_within_minutes: Option<i32>,
    /// Tiempo máximo para resolver (minutos)
    pub resolve_within_minutes: Option<i32>,
    /// Condición de cierre
    pub closure_condition: ClosureCondition,
}

/// Condición de cierre del incidente
#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum ClosureCondition {
    /// Staff llega y se va
    StaffDeparts,
    /// Se resuelve manualmente
    ManualResolution,
    /// Timeout
    Timeout { minutes: i32 },
}

/// Incidente en memoria
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Incident {
    pub id: String,
    pub bed_id: String,
    pub resident_id: Option<String>,
    pub preset_id: String,
    pub status: IncidentStatus,
    pub primary_event: IncidentEvent,
    pub timeline: Vec<IncidentEvent>,
    pub clip_window_id: Option<String>,
    pub created_at: DateTime<Utc>,
    pub updated_at: DateTime<Utc>,
    pub closed_at: Option<DateTime<Utc>>,
}

/// Gestor de incidentes
#[derive(Debug, Clone)]
pub struct IncidentManager {
    incidents: HashMap<String, Incident>,
    presets: Vec<IncidentPreset>,
}

impl IncidentManager {
    pub fn new() -> Self {
        Self {
            incidents: HashMap::new(),
            presets: Self::default_presets(),
        }
    }

    /// Presets por defecto
    fn default_presets() -> Vec<IncidentPreset> {
        vec![
            IncidentPreset {
                id: "fall".to_string(),
                name: "Caída detectada".to_string(),
                description: "Residente cayó de la cama o en la habitación".to_string(),
                requires_staff: true,
                clip_window_minutes: 10,
                acknowledge_within_minutes: Some(2),
                arrive_within_minutes: Some(5),
                resolve_within_minutes: Some(30),
                closure_condition: ClosureCondition::StaffDeparts,
            },
            IncidentPreset {
                id: "bed_exit".to_string(),
                name: "Salida de cama".to_string(),
                description: "Residente salió de la cama sin asistencia".to_string(),
                requires_staff: true,
                clip_window_minutes: 5,
                acknowledge_within_minutes: Some(2),
                arrive_within_minutes: Some(5),
                resolve_within_minutes: Some(15),
                closure_condition: ClosureCondition::StaffDeparts,
            },
            IncidentPreset {
                id: "dwell_exceeded".to_string(),
                name: "Permanencia excedida".to_string(),
                description: "Residente fuera de cama por tiempo excesivo".to_string(),
                requires_staff: true,
                clip_window_minutes: 10,
                acknowledge_within_minutes: Some(2),
                arrive_within_minutes: Some(5),
                resolve_within_minutes: Some(20),
                closure_condition: ClosureCondition::StaffDeparts,
            },
            IncidentPreset {
                id: "bathroom_dwell".to_string(),
                name: "Permanencia en baño".to_string(),
                description: "Residente en baño por tiempo excesivo".to_string(),
                requires_staff: true,
                clip_window_minutes: 10,
                acknowledge_within_minutes: Some(2),
                arrive_within_minutes: Some(5),
                resolve_within_minutes: Some(20),
                closure_condition: ClosureCondition::StaffDeparts,
            },
        ]
    }

    /// Busca preset por ID
    pub fn find_preset(&self, preset_id: &str) -> Option<&IncidentPreset> {
        self.presets.iter().find(|p| p.id == preset_id)
    }

    /// Verifica si un evento de escena requiere incidente
    pub fn requires_incident(&self, event: &SceneEvent) -> Option<&IncidentPreset> {
        // Regla: dwell events siempre requieren incidente
        if event.event_type == mana_engine_v2::SceneEventType::Dwell {
            return self.find_preset("dwell_exceeded");
        }

        // Regla: transiciones in_bed → out_of_bed son bed_exit
        if let mana_engine_v2::scene_event::TriggerInfo::TransitionDetected { from_state, to_state } = &event.trigger {
            if from_state.is_in_bed() && to_state.is_out_of_bed() {
                return self.find_preset("bed_exit");
            }
        }

        // Regla: perception out_of_bed es bed_exit
        if event.poi.state.is_out_of_bed() {
            return self.find_preset("bed_exit");
        }

        None
    }

    /// Crea un nuevo incidente
    pub fn create_incident(
        &mut self,
        bed_id: &str,
        resident_id: Option<&str>,
        preset_id: &str,
        scene_event: &SceneEvent,
    ) -> Result<&Incident, String> {
        let preset = self.find_preset(preset_id)
            .ok_or_else(|| format!("Preset not found: {}", preset_id))?;

        let now = Utc::now();
        let incident_id = format!("inc-{}-{}", now.timestamp(), bed_id);

        // Extraer event_id del trigger
        let event_id = match &scene_event.trigger {
            mana_engine_v2::scene_event::TriggerInfo::Perception { perception_event_id, .. } => {
                perception_event_id.clone()
            }
            _ => uuid::Uuid::new_v4().to_string(),
        };

        let primary_event = IncidentEvent {
            event_id,
            event_type: IncidentEventType::Primary,
            timestamp: now,
            data: serde_json::to_value(scene_event).unwrap_or_default(),
        };

        let incident = Incident {
            id: incident_id.clone(),
            bed_id: bed_id.to_string(),
            resident_id: resident_id.map(|s| s.to_string()),
            preset_id: preset_id.to_string(),
            status: if preset.requires_staff {
                IncidentStatus::WaitingForStaff
            } else {
                IncidentStatus::CollectingEvidence
            },
            primary_event: primary_event.clone(),
            timeline: vec![primary_event],
            clip_window_id: None,
            created_at: now,
            updated_at: now,
            closed_at: None,
        };

        self.incidents.insert(incident_id.clone(), incident);
        Ok(self.incidents.get(&incident_id).unwrap())
    }

    /// Obtiene un incidente por ID
    pub fn get_incident(&self, id: &str) -> Option<&Incident> {
        self.incidents.get(id)
    }

    /// Obtiene un incidente mutable por ID
    pub fn get_incident_mut(&mut self, id: &str) -> Option<&mut Incident> {
        self.incidents.get_mut(id)
    }

    /// Obtiene incidente abierto para una cama
    pub fn open_incident_for_bed(&self, bed_id: &str) -> Option<&Incident> {
        self.incidents.values().find(|i| {
            i.bed_id == bed_id && i.status != IncidentStatus::Closed
        })
    }

    /// Agrega evento a la timeline del incidente
    pub fn add_event(
        &mut self,
        incident_id: &str,
        event_type: IncidentEventType,
        data: serde_json::Value,
    ) -> Result<(), String> {
        let incident = self.incidents.get_mut(incident_id)
            .ok_or_else(|| format!("Incident not found: {}", incident_id))?;

        if incident.status == IncidentStatus::Closed {
            return Err("Incident is closed".to_string());
        }

        let event = IncidentEvent {
            event_id: uuid::Uuid::new_v4().to_string(),
            event_type,
            timestamp: Utc::now(),
            data,
        };

        incident.timeline.push(event);
        incident.updated_at = Utc::now();

        Ok(())
    }

    /// Procesa llegada de staff
    pub fn staff_arrived(&mut self, incident_id: &str, staff_id: &str) -> Result<(), String> {
        let incident = self.incidents.get_mut(incident_id)
            .ok_or_else(|| format!("Incident not found: {}", incident_id))?;

        if incident.status != IncidentStatus::WaitingForStaff {
            return Err(format!("Cannot transition from {:?} to StaffOnSite", incident.status));
        }

        incident.status = IncidentStatus::StaffOnSite;
        incident.updated_at = Utc::now();

        // Agregar evento a timeline
        let event = IncidentEvent {
            event_id: uuid::Uuid::new_v4().to_string(),
            event_type: IncidentEventType::StaffArrived,
            timestamp: Utc::now(),
            data: serde_json::json!({ "staff_id": staff_id }),
        };
        incident.timeline.push(event);

        Ok(())
    }

    /// Procesa salida de staff
    pub fn staff_departed(&mut self, incident_id: &str, staff_id: &str) -> Result<(), String> {
        // Obtener preset_id y status primero para evitar borrow conflict
        let (preset_id, current_status) = {
            let incident = self.incidents.get(incident_id)
                .ok_or_else(|| format!("Incident not found: {}", incident_id))?;
            (incident.preset_id.clone(), incident.status.clone())
        };

        if current_status != IncidentStatus::StaffOnSite {
            return Err(format!("Cannot transition from {:?} to ClosingIncident", current_status));
        }

        // Verificar condición de cierre
        let closure_condition = {
            let preset = self.find_preset(&preset_id)
                .ok_or_else(|| format!("Preset not found: {}", preset_id))?;
            preset.closure_condition.clone()
        };

        let incident = self.incidents.get_mut(incident_id)
            .ok_or_else(|| format!("Incident not found: {}", incident_id))?;

        match &closure_condition {
            ClosureCondition::StaffDeparts => {
                incident.status = IncidentStatus::Closed;
                incident.closed_at = Some(Utc::now());
            }
            ClosureCondition::ManualResolution => {
                incident.status = IncidentStatus::ClosingIncident;
            }
            ClosureCondition::Timeout { .. } => {
                incident.status = IncidentStatus::ClosingIncident;
            }
        }

        incident.updated_at = Utc::now();

        // Agregar evento a timeline
        let event = IncidentEvent {
            event_id: uuid::Uuid::new_v4().to_string(),
            event_type: IncidentEventType::StaffDeparted,
            timestamp: Utc::now(),
            data: serde_json::json!({ "staff_id": staff_id }),
        };
        incident.timeline.push(event);

        Ok(())
    }

    /// Cierra manualmente un incidente
    pub fn close_incident(&mut self, incident_id: &str, resolution: &str) -> Result<(), String> {
        let incident = self.incidents.get_mut(incident_id)
            .ok_or_else(|| format!("Incident not found: {}", incident_id))?;

        if incident.status == IncidentStatus::Closed {
            return Err("Incident is already closed".to_string());
        }

        incident.status = IncidentStatus::Closed;
        incident.closed_at = Some(Utc::now());
        incident.updated_at = Utc::now();

        // Agregar evento de resolución
        let event = IncidentEvent {
            event_id: uuid::Uuid::new_v4().to_string(),
            event_type: IncidentEventType::Resolution,
            timestamp: Utc::now(),
            data: serde_json::json!({ "resolution": resolution }),
        };
        incident.timeline.push(event);

        Ok(())
    }

    /// Lista todos los incidentes abiertos
    pub fn open_incidents(&self) -> Vec<&Incident> {
        self.incidents.values()
            .filter(|i| i.status != IncidentStatus::Closed)
            .collect()
    }

    /// Lista todos los incidentes
    pub fn all_incidents(&self) -> Vec<&Incident> {
        self.incidents.values().collect()
    }
}

impl Default for IncidentManager {
    fn default() -> Self {
        Self::new()
    }
}
