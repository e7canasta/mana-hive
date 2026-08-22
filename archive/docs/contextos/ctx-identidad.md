# Contexto `ctx-identidad`

Clase: generico.

## Pregunta

Quien puede entrar al hub y que capabilities tiene el actor autenticado?

## Lenguaje y ownership

Este contexto posee usuarios, credenciales y sesiones. No posee roles
asistenciales como enfermeria o medico; esos son valores opcionales de
`job_title`. Un role es un perfil de acceso, no un puesto.

No posee los chequeos de autorizacion de otros contextos. Publica hacia
`mana-app` un actor autenticado y su conjunto de capabilities resueltas.

## Agregados

### `User`

Raiz de la persona con acceso. Posee identidad de login, role de acceso, nombre,
puesto y hash de password.

### `Session`

Raiz de una sesion bearer. El token en claro existe solo en la respuesta de
login y en el cliente. El store guarda unicamente su hash.

Capabilities y features se derivan del role y de la configuracion activa de la
plataforma. No son una tabla mutable de este contexto.

## Tablas

### `users`

```text
id             TEXT PRIMARY KEY
username       TEXT NOT NULL UNIQUE       -- normalized lowercase
display_name   TEXT NOT NULL
role           TEXT NOT NULL              -- supervisor | staff
job_title      TEXT NULL
password_hash  TEXT NOT NULL              -- PHC Argon2id string
retired_at     TEXT NULL
retired_by     TEXT NULL                 -- actor id, opaque
created_at     TEXT NOT NULL
updated_at     TEXT NOT NULL
```

La base puede usar un `CHECK` para el vocabulario cerrado de roles. `active` es
una proyeccion wire (`retired_at IS NULL`), no un segundo modelo persistido de
borrado.

### `auth_sessions`

```text
token_hash     BLOB PRIMARY KEY           -- exactly 32 bytes, SHA-256
user_id        TEXT NOT NULL
expires_at     TEXT NOT NULL
created_at     TEXT NOT NULL
last_seen_at   TEXT NULL
```

`user_id` es una referencia opaca de identidad. La consulta de sesion exige que
el usuario no este retirado y que la expiracion sea futura. Logout puede borrar
la sesion; limpiar filas vencidas es una tarea operativa.

Indexes:

- `(user_id, expires_at)` for session cleanup;
- `username` is unique after normalization.

## Tipos de dominio

- `UserId` and `SessionId` are typed IDs.
- `Username` trims and lowercases at the boundary.
- `DisplayName` and `JobTitle` validate length and non-empty rules.
- `Role` is a closed enum.
- `Capability` and `Feature` are closed value sets owned by this context.
- `PasswordHash` has no public formatting or logging implementation.
- `ClearSessionToken` is not persisted and is not accepted by a row mapper.
- `TokenHash` can only be constructed by hashing a clear token.

## Invariantes

1. A username is unique in normalized form.
2. A retired user cannot authenticate or create a new session.
3. A session stores only the token hash.
4. An expired session never authenticates.
5. The role and job title are independent axes.
6. Capabilities come from role policy intersected with enabled platform
   capabilities; the client cannot submit them.
7. Logout is safe to repeat from the HTTP perspective: deleting an absent
   session still returns `204`.
8. Password verification is constant-time at the hash comparison boundary.

## API

La forma wire publica sigue al cliente existente donde la semantica es clara.
Los DTOs Rust se escriben manualmente contra OpenAPI.

### `POST /api/v1/auth/login`

Request:

```json
{ "username": "gaston", "password": "gaston-demo" }
```

Response `200`:

```json
{
  "token": "opaque-bearer-token",
  "expires_at": "2026-08-18T20:00:00.000Z",
  "user": {
    "id": "user-1",
    "username": "gaston",
    "display_name": "Gaston",
    "role": "supervisor",
    "features": ["nursing", "residents", "alerts", "reports", "configuration"],
    "permissions": ["nursing", "residents", "alerts", "reports", "configuration"],
    "capabilities": ["master.structure.read"]
  }
}
```

`features` y `permissions` se mantienen en la respuesta wire porque los
clientes existentes los consumen. Internamente hay un solo conjunto de features
derivado, no dos fuentes de verdad.

Errors: `422 VALIDATION_ERROR`, `401 INVALID_CREDENTIALS`, `429 RATE_LIMITED`.

### `GET /api/v1/auth/me`

Requiere un token bearer. Devuelve `{ "user": AuthUser }`, o
`401 UNAUTHENTICATED`.

### `POST /api/v1/auth/logout`

Requiere un token bearer y devuelve `204`. La operacion es idempotente en el
borde de transporte aunque se borre la fila de sesion.

### `GET /api/v1/users?include_inactive=1`

Requiere `master.structure.read`. Devuelve `{ "users": AdminUser[] }`.
Los usuarios inactivos se incluyen solo cuando el parametro vale `1`.

### `POST /api/v1/users`

Requiere `master.structure.write`. Crea un usuario con username, nombre, role,
puesto opcional y password. Devuelve `201 { "user": AdminUser }`.

### `PATCH /api/v1/users/{userId}`

Requiere `master.structure.write`. Cambia nombre, role, puesto, password o
estado activo. El `active` booleano/numero del wire se mapea a retiro en el
dominio.

## Puertos de aplicacion

`mana-app` posee la solicitud autenticada y pasa:

```text
AuthenticatedActor { user_id, capabilities, features }
```

Ningun otro `ctx-*` importa este crate para autorizar. Un futuro alcance por
residencia es una preocupacion de autorizacion de aplicacion y no debe ocultarse
dentro de `Role`.

## Tests

- username normalization and uniqueness;
- Argon2id hash verification and wrong-password rejection;
- token hash never appears in a response or debug log;
- session expiration;
- retired user rejection;
- role capability intersection;
- login rate limit and `401` versus `403`;
- exact login, me, logout and users response shapes;
- empty SQLite bootstrap and seed login.

## No posee

- audit rows;
- staff groups or coverage;
- facility scope policy;
- business permissions inside a domain operation;
- client-side feature flags.
