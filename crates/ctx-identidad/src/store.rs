use chrono::Duration;
use diesel::{prelude::*, Connection, OptionalExtension, SqliteConnection};
use diesel_migrations::{embed_migrations, EmbeddedMigrations};
use mana_kernel::{Actor, Id, Instante};
use mana_storage::{connection as get_connection, DbConnection, DbPool};

use crate::{
    domain::{
        ClearSessionToken, CreateUserInput, DisplayName, JobTitle, LoginSession, PasswordHash,
        PersistedUser, Role, TokenHash, UpdateUserInput, User, UserId, Username,
    },
    schema::{auth_sessions, users},
    IdentityError,
};

pub const MIGRATIONS: EmbeddedMigrations = embed_migrations!();

#[derive(Clone)]
pub struct IdentityStore {
    pool: DbPool,
}

#[derive(Queryable, Selectable)]
#[diesel(table_name = users)]
#[diesel(check_for_backend(diesel::sqlite::Sqlite))]
struct UserRow {
    id: String,
    username: String,
    display_name: String,
    role: String,
    job_title: Option<String>,
    password_hash: String,
    retired_at: Option<String>,
    retired_by: Option<String>,
    created_at: String,
    updated_at: String,
}

#[allow(dead_code)]
#[derive(Queryable, Selectable)]
#[diesel(table_name = auth_sessions)]
#[diesel(check_for_backend(diesel::sqlite::Sqlite))]
struct SessionRow {
    token_hash: Vec<u8>,
    user_id: String,
    expires_at: String,
    created_at: String,
    last_seen_at: Option<String>,
}

#[derive(Insertable)]
#[diesel(table_name = users)]
struct NewUserRow<'a> {
    id: &'a str,
    username: &'a str,
    display_name: &'a str,
    role: &'a str,
    job_title: Option<&'a str>,
    password_hash: &'a str,
    retired_at: Option<&'a str>,
    retired_by: Option<&'a str>,
    created_at: &'a str,
    updated_at: &'a str,
}

#[derive(AsChangeset)]
#[diesel(table_name = users)]
#[diesel(treat_none_as_null = true)]
struct UserChangeset<'a> {
    display_name: &'a str,
    role: &'a str,
    job_title: Option<&'a str>,
    password_hash: &'a str,
    retired_at: Option<&'a str>,
    retired_by: Option<&'a str>,
    updated_at: &'a str,
}

pub fn run_migrations(pool: &DbPool) -> Result<(), IdentityError> {
    mana_storage::run_migrations(pool, MIGRATIONS).map_err(IdentityError::from)
}

impl IdentityStore {
    pub fn new(pool: DbPool) -> Self {
        Self { pool }
    }

    pub fn pool(&self) -> &DbPool {
        &self.pool
    }

    pub fn login(
        &self,
        username: &str,
        password: &str,
        now: Instante,
    ) -> Result<Option<LoginSession>, IdentityError> {
        let username = Username::parse(username)?;
        let mut connection = self.connection()?;
        let now_text = now.to_string();

        connection.transaction(|connection| {
            let row = users::table
                .filter(users::username.eq(username.as_str()))
                .select(UserRow::as_select())
                .first(connection)
                .optional()?;
            let Some(row) = row else {
                return Ok(None);
            };
            let user = user_from_row(row)?;
            if !user.is_active() || !user.verify_password(password) {
                return Ok(None);
            }

            let token = ClearSessionToken::generate()?;
            let token_hash = TokenHash::from_token(token.as_str());
            let expires_at = crate::domain::session_expiration(now);
            diesel::insert_into(auth_sessions::table)
                .values((
                    auth_sessions::token_hash.eq(token_hash.as_bytes().to_vec()),
                    auth_sessions::user_id.eq(user.id.as_str()),
                    auth_sessions::expires_at.eq(expires_at.to_string()),
                    auth_sessions::created_at.eq(&now_text),
                ))
                .execute(connection)
                .map_err(IdentityError::database)?;

            Ok(Some(LoginSession {
                token,
                expires_at,
                user,
            }))
        })
    }

    pub fn authenticate(&self, token: &str, now: Instante) -> Result<Option<User>, IdentityError> {
        let mut connection = self.connection()?;
        self.authenticate_in_transaction(&mut connection, token, now)
    }

    /// Autentica usando la conexion de una transaccion coordinada por
    /// `mana-app`. Tambien actualiza `last_seen_at` dentro de esa transaccion.
    pub fn authenticate_in_transaction(
        &self,
        connection: &mut SqliteConnection,
        token: &str,
        now: Instante,
    ) -> Result<Option<User>, IdentityError> {
        let token_hash = TokenHash::from_token(token);
        let now_text = now.to_string();
        let session = auth_sessions::table
            .find(token_hash.as_bytes().to_vec())
            .select(SessionRow::as_select())
            .first(connection)
            .optional()
            .map_err(IdentityError::database)?;
        let Some(session) = session else {
            return Ok(None);
        };
        if session.expires_at <= now_text {
            return Ok(None);
        }

        let user = users::table
            .find(session.user_id)
            .select(UserRow::as_select())
            .first(connection)
            .optional()
            .map_err(IdentityError::database)?
            .map(user_from_row)
            .transpose()?;
        let Some(user) = user.filter(User::is_active) else {
            return Ok(None);
        };

        let should_touch = match session
            .last_seen_at
            .as_deref()
            .and_then(|value| value.parse::<Instante>().ok())
        {
            Some(last_seen) => {
                *last_seen.as_datetime() + Duration::minutes(1) <= *now.as_datetime()
            }
            None => true,
        };
        if should_touch {
            diesel::update(auth_sessions::table.find(token_hash.as_bytes().to_vec()))
                .set(auth_sessions::last_seen_at.eq(now_text))
                .execute(connection)
                .map_err(IdentityError::database)?;
        }
        Ok(Some(user))
    }

    pub fn logout(&self, token: &str) -> Result<(), IdentityError> {
        let token_hash = TokenHash::from_token(token);
        let mut connection = self.connection()?;
        diesel::delete(auth_sessions::table.find(token_hash.as_bytes().to_vec()))
            .execute(&mut connection)
            .map_err(IdentityError::database)?;
        Ok(())
    }

    pub fn list_users(&self, include_inactive: bool) -> Result<Vec<User>, IdentityError> {
        let mut connection = self.connection()?;
        let mut query = users::table
            .select(UserRow::as_select())
            .order((users::display_name.asc(), users::username.asc()))
            .into_boxed();
        if !include_inactive {
            query = query.filter(users::retired_at.is_null());
        }
        query
            .load::<UserRow>(&mut connection)
            .map_err(IdentityError::database)?
            .into_iter()
            .map(user_from_row)
            .collect()
    }

    pub fn create_user(
        &self,
        id: UserId,
        input: CreateUserInput,
        now: Instante,
    ) -> Result<User, IdentityError> {
        let mut connection = self.connection()?;
        connection
            .transaction(|connection| self.create_user_in_transaction(connection, id, input, now))
    }

    /// Persiste un usuario sobre una transaccion que tambien puede escribir
    /// capacidades transversales, como auditoria.
    pub fn create_user_in_transaction(
        &self,
        connection: &mut SqliteConnection,
        id: UserId,
        input: CreateUserInput,
        now: Instante,
    ) -> Result<User, IdentityError> {
        let username = Username::parse(input.username)?;
        let display_name = DisplayName::parse(input.display_name)?;
        let job_title = input.job_title.map(JobTitle::parse).transpose()?;
        let password_hash = PasswordHash::hash(&input.password)?;
        let now_text = now.to_string();
        let row = NewUserRow {
            id: id.as_str(),
            username: username.as_str(),
            display_name: display_name.as_str(),
            role: input.role.as_str(),
            job_title: job_title.as_ref().map(JobTitle::as_str),
            password_hash: password_hash.as_stored(),
            retired_at: None,
            retired_by: None,
            created_at: &now_text,
            updated_at: &now_text,
        };

        diesel::insert_into(users::table)
            .values(row)
            .execute(connection)
            .map_err(IdentityError::database)?;
        user_from_row(
            users::table
                .find(id.as_str())
                .select(UserRow::as_select())
                .first(connection)
                .map_err(IdentityError::database)?,
        )
    }

    pub fn update_user(
        &self,
        id: &UserId,
        input: UpdateUserInput,
        actor: &Id<Actor>,
        now: Instante,
    ) -> Result<User, IdentityError> {
        let mut connection = self.connection()?;
        connection.transaction(|connection| {
            self.update_user_in_transaction(connection, id, input, actor, now)
        })
    }

    /// Actualiza un usuario sobre una transaccion coordinada por `mana-app`.
    pub fn update_user_in_transaction(
        &self,
        connection: &mut SqliteConnection,
        id: &UserId,
        input: UpdateUserInput,
        actor: &Id<Actor>,
        now: Instante,
    ) -> Result<User, IdentityError> {
        let current = users::table
            .find(id.as_str())
            .select(UserRow::as_select())
            .first(connection)
            .optional()
            .map_err(IdentityError::database)?
            .ok_or(IdentityError::NotFound)
            .and_then(user_from_row)?;

        let display_name = input
            .display_name
            .map(DisplayName::parse)
            .transpose()?
            .unwrap_or_else(|| current.display_name.clone());
        let role = input.role.unwrap_or_else(|| current.role.clone());
        let job_title = match input.job_title {
            Some(Some(value)) => Some(JobTitle::parse(value)?),
            Some(None) => None,
            None => current.job_title.clone(),
        };
        let password_hash = input
            .password
            .map(|password| PasswordHash::hash(&password))
            .transpose()?
            .unwrap_or_else(|| current.password_hash().clone());

        let (retired_at, retired_by) = match input.active {
            Some(true) => (None, None),
            Some(false) => (Some(now), Some(actor.clone())),
            None => (current.retired_at, current.retired_by.clone()),
        };
        let retired_at_text = retired_at.as_ref().map(ToString::to_string);
        let retired_by_text = retired_by.as_ref().map(ToString::to_string);
        let now_text = now.to_string();
        let changeset = UserChangeset {
            display_name: display_name.as_str(),
            role: role.as_str(),
            job_title: job_title.as_ref().map(JobTitle::as_str),
            password_hash: password_hash.as_stored(),
            retired_at: retired_at_text.as_deref(),
            retired_by: retired_by_text.as_deref(),
            updated_at: &now_text,
        };
        diesel::update(users::table.find(id.as_str()))
            .set(changeset)
            .execute(connection)
            .map_err(IdentityError::database)?;
        user_from_row(
            users::table
                .find(id.as_str())
                .select(UserRow::as_select())
                .first(connection)
                .map_err(IdentityError::database)?,
        )
    }

    fn connection(&self) -> Result<DbConnection, IdentityError> {
        get_connection(&self.pool).map_err(IdentityError::from)
    }
}

fn user_from_row(row: UserRow) -> Result<User, IdentityError> {
    let UserRow {
        id,
        username,
        display_name,
        role,
        job_title,
        password_hash,
        retired_at,
        retired_by,
        created_at,
        updated_at,
    } = row;
    let parse_instant = |label: &str, value: String| {
        value
            .parse::<Instante>()
            .map_err(|error| IdentityError::InvalidStoredData(format!("{label}: {error}")))
    };
    let retired_at = retired_at
        .map(|value| parse_instant("retired_at", value))
        .transpose()?;
    let retired_by = retired_by.map(Id::<Actor>::new);
    Ok(User::from_persisted(PersistedUser {
        id: UserId::new(id),
        username: Username::parse(username)?,
        display_name: DisplayName::parse(display_name)?,
        role: role.parse::<Role>()?,
        job_title: job_title.map(JobTitle::parse).transpose()?,
        retired_at,
        retired_by,
        created_at: parse_instant("created_at", created_at)?,
        updated_at: parse_instant("updated_at", updated_at)?,
        password_hash: PasswordHash::from_stored(password_hash)?,
    }))
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::domain::{CreateUserInput, Role, UpdateUserInput};
    use chrono::Duration;
    use mana_storage::build_pool;

    fn instant() -> Instante {
        "2026-08-18T12:00:00.000Z".parse().unwrap()
    }

    #[test]
    fn migrates_and_persists_the_complete_identity_lifecycle() {
        let pool = build_pool(":memory:").unwrap();
        run_migrations(&pool).unwrap();
        let store = IdentityStore::new(pool);
        let user_id = UserId::new("user-gaston");

        let user = store
            .create_user(
                user_id.clone(),
                CreateUserInput {
                    username: " Gaston ".to_owned(),
                    display_name: "Gaston".to_owned(),
                    role: Role::Supervisor,
                    job_title: Some("Director".to_owned()),
                    password: "gaston-demo".to_owned(),
                },
                instant(),
            )
            .unwrap();
        assert_eq!(user.username.as_str(), "gaston");
        assert!(user.is_active());

        let session = store
            .login("GASTON", "gaston-demo", instant())
            .unwrap()
            .unwrap();
        assert_eq!(session.token.as_str().len(), 43);
        assert_eq!(
            store
                .authenticate(session.token.as_str(), instant())
                .unwrap()
                .unwrap()
                .id
                .as_str(),
            "user-gaston"
        );

        let actor = Id::<Actor>::new("user-gaston");
        let retired = store
            .update_user(
                &user_id,
                UpdateUserInput {
                    display_name: None,
                    role: None,
                    job_title: Some(None),
                    active: Some(false),
                    password: None,
                },
                &actor,
                instant(),
            )
            .unwrap();
        assert!(!retired.is_active());
        assert!(store
            .login("gaston", "gaston-demo", instant())
            .unwrap()
            .is_none());
        assert_eq!(store.list_users(false).unwrap().len(), 0);
        assert_eq!(store.list_users(true).unwrap().len(), 1);

        store.logout(session.token.as_str()).unwrap();
        assert!(store
            .authenticate(session.token.as_str(), instant())
            .unwrap()
            .is_none());
    }

    #[test]
    fn duplicate_username_is_a_domain_conflict() {
        let pool = build_pool(":memory:").unwrap();
        run_migrations(&pool).unwrap();
        let store = IdentityStore::new(pool);
        let input = || CreateUserInput {
            username: "same".to_owned(),
            display_name: "Same".to_owned(),
            role: Role::Staff,
            job_title: None,
            password: "secret1".to_owned(),
        };
        store
            .create_user(UserId::new("user-1"), input(), instant())
            .unwrap();
        assert!(matches!(
            store.create_user(UserId::new("user-2"), input(), instant()),
            Err(IdentityError::Conflict)
        ));
    }

    #[test]
    fn last_seen_is_throttled_to_avoid_a_write_per_authenticated_request() {
        let pool = build_pool(":memory:").unwrap();
        run_migrations(&pool).unwrap();
        let store = IdentityStore::new(pool);
        store
            .create_user(
                UserId::new("user-1"),
                CreateUserInput {
                    username: "one".to_owned(),
                    display_name: "One".to_owned(),
                    role: Role::Staff,
                    job_title: None,
                    password: "secret1".to_owned(),
                },
                instant(),
            )
            .unwrap();
        let session = store.login("one", "secret1", instant()).unwrap().unwrap();
        store
            .authenticate(session.token.as_str(), instant())
            .unwrap();

        let seen_at = || {
            let hash = TokenHash::from_token(session.token.as_str());
            let mut connection = store.pool().get().unwrap();
            auth_sessions::table
                .find(hash.as_bytes().to_vec())
                .select(auth_sessions::last_seen_at)
                .first::<Option<String>>(&mut connection)
                .unwrap()
        };
        let first = seen_at();
        store
            .authenticate(
                session.token.as_str(),
                Instante::new(*instant().as_datetime() + Duration::seconds(30)),
            )
            .unwrap();
        assert_eq!(seen_at(), first);
        store
            .authenticate(
                session.token.as_str(),
                Instante::new(*instant().as_datetime() + Duration::seconds(61)),
            )
            .unwrap();
        assert_ne!(seen_at(), first);
    }
}
