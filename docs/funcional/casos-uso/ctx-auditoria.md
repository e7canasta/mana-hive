# Casos de uso: `ctx-auditoria`

## Frontera funcional

`ctx-auditoria` conserva los hechos de cambio del Registro. No decide si una
mutacion es valida y no posee las entidades que fueron modificadas.

Su valor de negocio es la trazabilidad: despues de una operacion relevante se
puede responder que paso, quien lo hizo, sobre que registro y cuando.

## Reglas del contexto

- Una entrada es append-only.
- `action`, `entity_type` y `entity_id` son obligatorios y no vacios.
- El reloj del servidor asigna `created_at`.
- Metadata debe ser JSON valido y no superar el limite del dominio.
- No hay foreign key hacia `users`; la historia sobrevive al retiro de un actor.
- Una mutacion auditable y su entrada confirman o revierten juntas.

## AUD-01 - Registrar mutacion relevante

**Objetivo:** dejar evidencia durable de una mutacion de negocio.

**Actor primario:** el caso de uso de `mana-app` que acaba de cambiar un
contexto.

**Disparador:** una mutacion valida esta lista para confirmarse.

**Precondiciones:** existe una transaccion abierta; la mutacion ya paso la
autorizacion y las invariantes de su contexto.

**Flujo principal:**

1. El application service construye una `AuditRecord` con actor, accion, tipo,
   ID y metadata.
2. Auditoria valida los campos y serializa la metadata.
3. Genera un ID de auditoria y un timestamp de servidor.
4. Inserta una fila append-only en `audit_log` usando la misma conexion.
5. Devuelve la entrada al application service.
6. La transaccion confirma negocio y auditoria juntas.

**Alternos y excepciones:**

- accion, entidad o metadata invalidas: la mutacion no puede confirmarse;
- metadata demasiado grande: error de validacion y rollback;
- error de SQLite: rollback de la mutacion y de la auditoria.

**Postcondiciones:** existe una evidencia consultable o no existe la mutacion.
No hay un estado valido donde solo una de las dos quede confirmada.

**Realizacion:** `AuditStore::record_in_transaction`, invocado por
`mana-app::create_user`, `update_user` y los comandos de residencia.

## AUD-02 - Consultar trazabilidad

**Objetivo:** investigar cambios del Registro sin modificar la historia.

**Actor primario:** auditor o actor con `audit.read`.

**Disparador:** `GET /api/v1/audit-log`.

**Precondiciones:** bearer valido y capability `audit.read`.

**Flujo principal:**

1. Se autentica el actor.
2. Se valida y acota `limit`.
3. Se aplican filtros opcionales por tipo, ID y accion.
4. Se ordenan las entradas de la mas reciente a la mas antigua.
5. La aplicacion resuelve `actor_name` como enriquecimiento de lectura.
6. Se devuelve `{ audit }`.

**Alternos y excepciones:**

- actor sin capability: `FORBIDDEN`;
- filtros invalidos o limite fuera de rango: se usa el limite por defecto o se
  acota al maximo definido por el dominio;
- actor retirado: la entrada permanece; el nombre puede no resolverse.

**Postcondiciones:** `audit_log` queda exactamente igual que antes de la
consulta.

**Realizacion:** `mana-app::list_audit` -> `AuditStore::list` ->
`GET /api/v1/audit-log`.

## Mutaciones que hoy dejan evidencia

- `user.created` y `user.updated`;
- `facility.created` y `facility.updated`;
- `wing.created` y `wing.updated`;
- `room.created` y `room.updated`;
- `bed.created` y `bed.updated`.

## Lo que no es auditoria

- No es autenticacion.
- No es autorizacion.
- No es historial de estados clinicos.
- No es un bus de eventos.
- No reemplaza una tabla de negocio consultable.
