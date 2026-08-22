//! Seam de aplicacion para el motor de alarmas.
//!
//! La evaluacion pura vive en `mana-motores`. El lazo que hacia IO (resolver
//! cama y perfil, leer observacion, deduplicar y persistir en Vigilancia) se
//! mudo a `mana-engine`, que es el dueño del motor y del reloj. Este modulo solo
//! conserva el alias de la evaluacion pura.

/// Alias de compatibilidad para el seam de `mana-app`: la evaluacion pura vive
/// en `mana-motores`.
pub use mana_motores::alarmas as evaluacion;
