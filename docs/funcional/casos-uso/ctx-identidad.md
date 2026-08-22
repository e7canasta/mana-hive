# Casos de uso: `ctx-identidad`

## Frontera funcional

`ctx-identidad` decide quien puede iniciar una sesion en el Registro y que actor
autenticado se entrega a la aplicacion. Posee usuarios, credenciales y sesiones.

No decide si una persona puede modificar una room, una cama o una alerta. Esas
decisiones usan capabilities en `mana-app`.

## Reglas del contexto

- El username es unico despues de trim y lowercase.
- Un usuario retirado no puede autenticarse.
- Un token vencido no autentica.
- Solo se persiste el hash del bearer, nunca el token claro.
- El password se persiste como hash Argon2id.
- `role` es perfil de acceso; `job_title` es informacion descriptiva.
- Las capabilities se derivan del role y de las capabilities activas del
  proceso; el cliente no las envia.
- Retirar un usuario no borra su historia de auditoria.

## IAM-01 - Iniciar sesion

**Objetivo:** obtener una sesion bearer para operar el Registro.

**Actor primario:** usuario no autenticado.

**Disparador:** el actor envia username y password.

**Precondiciones:** el servicio esta iniciado y el contexto tiene migraciones
aplicadas.

**Flujo principal:**

1. El sistema normaliza el username.
2. Busca el usuario por username normalizado.
3. Comprueba que el usuario esta activo.
4. Verifica el password contra el hash Argon2id.
5. Genera un token bearer y calcula su hash SHA-256.
6. Persiste la sesion con expiracion y devuelve el token claro una sola vez.
7. Deriva el usuario publico y sus capabilities efectivas.

**Alternos y excepciones:**

- username inexistente, password incorrecto o usuario retirado: mismo resultado
  funcional `INVALID_CREDENTIALS`; no se crea sesion.
- body incompleto: `VALIDATION_ERROR`.
- demasiados intentos invalidos del mismo cliente: `RATE_LIMITED`. El contador es
  responsabilidad del adapter HTTP, no del dominio de identidad.

**Postcondiciones:** existe como maximo una nueva fila de sesion para el intento
exitoso; el cliente posee un bearer que puede presentar a los siguientes casos.

**Realizacion:** `mana-app::login` -> `IdentityStore::login` ->
`POST /api/v1/auth/login`.

## IAM-02 - Resolver actor autenticado

**Objetivo:** convertir un bearer en un actor activo con capabilities para un
caso protegido.

**Actor primario:** un caso de uso protegido en `mana-app`.

**Disparador:** llega un bearer en `Authorization`.

**Precondiciones:** el token no esta vacio.

**Flujo principal:**

1. Se hashea el token recibido.
2. Se busca la sesion por su hash.
3. Se verifica que la expiracion sea futura.
4. Se carga el usuario y se verifica que siga activo.
5. Se actualiza `last_seen_at` como maximo una vez por minuto.
6. Se devuelve `AuthenticatedActor` con ID, role, features y capabilities.

**Alternos y excepciones:** cualquier ausencia, vencimiento o retiro produce
`UNAUTHENTICATED`. No se revela si fallo el token o el usuario.

**Postcondiciones:** ningun estado de negocio cambia; el caso llamador puede
aplicar su capability.

**Realizacion:**
`authenticated_actor_in_transaction`/`IdentityStore::authenticate_in_transaction`.
Tambien se expone mediante `GET /api/v1/auth/me`.

## IAM-03 - Cerrar sesion

**Objetivo:** impedir que un bearer deje de ser utilizable antes de su expiracion.

**Actor primario:** actor autenticado.

**Disparador:** el actor solicita logout.

**Precondiciones:** el bearer resuelve un actor activo.

**Flujo principal:**

1. Se resuelve el actor con IAM-02.
2. Se elimina la sesion por hash de token.
3. Se responde sin contenido.

**Alternos:** repetir el borrado de la misma sesion no produce una mutacion de
negocio adicional; el borde HTTP conserva la operacion como `204` mientras el
token sea autenticable al comenzar.

**Postcondiciones:** el token ya no resuelve una sesion valida.

**Realizacion:** `mana-app::logout` -> `IdentityStore::logout` ->
`POST /api/v1/auth/logout`.

## IAM-04 - Crear cuenta de acceso

**Objetivo:** habilitar a una nueva persona para entrar al Registro.

**Actor primario:** operador con `master.structure.write`.

**Disparador:** el operador envia username, nombre, role, password y job title
opcional.

**Precondiciones:** actor autenticado y capability de escritura.

**Flujo principal:**

1. Se resuelve el actor y se verifica la capability.
2. Se valida role, password y valores de texto.
3. Se normaliza el username.
4. Se genera un `UserId`.
5. Se guarda el usuario con password hasheado y estado activo.
6. Se registra `user.created` en la misma transaccion.
7. Se devuelve la vista administrativa sin password ni token.

**Alternos y excepciones:**

- username ya usado: `CONFLICT` y no se registra auditoria.
- password menor a seis caracteres, role invalido o texto invalido:
  `VALIDATION_ERROR`.
- falta de capability: `FORBIDDEN`.
- fallo de auditoria: rollback de usuario y auditoria.

**Postcondiciones:** el nuevo usuario puede iniciar sesion y el hecho de alta es
consultable.

**Realizacion:** `mana-app::create_user` ->
`IdentityStore::create_user_in_transaction` + `AuditStore::record_in_transaction`
-> `POST /api/v1/users`.

## IAM-05 - Mantener cuenta de acceso

**Objetivo:** corregir datos de una cuenta, cambiar su acceso o retirarla sin
borrar la historia.

**Actor primario:** operador con `master.structure.write`.

**Disparador:** `PATCH /api/v1/users/{userId}`.

**Precondiciones:** actor autorizado, usuario destino existente y al menos un
campo de cambio.

**Flujo principal:**

1. Se resuelve el actor y se verifica la capability.
2. Se carga el usuario destino.
3. Se validan solo los campos presentes.
4. Se actualizan nombre, role, job title, password o estado.
5. `active = false` escribe `retired_at` y `retired_by`.
6. `active = true` limpia el retiro y reactiva el usuario.
7. Se registra `user.updated` con los campos modificados.

**Alternos y excepciones:**

- usuario inexistente: `NOT_FOUND`.
- PATCH vacio: `VALIDATION_ERROR`.
- password o role invalido: `VALIDATION_ERROR`.
- fallo de auditoria: rollback completo.

**Postcondiciones:** el usuario refleja el cambio; si quedo retirado, no puede
crear nuevas sesiones; la auditoria conserva quien y que cambio.

**Realizacion:** `mana-app::update_user` ->
`IdentityStore::update_user_in_transaction` + auditoria.

## IAM-06 - Consultar cuentas

**Objetivo:** permitir que un operador conozca las cuentas administrables.

**Actor primario:** operador con `master.structure.read`.

**Disparador:** `GET /api/v1/users`.

**Flujo principal:**

1. Se resuelve el actor y se verifica la capability.
2. Por defecto se consultan usuarios activos.
3. Con `include_inactive=1` se agregan usuarios retirados.
4. Se ordena por nombre visible y username.

**Postcondiciones:** no cambia usuarios, sesiones ni auditoria.

**Realizacion:** `mana-app::list_users` -> `IdentityStore::list_users`.

## Lo que todavia no es un caso activo

- scopes por facility o wing;
- delegacion temporal de capabilities;
- recuperacion de password;
- limpieza operativa de sesiones vencidas como job separado.
