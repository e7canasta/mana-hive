# Contexto `ctx-auditoria`

Clase: generico.

## Pregunta

Que cambio, quien lo hizo, sobre que registro y en que instante?

## Lenguaje y ownership

Este contexto posee la traza de auditoria inmutable. No posee la mutacion de
negocio que produjo una entrada ni decide si una mutacion esta permitida.

Cada contexto que tiene una mutacion relevante declara un puerto de auditoria
pequeno desde el lado consumidor. `mana-app` adapta ese puerto a este contexto.
Ningun contexto de negocio declara una dependencia Cargo a `ctx-auditoria`.

## Agregado

### `AuditEntry`

Registro append-only. No se edita ni se borra mediante la API de aplicacion. La
metadata aporta contexto forense; no reemplaza un modelo de negocio consultable.

## Tabla: `audit_log`

```text
id             TEXT PRIMARY KEY
actor_id       TEXT NULL                  -- opaque UserId
action         TEXT NOT NULL              -- stable verb, e.g. user.created
entity_type    TEXT NOT NULL
entity_id      TEXT NOT NULL
metadata_json  TEXT NOT NULL DEFAULT '{}'
created_at     TEXT NOT NULL
```

Indexes:

- `(entity_type, entity_id, created_at)`;
- `(actor_id, created_at)`;
- `(action, created_at)` when operational queries require it.

No hay foreign key a `users`: la auditoria debe sobrevivir al retiro de un
usuario y el contexto no puede poseer la tabla de otro contexto.

## Invariantes

1. Una entrada es append-only.
2. `action`, `entity_type` y `entity_id` no son vacios.
3. `created_at` lo asigna el reloj del servidor, no un cliente no confiable.
4. La metadata es JSON valido y tiene un limite de tamaño.
5. Una mutacion que el contexto declara auditable no puede confirmar sin su
   audit entry in the same SQLite transaction.
6. Leer auditoria nunca la modifica.

## API

### `GET /api/v1/audit-log`

Requiere `audit.read`.

Query parameters:

- `limit`, capped by the server;
- `entity_type`;
- `entity_id`;
- optional `action` and time range once the query contract needs them.

Response:

```json
{
  "audit": [
    {
      "id": "audit-1",
      "actor_id": "user-1",
      "actor_name": "Gaston",
      "action": "user.created",
      "entity_type": "user",
      "entity_id": "user-2",
      "metadata": { "role": "staff" },
      "created_at": "2026-08-18T12:00:00.000Z"
    }
  ]
}
```

El nombre visible del actor es un enriquecimiento del read model. La fila de
auditoria conserva el ID opaco y no copia identidad mutable como autoridad.

## Puerto interno

El puerto es deliberadamente pequeño:

```text
record(actor_id, action, entity_type, entity_id, metadata) -> Result
```

Lo define el servicio de aplicacion que lo necesita. El writer concreto
pertenece a este contexto. Los tests pueden usar un collector en memoria sin
importar el store.

## Tests

- comportamiento append-only;
- el rollback de una transaccion tambien revierte la entrada de auditoria;
- validacion y limite de metadata;
- limites de filtros y paginacion;
- una mutacion de usuario y una de estructura producen exactamente una entrada.

## No posee

- autenticacion o capabilities;
- historia de estados de dominio como transiciones de alertas;
- analytics del cliente;
- un bus de eventos generico.
