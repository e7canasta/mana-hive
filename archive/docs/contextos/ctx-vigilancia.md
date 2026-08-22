# Contexto `ctx-vigilancia`

Clase: nucleo.

## Pregunta

Que alertas hay, quien las atendio, a quien se notifico y que escalamientos
ocurrieron?

## Lenguaje y ownership

Este contexto posee el ciclo operativo de alertas, referencias de evidencia,
historia de entregas y estado de escalamiento. No posee el evento de sensor, la
politica del residente ni la cobertura de staff.

La alerta es un registro de negocio, no una copia de un evento del detector. Un
evento o una ventana de permanencia pueden ser evidencia de una alerta; la
evidencia sigue siendo externa e inmutable.

## Agregados

### `Alert`

La raiz posee estado actual, referencia de evidencia, ID de regla, severidad y
estado de escalamiento. Su estado de dominio es un enum:

```rust
enum AlertStatus {
    Open,
    Acknowledged { by: UserId, at: Instant },
    Attending { by: UserId, at: Instant },
    Resolved { by: UserId, at: Instant },
}
```

Una transicion invalida es un error de dominio. Una alerta reconocida sin actor
no es representable.

### `NotificationDelivery`

Un intento de entrega. Es append-only. Un retry crea otra entrega; nunca edita el
intento anterior.

## Tablas

### `alerts`

```text
id                 TEXT PRIMARY KEY
resident_id        TEXT NULL
bed_id             TEXT NOT NULL
evidence_kind      TEXT NOT NULL       -- sensor_event | dwell_window | manual
evidence_ref       TEXT NULL
rule_id            TEXT NOT NULL       -- closed catalog vocabulary
level              TEXT NOT NULL       -- low | medium | high | critical
status             TEXT NOT NULL       -- open | acknowledged | attending | resolved
status_actor_id    TEXT NULL
status_at          TEXT NULL
title              TEXT NOT NULL
detail             TEXT NULL
occurred_at        TEXT NOT NULL
escalation_level   INTEGER NOT NULL DEFAULT 0
escalated_at       TEXT NULL
escalated_to       TEXT NULL
created_at         TEXT NOT NULL
updated_at         TEXT NOT NULL
```

El mapper de base valida el conjunto `status`, `status_actor_id` y `status_at`;
el enum de dominio es la proteccion principal. `rule_id` no es un tipo de
incidente clinico.

### `alert_transitions`

```text
id                 TEXT PRIMARY KEY
alert_id           TEXT NOT NULL
from_status        TEXT NULL
to_status          TEXT NOT NULL
actor_id           TEXT NULL
occurred_at        TEXT NOT NULL
sequence           INTEGER NOT NULL
```

Esta historia append-only permite revisar operaciones sin convertir todo el
Registro en event sourcing.

### `notification_deliveries`

```text
id                 TEXT PRIMARY KEY
alert_id           TEXT NOT NULL
recipient_kind     TEXT NOT NULL       -- user | staff_group | service
recipient_id       TEXT NOT NULL
channel            TEXT NOT NULL       -- push | tablet | sms | other
escalation_level   INTEGER NOT NULL DEFAULT 0
created_at         TEXT NOT NULL
```

El intento de entrega es inmutable. Sus resultados posteriores se registran como
eventos append-only:

### `notification_delivery_events`

```text
id                 TEXT PRIMARY KEY
delivery_id        TEXT NOT NULL
kind               TEXT NOT NULL       -- sent | acknowledged | failed
reason             TEXT NULL
occurred_at        TEXT NOT NULL
```

El read model expone `sent_at`, `acked_at` y `failed_reason` al plegar los
eventos de entrega. Un retry es otra fila de entrega, nunca una actualizacion que
oculte un intento anterior.

### `alert_escalations`

```text
id                 TEXT PRIMARY KEY
alert_id           TEXT NOT NULL
level              INTEGER NOT NULL
target_id          TEXT NOT NULL
occurred_at        TEXT NOT NULL
created_at         TEXT NOT NULL
```

El resumen de escalamiento actual queda en `alerts`; esta tabla conserva cada
escalamiento por si una politica escala mas de una vez.

## Invariantes

1. El orden de transicion es `open -> acknowledged -> attending ->
   resolved`.
2. Cada estado distinto de open lleva actor y timestamp.
3. `rule_id` viene del catalogo de alarmas cargado.
4. La evidencia de alerta es explicita: evento, ventana de permanencia o
   comando manual.
5. Una alerta de permanencia puede crearse por el paso del tiempo, no solo por
   un evento nuevo del detector.
6. El escalamiento es estado persistido con destino y timestamp, no un efecto
   invisible de un timer.
7. Entregas y resultados son append-only y pueden contarse independiente del
   acuse.
8. La historia de escalamiento es append-only aunque la alerta exponga su nivel
   actual como read model.
9. Una cama sin residente puede seguir vigilada por una politica fija de fallback.
10. `POST /alerts/{id}/view` audita el acceso a imagen, pero no expone ni
    persiste media en este contexto.

## API

- `GET /api/v1/alerts`
- `POST /api/v1/alerts`
- `PATCH /api/v1/alerts/{alertId}`
- `POST /api/v1/alerts/{alertId}/view`
- `GET /api/v1/alerts/{alertId}/deliveries`
- `GET /api/v1/wings/{wingId}/board` as an application read model
- `GET /api/v1/residents/{residentId}/current-state` as an application read
  model
- internal event ingestion and dwell sweep as application commands

La respuesta de alerta conserva los campos actuales amigables para el cliente y
agrega datos explicitos cuando se habilita notificacion:

```json
{
  "alert": {
    "id": "alert-1",
    "rule_id": "bed_exit",
    "status": "open",
    "escalation": { "level": 0, "escalated_at": null, "escalated_to": null },
    "delivery_summary": { "sent": 1, "acked": 0, "failed": 0 }
  }
}
```

## Puertos entre contextos

- politica efectiva de un residente e instante;
- asignacion actual y read model de cama/dispositivo;
- evidencia de observacion y episodio de permanencia;
- cobertura en el instante de la alerta;
- capability de auditoria;
- adapter del transporte de notificaciones.

`mana-app` resuelve estos puertos e inicia la transaccion de alerta. Vigilancia
no consulta tablas de otro contexto.

## Tiempo y scheduling

La operacion de dominio `evaluate_due_dwell(now)` es independiente de su
scheduler. El primero puede ser un loop dentro del proceso; luego puede ser un
worker durable o un workflow Temporal sin cambiar el agregado Alert ni la API.

## Tests

- cada transicion legal e ilegal;
- actor y timestamp no pueden omitirse de un estado distinto de open;
- la colision entre rule ID y tipo de incidente es imposible en Rust;
- una alerta de permanencia aparece al avanzar el reloj sin evento nuevo;
- un retry crea historia en vez de mutar el primer intento;
- el escalamiento sobrevive al reinicio;
- listados, updates, view y entregas cumplen OpenAPI.

## No posee

- evidencia del detector;
- versiones de politica del residente;
- membresia de staff e historia de cobertura;
- revision de incidentes;
- almacenamiento de video o imagen.
