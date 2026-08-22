//! Cliente Rust y herramientas declarativas para el hub Mana.

pub mod auditoria;
pub mod cobertura;
pub mod cuidado;
pub mod escenas;
pub mod historia;
pub mod identidad;
pub mod observacion;

/// El instante de ahora en el formato del contrato: ISO-8601 UTC con milisegundos.
pub fn now_rfc3339() -> String {
    chrono::Utc::now().to_rfc3339_opts(chrono::SecondsFormat::Millis, true)
}

pub mod platform;
pub mod poblacion;
pub mod politica;
pub mod residencia;
pub mod streams;
pub mod transport;
pub mod vigilancia;

pub use auditoria::{AuditEntry, AuditQuery, AuditResponse};
pub use cobertura::{
    AssignCoverageRequest, CoverageResponse, CreateGroupRequest, GroupResponse, GroupsResponse,
    Member, MemberEntry, MembersResponse, ReplaceMembersRequest, ReplaceShiftGridRequest, Shift,
    ShiftEntry, ShiftGridResponse, SingleCoverageResponse, StaffGroup, StaffGroupDetail,
    UpdateGroupRequest, WingCoverage,
};
pub use cuidado::{
    CareNote, CreateNoteRequest, CreateRoundRequest, NoteResponse, NotesResponse, Round,
    RoundResponse, RoundTask, RoundsResponse, TaskResponse, UpdateTaskRequest,
};
pub use escenas::{
    validate_scene, Scene, SceneCommand, SceneCommandResult, SceneError, SceneMeta, SceneRunReport,
    SceneRunner,
};
pub use historia::{
    CreateReviewRequest, CurrentInfo, DetectionInfo, Incident, IncidentResponse, IncidentsResponse,
    IngestRequest, IngestResponse, ReviewInfo,
};
pub use identidad::{
    AdminUser, AuthUser, CreateUserRequest, CurrentUserResponse, LoginRequest, LoginResponse,
    UpdateUserRequest, UserResponse, UsersResponse,
};
pub use observacion::{
    BathroomSummary, BedState, BoardBed, BoardResponse, BoardRoom, BoardWing, CompanionRoom,
    CompanionRoomsResponse, CurrentStateResponse, EventsResponse, IngestEventRequest,
    IngestEventResponse, MobilitySummary, PeekResponse, ReportsSummaryResponse, ResidentRef,
    SensorEvent, SleepSummary, SummariesResponse, SummaryIngestResponse, TimelineResponse,
};
pub use platform::{health, HealthResponse};
pub use poblacion::{
    AssignBedRequest, AssignmentResponse, AssignmentsResponse, BedAssignment,
    CreateResidentRequest, DischargeRequest, ResidentListItem, ResidentRecord, ResidentResponse,
    ResidentsResponse, RoomRef, UpdateResidentRequest,
};
pub use politica::{
    ApplyBulkRequest, ApplyRecommendationRequest, CatalogGroup, CatalogResponse, CatalogRiskFactor,
    CatalogRule, CatalogTemplate, PresetResponse, PresetsResponse, ProfileHistoryResponse,
    ProfileVersion, UpdatePresetRequest,
};
pub use residencia::{
    Bed, BedResponse, BedsResponse, CreateBedRequest, CreateFacilityRequest, CreateRoomRequest,
    CreateWingRequest, FacilitiesResponse, Facility, FacilityDetail, FacilityResponse,
    FacilityTree, PlanogramPlacement, PlanogramPlacementRequest, PlanogramResponse, PrivacyRegion,
    PrivacyRegionRequest, PrivacyRegionsResponse, ResidenceBed, ResidenceBedsResponse, Room,
    RoomResponse, RoomsResponse, SavePlanogramRequest, SavePrivacyRegionsRequest, TreeBed,
    TreeRegion, TreeResident, TreeRoom, TreeStream, TreeWing, UpdateBedRequest,
    UpdateFacilityRequest, UpdateRoomRequest, UpdateWingRequest, Wing, WingResponse, WingsResponse,
};
pub use streams::{
    CreateStreamRequest, RegionRequest, ReplaceRegionsRequest, Stream, StreamRegion,
    StreamsResponse, UpdateRegionRequest,
};
pub use transport::{ApiResponse, BearerToken, ManaClient, ManaError, DEFAULT_BASE_URL};
pub use vigilancia::{
    AddDeliveryEventRequest, AlertItem, AlertResponse, AlertsResponse, CreateAlertRequest,
    CreateDeliveryRequest, DeliveriesResponse, DeliveryEventItem, DeliveryItem,
    DeliverySummaryItem, EscalationItem, TransitionAlertRequest,
};
