//! mana-engine-v2: FSM puro + Digital Twin para el sistema de observación.
//!
//! Este crate implementa:
//! - **DigitalTwin**: estructura en memoria que mantiene el estado de la escena
//! - **FSM**: máquina de estados finitos para transiciones de persona
//! - **SceneEvent**: eventos completos de escena (perception, dwell, transition, change)
//!
//! El engine es puro: no tiene IO directo.

pub mod digital_twin;
pub mod fsm;
pub mod scene_event;
pub mod perception;

pub use digital_twin::{DigitalTwin, DwellRules, Timer, TimerState, BedTwin};
pub use fsm::{PersonState, FsmTransition};
pub use scene_event::{SceneEvent, SceneEventCore, SceneEventType, TriggerInfo};
pub use perception::PerceptionEvent;
