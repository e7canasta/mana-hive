//! Evaluacion pura: que alerta corresponde a esta observacion.
//!
//! Entra una observacion con el estado previo de la cama y el perfil efectivo
//! del residente; sale la lista de alertas que hay que crear. **No hace IO por
//! diseño**, y no es una preferencia de estilo: es el criterio de detencion de
//! F11.1. Si esta funcion necesitara ir a buscar algo, la composicion estaria
//! mal puesta y habria que rediscutir la frontera antes de escribir mas Rust.
//!
//! El referente de comportamiento es `api/alarm-engine.js`, que se fue con Node
//! en F9. Lo que aquel modulo resolvia con tres consultas —el estado previo, la
//! deduplicacion y las reglas efectivas— aca son parametros.

use std::collections::{BTreeMap, BTreeSet};

use mana_kernel::Instante;

/// Nivel que decide el motor antes de que `mana-app` lo traduzca al contexto
/// persistente de Vigilancia.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum NivelAlerta {
    Low,
    Medium,
    High,
}

impl NivelAlerta {
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::Low => "low",
            Self::Medium => "medium",
            Self::High => "high",
        }
    }
}

/// Evidencia que explica una decision del motor sin depender de la tabla que la
/// persistira.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum TipoEvidencia {
    SensorEvent,
    DwellWindow,
    Manual,
}

/// Las reglas que el vocabulario actual del detector permite resolver.
///
/// El resto del catalogo se configura y **no dispara**: necesita eventos que
/// hoy no llegan (baño, area comun, exterior, piso, silla, apoyo, sueño fuera
/// de la cama). Una regla que el panel muestra encendida y que nunca puede
/// sonar es una mentira operativa, asi que la lista es explicita y consultable
/// por [`reglas_pendientes`].
pub const REGLAS_OPERATIVAS: [&str; 7] = [
    "bed_exit",
    "bed_edge",
    "sitting_in_bed",
    "bed_entry",
    "out_of_bed_dwell",
    "in_bed_dwell",
    "room_absence_dwell",
];

/// Las reglas del catalogo que todavia esperan al detector.
pub fn reglas_pendientes<'a, I>(reglas_del_catalogo: I) -> Vec<String>
where
    I: IntoIterator<Item = &'a str>,
{
    reglas_del_catalogo
        .into_iter()
        .filter(|id| !REGLAS_OPERATIVAS.contains(id))
        .map(str::to_owned)
        .collect()
}

/// Vocabulario de estado del detector, tal como lo proyecta `current_bed_states`.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum EstadoObservado {
    AcostadoEnCama,
    SentadoEnCama,
    BordeDeCama,
    DePie,
    Desocupada,
    /// El detector informa "no se". **No** es "fuera de la cama": sin estado no
    /// hay transicion que reconstruir ni permanencia que vencer.
    Desconocido,
    /// Un estado que el vocabulario todavia no nombra. Se conserva como llego;
    /// rellenarlo seria inventar observacion.
    NoNombrado(String),
}

impl EstadoObservado {
    pub fn parse(value: &str) -> Self {
        match value {
            "laying_in_bed" => Self::AcostadoEnCama,
            "sitting_in_bed" => Self::SentadoEnCama,
            "sitting_on_bed_edge" => Self::BordeDeCama,
            "standing" => Self::DePie,
            "unoccupied" => Self::Desocupada,
            "unknown" => Self::Desconocido,
            otro => Self::NoNombrado(otro.to_owned()),
        }
    }

    pub fn as_str(&self) -> &str {
        match self {
            Self::AcostadoEnCama => "laying_in_bed",
            Self::SentadoEnCama => "sitting_in_bed",
            Self::BordeDeCama => "sitting_on_bed_edge",
            Self::DePie => "standing",
            Self::Desocupada => "unoccupied",
            Self::Desconocido => "unknown",
            Self::NoNombrado(valor) => valor,
        }
    }

    /// Las tres posturas que ocurren dentro de la cama. El borde cuenta: quien
    /// esta sentado al borde todavia no salio.
    pub fn en_cama(&self) -> bool {
        matches!(
            self,
            Self::AcostadoEnCama | Self::SentadoEnCama | Self::BordeDeCama
        )
    }

    /// Si el estado cuenta como "fuera de la cama" para una permanencia.
    ///
    /// Un estado que el vocabulario no nombra **cuenta**: el residente esta en
    /// algun lado y no es la cama. El unico que no cuenta es `Desconocido`, que
    /// es ausencia de observacion y no una ubicacion.
    pub fn cuenta_fuera_de_cama(&self) -> bool {
        !self.en_cama() && !matches!(self, Self::Desconocido)
    }
}

/// Momento del dia del residente. **No** es el turno laboral de la grilla: ese
/// es de `ctx-cobertura` y los dos ejes no se mezclan.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Turno {
    Dia,
    Noche,
}

impl Turno {
    pub const HORA_INICIO_DIA: u32 = 7;
    pub const HORA_INICIO_NOCHE: u32 = 19;

    /// El corte se resuelve en la hora **local de la residencia**, no en la del
    /// server: una alarma nocturna configurada en Buenos Aires no puede
    /// depender de UTC. Quien compone resuelve la hora local y la pasa aca.
    pub fn desde_hora_local(hora: u32) -> Self {
        if (Self::HORA_INICIO_DIA..Self::HORA_INICIO_NOCHE).contains(&hora) {
            Self::Dia
        } else {
            Self::Noche
        }
    }

    pub fn as_str(&self) -> &'static str {
        match self {
            Self::Dia => "day",
            Self::Noche => "night",
        }
    }

    fn etiqueta(&self) -> &'static str {
        match self {
            Self::Dia => "dia",
            Self::Noche => "noche",
        }
    }
}

/// Que hace la regla cuando se cumple. Es politica: quien recibe un `notify` no
/// ve lo mismo que quien recibe un `alarm`.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Accion {
    Off,
    Notify,
    Alarm,
}

impl Accion {
    pub fn parse(value: &str) -> Option<Self> {
        match value {
            "off" => Some(Self::Off),
            "notify" => Some(Self::Notify),
            "alarm" => Some(Self::Alarm),
            _ => None,
        }
    }

    pub fn as_str(&self) -> &'static str {
        match self {
            Self::Off => "off",
            Self::Notify => "notify",
            Self::Alarm => "alarm",
        }
    }

    /// `Off` no tiene nivel porque no produce alerta. Devolver un nivel por
    /// defecto convertiria una regla apagada en una encendida.
    pub fn nivel(&self) -> Option<NivelAlerta> {
        match self {
            Self::Off => None,
            Self::Notify => Some(NivelAlerta::Medium),
            Self::Alarm => Some(NivelAlerta::High),
        }
    }
}

/// Cuanta evidencia pide el sistema antes de disparar. Es calibracion de
/// politica, no mecanismo: el motor solo la aplica.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Sensibilidad {
    Baja,
    Estandar,
    Alta,
}

impl Sensibilidad {
    pub fn parse(value: &str) -> Option<Self> {
        match value {
            "low" => Some(Self::Baja),
            "standard" => Some(Self::Estandar),
            "high" => Some(Self::Alta),
            _ => None,
        }
    }

    pub fn as_str(&self) -> &'static str {
        match self {
            Self::Baja => "low",
            Self::Estandar => "standard",
            Self::Alta => "high",
        }
    }

    fn factor(&self) -> f64 {
        match self {
            Self::Baja => 1.5,
            Self::Estandar => 1.0,
            Self::Alta => 0.5,
        }
    }

    /// Con el temporizador en cero igual hay un piso: sin el, cualquier
    /// parpadeo del detector se convierte en una alarma.
    fn piso_segundos(&self) -> i64 {
        match self {
            Self::Baja => 60,
            Self::Estandar => 20,
            Self::Alta => 0,
        }
    }
}

/// Que dispara la regla: el evento o el reloj. Es la distincion que ordena la
/// fase entera.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Clase {
    /// Se dispara por el cambio de estado. Su temporizador es de confirmacion:
    /// cuanto espera el sistema antes de darlo por cierto.
    Transicion,
    /// Se dispara por el paso del tiempo. Su temporizador es de tolerancia:
    /// cuanto aguanta la situacion antes de avisar.
    Permanencia,
}

/// Una regla del catalogo ya resuelta para un residente: las capas
/// —preset del nivel de riesgo, plantilla, ajuste manual— ya se aplicaron.
///
/// El motor **no** las resuelve. Resolverlas es de `ctx-politica`, y esta
/// estructura es el contrato entre los dos.
#[derive(Debug, Clone)]
pub struct ReglaEfectiva {
    pub id: String,
    pub clase: Clase,
    pub dia: Accion,
    pub noche: Accion,
    /// `delay_minutes` en una transicion, `dwell_minutes` en una permanencia.
    pub minutos: i64,
    pub sensibilidad: Sensibilidad,
    /// `fall` es la unica regla bloqueada del catalogo: no se puede apagar en
    /// ninguna capa. Es condicion del dominio, no de la UI.
    pub bloqueada: bool,
    /// El titulo que ve el equipo.
    pub etiqueta: String,
}

impl ReglaEfectiva {
    pub fn accion_en(&self, turno: Turno) -> Accion {
        match turno {
            Turno::Dia => self.dia,
            Turno::Noche => self.noche,
        }
    }

    /// Cuanto tiene que sostenerse el estado antes de avisar, ya calibrado por
    /// la sensibilidad.
    pub fn espera_segundos(&self) -> i64 {
        if self.minutos <= 0 {
            return self.sensibilidad.piso_segundos();
        }
        ((self.minutos * 60) as f64 * self.sensibilidad.factor()).round() as i64
    }
}

/// El perfil efectivo del residente, vigente **en el instante de la
/// observacion**. No el de ahora: evaluar una caida de hace tres semanas con la
/// politica de hoy es reescribir la historia.
#[derive(Debug, Clone, Default)]
pub struct PerfilEfectivo {
    pub reglas: BTreeMap<String, ReglaEfectiva>,
}

impl PerfilEfectivo {
    pub fn new(reglas: impl IntoIterator<Item = ReglaEfectiva>) -> Self {
        Self {
            reglas: reglas
                .into_iter()
                .map(|regla| (regla.id.clone(), regla))
                .collect(),
        }
    }

    /// Reglas bloqueadas que llegaron apagadas en algun turno.
    ///
    /// Es una inconsistencia del perfil, no del motor: quien resuelve las capas
    /// tiene que impedirla. Se expone en vez de corregirse en silencio porque
    /// la falla que esta fase existe para eliminar es exactamente la silenciosa.
    pub fn bloqueadas_apagadas(&self) -> Vec<&str> {
        self.reglas
            .values()
            .filter(|regla| {
                regla.bloqueada && (regla.dia == Accion::Off || regla.noche == Accion::Off)
            })
            .map(|regla| regla.id.as_str())
            .collect()
    }
}

/// Que trajo al motor a evaluar.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Disparo {
    /// Llego una observacion del detector.
    Evento,
    /// Desperto el reloj. El barrido **solo** cierra permanencias vencidas: una
    /// transicion se reconoce cuando ocurre, no una hora despues.
    Barrido,
}

/// La observacion a evaluar, con todo lo que hace falta ya resuelto.
#[derive(Debug, Clone)]
pub struct Contexto {
    pub bed_id: String,
    /// `None` es una cama sin residente asignado. **No se deja de vigilar**: el
    /// perfil que llega es la politica fija de la cama.
    pub resident_id: Option<String>,
    pub estado: EstadoObservado,
    /// El ultimo estado **distinto** anterior a `state_since`. Un evento que
    /// repite el mismo estado no es una transicion.
    pub estado_previo: EstadoObservado,
    pub room_state: Option<String>,
    /// Desde cuando se sostiene el estado actual. El reloj de una permanencia
    /// arranca aca y **no** en la llegada del ultimo evento.
    pub state_since: Instante,
    pub occurred_at: Instante,
    pub turno: Turno,
    /// El `source_event_id` que trajo la observacion, si la trajo un evento.
    pub evidencia_ref: Option<String>,
}

/// Una alerta que corresponde crear. `mana-app` la traduce al comando del
/// contexto de Vigilancia; el motor decide, no persiste.
#[derive(Debug, Clone, PartialEq)]
pub struct AlertaNueva {
    pub bed_id: String,
    pub resident_id: Option<String>,
    pub rule_id: String,
    pub clase: Clase,
    pub nivel: NivelAlerta,
    pub titulo: String,
    pub detalle: String,
    pub evidence_kind: TipoEvidencia,
    pub evidence_ref: Option<String>,
    pub occurred_at: Instante,
}

/// La transicion que va de un estado al otro, si el catalogo le da nombre.
///
/// Sin estado previo conocido no hay transicion que reconstruir: `Desconocido`
/// no dispara nada, en ninguna direccion.
pub fn transicion_entre(previo: &EstadoObservado, nuevo: &EstadoObservado) -> Option<&'static str> {
    use EstadoObservado::*;

    if previo == nuevo || matches!(previo, Desconocido) {
        return None;
    }
    if previo.en_cama() && matches!(nuevo, DePie | Desocupada) {
        return Some("bed_exit");
    }
    if matches!(previo, AcostadoEnCama | SentadoEnCama) && matches!(nuevo, BordeDeCama) {
        return Some("bed_edge");
    }
    if matches!(previo, AcostadoEnCama) && matches!(nuevo, SentadoEnCama) {
        return Some("sitting_in_bed");
    }
    // Acostarse cierra el episodio: se reconoce desde cualquier estado anterior
    // conocido, venga de estar de pie, fuera de la habitacion o al borde.
    if matches!(nuevo, AcostadoEnCama) {
        return Some("bed_entry");
    }
    None
}

/// Las permanencias que este estado puede estar corriendo.
fn permanencias_candidatas(
    estado: &EstadoObservado,
    room_state: Option<&str>,
) -> Vec<&'static str> {
    let mut reglas = Vec::new();
    if estado.cuenta_fuera_de_cama() {
        reglas.push("out_of_bed_dwell");
    }
    if estado.en_cama() {
        reglas.push("in_bed_dwell");
    }
    if matches!(estado, EstadoObservado::Desocupada) || room_state == Some("empty") {
        reglas.push("room_absence_dwell");
    }
    reglas
}

/// Que alertas corresponden a esta observacion.
///
/// `ya_avisadas` son las reglas que ya avisaron **en este episodio** —desde
/// `state_since`—, no las que tienen una alerta abierta. La diferencia importa:
/// un episodio avisa una sola vez por regla aunque alguien resuelva la alerta
/// mientras el residente sigue fuera de la cama.
pub fn evaluar(
    contexto: &Contexto,
    perfil: &PerfilEfectivo,
    ya_avisadas: &BTreeSet<String>,
    disparo: Disparo,
) -> Vec<AlertaNueva> {
    let transcurridos = (*contexto.occurred_at.as_datetime() - *contexto.state_since.as_datetime())
        .num_seconds()
        .max(0);

    let mut alertas = Vec::new();

    if disparo == Disparo::Evento {
        if let Some(rule_id) = transicion_entre(&contexto.estado_previo, &contexto.estado) {
            considerar(
                &mut alertas,
                contexto,
                perfil,
                ya_avisadas,
                rule_id,
                Clase::Transicion,
                transcurridos,
            );
        }
    }

    for rule_id in permanencias_candidatas(&contexto.estado, contexto.room_state.as_deref()) {
        considerar(
            &mut alertas,
            contexto,
            perfil,
            ya_avisadas,
            rule_id,
            Clase::Permanencia,
            transcurridos,
        );
    }

    alertas
}

fn considerar(
    alertas: &mut Vec<AlertaNueva>,
    contexto: &Contexto,
    perfil: &PerfilEfectivo,
    ya_avisadas: &BTreeSet<String>,
    rule_id: &str,
    clase: Clase,
    transcurridos: i64,
) {
    if !REGLAS_OPERATIVAS.contains(&rule_id) {
        return;
    }
    let Some(regla) = perfil.reglas.get(rule_id) else {
        return;
    };
    if regla.clase != clase {
        return;
    }
    let Some(nivel) = regla.accion_en(contexto.turno).nivel() else {
        return;
    };
    if transcurridos < regla.espera_segundos() {
        return;
    }
    if ya_avisadas.contains(rule_id) {
        return;
    }

    let detalle = match clase {
        Clase::Transicion => format!("Transicion observada · turno {}", contexto.turno.etiqueta()),
        Clase::Permanencia => format!(
            "Sostenido {} min · turno {}",
            (transcurridos as f64 / 60.0).round() as i64,
            contexto.turno.etiqueta()
        ),
    };

    alertas.push(AlertaNueva {
        bed_id: contexto.bed_id.clone(),
        resident_id: contexto.resident_id.clone(),
        rule_id: rule_id.to_owned(),
        clase,
        nivel,
        titulo: regla.etiqueta.clone(),
        detalle,
        evidence_kind: match clase {
            Clase::Transicion => TipoEvidencia::SensorEvent,
            Clase::Permanencia => TipoEvidencia::DwellWindow,
        },
        evidence_ref: match clase {
            Clase::Transicion => contexto.evidencia_ref.clone(),
            // Una permanencia no la trajo ningun evento: eso es lo que la hace
            // una permanencia. Atarla al ultimo evento seria mentir sobre su
            // origen.
            Clase::Permanencia => None,
        },
        occurred_at: contexto.occurred_at,
    });
}
