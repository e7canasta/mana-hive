use tabled::{Table, Tabled};
use serde_json::Value;

#[derive(Tabled)]
struct RuleRow {
    #[tabled(rename = "Regla")]
    id: String,
    #[tabled(rename = "Descripcion")]
    label: String,
    #[tabled(rename = "Grupo")]
    group: String,
    #[tabled(rename = "Dia")]
    day: String,
    #[tabled(rename = "Noche")]
    night: String,
    #[tabled(rename = "Timer")]
    timer: String,
    #[tabled(rename = "Fuente")]
    source: String,
}

pub fn render_profile(data: &Value) {
    let preset = data.get("preset").or_else(|| data.get("profile"));
    let Some(preset) = preset else {
        println!("Perfil no encontrado");
        return;
    };

    let resident = data.get("resident").or_else(|| {
        preset.get("resident")
    });

    let effective = preset.get("effective").unwrap_or(&Value::Null);
    let rules = effective.get("rules").unwrap_or(&Value::Null);

    // Header del residente
    if let Some(res) = resident {
        let name = res.get("full_name").and_then(|v| v.as_str()).unwrap_or("?");
        let room = res.get("room_number").and_then(|v| v.as_str()).unwrap_or("?");
        let bed = res.get("bed_label").and_then(|v| v.as_str()).unwrap_or("?");
        let wing = res.get("wing_name").and_then(|v| v.as_str()).unwrap_or("?");
        println!("\n  Residente: {name}");
        println!("  Habitacion: {room} ({wing}) · Cama: {bed}");
    }

    let level = effective.get("level").and_then(|v| v.as_str()).unwrap_or("?");
    let mode = effective.get("mode").and_then(|v| v.as_str()).unwrap_or("?");
    let template = effective.get("template_id").and_then(|v| v.as_str()).unwrap_or("?");
    let mobility = effective.get("mobility_aid").and_then(|v| v.as_str()).unwrap_or("?");

    println!("\n  Riesgo: {level} · Modo: {mode} · Plantilla: {template} · Apoyo: {mobility}");

    // Contar alarmas por turno
    let mut day_alarms = 0;
    let mut day_notif = 0;
    let mut night_alarms = 0;
    let mut night_notif = 0;

    if let Some(rules_obj) = rules.as_object() {
        for (_, rule) in rules_obj {
            if let Some(day) = rule.get("day").and_then(|v| v.as_str()) {
                match day {
                    "alarm" => day_alarms += 1,
                    "notify" => day_notif += 1,
                    _ => {}
                }
            }
            if let Some(night) = rule.get("night").and_then(|v| v.as_str()) {
                match night {
                    "alarm" => night_alarms += 1,
                    "notify" => night_notif += 1,
                    _ => {}
                }
            }
        }
    }

    println!("  Turno dia: {day_alarms} alarmas, {day_notif} notificaciones");
    println!("  Turno noche: {night_alarms} alarmas, {night_notif} notificaciones\n");

    // Tabla de reglas customizadas
    let mut rows: Vec<RuleRow> = Vec::new();

    if let Some(rules_obj) = rules.as_object() {
        let mut sorted: Vec<_> = rules_obj.iter().collect();
        sorted.sort_by_key(|(k, _)| k.to_string());

        for (rule_id, rule) in sorted {
            let customized = rule.get("customized").and_then(|v| v.as_bool()).unwrap_or(false);
            if !customized {
                continue;
            }

            let day = rule.get("day").and_then(|v| v.as_str()).unwrap_or("off");
            let night = rule.get("night").and_then(|v| v.as_str()).unwrap_or("off");
            let params = rule.get("params").unwrap_or(&Value::Null);
            let source = rule.get("source").and_then(|v| v.as_str()).unwrap_or("?");

            let timer = if let Some(d) = params.get("delay_minutes").and_then(|v| v.as_i64()) {
                format!("{d} min confirm")
            } else if let Some(d) = params.get("dwell_minutes").and_then(|v| v.as_i64()) {
                format!("{d} min dwell")
            } else {
                "-".to_string()
            };

            rows.push(RuleRow {
                id: rule_id.clone(),
                label: rule_id.clone(), // Podriamos mapear a labels
                group: rule.get("group").and_then(|v| v.as_str()).unwrap_or("?").to_string(),
                day: day.to_string(),
                night: night.to_string(),
                timer,
                source: source.to_string(),
            });
        }
    }

    if rows.is_empty() {
        println!("  Sin reglas customizadas");
    } else {
        println!("  Reglas customizadas:");
        let table = Table::new(&rows).to_string();
        for line in table.lines() {
            println!("  {line}");
        }
    }

    println!();
}
