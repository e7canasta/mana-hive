# Modelo de dominio: `ctx-auditoria`

## Pregunta del contexto

Que cambio, quien lo hizo, sobre que registro y en que instante?

## Objeto de dominio

### `AuditEntry` - hecho inmutable

Una entrada no representa el estado actual de una entidad. Representa el hecho
de que una mutacion fue confirmada.

```text
AuditEntry
  actor_id?     -> referencia opaca a Actor
  action        -> verbo estable: user.created, room.updated, ...
  entity_type   -> tipo funcional
  entity_id     -> identidad opaca del owner
  metadata      -> contexto forense JSON
  created_at    -> reloj del servidor
```

`AuditRecord` es el comando de escritura. `AuditFilter` es el objeto de consulta.
Ninguno es una entidad de negocio de otro contexto.

## Relaciones de dominio

```text
Actor 0..1 ---- 0..* AuditEntry
Entidad de negocio 1 ---- 0..* AuditEntry
```

Las relaciones son referencias opacas (`actor_id`, `entity_type`, `entity_id`).
Auditoria no hace foreign keys hacia usuarios ni hacia facilities, porque debe
seguir existiendo aunque el owner cambie o sea retirado.

## Invariantes

- append-only: no update ni delete desde la aplicacion;
- accion, tipo e ID no vacios;
- metadata JSON objeto valido;
- metadata menor o igual a 16 KiB;
- timestamp asignado por servidor;
- una mutacion auditable y su entrada tienen el mismo commit.

## Mapeo a casos de uso

| Caso                          | Objetos usados              | Regla principal                       | Servicio de aplicacion  |
| ----------------------------- | --------------------------- | ------------------------------------- | ----------------------- |
| AUD-01 Registrar mutacion     | `AuditRecord`, `AuditEntry` | no confirmar negocio sin auditoria    | `record_in_transaction` |
| AUD-02 Consultar trazabilidad | `AuditFilter`, `AuditEntry` | leer sin modificar; limite maximo 500 | `list_audit`            |

AUD-01 es invocado por los casos de uso de identidad y residencia. No es una
dependencia Cargo de esos contextos: `mana-app` coordina el puerto.

Detalle funcional: [`../casos-uso/ctx-auditoria.md`](../casos-uso/ctx-auditoria.md).

## Mapeo a modelo de datos

| Objeto           | Tabla       | Columnas principales                                                                  | Restricciones e indices                               | Migracion    |
| ---------------- | ----------- | ------------------------------------------------------------------------------------- | ----------------------------------------------------- | ------------ |
| `AuditEntry`     | `audit_log` | `id`, `actor_id`, `action`, `entity_type`, `entity_id`, `metadata_json`, `created_at` | checks de labels; indices por entidad, actor y accion | `0002_audit` |
| `AuditRecord`    | ninguna     | se valida antes de insert                                                             | comando transitorio                                   | ninguna      |
| `AuditFilter`    | ninguna     | se traduce a query                                                                    | default 100, maximo 500                               | ninguna      |
| `AuditEntryView` | ninguna     | agrega `actor_name`                                                                   | read model de `mana-app`                              | ninguna      |

`metadata_json` es persistencia de contexto forense, no un sustituto de tablas
para usuarios, habitaciones o residentes.

## Realizacion

- Dominio: `crates/ctx-auditoria/src/domain.rs`.
- Persistencia: `crates/ctx-auditoria/src/store.rs`.
- Aplicacion: `crates/mana-app/src/auditoria.rs`.
- Transporte: `crates/mana-http/src/audit.rs`.
- Migracion: `crates/ctx-auditoria/migrations/0002_audit/`.
