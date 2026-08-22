# Funcion: `ctx-identidad`

## Proposito

Controlar quien puede entrar al hub y que capabilities recibe el actor
autenticado. Es un contexto generico: conoce usuarios, credenciales y sesiones,
pero no conoce permisos de negocio especificos de cada dominio.

## Actores

| Actor                       | Puede hacer                                               |
| --------------------------- | --------------------------------------------------------- |
| Visitante                   | Intentar login                                            |
| Usuario autenticado         | Consultar sesion y cerrar sesion                          |
| Administrador de estructura | Listar, crear y actualizar usuarios                       |
| Aplicacion                  | Resolver bearer y capabilities para autorizar operaciones |

## Funciones implementadas

- Normalizar username a lowercase y validar textos.
- Crear sesiones bearer con expiracion.
- Guardar solamente el hash SHA-256 del token.
- Rechazar usuarios retirados y sesiones vencidas.
- Resolver capabilities desde role y configuracion activa.
- Aplicar rate limit local a credenciales invalidas.
- Crear y actualizar usuarios con retiro logico.

## Datos funcionales

| Concepto         | Regla                                                          |
| ---------------- | -------------------------------------------------------------- |
| Username         | Unico despues de normalizar                                    |
| Password         | Nunca sale en una respuesta; se guarda como Argon2id           |
| Token            | El claro solo aparece en login; SQLite guarda su hash          |
| Role             | `supervisor` o `staff`                                         |
| Usuario retirado | No inicia sesion y no resuelve bearer                          |
| Capabilities     | Derivadas del role e intersectadas con la configuracion activa |

## Flujo de autenticacion

```text
HTTP bearer
  -> mana-app
  -> hash del token
  -> auth_sessions
  -> usuario activo
  -> actor + capabilities
```

El `last_seen_at` se actualiza como maximo una vez por minuto para evitar una
escritura en cada request autenticada.

## Endpoints activos en Rust

- `POST /api/v1/auth/login`
- `GET /api/v1/auth/me`
- `POST /api/v1/auth/logout`
- `GET /api/v1/users`
- `POST /api/v1/users`
- `PATCH /api/v1/users/{userId}`

Todos estan marcados como `sirve = "rust"` en `rutas.toml`.

## Auditoria asociada

Las operaciones `create_user` y `update_user` se confirman junto con:

- `user.created`;
- `user.updated`.

Si falla la auditoria, falla la mutacion completa.

## Errores que entiende el cliente

- `VALIDATION_ERROR`: body incompleto o valor invalido.
- `INVALID_CREDENTIALS`: username o password no validos.
- `UNAUTHENTICATED`: falta bearer valido.
- `FORBIDDEN`: actor autenticado sin capability requerida.
- `CONFLICT`: username ya utilizado.
- `RATE_LIMITED`: demasiados intentos fallidos.

## No es responsabilidad de identidad

- Decidir si un actor puede modificar una room concreta.
- Resolver ocupacion de camas.
- Poseer la auditoria.
- Mantener flags de UI como una segunda fuente de verdad.

## Verificacion

La cobertura funcional esta en los tests de `mana-app`, `mana-http` y
`ctx-identidad`, incluyendo login, capabilities, usuarios, logout, expiracion,
rate limit y formas wire.
