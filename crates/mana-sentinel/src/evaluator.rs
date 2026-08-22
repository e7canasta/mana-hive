use mana_engine_v2::SceneEvent;
use serde::{Deserialize, Serialize};

/// Categoría del evento evaluado.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub enum EventCategory {
    /// No hacer nada
    Off,
    /// Notificar al personal
    Notify,
    /// Generar alarma
    Alarm,
    /// Marcar para revisión
    Mark,
}

impl EventCategory {
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::Off => "off",
            Self::Notify => "notify",
            Self::Alarm => "alarm",
            Self::Mark => "mark",
        }
    }
}

/// Evento categorizado por el evaluator.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CategorizedEvent {
    pub scene_event: SceneEvent,
    pub category: EventCategory,
    pub rule_id: Option<String>,
    pub reason: String,
}

/// Evaluador de presets para scene events.
#[derive(Debug, Clone)]
pub struct PresetEvaluator;

impl PresetEvaluator {
    /// Evalúa un scene event contra los presets.
    pub fn evaluate(&self, event: &SceneEvent) -> CategorizedEvent {
        // Por ahora implementamos reglas básicas
        // TODO: integrar con alarm catalog
        let category = match &event.event_type {
            &mana_engine_v2::scene_event::SceneEventType::Perception => {
                self.evaluate_perception(event)
            }
            &mana_engine_v2::scene_event::SceneEventType::Transition => {
                self.evaluate_transition(event)
            }
            &mana_engine_v2::scene_event::SceneEventType::Dwell => EventCategory::Alarm,
            &mana_engine_v2::scene_event::SceneEventType::Change => EventCategory::Off,
        };
        
        CategorizedEvent {
            scene_event: event.clone(),
            category,
            rule_id: None,
            reason: format!("Event type: {:?}", event.event_type),
        }
    }

    fn evaluate_perception(&self, event: &SceneEvent) -> EventCategory {
        // Regla básica: si hay transición a out_of_bed, notificar
        if event.poi.state.is_out_of_bed() {
            EventCategory::Notify
        } else {
            EventCategory::Off
        }
    }

    fn evaluate_transition(&self, event: &SceneEvent) -> EventCategory {
        // Regla básica: transiciones sospechosas son alarma
        match &event.trigger {
            mana_engine_v2::scene_event::TriggerInfo::TransitionDetected { from_state, to_state } => {
                if from_state.is_in_bed() && to_state.is_out_of_bed() {
                    EventCategory::Alarm
                } else {
                    EventCategory::Notify
                }
            }
            _ => EventCategory::Off,
        }
    }
}
