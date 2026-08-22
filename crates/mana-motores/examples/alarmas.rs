//! Demo de producto: una persona sale de la cama y el motor decide crear una
//! alerta. La persistencia y el envio son responsabilidad de `mana-app`.

use std::collections::BTreeSet;

use mana_kernel::Instante;
use mana_motores::{
    evaluar, Accion, Clase, Contexto, Disparo, EstadoObservado, NivelAlerta, PerfilEfectivo,
    ReglaEfectiva, Sensibilidad, Turno,
};

fn instante(value: &str) -> Instante {
    value.parse().expect("instante valido")
}

fn main() {
    let profile = PerfilEfectivo::new([ReglaEfectiva {
        id: "bed_exit".to_owned(),
        clase: Clase::Transicion,
        dia: Accion::Alarm,
        noche: Accion::Alarm,
        minutos: 0,
        sensibilidad: Sensibilidad::Alta,
        bloqueada: false,
        etiqueta: "Sale de la cama".to_owned(),
    }]);
    let context = Contexto {
        bed_id: "bed-118-a".to_owned(),
        resident_id: Some("resident-1".to_owned()),
        estado: EstadoObservado::DePie,
        estado_previo: EstadoObservado::AcostadoEnCama,
        room_state: Some("occupied".to_owned()),
        state_since: instante("2026-08-19T02:00:00Z"),
        occurred_at: instante("2026-08-19T02:00:10Z"),
        turno: Turno::Noche,
        evidencia_ref: Some("event-1".to_owned()),
    };

    let alerts = evaluar(&context, &profile, &BTreeSet::new(), Disparo::Evento);
    for alert in alerts {
        let level = match alert.nivel {
            NivelAlerta::Low => "low",
            NivelAlerta::Medium => "medium",
            NivelAlerta::High => "high",
        };
        println!("crear alerta {} para {}", level, alert.resident_id.unwrap());
        println!("regla: {}", alert.rule_id);
        println!("evidencia: {:?}", alert.evidence_kind);
    }
}
