# Modelo de dominio: `ctx-identidad`

## Pregunta del contexto

Quien puede entrar al Registro y que actor autenticado recibe la aplicacion?

## Objetos de dominio

### `User` - entidad de acceso

Representa una identidad que puede iniciar sesion.

Responsabilidades:

- conservar username normalizado;
- conservar nombre visible, role y job title;
- verificar password contra `PasswordHash`;
- saber si esta activo o retirado;
- exponer una vista publica sin password ni token;
- derivar capabilities junto con la politica activa de plataforma.

El role no es un puesto asistencial. `job_title` describe a la persona y no
autoriza por si mismo.

### `Session` - entidad de autenticacion

Representa la autorizacion temporal obtenida despues de un login.

Responsabilidades:

- conservar solo `TokenHash`;
- asociarse a un `UserId`;
- expirar en un instante determinado;
- actualizar `last_seen_at` con throttling;
- dejar de autenticar al hacer logout o retirar el usuario.

### `AuthenticatedActor` - read model de aplicacion

No es una tabla ni un agregado persistido. Es el resultado de resolver una
sesion:

```text
User + Session valida + capabilities activas
    -> AuthenticatedActor
```

`mana-app` lo usa para autorizar los casos de otros contextos.

### Value objects

| Tipo                     | Regla                                           |
| ------------------------ | ----------------------------------------------- |
| `UserId`                 | ID opaco de usuario                             |
| `SessionId` / token hash | El token claro no se persiste                   |
| `Username`               | trim, lowercase, no vacio                       |
| `Role`                   | vocabulario cerrado: `supervisor`, `staff`      |
| `PasswordHash`           | hash Argon2id validable, no logueable           |
| `Capability`             | permiso cerrado resuelto por politica           |
| `Feature`                | feature derivada para compatibilidad de cliente |

## Relaciones de dominio

```text
User 1 -------- 0..* Session
  |
  +--> Role --> capabilities efectivas
  |
  +--> retired_at? bloquea nuevas sesiones
```

Las sesiones dependen de un usuario para autenticarse, pero el token se busca
por hash y el usuario debe seguir activo. El retiro no borra necesariamente las
sesiones historicas; impide resolverlas.

## Invariantes

- username unico despues de normalizar;
- usuario retirado no autentica;
- sesion vencida no autentica;
- token claro nunca sale del limite de login;
- password nunca aparece en wire ni debug;
- role y job title son ejes independientes;
- capabilities no son input del cliente.

## Mapeo a casos de uso

| Caso                     | Objetos usados                                | Regla principal               | Servicio de aplicacion |
| ------------------------ | --------------------------------------------- | ----------------------------- | ---------------------- |
| IAM-01 Iniciar sesion    | `User`, `Session`, `Username`, `PasswordHash` | activo + password valido      | `login`                |
| IAM-02 Resolver actor    | `Session`, `User`, `TokenHash`                | token vigente + user activo   | `authenticated_actor`  |
| IAM-03 Cerrar sesion     | `Session`, `TokenHash`                        | eliminar sesion autenticada   | `logout`               |
| IAM-04 Crear cuenta      | `User`, `Role`, `PasswordHash`                | username y password validos   | `create_user`          |
| IAM-05 Mantener cuenta   | `User`, `Role`, `retired_at`                  | PATCH no vacio; retiro logico | `update_user`          |
| IAM-06 Consultar cuentas | `User`                                        | capability de lectura         | `list_users`           |

Detalle funcional: [`../casos-uso/ctx-identidad.md`](../casos-uso/ctx-identidad.md).

## Mapeo a modelo de datos

| Objeto               | Tabla           | Columnas principales                                                                                           | Restricciones e indices                                            | Migracion       |
| -------------------- | --------------- | -------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------ | --------------- |
| `User`               | `users`         | `id`, `username`, `display_name`, `role`, `job_title`, `password_hash`, `retired_at`, `retired_by`, timestamps | `username UNIQUE`; `role CHECK`                                    | `0001_identity` |
| `Session`            | `auth_sessions` | `token_hash`, `user_id`, `expires_at`, `created_at`, `last_seen_at`                                            | token hash de 32 bytes; FK a `users`; indice por user y expiracion | `0001_identity` |
| `AuthenticatedActor` | ninguna         | se compone en memoria                                                                                          | no es persistencia                                                 | ninguna         |
| `Capability`         | ninguna         | se deriva de role + configuracion                                                                              | no es tabla mutable                                                | ninguna         |

La FK de `auth_sessions.user_id` pertenece al mismo contexto. Auditoria guarda
`actor_id` como referencia opaca y no depende de esta FK.

## Realizacion

- Dominio: `crates/ctx-identidad/src/domain.rs`.
- Persistencia: `crates/ctx-identidad/src/store.rs`.
- Aplicacion: `crates/mana-app/src/identidad.rs`.
- Transporte: `crates/mana-http/src/identity.rs`.
- Migracion: `crates/ctx-identidad/migrations/0001_identity/`.
