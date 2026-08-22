//! Demo de producto: una noche dificil y una marcha lenta producen una
//! recomendacion explicable. No consulta SQLite ni arranca el hub.

use mana_motores::{recomendar, AlarmCatalog, MobilityAid, Senales};

fn main() {
    let catalog = AlarmCatalog::parse(include_str!("../../../config/alarm-catalog.toml"))
        .expect("el catalogo de demo tiene que cargar");
    let traits = vec!["fall_risk".to_owned()];
    let recommendation = recomendar(
        &Senales {
            bed_exits_per_night: Some(3.2),
            wakes_per_night: Some(5.0),
            walking_speed_mps: Some(0.45),
            ..Default::default()
        },
        MobilityAid::Walker,
        &traits,
        &catalog.recomendacion,
        &catalog.templates,
    );

    println!("nivel sugerido: {}", recommendation.level.as_str());
    println!("puntaje: {}", recommendation.score);
    println!("señales evaluadas: {}", recommendation.signals_evaluated);
    println!("plantilla sugerida: {}", recommendation.suggested_template);
    for factor in recommendation.factors {
        println!(
            "factor {} (+{}): {}",
            factor.id, factor.weight, factor.detail
        );
    }
}
