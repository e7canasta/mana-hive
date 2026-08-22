//! El motor de recomendacion: que nivel de alarma propone la evidencia.
//!
//! **Propone un nivel de alarma. No infiere un diagnostico ni cierra un caso.**
//! La distincion no es retorica: lo que sale de aca alimenta una sugerencia que
//! un humano acepta o ignora, y el dia que se lea como un juicio clinico va a
//! estar mal usado.
//!
//! La division con Node es la que importa. Alla los umbrales, los pesos y los
//! cortes de nivel vivian dentro de `predictRiskLevel`, asi que subir la
//! exigencia de una señal era un deploy. Aca la politica es dato —el bloque
//! `[recomendacion]` del catalogo— y el mecanismo es esto: aplicar reglas,
//! sumar y cortar.
//!
//! Y es **puro**. Juntar las señales cruza Observacion, Historia y Poblacion, y
//! ese cruce es de `mana-app`; aca entran ya juntas.

use serde::Deserialize;
use std::collections::BTreeMap;

use crate::catalogo::{MobilityAid, RiskLevel};

/// Las señales observadas de un residente en la ventana de analisis.
///
/// **`None` no es cero.** Una noche sin velocidad de marcha observada queda
/// fuera del promedio en vez de entrar como cero y castigar el perfil; un
/// dominio que nadie observo no cuenta como "todo bien". Es la misma regla que
/// gobierna `sleeping` en la proyeccion y no se negocia por conveniencia.
#[derive(Debug, Clone, Default, PartialEq)]
pub struct Senales {
    pub nights_observed: i64,
    pub days_observed: i64,
    pub bathroom_days_observed: i64,
    pub bed_exits_per_night: Option<f64>,
    pub wakes_per_night: Option<f64>,
    pub awake_minutes_per_night: Option<f64>,
    pub bathroom_visits_per_day: Option<f64>,
    pub walking_speed_mps: Option<f64>,
    pub transfers_per_day: Option<f64>,
    pub alerts_per_day: Option<f64>,
    pub falls: i64,
    pub severe_falls: i64,
    pub transfer_incidents: i64,
}

impl Senales {
    fn numerica(&self, nombre: &str) -> Option<f64> {
        match nombre {
            "bed_exits_per_night" => self.bed_exits_per_night,
            "wakes_per_night" => self.wakes_per_night,
            "awake_minutes_per_night" => self.awake_minutes_per_night,
            "bathroom_visits_per_day" => self.bathroom_visits_per_day,
            "walking_speed_mps" => self.walking_speed_mps,
            "transfers_per_day" => self.transfers_per_day,
            "alerts_per_day" => self.alerts_per_day,
            // Una señal que la politica nombra y el motor no conoce **no** vale
            // cero: vale "no observada", y la regla no se evalua. Rellenarla
            // seria puntuar sobre algo que nadie midio.
            _ => None,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum Direccion {
    /// La señal preocupa cuando **sube**: salidas de cama, despertares.
    AlMenos,
    /// La señal preocupa cuando **baja**: velocidad de marcha.
    ComoMucho,
}

#[derive(Debug, Clone, Deserialize)]
pub struct Banda {
    pub umbral: f64,
    pub puntos: i32,
}

/// Una regla de puntaje. El `tipo` decide como se lee el resto.
#[derive(Debug, Clone, Deserialize)]
#[serde(tag = "tipo", rename_all = "snake_case")]
pub enum ReglaDeRiesgo {
    /// Bandas sobre una señal numerica. La primera que se cumple gana: las
    /// bandas se ordenan de la mas exigente a la menos.
    Banda {
        factor: String,
        senal: String,
        direccion: Direccion,
        #[serde(default)]
        unidad: String,
        #[serde(default)]
        decimales: usize,
        /// Accesorios para los que la regla **no** aplica.
        #[serde(default)]
        sin_accesorio: Vec<MobilityAid>,
        bandas: Vec<Banda>,
    },
    /// Historial de incidentes en la ventana larga.
    Incidentes {
        factor: String,
        kinds: Vec<String>,
        puntos_con_caida: i32,
        puntos_sin_caida: i32,
        puntos_si_grave: i32,
        #[serde(default)]
        severidades_graves: Vec<String>,
    },
    /// Un atributo declarado del residente.
    Rasgo {
        factor: String,
        code: String,
        puntos: i32,
        detalle: String,
    },
    /// El accesorio de movilidad declarado. El factor es el accesorio mismo.
    Accesorio { puntos: i32, detalle: String },
}

/// La politica de recomendacion: ventanas, cortes y reglas. Todo dato.
#[derive(Debug, Clone, Deserialize)]
pub struct PoliticaDeRecomendacion {
    #[serde(default = "ventana_por_defecto")]
    pub window_days: i64,
    #[serde(default = "ventana_de_incidentes_por_defecto")]
    pub incident_window_days: i64,
    #[serde(default)]
    pub medium_at: i32,
    #[serde(default)]
    pub high_at: i32,
    #[serde(default)]
    pub reglas: Vec<ReglaDeRiesgo>,
}

fn ventana_por_defecto() -> i64 {
    14
}

fn ventana_de_incidentes_por_defecto() -> i64 {
    90
}

impl Default for PoliticaDeRecomendacion {
    fn default() -> Self {
        Self {
            window_days: ventana_por_defecto(),
            incident_window_days: ventana_de_incidentes_por_defecto(),
            medium_at: 3,
            high_at: 6,
            reglas: Vec::new(),
        }
    }
}

impl PoliticaDeRecomendacion {
    /// El corte de nivel. Es lo unico que traduce un puntaje en una propuesta.
    fn nivel_para(&self, puntaje: i32) -> RiskLevel {
        if puntaje >= self.high_at {
            RiskLevel::High
        } else if puntaje >= self.medium_at {
            RiskLevel::Medium
        } else {
            RiskLevel::Low
        }
    }
}

/// Un factor que sumo, con cuanto y por que.
///
/// El `detalle` no es decoracion: es lo que hace revisable la recomendacion.
/// Un numero sin su razon no se puede discutir, y una sugerencia clinica que no
/// se puede discutir no se deberia poder aceptar.
#[derive(Debug, Clone, PartialEq)]
pub struct FactorDeRiesgo {
    pub id: String,
    pub weight: i32,
    pub detail: String,
}

#[derive(Debug, Clone, PartialEq)]
pub struct Recomendacion {
    pub level: RiskLevel,
    pub score: i32,
    pub factors: Vec<FactorDeRiesgo>,
    /// Cuantas reglas encontraron su señal. **Es la medida de confianza**: un
    /// nivel bajo con cero señales evaluadas no dice "esta bien", dice "no
    /// sabemos", y quien lo lea tiene que poder distinguirlo.
    pub signals_evaluated: i32,
    pub suggested_template: String,
}

/// La plantilla sugerida sale del perfil operativo declarado, no del historial:
/// es una recomendacion de configuracion, no un diagnostico.
pub fn plantilla_sugerida(rasgos: &[String], templates: &[crate::catalogo::Template]) -> String {
    templates
        .iter()
        .find(|t| {
            !t.recommended_for.is_empty() && t.recommended_for.iter().any(|r| rasgos.contains(r))
        })
        .map(|t| t.id.clone())
        .unwrap_or_else(|| "balanced".to_owned())
}

/// Recomienda un nivel a partir de las señales, el accesorio y los rasgos.
///
/// Puro: no va a buscar nada. Las señales llegan ya juntas porque juntarlas
/// cruza tres contextos y ese cruce es de `mana-app`.
pub fn recomendar(
    senales: &Senales,
    accesorio: MobilityAid,
    rasgos: &[String],
    politica: &PoliticaDeRecomendacion,
    templates: &[crate::catalogo::Template],
) -> Recomendacion {
    let mut acumulado: BTreeMap<String, FactorDeRiesgo> = BTreeMap::new();
    let mut orden: Vec<String> = Vec::new();
    let mut score = 0;
    let mut evaluadas = 0;

    // Un mismo factor puede llegar por dos vias —un incidente y el perfil
    // declarado—. Se acumula en una sola entrada para no repetir el chip.
    let mut sumar = |factor: &str, puntos: i32, detalle: String, score: &mut i32| {
        *score += puntos;
        match acumulado.get_mut(factor) {
            Some(existente) => {
                existente.weight += puntos;
                existente.detail = format!("{} · {}", existente.detail, detalle);
            }
            None => {
                orden.push(factor.to_owned());
                acumulado.insert(
                    factor.to_owned(),
                    FactorDeRiesgo {
                        id: factor.to_owned(),
                        weight: puntos,
                        detail: detalle,
                    },
                );
            }
        }
    };

    for regla in &politica.reglas {
        match regla {
            ReglaDeRiesgo::Incidentes {
                factor,
                kinds,
                puntos_con_caida,
                puntos_sin_caida,
                puntos_si_grave,
                ..
            } => {
                let caidas = if kinds.iter().any(|k| k == "fall") {
                    senales.falls
                } else {
                    0
                };
                let transferencias = if kinds.iter().any(|k| k == "transfer") {
                    senales.transfer_incidents
                } else {
                    0
                };
                let eventos = caidas + transferencias;
                if eventos == 0 {
                    continue;
                }
                evaluadas += 1;
                let puntos = if caidas > 0 {
                    *puntos_con_caida
                } else {
                    *puntos_sin_caida
                } + if senales.severe_falls > 0 {
                    *puntos_si_grave
                } else {
                    0
                };
                let plural = if eventos == 1 { "" } else { "s" };
                sumar(
                    factor,
                    puntos,
                    format!(
                        "{eventos} evento{plural} en {} dias",
                        politica.incident_window_days
                    ),
                    &mut score,
                );
            }

            ReglaDeRiesgo::Banda {
                factor,
                senal,
                direccion,
                unidad,
                decimales,
                sin_accesorio,
                bandas,
            } => {
                if sin_accesorio.contains(&accesorio) {
                    continue;
                }
                let Some(valor) = senales.numerica(senal) else {
                    continue;
                };
                evaluadas += 1;
                let banda = bandas.iter().find(|banda| match direccion {
                    Direccion::AlMenos => valor >= banda.umbral,
                    Direccion::ComoMucho => valor < banda.umbral,
                });
                if let Some(banda) = banda {
                    let detalle = if unidad.is_empty() {
                        format!("{valor:.*}", *decimales)
                    } else {
                        format!("{valor:.*} {unidad}", *decimales)
                    };
                    sumar(factor, banda.puntos, detalle, &mut score);
                }
            }

            ReglaDeRiesgo::Rasgo {
                factor,
                code,
                puntos,
                detalle,
            } => {
                if !rasgos.iter().any(|r| r == code) {
                    continue;
                }
                evaluadas += 1;
                sumar(factor, *puntos, detalle.clone(), &mut score);
            }

            ReglaDeRiesgo::Accesorio { puntos, detalle } => {
                if accesorio == MobilityAid::None {
                    continue;
                }
                evaluadas += 1;
                sumar(accesorio.as_str(), *puntos, detalle.clone(), &mut score);
            }
        }
    }

    let factors = orden
        .into_iter()
        .filter_map(|id| acumulado.remove(&id))
        .collect();

    Recomendacion {
        level: politica.nivel_para(score),
        score,
        factors,
        signals_evaluated: evaluadas,
        suggested_template: plantilla_sugerida(rasgos, templates),
    }
}

#[cfg(test)]
mod tests;
