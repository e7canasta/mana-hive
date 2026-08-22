//! Demo de producto: autopilot aplica una subida, propone una bajada y salta
//! una recomendacion sin evidencia. No consulta SQLite ni arranca el hub.

use mana_kernel::Instante;
use mana_motores::{
    decidir, recomendar, AlarmCatalog, AutopilotInput, AutopilotProfile, MobilityAid, Senales,
};

fn main() {
    let catalog = AlarmCatalog::parse(include_str!("../../../config/alarm-catalog.toml"))
        .expect("el catalogo de demo tiene que cargar");
    let now: Instante = "2026-08-19T12:00:00.000Z".parse().expect("instante valido");
    let rasgos = vec!["fall_risk".to_owned()];

    let subida = recomendar(
        &Senales {
            falls: 1,
            severe_falls: 1,
            bed_exits_per_night: Some(3.2),
            walking_speed_mps: Some(0.45),
            ..Default::default()
        },
        MobilityAid::Walker,
        &rasgos,
        &catalog.recomendacion,
        &catalog.templates,
    );
    mostrar(
        "subida",
        decidir(
            &AutopilotInput {
                current_profile: AutopilotProfile {
                    risk_level: mana_motores::RiskLevel::Medium,
                    autopilot: true,
                },
                recommendation: subida,
                last_change_at: None,
                now,
            },
            &catalog.autopilot,
        ),
    );

    let bajada = recomendar(
        &Senales {
            bed_exits_per_night: Some(0.0),
            ..Default::default()
        },
        MobilityAid::None,
        &[],
        &catalog.recomendacion,
        &catalog.templates,
    );
    mostrar(
        "bajada",
        decidir(
            &AutopilotInput {
                current_profile: AutopilotProfile {
                    risk_level: mana_motores::RiskLevel::High,
                    autopilot: true,
                },
                recommendation: bajada,
                last_change_at: None,
                now,
            },
            &catalog.autopilot,
        ),
    );

    let sin_evidencia = recomendar(
        &Senales::default(),
        MobilityAid::None,
        &[],
        &catalog.recomendacion,
        &catalog.templates,
    );
    mostrar(
        "sin evidencia",
        decidir(
            &AutopilotInput {
                current_profile: AutopilotProfile {
                    risk_level: mana_motores::RiskLevel::Medium,
                    autopilot: true,
                },
                recommendation: sin_evidencia,
                last_change_at: None,
                now,
            },
            &catalog.autopilot,
        ),
    );
}

fn mostrar(caso: &str, decision: mana_motores::AutopilotDecision) {
    println!(
        "{caso}: accion={}, motivo={}, nivel_actual={}, nivel_recomendado={}, senales={}",
        decision.action.as_str(),
        decision.reason.as_str(),
        decision.current_level.as_str(),
        decision.recommended_level.as_str(),
        decision.signals_evaluated,
    );
}
