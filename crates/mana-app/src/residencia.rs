use ctx_identidad::IdentityStore;
use ctx_residencia::{
    new_bed_id, new_facility_id, new_room_id, new_wing_id, Bed, BedInput, BedUpdate, Facility,
    FacilityId, FacilityInput, FacilityUpdate, PlanogramPlacementInput, PrivacyRegionInput,
    ResidenceStore, Room, RoomId, RoomInput, RoomUpdate, Wing, WingId, WingInput, WingUpdate,
};
use mana_kernel::Fallo;
use serde_json::json;

use crate::{
    error::AppFailure,
    identidad::{
        actor_id, authenticated_actor, authenticated_actor_in_transaction, require_capability,
        required_token,
    },
    state::{AppState, Stores},
};

#[derive(Clone, Debug)]
pub struct CreateFacilityCommand {
    pub name: String,
    pub timezone: String,
}

#[derive(Clone, Debug, Default)]
pub struct UpdateFacilityCommand {
    pub name: Option<String>,
    pub timezone: Option<String>,
}

#[derive(Clone, Debug)]
pub struct CreateWingCommand {
    pub name: String,
    pub floor: String,
    pub sort_order: Option<i32>,
}

#[derive(Clone, Debug, Default)]
pub struct UpdateWingCommand {
    pub name: Option<String>,
    pub floor: Option<String>,
    pub sort_order: Option<i32>,
}

#[derive(Clone, Debug)]
pub struct CreateRoomCommand {
    pub number: String,
    pub room_type: Option<String>,
    pub stream_key: Option<String>,
}

#[derive(Clone, Debug, Default)]
pub struct UpdateRoomCommand {
    pub number: Option<String>,
    pub room_type: Option<String>,
    pub stream_key: Option<Option<String>>,
}

#[derive(Clone, Debug)]
pub struct BedCommand {
    pub label: String,
    pub monitor_key: Option<String>,
}

#[derive(Clone, Debug, Default)]
pub struct UpdateBedCommand {
    pub label: Option<String>,
    pub monitor_key: Option<Option<String>>,
}

#[derive(Clone, Debug)]
pub struct PlanogramPlacementCommand {
    pub room_id: String,
    pub x: f64,
    pub y: f64,
    pub sort_order: i32,
}

#[derive(Clone, Debug)]
pub struct SavePlanogramCommand {
    pub placements: Vec<PlanogramPlacementCommand>,
}

#[derive(Clone, Debug)]
pub struct PrivacyRegionCommand {
    pub x: f64,
    pub y: f64,
    pub w: f64,
    pub h: f64,
}

#[derive(Clone, Debug)]
pub struct SavePrivacyRegionsCommand {
    pub regions: Vec<PrivacyRegionCommand>,
}

#[derive(Clone, Debug)]
pub struct FacilityView {
    pub id: String,
    pub name: String,
    pub timezone: String,
}

#[derive(Clone, Debug)]
pub struct FacilityDetailView {
    pub id: String,
    pub name: String,
    pub timezone: String,
    pub wings: Vec<WingView>,
}

#[derive(Clone, Debug)]
pub struct TreeResidentView {
    pub id: String,
    pub name: String,
}

#[derive(Clone, Debug)]
pub struct TreeBedView {
    pub id: String,
    pub label: String,
    pub monitor_key: Option<String>,
    pub resident: Option<TreeResidentView>,
}

#[derive(Clone, Debug)]
pub struct TreeRegionView {
    pub id: String,
    pub region_type: String,
    pub points: Vec<(f64, f64)>,
    pub label: Option<String>,
    pub is_static: bool,
}

#[derive(Clone, Debug)]
pub struct TreeStreamView {
    pub id: String,
    pub stream_key: String,
    pub name: Option<String>,
    pub regions: Vec<TreeRegionView>,
}

#[derive(Clone, Debug)]
pub struct TreeRoomView {
    pub id: String,
    pub number: String,
    pub room_type: String,
    pub stream_key: Option<String>,
    pub beds: Vec<TreeBedView>,
    pub streams: Vec<TreeStreamView>,
}

#[derive(Clone, Debug)]
pub struct TreeWingView {
    pub id: String,
    pub name: String,
    pub floor: String,
    pub sort_order: i32,
    pub rooms: Vec<TreeRoomView>,
}

#[derive(Clone, Debug)]
pub struct FacilityTreeView {
    pub id: String,
    pub name: String,
    pub timezone: String,
    pub wings: Vec<TreeWingView>,
}

#[derive(Clone, Debug)]
pub struct WingView {
    pub id: String,
    pub facility_id: String,
    pub name: String,
    pub floor: String,
    pub sort_order: i32,
    pub bed_count: Option<i32>,
}

#[derive(Clone, Debug)]
pub struct RoomView {
    pub id: String,
    pub wing_id: String,
    pub number: String,
    pub room_type: String,
    pub stream_key: Option<String>,
}

#[derive(Clone, Debug)]
pub struct BedView {
    pub id: String,
    pub room_id: String,
    pub label: String,
    pub monitor_key: Option<String>,
}

#[derive(Clone, Debug)]
pub struct ResidenceBedView {
    pub id: String,
    pub room_id: String,
    pub label: String,
    pub monitor_key: Option<String>,
    pub room_number: String,
    pub room_type: String,
    pub stream_key: Option<String>,
    pub wing_id: String,
    pub wing_name: String,
    pub wing_floor: String,
}

#[derive(Clone, Debug)]
pub struct PlanogramPlacementView {
    pub id: String,
    pub wing_id: String,
    pub room_id: String,
    pub x: f64,
    pub y: f64,
    pub sort_order: i32,
    pub room_number: String,
    pub room_type: String,
    pub stream_key: Option<String>,
}

#[derive(Clone, Debug)]
pub struct PrivacyRegionView {
    pub x: f64,
    pub y: f64,
    pub w: f64,
    pub h: f64,
}

impl AppState {
    pub async fn list_facilities(&self, token: &str) -> Result<Vec<FacilityView>, AppFailure> {
        let token = required_token(token)?;
        let enabled = self.enabled_capabilities.clone();
        run_residence_blocking(
            self.identity.clone(),
            self.residence.clone(),
            move |identity, residence| {
                let actor = authenticated_actor(&identity, &token, &enabled)?;
                require_capability(&actor, "master.structure.read")?;
                residence
                    .list_facilities()
                    .map_err(AppFailure::from)
                    .map(|facilities| facilities.into_iter().map(facility_view).collect())
            },
        )
        .await
    }

    pub async fn facility_detail(
        &self,
        token: &str,
        facility_id: &str,
    ) -> Result<FacilityDetailView, AppFailure> {
        let token = required_token(token)?;
        let facility_id = required_id(facility_id)?;
        let enabled = self.enabled_capabilities.clone();
        run_residence_blocking(
            self.identity.clone(),
            self.residence.clone(),
            move |identity, residence| {
                let actor = authenticated_actor(&identity, &token, &enabled)?;
                require_capability(&actor, "master.structure.read")?;
                let facility_id = FacilityId::new(facility_id);
                let facility = residence
                    .get_facility(&facility_id)
                    .map_err(AppFailure::from)?;
                let wings = residence
                    .list_wings(&facility_id)
                    .map_err(AppFailure::from)?
                    .into_iter()
                    .map(|wing| wing_view(wing, None))
                    .collect();
                Ok(FacilityDetailView {
                    id: facility.id.into_string(),
                    name: facility.name,
                    timezone: facility.timezone,
                    wings,
                })
            },
        )
        .await
    }

    pub async fn facility_tree(
        &self,
        token: &str,
        facility_id: &str,
    ) -> Result<FacilityTreeView, AppFailure> {
        let token = required_token(token)?;
        let facility_id = required_id(facility_id)?;
        let enabled = self.enabled_capabilities.clone();
        run_tree_blocking(
            self.identity.clone(),
            self.residence.clone(),
            self.poblacion.clone(),
            self.streams.clone(),
            move |identity, residence, poblacion, streams| {
                let actor = authenticated_actor(&identity, &token, &enabled)?;
                require_capability(&actor, "master.structure.read")?;
                let facility_id = FacilityId::new(facility_id);
                let tree = residence
                    .facility_tree(&facility_id)
                    .map_err(AppFailure::from)?;

                let open = poblacion
                    .list_open_assignments()
                    .map_err(AppFailure::from)?;

                use std::collections::HashMap;
                let mut resident_by_bed: HashMap<String, (String, String)> = HashMap::new();
                for assignment in &open {
                    if assignment.ends_at.is_none() {
                        if let Ok(res) = poblacion.get_resident(&assignment.resident_id) {
                            resident_by_bed.insert(
                                assignment.bed_id.as_str().to_owned(),
                                (res.id.into_string(), res.full_name),
                            );
                        }
                    }
                }

                Ok(FacilityTreeView {
                    id: tree.id,
                    name: tree.name,
                    timezone: tree.timezone,
                    wings: tree
                        .wings
                        .into_iter()
                        .map(|w| TreeWingView {
                            id: w.id,
                            name: w.name,
                            floor: w.floor,
                            sort_order: w.sort_order,
                            rooms: w
                                .rooms
                                .into_iter()
                                .map(|r| {
                                    let room_streams = streams
                                        .list_streams(&r.id)
                                        .unwrap_or_default()
                                        .into_iter()
                                        .map(|s| {
                                            let regions = streams
                                                .list_regions(&s.id.to_string())
                                                .unwrap_or_default()
                                                .into_iter()
                                                .map(|reg| TreeRegionView {
                                                    id: reg.id.to_string(),
                                                    region_type: reg.region_type.to_string(),
                                                    points: reg.points,
                                                    label: reg.label,
                                                    is_static: reg.is_static,
                                                })
                                                .collect();
                                            TreeStreamView {
                                                id: s.id.to_string(),
                                                stream_key: s.stream_key,
                                                name: s.name,
                                                regions,
                                            }
                                        })
                                        .collect();
                                    TreeRoomView {
                                        id: r.id,
                                        number: r.number,
                                        room_type: r.room_type,
                                        stream_key: r.stream_key,
                                        beds: r
                                            .beds
                                            .into_iter()
                                            .map(|b| {
                                                let resident = resident_by_bed
                                                    .get(&b.id)
                                                    .map(|(id, name)| TreeResidentView {
                                                        id: id.clone(),
                                                        name: name.clone(),
                                                    });
                                                TreeBedView {
                                                    id: b.id,
                                                    label: b.label,
                                                    monitor_key: b.monitor_key,
                                                    resident,
                                                }
                                            })
                                            .collect(),
                                        streams: room_streams,
                                    }
                                })
                                .collect(),
                        })
                        .collect(),
                })
            },
        )
        .await
    }

    pub async fn list_wings(&self, token: &str) -> Result<Vec<WingView>, AppFailure> {
        let token = required_token(token)?;
        let enabled = self.enabled_capabilities.clone();
        run_residence_blocking(
            self.identity.clone(),
            self.residence.clone(),
            move |identity, residence| {
                let actor = authenticated_actor(&identity, &token, &enabled)?;
                require_capability(&actor, "master.structure.read")?;
                residence
                    .list_wings_overview()
                    .map_err(AppFailure::from)
                    .map(|wings| {
                        wings
                            .into_iter()
                            .map(|(wing, bed_count)| wing_view(wing, Some(bed_count as i32)))
                            .collect()
                    })
            },
        )
        .await
    }

    pub async fn list_residence_beds(
        &self,
        token: &str,
    ) -> Result<Vec<ResidenceBedView>, AppFailure> {
        let token = required_token(token)?;
        let enabled = self.enabled_capabilities.clone();
        run_residence_blocking(
            self.identity.clone(),
            self.residence.clone(),
            move |identity, residence| {
                let actor = authenticated_actor(&identity, &token, &enabled)?;
                require_capability(&actor, "master.structure.read")?;
                residence
                    .list_beds_all()
                    .map_err(AppFailure::from)
                    .map(|beds| beds.into_iter().map(residence_bed_view).collect())
            },
        )
        .await
    }

    pub async fn planogram(
        &self,
        token: &str,
        wing_id: &str,
    ) -> Result<Vec<PlanogramPlacementView>, AppFailure> {
        let token = required_token(token)?;
        let wing_id = required_id(wing_id)?;
        let enabled = self.enabled_capabilities.clone();
        run_residence_blocking(
            self.identity.clone(),
            self.residence.clone(),
            move |identity, residence| {
                let actor = authenticated_actor(&identity, &token, &enabled)?;
                require_capability(&actor, "master.structure.read")?;
                let wing_id = WingId::new(wing_id);
                residence
                    .planogram(&wing_id)
                    .map_err(AppFailure::from)
                    .map(|entries| entries.into_iter().map(planogram_view).collect())
            },
        )
        .await
    }

    pub async fn save_planogram(
        &self,
        token: &str,
        wing_id: &str,
        command: SavePlanogramCommand,
    ) -> Result<Vec<PlanogramPlacementView>, AppFailure> {
        let token = required_token(token)?;
        let wing_id = required_id(wing_id)?;
        let enabled = self.enabled_capabilities.clone();
        self.transaction(move |connection, stores| {
            let Stores {
                identity,
                audit,
                residence,
                ..
            } = stores;
            let actor = authenticated_actor_in_transaction(identity, connection, &token, &enabled)?;
            require_capability(&actor, "master.structure.write")?;
            let wing_id = WingId::new(wing_id);
            let inputs = command
                .placements
                .into_iter()
                .map(|placement| PlanogramPlacementInput {
                    room_id: RoomId::new(placement.room_id),
                    x: placement.x,
                    y: placement.y,
                    sort_order: placement.sort_order,
                })
                .collect();
            let saved = residence.save_planogram_in_transaction(
                connection,
                &wing_id,
                inputs,
                mana_kernel::Instante::now(),
            )?;
            let record = ctx_auditoria::AuditRecord::new(
                Some(actor_id(&actor)),
                "planogram.updated",
                "wing",
                wing_id.as_str(),
                json!({"placements": saved.len()}),
            )?;
            audit.record_in_transaction(connection, record)?;
            Ok(saved.into_iter().map(planogram_view).collect())
        })
        .await
    }

    pub async fn privacy_regions(
        &self,
        token: &str,
        room_id: &str,
    ) -> Result<Vec<PrivacyRegionView>, AppFailure> {
        let token = required_token(token)?;
        let room_id = required_id(room_id)?;
        let enabled = self.enabled_capabilities.clone();
        run_residence_blocking(
            self.identity.clone(),
            self.residence.clone(),
            move |identity, residence| {
                let actor = authenticated_actor(&identity, &token, &enabled)?;
                require_capability(&actor, "master.structure.read")?;
                let room_id = RoomId::new(room_id);
                residence
                    .privacy_regions(&room_id)
                    .map_err(AppFailure::from)
                    .map(|regions| regions.into_iter().map(privacy_region_view).collect())
            },
        )
        .await
    }

    pub async fn save_privacy_regions(
        &self,
        token: &str,
        room_id: &str,
        command: SavePrivacyRegionsCommand,
    ) -> Result<Vec<PrivacyRegionView>, AppFailure> {
        let token = required_token(token)?;
        let room_id = required_id(room_id)?;
        let enabled = self.enabled_capabilities.clone();
        self.transaction(move |connection, stores| {
            let Stores {
                identity,
                audit,
                residence,
                ..
            } = stores;
            let actor = authenticated_actor_in_transaction(identity, connection, &token, &enabled)?;
            require_capability(&actor, "master.structure.write")?;
            let room_id = RoomId::new(room_id);
            let inputs = command
                .regions
                .into_iter()
                .map(|region| PrivacyRegionInput {
                    x: region.x,
                    y: region.y,
                    w: region.w,
                    h: region.h,
                })
                .collect();
            let saved = residence.save_privacy_regions_in_transaction(
                connection,
                &room_id,
                inputs,
                mana_kernel::Instante::now(),
            )?;
            let record = ctx_auditoria::AuditRecord::new(
                Some(actor_id(&actor)),
                "room.privacy_regions.updated",
                "room",
                room_id.as_str(),
                json!({"regions": saved.len()}),
            )?;
            audit.record_in_transaction(connection, record)?;
            Ok(saved.into_iter().map(privacy_region_view).collect())
        })
        .await
    }

    pub async fn list_rooms(
        &self,
        token: &str,
        wing_id: &str,
    ) -> Result<Vec<RoomView>, AppFailure> {
        let token = required_token(token)?;
        let wing_id = required_id(wing_id)?;
        let enabled = self.enabled_capabilities.clone();
        run_residence_blocking(
            self.identity.clone(),
            self.residence.clone(),
            move |identity, residence| {
                let actor = authenticated_actor(&identity, &token, &enabled)?;
                require_capability(&actor, "master.structure.read")?;
                residence
                    .list_rooms(&ctx_residencia::WingId::new(wing_id))
                    .map_err(AppFailure::from)
                    .map(|rooms| rooms.into_iter().map(room_view).collect())
            },
        )
        .await
    }

    pub async fn list_beds(&self, token: &str, room_id: &str) -> Result<Vec<BedView>, AppFailure> {
        let token = required_token(token)?;
        let room_id = required_id(room_id)?;
        let enabled = self.enabled_capabilities.clone();
        run_residence_blocking(
            self.identity.clone(),
            self.residence.clone(),
            move |identity, residence| {
                let actor = authenticated_actor(&identity, &token, &enabled)?;
                require_capability(&actor, "master.structure.read")?;
                residence
                    .list_beds(&RoomId::new(room_id))
                    .map_err(AppFailure::from)
                    .map(|beds| beds.into_iter().map(bed_view).collect())
            },
        )
        .await
    }

    pub async fn create_facility(
        &self,
        token: &str,
        command: CreateFacilityCommand,
    ) -> Result<FacilityView, AppFailure> {
        let token = required_token(token)?;
        let enabled = self.enabled_capabilities.clone();
        self.transaction(move |connection, stores| {
            let Stores {
                identity,
                audit,
                residence,
                ..
            } = stores;
            let actor = authenticated_actor_in_transaction(identity, connection, &token, &enabled)?;
            require_capability(&actor, "master.structure.write")?;
            let created = residence.create_facility_in_transaction(
                connection,
                new_facility_id(),
                FacilityInput {
                    name: command.name,
                    timezone: command.timezone,
                },
                mana_kernel::Instante::now(),
            )?;
            let record = ctx_auditoria::AuditRecord::new(
                Some(actor_id(&actor)),
                "facility.created",
                "facility",
                created.id.as_str(),
                json!({"name": &created.name, "timezone": &created.timezone}),
            )?;
            audit.record_in_transaction(connection, record)?;
            Ok(facility_view(created))
        })
        .await
    }

    pub async fn update_facility(
        &self,
        token: &str,
        facility_id: &str,
        command: UpdateFacilityCommand,
    ) -> Result<FacilityView, AppFailure> {
        let token = required_token(token)?;
        let facility_id = required_id(facility_id)?;
        let enabled = self.enabled_capabilities.clone();
        self.transaction(move |connection, stores| {
            let Stores {
                identity,
                audit,
                residence,
                ..
            } = stores;
            let actor = authenticated_actor_in_transaction(identity, connection, &token, &enabled)?;
            require_capability(&actor, "master.structure.write")?;
            let fields = facility_update_fields(&command);
            let updated = residence.update_facility_in_transaction(
                connection,
                &FacilityId::new(facility_id),
                FacilityUpdate {
                    name: command.name,
                    timezone: command.timezone,
                },
                mana_kernel::Instante::now(),
            )?;
            let record = ctx_auditoria::AuditRecord::new(
                Some(actor_id(&actor)),
                "facility.updated",
                "facility",
                updated.id.as_str(),
                json!({"fields": fields}),
            )?;
            audit.record_in_transaction(connection, record)?;
            Ok(facility_view(updated))
        })
        .await
    }

    pub async fn create_wing(
        &self,
        token: &str,
        facility_id: &str,
        command: CreateWingCommand,
    ) -> Result<WingView, AppFailure> {
        let token = required_token(token)?;
        let facility_id = required_id(facility_id)?;
        let enabled = self.enabled_capabilities.clone();
        self.transaction(move |connection, stores| {
            let Stores {
                identity,
                audit,
                residence,
                ..
            } = stores;
            let actor = authenticated_actor_in_transaction(identity, connection, &token, &enabled)?;
            require_capability(&actor, "master.structure.write")?;
            let created = residence.create_wing_in_transaction(
                connection,
                new_wing_id(),
                WingInput {
                    facility_id: FacilityId::new(facility_id),
                    name: command.name,
                    floor: command.floor,
                    sort_order: command.sort_order.unwrap_or_default(),
                },
                mana_kernel::Instante::now(),
            )?;
            let record = ctx_auditoria::AuditRecord::new(
                Some(actor_id(&actor)),
                "wing.created",
                "wing",
                created.id.as_str(),
                json!({"name": &created.name, "floor": &created.floor}),
            )?;
            audit.record_in_transaction(connection, record)?;
            Ok(wing_view(created, None))
        })
        .await
    }

    pub async fn update_wing(
        &self,
        token: &str,
        wing_id: &str,
        command: UpdateWingCommand,
    ) -> Result<WingView, AppFailure> {
        let token = required_token(token)?;
        let wing_id = required_id(wing_id)?;
        let enabled = self.enabled_capabilities.clone();
        self.transaction(move |connection, stores| {
            let Stores {
                identity,
                audit,
                residence,
                ..
            } = stores;
            let actor = authenticated_actor_in_transaction(identity, connection, &token, &enabled)?;
            require_capability(&actor, "master.structure.write")?;
            let fields = wing_update_fields(&command);
            let updated = residence.update_wing_in_transaction(
                connection,
                &ctx_residencia::WingId::new(wing_id),
                WingUpdate {
                    name: command.name,
                    floor: command.floor,
                    sort_order: command.sort_order,
                },
                mana_kernel::Instante::now(),
            )?;
            let record = ctx_auditoria::AuditRecord::new(
                Some(actor_id(&actor)),
                "wing.updated",
                "wing",
                updated.id.as_str(),
                json!({"fields": fields}),
            )?;
            audit.record_in_transaction(connection, record)?;
            Ok(wing_view(updated, None))
        })
        .await
    }

    pub async fn create_room(
        &self,
        token: &str,
        wing_id: &str,
        command: CreateRoomCommand,
    ) -> Result<RoomView, AppFailure> {
        let token = required_token(token)?;
        let wing_id = required_id(wing_id)?;
        let enabled = self.enabled_capabilities.clone();
        self.transaction(move |connection, stores| {
            let Stores { identity, audit, residence, .. } = stores;
            let actor = authenticated_actor_in_transaction(identity, connection, &token, &enabled)?;
            require_capability(&actor, "master.structure.write")?;
            let created = residence.create_room_in_transaction(
                connection,
                new_room_id(),
                RoomInput {
                    wing_id: ctx_residencia::WingId::new(wing_id),
                    number: command.number,
                    room_type: command.room_type.unwrap_or_else(|| "single".to_owned()),
                    stream_key: command.stream_key,
                },
                mana_kernel::Instante::now(),
            )?;
            let record = ctx_auditoria::AuditRecord::new(
                Some(actor_id(&actor)),
                "room.created",
                "room",
                created.id.as_str(),
                json!({"number": &created.number, "stream_key": created.stream_key.as_ref().map(|key| key.as_str())}),
            )?;
            audit.record_in_transaction(connection, record)?;
            Ok(room_view(created))
        })
        .await
    }

    pub async fn update_room(
        &self,
        token: &str,
        room_id: &str,
        command: UpdateRoomCommand,
    ) -> Result<RoomView, AppFailure> {
        let token = required_token(token)?;
        let room_id = required_id(room_id)?;
        let enabled = self.enabled_capabilities.clone();
        self.transaction(move |connection, stores| {
            let Stores {
                identity,
                audit,
                residence,
                ..
            } = stores;
            let actor = authenticated_actor_in_transaction(identity, connection, &token, &enabled)?;
            require_capability(&actor, "master.structure.write")?;
            let fields = room_update_fields(&command);
            let updated = residence.update_room_in_transaction(
                connection,
                &RoomId::new(room_id),
                RoomUpdate {
                    number: command.number,
                    room_type: command.room_type,
                    stream_key: command.stream_key,
                },
                mana_kernel::Instante::now(),
            )?;
            let record = ctx_auditoria::AuditRecord::new(
                Some(actor_id(&actor)),
                "room.updated",
                "room",
                updated.id.as_str(),
                json!({"fields": fields}),
            )?;
            audit.record_in_transaction(connection, record)?;
            Ok(room_view(updated))
        })
        .await
    }

    pub async fn create_bed(
        &self,
        token: &str,
        room_id: &str,
        command: BedCommand,
    ) -> Result<BedView, AppFailure> {
        let token = required_token(token)?;
        let room_id = required_id(room_id)?;
        let enabled = self.enabled_capabilities.clone();
        self.transaction(move |connection, stores| {
            let Stores { identity, audit, residence, .. } = stores;
            let actor = authenticated_actor_in_transaction(identity, connection, &token, &enabled)?;
            require_capability(&actor, "master.structure.write")?;
            let created = residence.create_bed_in_transaction(
                connection,
                new_bed_id(),
                BedInput {
                    room_id: RoomId::new(room_id),
                    label: command.label,
                    monitor_key: command.monitor_key,
                },
                mana_kernel::Instante::now(),
            )?;
            let record = ctx_auditoria::AuditRecord::new(
                Some(actor_id(&actor)),
                "bed.created",
                "bed",
                created.id.as_str(),
                json!({"label": &created.label, "monitor_key": created.monitor_key.as_ref().map(|key| key.as_str())}),
            )?;
            audit.record_in_transaction(connection, record)?;
            Ok(bed_view(created))
        })
        .await
    }

    pub async fn update_bed(
        &self,
        token: &str,
        bed_id: &str,
        command: UpdateBedCommand,
    ) -> Result<BedView, AppFailure> {
        let token = required_token(token)?;
        let bed_id = required_id(bed_id)?;
        let enabled = self.enabled_capabilities.clone();
        self.transaction(move |connection, stores| {
            let Stores {
                identity,
                audit,
                residence,
                ..
            } = stores;
            let actor = authenticated_actor_in_transaction(identity, connection, &token, &enabled)?;
            require_capability(&actor, "master.structure.write")?;
            let fields = bed_update_fields(&command);
            let updated = residence.update_bed_in_transaction(
                connection,
                &ctx_residencia::BedId::new(bed_id),
                BedUpdate {
                    label: command.label,
                    monitor_key: command.monitor_key,
                },
                mana_kernel::Instante::now(),
            )?;
            let record = ctx_auditoria::AuditRecord::new(
                Some(actor_id(&actor)),
                "bed.updated",
                "bed",
                updated.id.as_str(),
                json!({"fields": fields}),
            )?;
            audit.record_in_transaction(connection, record)?;
            Ok(bed_view(updated))
        })
        .await
    }
}

async fn run_residence_blocking<T, F>(
    identity: IdentityStore,
    residence: ResidenceStore,
    operation: F,
) -> Result<T, AppFailure>
where
    T: Send + 'static,
    F: FnOnce(IdentityStore, ResidenceStore) -> Result<T, AppFailure> + Send + 'static,
{
    tokio::task::spawn_blocking(move || operation(identity, residence))
        .await
        .map_err(|error| {
            tracing::error!(error = %error, "tarea SQLite abortada");
            AppFailure::new(Fallo::InternalError, "No se pudo completar la operacion")
        })?
}

fn required_id(value: &str) -> Result<String, AppFailure> {
    let value = value.trim();
    if value.is_empty() {
        Err(AppFailure::new(Fallo::NotFound, "Recurso no encontrado"))
    } else {
        Ok(value.to_owned())
    }
}

fn facility_view(facility: Facility) -> FacilityView {
    FacilityView {
        id: facility.id.into_string(),
        name: facility.name,
        timezone: facility.timezone,
    }
}

fn wing_view(wing: Wing, bed_count: Option<i32>) -> WingView {
    WingView {
        id: wing.id.into_string(),
        facility_id: wing.facility_id.into_string(),
        name: wing.name,
        floor: wing.floor,
        sort_order: wing.sort_order,
        bed_count,
    }
}

fn room_view(room: Room) -> RoomView {
    RoomView {
        id: room.id.into_string(),
        wing_id: room.wing_id.into_string(),
        number: room.number,
        room_type: room.room_type,
        stream_key: room.stream_key.map(|key| key.as_str().to_owned()),
    }
}

fn bed_view(bed: Bed) -> BedView {
    BedView {
        id: bed.id.into_string(),
        room_id: bed.room_id.into_string(),
        label: bed.label,
        monitor_key: bed.monitor_key.map(|key| key.as_str().to_owned()),
    }
}

fn residence_bed_view(bed: ctx_residencia::ResidenceBed) -> ResidenceBedView {
    ResidenceBedView {
        id: bed.bed.id.into_string(),
        room_id: bed.bed.room_id.into_string(),
        label: bed.bed.label,
        monitor_key: bed.bed.monitor_key.map(|key| key.as_str().to_owned()),
        room_number: bed.room_number,
        room_type: bed.room_type,
        stream_key: bed.stream_key,
        wing_id: bed.wing_id.into_string(),
        wing_name: bed.wing_name,
        wing_floor: bed.wing_floor,
    }
}

fn planogram_view(entry: ctx_residencia::PlanogramEntry) -> PlanogramPlacementView {
    PlanogramPlacementView {
        id: entry.id,
        wing_id: entry.wing_id.into_string(),
        room_id: entry.room_id.into_string(),
        x: entry.x,
        y: entry.y,
        sort_order: entry.sort_order,
        room_number: entry.room_number,
        room_type: entry.room_type,
        stream_key: entry.stream_key,
    }
}

fn privacy_region_view(region: ctx_residencia::PrivacyRegion) -> PrivacyRegionView {
    PrivacyRegionView {
        x: region.x,
        y: region.y,
        w: region.w,
        h: region.h,
    }
}

fn facility_update_fields(command: &UpdateFacilityCommand) -> Vec<&'static str> {
    let mut fields = Vec::new();
    if command.name.is_some() {
        fields.push("name");
    }
    if command.timezone.is_some() {
        fields.push("timezone");
    }
    fields
}

fn wing_update_fields(command: &UpdateWingCommand) -> Vec<&'static str> {
    let mut fields = Vec::new();
    if command.name.is_some() {
        fields.push("name");
    }
    if command.floor.is_some() {
        fields.push("floor");
    }
    if command.sort_order.is_some() {
        fields.push("sort_order");
    }
    fields
}

fn room_update_fields(command: &UpdateRoomCommand) -> Vec<&'static str> {
    let mut fields = Vec::new();
    if command.number.is_some() {
        fields.push("number");
    }
    if command.room_type.is_some() {
        fields.push("type");
    }
    if command.stream_key.is_some() {
        fields.push("stream_key");
    }
    fields
}

fn bed_update_fields(command: &UpdateBedCommand) -> Vec<&'static str> {
    let mut fields = Vec::new();
    if command.label.is_some() {
        fields.push("label");
    }
    if command.monitor_key.is_some() {
        fields.push("monitor_key");
    }
    fields
}

pub(crate) async fn run_tree_blocking<T, F>(
    identity: ctx_identidad::IdentityStore,
    residence: ctx_residencia::ResidenceStore,
    poblacion: ctx_poblacion::PopulationStore,
    streams: ctx_streams::StreamsStore,
    operation: F,
) -> Result<T, AppFailure>
where
    T: Send + 'static,
    F: FnOnce(
            ctx_identidad::IdentityStore,
            ctx_residencia::ResidenceStore,
            ctx_poblacion::PopulationStore,
            ctx_streams::StreamsStore,
        ) -> Result<T, AppFailure>
        + Send
        + 'static,
{
    tokio::task::spawn_blocking(move || operation(identity, residence, poblacion, streams))
        .await
        .map_err(|error| {
            tracing::error!(error = %error, "tarea SQLite abortada");
            AppFailure::new(mana_kernel::Fallo::InternalError, "No se pudo completar la operacion")
        })?
}
