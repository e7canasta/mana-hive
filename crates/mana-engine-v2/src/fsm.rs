use serde::{Deserialize, Serialize};

/// Estados de persona según el FSM de observación.
///
/// Agrupaciones:
/// - `in_bed` = {Lying, SittingInBed, BedEdge}
/// - `out_of_bed` = NOT(in_bed) && NOT(Unknown)
#[derive(Debug, Clone, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub enum PersonState {
    // En cama (in_bed)
    Lying,
    SittingInBed,
    BedEdge,
    
    // Fuera de cama (out_of_bed)
    Standing,
    InBathroom,
    InRoom,
    InHallway,
    Outdoor,
    
    // Muebles
    InChair,
    InWheelchair,
    
    // Desconocido
    Unknown,
}

impl PersonState {
    /// ¿El estado es "en cama"?
    pub fn is_in_bed(&self) -> bool {
        matches!(self, 
            PersonState::Lying | 
            PersonState::SittingInBed | 
            PersonState::BedEdge
        )
    }

    /// ¿El estado es "fuera de cama"?
    pub fn is_out_of_bed(&self) -> bool {
        !self.is_in_bed() && !matches!(self, PersonState::Unknown)
    }

    /// Parsea un string a PersonState.
    pub fn parse(s: &str) -> Self {
        match s.to_lowercase().as_str() {
            "lying" => PersonState::Lying,
            "sitting_in_bed" | "sitting" => PersonState::SittingInBed,
            "bed_edge" => PersonState::BedEdge,
            "standing" => PersonState::Standing,
            "in_bathroom" => PersonState::InBathroom,
            "in_room" => PersonState::InRoom,
            "in_hallway" => PersonState::InHallway,
            "outdoor" => PersonState::Outdoor,
            "in_chair" => PersonState::InChair,
            "in_wheelchair" => PersonState::InWheelchair,
            _ => PersonState::Unknown,
        }
    }

    /// Convierte a string para persistencia.
    pub fn as_str(&self) -> &'static str {
        match self {
            PersonState::Lying => "lying",
            PersonState::SittingInBed => "sitting_in_bed",
            PersonState::BedEdge => "bed_edge",
            PersonState::Standing => "standing",
            PersonState::InBathroom => "in_bathroom",
            PersonState::InRoom => "in_room",
            PersonState::InHallway => "in_hallway",
            PersonState::Outdoor => "outdoor",
            PersonState::InChair => "in_chair",
            PersonState::InWheelchair => "in_wheelchair",
            PersonState::Unknown => "unknown",
        }
    }
}

/// Transición de estado registrada por el FSM.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct FsmTransition {
    pub from: PersonState,
    pub to: PersonState,
    pub at: chrono::DateTime<chrono::Utc>,
    pub confidence: f64,
}

/// Tabla de transiciones válidas del FSM.
pub fn valid_transitions(from: &PersonState) -> Vec<PersonState> {
    match from {
        PersonState::Lying => vec![
            PersonState::SittingInBed,
            PersonState::BedEdge,
            PersonState::Standing,
            PersonState::InBathroom,
            PersonState::InRoom,
            PersonState::InChair,
            PersonState::InWheelchair,
            PersonState::Unknown,
        ],
        
        PersonState::SittingInBed => vec![
            PersonState::Lying,
            PersonState::BedEdge,
            PersonState::Standing,
            PersonState::InBathroom,
            PersonState::InRoom,
            PersonState::InChair,
            PersonState::InWheelchair,
            PersonState::Unknown,
        ],
        
        PersonState::BedEdge => vec![
            PersonState::Lying,
            PersonState::SittingInBed,
            PersonState::Standing,
            PersonState::InBathroom,
            PersonState::InRoom,
            PersonState::InChair,
            PersonState::InWheelchair,
            PersonState::Unknown,
        ],
        
        PersonState::Standing => vec![
            PersonState::Lying,
            PersonState::SittingInBed,
            PersonState::BedEdge,
            PersonState::InBathroom,
            PersonState::InRoom,
            PersonState::InHallway,
            PersonState::Outdoor,
            PersonState::InChair,
            PersonState::InWheelchair,
            PersonState::Unknown,
        ],
        
        PersonState::InBathroom => vec![
            PersonState::Lying,
            PersonState::SittingInBed,
            PersonState::BedEdge,
            PersonState::Standing,
            PersonState::InRoom,
            PersonState::InHallway,
            PersonState::Unknown,
        ],
        
        PersonState::InRoom => vec![
            PersonState::Lying,
            PersonState::SittingInBed,
            PersonState::BedEdge,
            PersonState::Standing,
            PersonState::InBathroom,
            PersonState::InHallway,
            PersonState::Outdoor,
            PersonState::InChair,
            PersonState::InWheelchair,
            PersonState::Unknown,
        ],
        
        PersonState::InHallway => vec![
            PersonState::Standing,
            PersonState::InBathroom,
            PersonState::InRoom,
            PersonState::Outdoor,
            PersonState::Unknown,
        ],
        
        PersonState::Outdoor => vec![
            PersonState::Standing,
            PersonState::InRoom,
            PersonState::InHallway,
            PersonState::Unknown,
        ],
        
        PersonState::InChair => vec![
            PersonState::Lying,
            PersonState::SittingInBed,
            PersonState::BedEdge,
            PersonState::Standing,
            PersonState::InRoom,
            PersonState::Unknown,
        ],
        
        PersonState::InWheelchair => vec![
            PersonState::Lying,
            PersonState::SittingInBed,
            PersonState::BedEdge,
            PersonState::Standing,
            PersonState::InRoom,
            PersonState::Unknown,
        ],
        
        PersonState::Unknown => vec![
            PersonState::Lying,
            PersonState::SittingInBed,
            PersonState::BedEdge,
            PersonState::Standing,
            PersonState::InBathroom,
            PersonState::InRoom,
            PersonState::InHallway,
            PersonState::Outdoor,
            PersonState::InChair,
            PersonState::InWheelchair,
        ],
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_in_bed_states() {
        assert!(PersonState::Lying.is_in_bed());
        assert!(PersonState::SittingInBed.is_in_bed());
        assert!(PersonState::BedEdge.is_in_bed());
        assert!(!PersonState::Standing.is_in_bed());
        assert!(!PersonState::Unknown.is_in_bed());
    }

    #[test]
    fn test_out_of_bed_states() {
        assert!(PersonState::Standing.is_out_of_bed());
        assert!(PersonState::InBathroom.is_out_of_bed());
        assert!(!PersonState::Lying.is_out_of_bed());
        assert!(!PersonState::Unknown.is_out_of_bed());
    }

    #[test]
    fn test_parse_roundtrip() {
        let states = vec![
            PersonState::Lying,
            PersonState::SittingInBed,
            PersonState::BedEdge,
            PersonState::Standing,
            PersonState::InBathroom,
            PersonState::InRoom,
            PersonState::InHallway,
            PersonState::Outdoor,
            PersonState::InChair,
            PersonState::InWheelchair,
            PersonState::Unknown,
        ];
        
        for state in states {
            let s = state.as_str();
            let parsed = PersonState::parse(s);
            assert_eq!(state, parsed, "Failed for {}", s);
        }
    }

    #[test]
    fn test_valid_transitions_from_unknown() {
        let transitions = valid_transitions(&PersonState::Unknown);
        assert_eq!(transitions.len(), 10); // All except Unknown itself
    }

    #[test]
    fn test_valid_transitions_from_lying() {
        let transitions = valid_transitions(&PersonState::Lying);
        assert!(transitions.contains(&PersonState::SittingInBed));
        assert!(transitions.contains(&PersonState::BedEdge));
        assert!(transitions.contains(&PersonState::Standing));
        assert!(!transitions.contains(&PersonState::Lying)); // Can't transition to same state
    }
}
