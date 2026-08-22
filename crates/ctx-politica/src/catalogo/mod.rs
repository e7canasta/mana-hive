//! Reexport de los tipos de valor del catalogo.
//!
//! El catalogo y su resolucion son puros y viven en `mana-motores`. Este modulo
//! conserva la frontera publica de `ctx-politica` para los consumidores que
//! trabajan con perfiles y persistencia.

pub use mana_motores::catalogo::*;
