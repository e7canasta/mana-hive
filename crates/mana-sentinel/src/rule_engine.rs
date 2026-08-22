//! Rule engine for Sentinel.
//!
//! This module absorbs the business logic from mana-engine v1:
//! - Alarm catalog loading and caching
//! - Profile resolution (risk_level → effective rules)
//! - Rule evaluation using mana_motores
//! - Per-episode deduplication

use std::collections::{BTreeSet, HashMap};
use std::sync::Arc;

use ctx_politica::{AlarmCatalog, AlarmProfileVersion, MobilityAid, RiskLevel};
use mana_engine_v2::SceneEvent;
use mana_kernel::Instante;
use mana_motores::{Contexto, EstadoObservado, PerfilEfectivo, Turno};

use crate::evaluator::{CategorizedEvent, EventCategory};

/// Dedup key: (bed_id, rule_id) → state_since when the rule last fired.
type DedupCache = HashMap<(String, String), Instante>;

/// Rule engine that evaluates scene events against the alarm catalog.
pub struct RuleEngine {
    catalog: Arc<AlarmCatalog>,
    dedup: std::sync::Arc<tokio::sync::RwLock<DedupCache>>,
}

impl RuleEngine {
    /// Create a new RuleEngine with the given catalog.
    pub fn new(catalog: AlarmCatalog) -> Self {
        Self {
            catalog: Arc::new(catalog),
            dedup: std::sync::Arc::new(tokio::sync::RwLock::new(HashMap::new())),
        }
    }

    /// Evaluate a scene event against the alarm catalog.
    ///
    /// Returns a CategorizedEvent with the category (Off/Notify/Alarm/Mark)
    /// and any alerts that should be created.
    pub async fn evaluate(
        &self,
        scene: &SceneEvent,
        profile: Option<&AlarmProfileVersion>,
    ) -> CategorizedEvent {
        let dedup = self.dedup.read().await;

        // 1. Resolve effective profile
        let effective = perfil_efectivo_from_profile(&self.catalog, profile);

        // 2. Build evaluation context from scene event
        let Some(contexto) = build_contexto(scene) else {
            return CategorizedEvent {
                scene_event: scene.clone(),
                category: EventCategory::Off,
                rule_id: None,
                reason: "No se pudo construir contexto de evaluación".into(),
            };
        };

        // 3. Get dedup set for this bed
        let state_since = Instante::from(scene.poi.state_since);
        let ya_avisadas = get_dedup_set(&dedup, &scene.bed_id, &state_since);

        // 4. Evaluate rules
        let alertas = mana_motores::evaluar(&contexto, &effective, &ya_avisadas, mana_motores::Disparo::Evento);

        drop(dedup);

        if alertas.is_empty() {
            CategorizedEvent {
                scene_event: scene.clone(),
                category: EventCategory::Off,
                rule_id: None,
                reason: "No alerts fired".into(),
            }
        } else {
            let primary = &alertas[0];
            let category = EventCategory::Alarm;

            {
                let mut dedup = self.dedup.write().await;
                for alerta in &alertas {
                    dedup.insert(
                        (scene.bed_id.to_string(), alerta.rule_id.clone()),
                        state_since.clone(),
                    );
                }
            }

            CategorizedEvent {
                scene_event: scene.clone(),
                category,
                rule_id: Some(primary.rule_id.clone()),
                reason: format!(
                    "Alert: {} (level: {:?})",
                    primary.titulo, primary.nivel
                ),
            }
        }
    }
}

/// Map PersonState (v2) to EstadoObservado (v1).
fn map_person_state(state: &mana_engine_v2::fsm::PersonState) -> EstadoObservado {
    use mana_engine_v2::fsm::PersonState;
    match state {
        PersonState::Lying => EstadoObservado::AcostadoEnCama,
        PersonState::SittingInBed => EstadoObservado::SentadoEnCama,
        PersonState::BedEdge => EstadoObservado::BordeDeCama,
        PersonState::Standing => EstadoObservado::DePie,
        PersonState::InBathroom => EstadoObservado::NoNombrado("bathroom".into()),
        PersonState::InRoom => EstadoObservado::NoNombrado("room".into()),
        PersonState::InHallway => EstadoObservado::NoNombrado("hallway".into()),
        PersonState::Outdoor => EstadoObservado::NoNombrado("outdoor".into()),
        PersonState::InChair => EstadoObservado::NoNombrado("chair".into()),
        PersonState::InWheelchair => EstadoObservado::NoNombrado("wheelchair".into()),
        PersonState::Unknown => EstadoObservado::Desconocido,
    }
}

/// Build evaluation context from a scene event.
fn build_contexto(scene: &SceneEvent) -> Option<Contexto> {
    let estado = map_person_state(&scene.poi.state);

    let estado_previo = match &scene.trigger {
        mana_engine_v2::scene_event::TriggerInfo::TransitionDetected {
            from_state, ..
        } => map_person_state(from_state),
        _ => EstadoObservado::Desconocido,
    };

    // Map room occupancy to room_state string
    let room_state = Some(format!("{:?}", scene.room.occupancy));

    // Determine shift from timestamp (TODO: resolve from timezone)
    let turno = Turno::Dia;

    Some(Contexto {
        bed_id: scene.bed_id.to_string(),
        resident_id: scene.resident_id.clone(),
        estado_previo,
        estado,
        room_state,
        occurred_at: Instante::from(scene.timestamp),
        state_since: Instante::from(scene.poi.state_since),
        turno,
        evidencia_ref: match &scene.trigger {
            mana_engine_v2::scene_event::TriggerInfo::Perception {
                perception_event_id,
                ..
            } => Some(perception_event_id.clone()),
            _ => None,
        },
    })
}

/// Get the dedup set for a bed: rules that already fired in this episode.
fn get_dedup_set(dedup: &DedupCache, bed_id: &str, state_since: &Instante) -> BTreeSet<String> {
    dedup
        .iter()
        .filter(|((bid, _), since)| bid == bed_id && *since >= state_since)
        .map(|((_, rule_id), _)| rule_id.clone())
        .collect()
}

/// Convert a stored AlarmProfileVersion into effective rules.
fn perfil_efectivo_from_profile(
    catalog: &AlarmCatalog,
    profile: Option<&AlarmProfileVersion>,
) -> PerfilEfectivo {
    let input = match profile {
        Some(p) => p.to_perfil_input(),
        None => ctx_politica::PerfilEfectivoInput {
            risk_level: RiskLevel::Medium,
            mobility_aid: MobilityAid::None,
            is_custom: false,
            template_id: "balanced".to_owned(),
            overrides: ctx_politica::Overrides::empty(),
        },
    };

    mana_motores::perfil_efectivo(catalog, input.risk_level, input.mobility_aid, input.is_custom, &input.template_id, input.overrides.as_value())
}
