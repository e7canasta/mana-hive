use std::{collections::BTreeSet, env, sync::OnceLock};

use ctx_auditoria::AuditRecord;
use ctx_identidad::{
    new_user_id, AuthenticatedUser, CreateUserInput, IdentityError, IdentityStore, Role,
    UpdateUserInput,
};
use diesel::SqliteConnection;
use mana_kernel::{Actor, Fallo, Id, Instante};
use serde_json::json;

use crate::{
    error::AppFailure,
    state::{AppState, Stores},
};

/// Token estatico para server-to-server. Se establece una vez al arrancar.
static SERVER_TOKEN: OnceLock<String> = OnceLock::new();

/// Establece el token de server-to-server. Solo debe llamarse una vez al inicio.
pub fn set_server_token(token: String) {
    let _ = SERVER_TOKEN.set(token);
}

/// Obtiene el token de server-to-server, si esta configurado.
fn server_token() -> Option<&'static str> {
    SERVER_TOKEN.get().map(|s| s.as_str())
}

const DEFAULT_ACTIVE_CAPABILITIES: [&str; 24] = [
    "master.structure.read",
    "master.structure.write",
    "monitoring.board.read",
    "monitoring.live.read",
    "residents.list.read",
    "residents.snapshot.read",
    "residents.live.read",
    "residents.write",
    "sleep.read",
    "mobility.read",
    "bathroom.read",
    "care.read",
    "incidents.read",
    "incidents.manage",
    "rounds.read",
    "rounds.manage",
    "alerts.read",
    "alerts.manage",
    "config.alarms.read",
    "config.alarms.manage",
    "analytics.read",
    "audit.read",
    "streams.read",
    "streams.write",
];

/// Entrada de login independiente de JSON y HTTP.
#[derive(Clone, Debug)]
pub struct LoginCommand {
    pub username: String,
    pub password: String,
}

/// Entrada de alta de usuario independiente de JSON y HTTP.
#[derive(Clone, Debug)]
pub struct CreateUserCommand {
    pub username: String,
    pub display_name: String,
    pub role: String,
    pub job_title: Option<String>,
    pub password: String,
}

/// Entrada de cambios de usuario independiente de JSON y HTTP.
#[derive(Clone, Debug)]
pub struct UpdateUserCommand {
    pub display_name: Option<String>,
    pub role: Option<String>,
    pub job_title: Option<Option<String>>,
    pub active: Option<bool>,
    pub password: Option<String>,
}

#[derive(Clone, Debug)]
pub struct AuthenticatedView {
    pub id: String,
    pub username: String,
    pub display_name: String,
    pub role: String,
    pub features: Vec<String>,
    pub capabilities: Vec<String>,
}

#[derive(Clone, Debug)]
pub struct LoginResult {
    pub token: String,
    pub expires_at: String,
    pub user: AuthenticatedView,
}

#[derive(Clone, Debug)]
pub struct AdminUserView {
    pub id: String,
    pub username: String,
    pub display_name: String,
    pub role: String,
    pub job_title: Option<String>,
    pub active: bool,
}

impl AppState {
    pub fn seed_demo(&self) -> Result<(), AppFailure> {
        let users = [
            (
                "user-admin",
                "admin",
                "Admin",
                Role::Owner,
                "System Administrator",
                "DEMO_ADMIN_PASSWORD",
                "mana123",
            ),
            (
                "user-gaston",
                "gaston",
                "Gaston",
                Role::Owner,
                "Director médico",
                "DEMO_GASTON_PASSWORD",
                "mana123",
            ),
            (
                "user-mana",
                "mana",
                "System",
                Role::Owner,
                "System Service Account",
                "DEMO_MANA_PASSWORD",
                "mana123",
            ),
        ];
        for (id, username, display_name, role, job_title, password_env, fallback) in users {
            let password = env::var(password_env).unwrap_or_else(|_| fallback.to_owned());
            let input = CreateUserInput {
                username: username.to_owned(),
                display_name: display_name.to_owned(),
                role,
                job_title: Some(job_title.to_owned()),
                password,
            };
            match self
                .identity
                .create_user(ctx_identidad::UserId::new(id), input, Instante::now())
            {
                Ok(_) | Err(IdentityError::Conflict) => {}
                Err(error) => return Err(AppFailure::from(error)),
            }
        }
        Ok(())
    }

    pub async fn login(&self, command: LoginCommand) -> Result<LoginResult, AppFailure> {
        let username = command.username;
        let password = command.password;
        let store = self.identity.clone();
        let result = run_blocking(store, move |store| {
            store
                .login(&username, &password, Instante::now())
                .map_err(AppFailure::from)
        })
        .await?;
        let Some(session) = result else {
            return Err(AppFailure::new(
                Fallo::InvalidCredentials,
                "Usuario o clave invalidos",
            ));
        };
        Ok(LoginResult {
            token: session.token.as_str().to_owned(),
            expires_at: session.expires_at.to_string(),
            user: auth_user(session.user.public(&self.enabled_capabilities)),
        })
    }

    pub async fn current_user(&self, token: &str) -> Result<AuthenticatedView, AppFailure> {
        let token = required_token(token)?;
        let enabled = self.enabled_capabilities.clone();
        let user = run_blocking(self.identity.clone(), move |store| {
            store
                .authenticate(&token, Instante::now())
                .map_err(AppFailure::from)
        })
        .await?
        .ok_or_else(|| AppFailure::new(Fallo::Unauthenticated, "Se requiere iniciar sesion"))?;
        Ok(auth_user(user.public(&enabled)))
    }

    pub async fn logout(&self, token: &str) -> Result<(), AppFailure> {
        let token = required_token(token)?;
        run_blocking(self.identity.clone(), move |store| {
            let authenticated = store
                .authenticate(&token, Instante::now())
                .map_err(AppFailure::from)?;
            if authenticated.is_none() {
                return Err(AppFailure::new(
                    Fallo::Unauthenticated,
                    "Se requiere iniciar sesion",
                ));
            }
            store.logout(&token).map_err(AppFailure::from)
        })
        .await
    }

    pub async fn list_users(
        &self,
        token: &str,
        include_inactive: bool,
    ) -> Result<Vec<AdminUserView>, AppFailure> {
        let token = required_token(token)?;
        let enabled = self.enabled_capabilities.clone();
        run_blocking(self.identity.clone(), move |store| {
            let actor = authenticated_actor(&store, &token, &enabled)?;
            require_capability(&actor, "master.structure.read")?;
            store.list_users(include_inactive).map_err(AppFailure::from)
        })
        .await
        .map(|users| users.into_iter().map(admin_user).collect())
    }

    pub async fn create_user(
        &self,
        token: &str,
        command: CreateUserCommand,
    ) -> Result<AdminUserView, AppFailure> {
        let token = required_token(token)?;
        let input = create_input(command)?;
        let user_id = new_user_id()
            .map_err(|error| AppFailure::new(Fallo::InternalError, error.to_string()))?;
        let enabled = self.enabled_capabilities.clone();
        let username = input.username.trim().to_ascii_lowercase();
        let role = input.role.as_str().to_owned();
        self.transaction(move |connection, stores| {
            let Stores {
                identity: store,
                audit,
                ..
            } = stores;
            let actor = authenticated_actor_in_transaction(store, connection, &token, &enabled)?;
            require_capability(&actor, "master.structure.write")?;
            let created = store
                .create_user_in_transaction(connection, user_id, input, Instante::now())
                .map_err(AppFailure::from)?;
            let record = AuditRecord::new(
                Some(actor_id(&actor)),
                "user.created",
                "user",
                created.id.as_str(),
                json!({"username": username, "role": role}),
            )?;
            audit.record_in_transaction(connection, record)?;
            Ok(created)
        })
        .await
        .map(admin_user)
    }

    pub async fn update_user(
        &self,
        token: &str,
        user_id: &str,
        command: UpdateUserCommand,
    ) -> Result<AdminUserView, AppFailure> {
        let token = required_token(token)?;
        if user_id.trim().is_empty() {
            return Err(AppFailure::new(Fallo::NotFound, "Usuario no encontrado"));
        }
        let audit_fields = update_audit_fields(&command);
        let input = update_input(command)?;
        let target_id = user_id.to_owned();
        let enabled = self.enabled_capabilities.clone();
        self.transaction(move |connection, stores| {
            let Stores {
                identity: store,
                audit,
                ..
            } = stores;
            let actor = authenticated_actor_in_transaction(store, connection, &token, &enabled)?;
            require_capability(&actor, "master.structure.write")?;
            let target = ctx_identidad::UserId::new(target_id);
            let updated = store
                .update_user_in_transaction(
                    connection,
                    &target,
                    input,
                    &actor_id(&actor),
                    Instante::now(),
                )
                .map_err(AppFailure::from)?;
            let record = AuditRecord::new(
                Some(actor_id(&actor)),
                "user.updated",
                "user",
                updated.id.as_str(),
                json!({"fields": audit_fields}),
            )?;
            audit.record_in_transaction(connection, record)?;
            Ok(updated)
        })
        .await
        .map(admin_user)
    }
}

impl From<IdentityError> for AppFailure {
    fn from(error: IdentityError) -> Self {
        match error {
            IdentityError::Conflict => AppFailure::new(Fallo::Conflict, "El usuario ya existe"),
            IdentityError::NotFound => AppFailure::new(Fallo::NotFound, "Usuario no encontrado"),
            IdentityError::Domain(error) => AppFailure::validation(error.to_string(), None),
            other => {
                tracing::error!(error = %other, "fallo en el contexto de identidad");
                AppFailure::new(Fallo::InternalError, "No se pudo completar la operacion")
            }
        }
    }
}

async fn run_blocking<T, F>(store: IdentityStore, operation: F) -> Result<T, AppFailure>
where
    T: Send + 'static,
    F: FnOnce(IdentityStore) -> Result<T, AppFailure> + Send + 'static,
{
    tokio::task::spawn_blocking(move || operation(store))
        .await
        .map_err(|error| {
            tracing::error!(error = %error, "tarea SQLite abortada");
            AppFailure::new(Fallo::InternalError, "No se pudo completar la operacion")
        })?
}

pub(crate) fn required_token(token: &str) -> Result<String, AppFailure> {
    let token = token.trim();
    if token.is_empty() {
        Err(AppFailure::new(
            Fallo::Unauthenticated,
            "Se requiere iniciar sesion",
        ))
    } else {
        Ok(token.to_owned())
    }
}

pub(crate) fn authenticated_actor(
    store: &IdentityStore,
    token: &str,
    enabled: &BTreeSet<String>,
) -> Result<AuthenticatedUser, AppFailure> {
    // Check for server-to-server token first
    if let Some(server_tok) = server_token() {
        if server_tok == token {
            return Ok(server_token_actor(enabled));
        }
    }
    store
        .authenticate(token, Instante::now())
        .map_err(AppFailure::from)?
        .map(|user| user.public(enabled))
        .ok_or_else(|| AppFailure::new(Fallo::Unauthenticated, "Se requiere iniciar sesion"))
}

pub(crate) fn authenticated_actor_in_transaction(
    store: &IdentityStore,
    connection: &mut SqliteConnection,
    token: &str,
    enabled: &BTreeSet<String>,
) -> Result<AuthenticatedUser, AppFailure> {
    // Check for server-to-server token first
    if let Some(server_tok) = server_token() {
        if server_tok == token {
            return Ok(server_token_actor(enabled));
        }
    }
    store
        .authenticate_in_transaction(connection, token, Instante::now())
        .map_err(AppFailure::from)?
        .map(|user| user.public(enabled))
        .ok_or_else(|| AppFailure::new(Fallo::Unauthenticated, "Se requiere iniciar sesion"))
}

/// Autentica con token de server-to-server. Retorna un usuario sistema con
/// todas las capacidades sin consultar la base.
pub(crate) fn server_token_actor(_enabled: &BTreeSet<String>) -> AuthenticatedUser {
    AuthenticatedUser {
        id: Id::new("user-system"),
        username: ctx_identidad::Username::parse("system").unwrap(),
        display_name: ctx_identidad::DisplayName::parse("System (server-to-server)").unwrap(),
        role: ctx_identidad::Role::Owner,
        capabilities: ctx_identidad::Role::Owner
            .capabilities()
            .iter()
            .map(|c| c.to_owned())
            .collect(),
        features: ctx_identidad::Role::Owner
            .features()
            .iter()
            .map(|f| f.to_owned())
            .collect(),
    }
}

pub(crate) fn actor_id(actor: &AuthenticatedUser) -> Id<Actor> {
    Id::new(actor.id.as_str())
}

pub(crate) fn require_capability(
    actor: &AuthenticatedUser,
    capability: &str,
) -> Result<(), AppFailure> {
    if actor
        .capabilities
        .iter()
        .any(|candidate| candidate.as_str() == capability)
    {
        Ok(())
    } else {
        Err(AppFailure::new(
            Fallo::Forbidden,
            match capability {
                "audit.read" => "No tenes permiso para ver la auditoria",
                "config.alarms.read" => "No tenes permiso para ver la configuracion de alarmas",
                "config.alarms.manage" => "No tenes permiso para configurar alarmas",
                _ => "No tenes permiso para configurar la estructura",
            },
        ))
    }
}

fn update_audit_fields(command: &UpdateUserCommand) -> Vec<String> {
    let mut fields = Vec::new();
    if command.display_name.is_some() {
        fields.push("display_name".to_owned());
    }
    if command.role.is_some() {
        fields.push("role".to_owned());
    }
    if command.job_title.is_some() {
        fields.push("job_title".to_owned());
    }
    if command.active.is_some() {
        fields.push("active".to_owned());
    }
    if command.password.is_some() {
        fields.push("password".to_owned());
    }
    fields
}

fn create_input(command: CreateUserCommand) -> Result<CreateUserInput, AppFailure> {
    let role = command
        .role
        .as_str()
        .parse::<Role>()
        .map_err(|error| AppFailure::validation(error.to_string(), Some("role")))?;
    let password = command.password;
    if password.chars().count() < 6 {
        return Err(AppFailure::validation(
            "password debe tener al menos 6 caracteres",
            Some("password"),
        ));
    }
    Ok(CreateUserInput {
        username: command.username,
        display_name: command.display_name,
        role,
        job_title: command.job_title.filter(|value| !value.trim().is_empty()),
        password,
    })
}

fn update_input(command: UpdateUserCommand) -> Result<UpdateUserInput, AppFailure> {
    if command.display_name.is_none()
        && command.role.is_none()
        && command.job_title.is_none()
        && command.active.is_none()
        && command.password.is_none()
    {
        return Err(AppFailure::validation(
            "No hay campos para actualizar",
            None,
        ));
    }
    let role = command
        .role
        .as_deref()
        .map(str::parse::<Role>)
        .transpose()
        .map_err(|error| AppFailure::validation(error.to_string(), Some("role")))?;
    if let Some(password) = &command.password {
        if password.chars().count() < 6 {
            return Err(AppFailure::validation(
                "password debe tener al menos 6 caracteres",
                Some("password"),
            ));
        }
    }
    Ok(UpdateUserInput {
        display_name: command.display_name,
        role,
        job_title: command
            .job_title
            .map(|value| value.filter(|title| !title.trim().is_empty())),
        active: command.active,
        password: command.password,
    })
}

pub(crate) fn enabled_capabilities_from_env() -> BTreeSet<String> {
    match env::var("API_ENABLED_CAPABILITIES") {
        Ok(value) if !value.trim().is_empty() => value
            .split(',')
            .map(str::trim)
            .filter(|value| !value.is_empty())
            .map(str::to_owned)
            .collect(),
        _ => DEFAULT_ACTIVE_CAPABILITIES
            .into_iter()
            .map(str::to_owned)
            .collect(),
    }
}

fn auth_user(user: AuthenticatedUser) -> AuthenticatedView {
    let features = user
        .features
        .into_iter()
        .map(|feature| feature.as_str().to_owned())
        .collect::<Vec<_>>();
    AuthenticatedView {
        id: user.id.into_string(),
        username: user.username.as_str().to_owned(),
        display_name: user.display_name.as_str().to_owned(),
        role: user.role.as_str().to_owned(),
        features,
        capabilities: user
            .capabilities
            .into_iter()
            .map(|capability| capability.as_str().to_owned())
            .collect(),
    }
}

fn admin_user(user: ctx_identidad::User) -> AdminUserView {
    let active = user.is_active();
    AdminUserView {
        id: user.id.into_string(),
        username: user.username.as_str().to_owned(),
        display_name: user.display_name.as_str().to_owned(),
        role: user.role.as_str().to_owned(),
        job_title: user
            .job_title
            .as_ref()
            .map(|title| title.as_str().to_owned()),
        active,
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::AuditQuery;
    use ctx_auditoria::run_migrations as run_audit_migrations;
    use ctx_identidad::run_migrations;
    use mana_storage::build_pool;

    async fn app() -> AppState {
        let pool = build_pool(":memory:").unwrap();
        run_migrations(&pool).unwrap();
        run_audit_migrations(&pool).unwrap();
        let app = AppState::from_pool(pool, ctx_politica::AlarmCatalog::empty());
        app.seed_demo().unwrap();
        app
    }

    #[tokio::test]
    async fn identity_cases_cover_login_authorization_users_and_logout() {
        let app = app().await;
        let login = app
            .login(LoginCommand {
                username: "gaston".to_owned(),
                password: "mana123".to_owned(),
            })
            .await
            .unwrap();
        assert_eq!(login.user.username, "gaston");
        assert!(login
            .user
            .capabilities
            .contains(&"master.structure.write".to_owned()));

        let users = app.list_users(&login.token, true).await.unwrap();
        assert_eq!(users.len(), 3);

        let created = app
            .create_user(
                &login.token,
                CreateUserCommand {
                    username: "maria".to_owned(),
                    display_name: "Maria Demo".to_owned(),
                    role: "staff".to_owned(),
                    job_title: Some("Enfermera".to_owned()),
                    password: "secret1".to_owned(),
                },
            )
            .await
            .unwrap();
        assert_eq!(created.username, "maria");

        let created_audit = app
            .list_audit(
                &login.token,
                AuditQuery {
                    entity_type: Some("user".to_owned()),
                    entity_id: Some(created.id.clone()),
                    ..AuditQuery::default()
                },
            )
            .await
            .unwrap();
        assert_eq!(created_audit.len(), 1);
        assert_eq!(created_audit[0].action, "user.created");
        assert_eq!(created_audit[0].metadata["role"], "staff");

        let updated = app
            .update_user(
                &login.token,
                &created.id,
                UpdateUserCommand {
                    display_name: None,
                    role: None,
                    job_title: None,
                    active: Some(false),
                    password: None,
                },
            )
            .await
            .unwrap();
        assert!(!updated.active);

        let updated_audit = app
            .list_audit(
                &login.token,
                AuditQuery {
                    entity_type: Some("user".to_owned()),
                    entity_id: Some(created.id.clone()),
                    ..AuditQuery::default()
                },
            )
            .await
            .unwrap();
        assert_eq!(updated_audit.len(), 2);
        assert!(updated_audit
            .iter()
            .any(|entry| entry.action == "user.updated"));

        app.update_user(
            &login.token,
            &created.id,
            UpdateUserCommand {
                display_name: None,
                role: None,
                job_title: None,
                active: None,
                password: Some("secret2".to_owned()),
            },
        )
        .await
        .unwrap();
        let password_audit = app
            .list_audit(
                &login.token,
                AuditQuery {
                    entity_type: Some("user".to_owned()),
                    entity_id: Some(created.id.clone()),
                    ..AuditQuery::default()
                },
            )
            .await
            .unwrap();
        assert_eq!(password_audit.len(), 3);
        let password_entry = password_audit
            .iter()
            .find(|entry| entry.metadata["fields"] == serde_json::json!(["password"]))
            .expect("el cambio de password debe quedar auditado");
        assert_eq!(
            password_entry.metadata["fields"],
            serde_json::json!(["password"])
        );

        app.create_user(
            &login.token,
            CreateUserCommand {
                username: "staff".to_owned(),
                display_name: "Staff User".to_owned(),
                role: "staff".to_owned(),
                job_title: None,
                password: "staff-demo".to_owned(),
            },
        )
        .await
        .unwrap();
        let staff = app
            .login(LoginCommand {
                username: "staff".to_owned(),
                password: "staff-demo".to_owned(),
            })
            .await
            .unwrap();
        let forbidden_audit = app
            .list_audit(&staff.token, AuditQuery::default())
            .await
            .unwrap_err();
        assert_eq!(forbidden_audit.fallo, Fallo::Forbidden);
        let forbidden = app
            .create_user(
                &staff.token,
                CreateUserCommand {
                    username: "blocked".to_owned(),
                    display_name: "Blocked".to_owned(),
                    role: "staff".to_owned(),
                    job_title: None,
                    password: "secret1".to_owned(),
                },
            )
            .await
            .unwrap_err();
        assert_eq!(forbidden.fallo, Fallo::Forbidden);

        app.logout(&login.token).await.unwrap();
        assert_eq!(
            app.current_user(&login.token).await.unwrap_err().fallo,
            Fallo::Unauthenticated
        );
    }

    #[tokio::test]
    async fn invalid_credentials_and_validation_keep_their_contract_codes() {
        let app = app().await;
        let invalid = app
            .login(LoginCommand {
                username: "gaston".to_owned(),
                password: "wrong-password".to_owned(),
            })
            .await
            .unwrap_err();
        assert_eq!(invalid.fallo, Fallo::InvalidCredentials);

        let missing = app
            .login(LoginCommand {
                username: String::new(),
                password: "secret1".to_owned(),
            })
            .await
            .unwrap_err();
        assert_eq!(missing.fallo, Fallo::ValidationError);
    }
}
