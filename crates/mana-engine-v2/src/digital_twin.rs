use std::collections::HashMap;

use crate::{
    fsm::{PersonState, FsmTransition},
    perception::PerceptionEvent,
    scene_event::{
        BedOccupancy, BedState, ChairState, Location, ObjectOccupancy, ObjectPresence,
        PersonOfInterest, RoomOccupancy, RoomState, SceneEvent, TriggerInfo,
        WalkerState, WheelchairState,
    },
};

/// Estado de un timer.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum TimerState {
    Active,
    Fired,
    Cancelled,
}

/// Timer: existe, tiene estado, se evalúa en cada tick.
#[derive(Debug, Clone)]
pub struct Timer {
    pub rule_id: String,
    pub bed_id: String,
    pub state: TimerState,
    pub started_at: chrono::DateTime<chrono::Utc>,
    pub threshold_minutes: i32,
}

impl Timer {
    pub fn new(
        rule_id: String,
        bed_id: String,
        started_at: chrono::DateTime<chrono::Utc>,
        threshold_minutes: i32,
    ) -> Self {
        Self {
            rule_id,
            bed_id,
            state: TimerState::Active,
            started_at,
            threshold_minutes,
        }
    }

    /// Evalúa si el timer se disparó. Returns true si acaba de disparar.
    pub fn evaluate(&mut self, now: chrono::DateTime<chrono::Utc>) -> bool {
        if self.state != TimerState::Active {
            return false;
        }
        let elapsed = now.signed_duration_since(self.started_at);
        let threshold = chrono::Duration::minutes(self.threshold_minutes as i64);
        if elapsed >= threshold {
            self.state = TimerState::Fired;
            true
        } else {
            false
        }
    }

    pub fn cancel(&mut self) {
        if self.state == TimerState::Active {
            self.state = TimerState::Cancelled;
        }
    }

    pub fn elapsed_minutes(&self, now: chrono::DateTime<chrono::Utc>) -> i32 {
        now.signed_duration_since(self.started_at).num_minutes() as i32
    }
}

/// Reglas de dwell: umbrales por estado.
#[derive(Debug, Clone)]
pub struct DwellRules {
    pub out_of_bed: i32,
    pub in_bed: i32,
    pub standing: i32,
    pub in_bathroom: i32,
}

impl Default for DwellRules {
    fn default() -> Self {
        Self {
            out_of_bed: 10,
            in_bed: 300,
            standing: 5,
            in_bathroom: 30,
        }
    }
}

impl DwellRules {
    pub fn threshold_for_state(&self, state: &PersonState) -> Option<i32> {
        match state {
            PersonState::Lying | PersonState::SittingInBed | PersonState::BedEdge => {
                Some(self.in_bed)
            }
            PersonState::Standing => Some(self.standing),
            PersonState::InBathroom => Some(self.in_bathroom),
            PersonState::InRoom | PersonState::InHallway | PersonState::Outdoor => {
                Some(self.out_of_bed)
            }
            PersonState::InChair | PersonState::InWheelchair => Some(self.out_of_bed),
            PersonState::Unknown => None,
        }
    }
}

/// Digital twin: un PLC que evalúa el estado de las camas en cada scan.
#[derive(Debug, Clone)]
pub struct DigitalTwin {
    beds: HashMap<String, BedTwin>,
    dwell_rules: DwellRules,
}

// === Nested value objects ===

#[derive(Debug, Clone)]
pub struct PersonTwin {
    pub state: PersonState,
    pub state_since: chrono::DateTime<chrono::Utc>,
    pub location: Location,
    pub sleeping: Option<bool>,
    pub confidence: f64,
}

#[derive(Debug, Clone)]
pub struct ObjectStates {
    pub bed: BedOccupancy,
    pub chair: ObjectOccupancy,
    pub wheelchair: ObjectOccupancy,
    pub walker: ObjectPresence,
}

#[derive(Debug, Clone)]
pub struct RoomTwin {
    pub occupancy: RoomOccupancy,
    pub resident_count: i32,
    pub staff_count: i32,
    pub visitor_count: i32,
}

#[derive(Debug, Clone)]
pub struct Extremities {
    pub out_of_bed: bool,
    pub body_parts_out: Vec<String>,
}

/// Twin de una cama: tiene estado, objetos, y TIMERS.
#[derive(Debug, Clone)]
pub struct BedTwin {
    pub bed_id: String,
    pub resident_id: Option<String>,

    pub person: PersonTwin,
    pub objects: ObjectStates,
    pub room: RoomTwin,
    pub transitions: Vec<FsmTransition>,
    pub extremities: Extremities,
    pub timers: Vec<Timer>,
}

impl DigitalTwin {
    pub fn new() -> Self {
        Self {
            beds: HashMap::new(),
            dwell_rules: DwellRules::default(),
        }
    }

    pub fn with_dwell_rules(rules: DwellRules) -> Self {
        Self {
            beds: HashMap::new(),
            dwell_rules: rules,
        }
    }

    pub fn get_or_create_bed(&mut self, bed_id: &str) -> &mut BedTwin {
        self.beds
            .entry(bed_id.to_string())
            .or_insert_with(|| BedTwin::new(bed_id.to_string()))
    }

    pub fn get_bed(&self, bed_id: &str) -> Option<&BedTwin> {
        self.beds.get(bed_id)
    }

    pub fn dwell_rules(&self) -> &DwellRules {
        &self.dwell_rules
    }

    /// SCAN LOOP: evalúa timers de todos los beds.
    pub fn tick(&mut self, now: chrono::DateTime<chrono::Utc>) -> Vec<SceneEvent> {
        let mut outputs = vec![];
        let mut beds_to_clean: Vec<String> = vec![];

        for (bed_id, bed) in &mut self.beds {
            let mut fired_rules: Vec<(String, i32, i32)> = vec![];

            for timer in &mut bed.timers {
                if timer.state == TimerState::Active {
                    let elapsed = now.signed_duration_since(timer.started_at);
                    let threshold = chrono::Duration::minutes(timer.threshold_minutes as i64);
                    if elapsed >= threshold {
                        timer.state = TimerState::Fired;
                        fired_rules.push((
                            timer.rule_id.clone(),
                            timer.elapsed_minutes(now),
                            timer.threshold_minutes,
                        ));
                    }
                }
            }

            for (rule_id, duration, threshold) in fired_rules {
                outputs.push(SceneEvent {
                    event_type: crate::scene_event::SceneEventType::Dwell,
                    bed_id: bed.bed_id.clone(),
                    resident_id: bed.resident_id.clone(),
                    timestamp: now,
                    trace_id: None,
                    trigger: TriggerInfo::DwellCompleted {
                        rule_id,
                        duration_minutes: duration,
                        threshold_minutes: threshold,
                    },
                    poi: bed.to_poi(),
                    bed: bed.to_bed_state(),
                    chair: bed.to_chair_state(),
                    wheelchair: bed.to_wheelchair_state(),
                    walker: bed.to_walker_state(),
                    room: bed.to_room_state(),
                    accompanied_by: None,
                });
            }

            beds_to_clean.push(bed_id.clone());
        }

        // Limpiar timers disparados/cancelados
        for bed_id in beds_to_clean {
            if let Some(bed) = self.beds.get_mut(&bed_id) {
                bed.timers.retain(|t| t.state == TimerState::Active);
            }
        }

        outputs
    }

    /// Procesa un perception event.
    pub fn on_perception_event(&mut self, event: PerceptionEvent) -> Vec<SceneEvent> {
        let bed_id = match &event.bed_id {
            Some(id) => id.clone(),
            None => return vec![],
        };

        let dwell_rules = self.dwell_rules.clone();
        let bed = self.get_or_create_bed(&bed_id);
        let old_state = bed.person.state.clone();
        let new_state = event.map_state_to_person_state();
        let new_location = event.map_zone_to_location();
        let mut outputs = vec![];

        if old_state != new_state {
            bed.cancel_timers_for_state(&old_state);
            bed.person.state = new_state.clone();
            bed.person.state_since = event.occurred_at;
            bed.person.location = new_location.clone();
            bed.person.confidence = event.confidence;
            bed.start_timers_for_state(&new_state, &dwell_rules, event.occurred_at);

            bed.transitions.push(FsmTransition {
                from: old_state.clone(),
                to: new_state.clone(),
                at: event.occurred_at,
                confidence: event.confidence,
            });

            outputs.push(SceneEvent::transition(
                bed_id.clone(),
                event.resident_id.clone(),
                bed.to_poi(),
                old_state,
                new_state,
                bed.to_bed_state(),
                bed.to_chair_state(),
                bed.to_wheelchair_state(),
                bed.to_walker_state(),
                bed.to_room_state(),
                event.trace_id.clone(),
            ));
        }

        bed.update_objects(&event);
        bed.update_room(&event);

        bed.extremities.out_of_bed = event.extremities_out_of_bed.unwrap_or(false);
        bed.extremities.body_parts_out = event.body_parts_out.unwrap_or_default();

        if event.resident_id.is_some() {
            bed.resident_id = event.resident_id.clone();
        }

        outputs.push(SceneEvent::perception(
            bed_id,
            event.resident_id,
            bed.to_poi(),
            event.event_id,
            event.confidence,
            bed.to_bed_state(),
            bed.to_chair_state(),
            bed.to_wheelchair_state(),
            bed.to_walker_state(),
            bed.to_room_state(),
            event.trace_id,
        ));

        outputs
    }
}

impl BedTwin {
    pub fn new(bed_id: String) -> Self {
        Self {
            bed_id,
            resident_id: None,
            person: PersonTwin {
                state: PersonState::Unknown,
                state_since: chrono::Utc::now(),
                location: Location::Unknown,
                sleeping: None,
                confidence: 0.0,
            },
            objects: ObjectStates {
                bed: BedOccupancy::Unknown,
                chair: ObjectOccupancy::Unknown,
                wheelchair: ObjectOccupancy::Unknown,
                walker: ObjectPresence::Unknown,
            },
            room: RoomTwin {
                occupancy: RoomOccupancy::Empty,
                resident_count: 0,
                staff_count: 0,
                visitor_count: 0,
            },
            transitions: vec![],
            extremities: Extremities {
                out_of_bed: false,
                body_parts_out: vec![],
            },
            timers: vec![],
        }
    }

    /// Cancela timers que pertenecen al estado dado.
    pub fn cancel_timers_for_state(&mut self, state: &PersonState) {
        let state_str = format!("{:?}", state);
        for timer in &mut self.timers {
            if timer.rule_id.contains(&state_str) {
                timer.cancel();
            }
        }
    }

    /// Inicia timers para un nuevo estado.
    pub fn start_timers_for_state(
        &mut self,
        state: &PersonState,
        rules: &DwellRules,
        now: chrono::DateTime<chrono::Utc>,
    ) {
        if let Some(threshold) = rules.threshold_for_state(state) {
            let rule_id = format!("dwell_{:?}", state);
            self.timers
                .push(Timer::new(rule_id, self.bed_id.clone(), now, threshold));
        }
    }

    pub fn to_poi(&self) -> PersonOfInterest {
        PersonOfInterest {
            resident_id: self.resident_id.clone().unwrap_or_default(),
            state: self.person.state.clone(),
            state_since: self.person.state_since,
            location: self.person.location.clone(),
            sleeping: self.person.sleeping,
            confidence: self.person.confidence,
        }
    }

    pub fn to_bed_state(&self) -> BedState {
        BedState {
            occupancy: self.objects.bed.clone(),
        }
    }

    pub fn to_chair_state(&self) -> ChairState {
        ChairState {
            occupancy: self.objects.chair.clone(),
        }
    }

    pub fn to_wheelchair_state(&self) -> WheelchairState {
        WheelchairState {
            occupancy: self.objects.wheelchair.clone(),
        }
    }

    pub fn to_walker_state(&self) -> WalkerState {
        WalkerState {
            presence: self.objects.walker.clone(),
        }
    }

    pub fn to_room_state(&self) -> RoomState {
        RoomState {
            occupancy: self.room.occupancy.clone(),
            resident_count: self.room.resident_count,
            staff_count: self.room.staff_count,
            visitor_count: self.room.visitor_count,
        }
    }

    pub fn update_objects(&mut self, event: &PerceptionEvent) {
        if let Some(objects) = &event.objects {
            if let Some(bed) = objects.get("bed") {
                if let Some(occupancy) = bed.get("occupancy").and_then(|o| o.as_str()) {
                    self.objects.bed = match occupancy {
                        "occupied" => BedOccupancy::Occupied,
                        "empty" => BedOccupancy::Empty,
                        _ => BedOccupancy::Unknown,
                    };
                }
            }
            if let Some(chair) = objects.get("chair") {
                if let Some(occupancy) = chair.get("occupancy").and_then(|o| o.as_str()) {
                    self.objects.chair = match occupancy {
                        "occupied" => ObjectOccupancy::Occupied,
                        "empty" => ObjectOccupancy::Empty,
                        _ => ObjectOccupancy::Unknown,
                    };
                }
            }
            if let Some(wheelchair) = objects.get("wheelchair") {
                if let Some(occupancy) = wheelchair.get("occupancy").and_then(|o| o.as_str()) {
                    self.objects.wheelchair = match occupancy {
                        "occupied" => ObjectOccupancy::Occupied,
                        "empty" => ObjectOccupancy::Empty,
                        _ => ObjectOccupancy::Unknown,
                    };
                }
            }
            if let Some(walker) = objects.get("walker") {
                if let Some(presence) = walker.get("presence").and_then(|p| p.as_str()) {
                    self.objects.walker = match presence {
                        "present" => ObjectPresence::Present,
                        "absent" => ObjectPresence::Absent,
                        _ => ObjectPresence::Unknown,
                    };
                }
            }
        }
    }

    pub fn update_room(&mut self, event: &PerceptionEvent) {
        if let Some(objects) = &event.objects {
            if let Some(room) = objects.get("room") {
                if let Some(occupancy) = room.get("occupancy").and_then(|o| o.as_str()) {
                    self.room.occupancy = match occupancy {
                        "empty" => RoomOccupancy::Empty,
                        "resident" => RoomOccupancy::Resident,
                        "staff" => RoomOccupancy::Staff,
                        "resident_and_staff" => RoomOccupancy::ResidentAndStaff,
                        "resident_and_visitor" => RoomOccupancy::ResidentAndVisitor,
                        _ => RoomOccupancy::Empty,
                    };
                }
                if let Some(count) = room.get("resident_count").and_then(|c| c.as_i64()) {
                    self.room.resident_count = count as i32;
                }
                if let Some(count) = room.get("staff_count").and_then(|c| c.as_i64()) {
                    self.room.staff_count = count as i32;
                }
                if let Some(count) = room.get("visitor_count").and_then(|c| c.as_i64()) {
                    self.room.visitor_count = count as i32;
                }
            }
        }
    }
}

impl Default for DigitalTwin {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_new_twin_is_empty() {
        let twin = DigitalTwin::new();
        assert!(twin.beds.is_empty());
    }

    #[test]
    fn test_get_or_create_bed() {
        let mut twin = DigitalTwin::new();
        let bed = twin.get_or_create_bed("118-A");
        assert_eq!(bed.bed_id, "118-A");
        assert_eq!(bed.person.state, PersonState::Unknown);
    }

    #[test]
    fn test_perception_event_creates_bed() {
        let mut twin = DigitalTwin::new();
        let t0 = chrono::Utc::now();
        let event = PerceptionEvent {
            event_id: "evt-001".to_string(),
            trace_id: None,
            monitor_key: "mon-001".to_string(),
            bed_id: Some("118-A".to_string()),
            resident_id: Some("res-001".to_string()),
            state: Some("lying".to_string()),
            sleeping: Some(true),
            zone: Some("bed".to_string()),
            extremities_out_of_bed: Some(false),
            body_parts_out: None,
            objects: None,
            room: None,
            confidence: 0.95,
            occurred_at: t0,
        };

        let outputs = twin.on_perception_event(event);
        assert_eq!(outputs.len(), 2); // Transition + Perception

        let bed = twin.get_bed("118-A").unwrap();
        assert_eq!(bed.person.state, PersonState::Lying);
        assert_eq!(bed.resident_id, Some("res-001".to_string()));
    }

    #[test]
    fn test_timer_lifecycle() {
        let mut timer = Timer::new(
            "test".to_string(),
            "118-A".to_string(),
            chrono::Utc::now(),
            10,
        );
        assert_eq!(timer.state, TimerState::Active);

        // Todavía no se disparó
        let just_now = chrono::Utc::now();
        assert!(!timer.evaluate(just_now));

        // Después de 10 minutos sí
        let later = chrono::Utc::now() + chrono::Duration::minutes(11);
        assert!(timer.evaluate(later));
        assert_eq!(timer.state, TimerState::Fired);
    }

    #[test]
    fn test_cancel_timer() {
        let mut timer = Timer::new(
            "test".to_string(),
            "118-A".to_string(),
            chrono::Utc::now(),
            10,
        );
        timer.cancel();
        assert_eq!(timer.state, TimerState::Cancelled);

        // No se evalúa si está cancelado
        let later = chrono::Utc::now() + chrono::Duration::minutes(11);
        assert!(!timer.evaluate(later));
    }

    #[test]
    fn test_tick_fires_dwell() {
        let mut twin = DigitalTwin::new();

        // Llega evento lying a las 02:00
        let t0 = chrono::Utc::now();
        let event = PerceptionEvent {
            event_id: "evt-001".to_string(),
            trace_id: None,
            monitor_key: "mon-001".to_string(),
            bed_id: Some("118-A".to_string()),
            resident_id: Some("res-001".to_string()),
            state: Some("lying".to_string()),
            sleeping: Some(true),
            zone: Some("bed".to_string()),
            extremities_out_of_bed: Some(false),
            body_parts_out: None,
            objects: None,
            room: None,
            confidence: 0.95,
            occurred_at: t0,
        };
        twin.on_perception_event(event);

        // Tick inmediato: no debería disparar (0 min < 300 min)
        let tick1 = twin.tick(t0 + chrono::Duration::minutes(5));
        assert_eq!(tick1.len(), 0);

        // Tick después de 5 horas: debería disparar
        let tick2 = twin.tick(t0 + chrono::Duration::hours(5));
        assert_eq!(tick2.len(), 1);
        assert_eq!(tick2[0].event_type, crate::scene_event::SceneEventType::Dwell);

        // Tick siguiente: no debería disparar de nuevo (deduplicación)
        let tick3 = twin.tick(t0 + chrono::Duration::hours(5) + chrono::Duration::minutes(1));
        assert_eq!(tick3.len(), 0);
    }

    #[test]
    fn test_transition_cancels_old_timers() {
        let mut twin = DigitalTwin::new();

        // Llega evento lying
        let t0 = chrono::Utc::now();
        let event = PerceptionEvent {
            event_id: "evt-001".to_string(),
            trace_id: None,
            monitor_key: "mon-001".to_string(),
            bed_id: Some("118-A".to_string()),
            resident_id: Some("res-001".to_string()),
            state: Some("lying".to_string()),
            sleeping: Some(true),
            zone: Some("bed".to_string()),
            extremities_out_of_bed: Some(false),
            body_parts_out: None,
            objects: None,
            room: None,
            confidence: 0.95,
            occurred_at: t0,
        };
        twin.on_perception_event(event);

        // Timer lying debería estar activo
        let bed = twin.get_bed("118-A").unwrap();
        assert_eq!(bed.timers.len(), 1);
        assert_eq!(bed.timers[0].state, TimerState::Active);

        // Transición a standing
        let event2 = PerceptionEvent {
            event_id: "evt-002".to_string(),
            trace_id: None,
            monitor_key: "mon-001".to_string(),
            bed_id: Some("118-A".to_string()),
            resident_id: Some("res-001".to_string()),
            state: Some("standing".to_string()),
            sleeping: Some(false),
            zone: Some("bed".to_string()),
            extremities_out_of_bed: Some(false),
            body_parts_out: None,
            objects: None,
            room: None,
            confidence: 0.95,
            occurred_at: t0 + chrono::Duration::minutes(1),
        };
        twin.on_perception_event(event2);

        // Timer lying debería estar cancelado, standing activo
        let bed = twin.get_bed("118-A").unwrap();
        assert_eq!(bed.timers.len(), 2);
        assert_eq!(bed.timers[0].state, TimerState::Cancelled); // lying
        assert_eq!(bed.timers[1].state, TimerState::Active);    // standing
    }
}
