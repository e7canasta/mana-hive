//! Casos de uso y composicion de la aplicacion.

mod auditoria;
pub mod cobertura;
pub mod cuidado;
mod error;
pub mod historia;
pub mod identidad;
pub mod motor;
pub mod observation;
pub mod perfil;
mod poblacion;
pub mod politica;
mod recomendacion;
mod reloj;
mod residencia;
mod seed;
mod state;
pub mod streams;
pub mod vigilancia;

pub use auditoria::{AuditEntryView, AuditQuery};
pub use cobertura::{
    AssignCoverageCommand, CoverageView, CreateGroupCommand, GridView, GroupView, MemberEntry,
    MemberView, ReplaceGridCommand, ReplaceMembersCommand, ShiftEntry, ShiftView,
    UpdateGroupCommand, WingCoverageView,
};
pub use ctx_politica::AlarmCatalog;
pub use ctx_politica::Overrides;
pub use cuidado::{
    CareActivityResponseView, CareActivityView, CarePeriodView, CreateNoteCommand,
    CreateRoundCommand, NoteView, RoundView, TaskView, UpdateTaskCommand,
};
pub use error::AppFailure;
pub use historia::{
    CreateReviewCommand, CurrentView, DetectionView, IncidentSequenceView, IncidentView,
    IngestIncidentCommand, IngestResultView, ReviewView, SequenceDerivedView, SequenceEventView,
    SequenceWindowView,
};
pub use identidad::{
    AdminUserView, AuthenticatedView, CreateUserCommand, LoginCommand, LoginResult,
    UpdateUserCommand,
};
pub use observation::{
    AlertaEmitidaView, BathroomSummaryView, BedStateView, BoardBedView, BoardRoomView, BoardView,
    BoardWingView, CompanionRoomView, CompanionRoomsView, CurrentStateView, EventsView,
    IngestBathroomCommand, IngestEventCommand, IngestEventView, IngestMobilityCommand,
    IngestSleepCommand, MobilitySummaryView, PeekView, ReportsSummaryView, ResidentRefView,
    SensorEventView, SleepSummaryView, SummaryIngestView, TimelineItemView, TimelineView,
};
pub use poblacion::{
    AssignBedCommand, BedAssignmentView, CreateResidentCommand, DischargeCommand,
    DischargeResultView, ResidentListItemView, ResidentRecordView, RoomRefView,
    UpdateResidentCommand,
};
pub use politica::{
    AlarmPresetsSummaryView, AlarmPresetsView, AlarmProfileSettingsView, AlarmProfileView,
    AlarmResidentView, ApplyRecommendationCommand, ApplyRecommendationsCommand,
    AutopilotDecisionView, AutopilotPolicyView, CatalogGroupView, CatalogParamView,
    CatalogPresetRuleView, CatalogRiskFactorView, CatalogRuleView, CatalogTemplateView,
    CatalogView, EffectiveRuleView, EffectiveView, ProfileHistoryView, ProfileVersionView,
    ProfileWithEffectiveView, RecommendationFactorView, RecommendationView, UpdateProfileCommand,
};
pub use residencia::{
    BedCommand, BedView, CreateFacilityCommand, CreateRoomCommand, CreateWingCommand,
    FacilityDetailView, FacilityTreeView, FacilityView, PlanogramPlacementCommand,
    PlanogramPlacementView, PrivacyRegionCommand, PrivacyRegionView, ResidenceBedView, RoomView,
    SavePlanogramCommand, SavePrivacyRegionsCommand, TreeBedView, TreeRegionView, TreeResidentView,
    TreeRoomView, TreeStreamView, TreeWingView, UpdateBedCommand, UpdateFacilityCommand,
    UpdateRoomCommand, UpdateWingCommand, WingView,
};
pub use state::{AppInitError, AppState};
pub use streams::{
    CreateStreamCommand, RegionCommand, ReplaceRegionsCommand, StreamRegionView, StreamView,
    UpdateRegionCommand,
};
pub use vigilancia::{
    AddDeliveryEventCommand, AlertView, AlertsListView, CreateAlertCommand, CreateDeliveryCommand,
    DeliveriesListView, DeliveryEventView, DeliverySummaryView, DeliveryView, EscalationView,
    TransitionAlertCommand, TransitionView,
};
