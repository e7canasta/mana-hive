# Casos de uso: ctx-vigilancia

## VIG-01 Crear alerta

**Precondiciones**: El usuario esta autenticado. La cama existe.

**Flujo**:

1. El usuario envia POST /api/v1/alerts con bed_id, evidence_kind, rule_id, level, title, occurred_at.
2. El sistema valida evidence_kind (sensor_event, dwell_window, manual).
3. El sistema valida level (low, medium, high, critical).
4. El sistema crea la alerta en estado open.
5. El sistema registra la primera transicion (null -> open).
6. El sistema devuelve la alerta con escalation y delivery_summary.

**Postcondiciones**: La alerta existe en estado open con una transicion inicial.

## VIG-02 Transicionar alerta a acknowledged

**Precondiciones**: La alerta existe en estado open.

**Flujo**:

1. El usuario envia PATCH /api/v1/alerts/{id} con to_status = "acknowledged".
2. El sistema valida que la transicion open -> acknowledged es legal.
3. El sistema valida que hay un actor_id.
4. El sistema actualiza el estado de la alerta.
5. El sistema registra la transicion (open -> acknowledged).
6. El sistema devuelve la alerta actualizada.

**Postcondiciones**: La alerta esta en estado acknowledged con actor y timestamp.

## VIG-03 Transicionar alerta a attending

**Precondiciones**: La alerta existe en estado acknowledged.

**Flujo**:

1. El usuario envia PATCH /api/v1/alerts/{id} con to_status = "attending".
2. El sistema valida que la transicion acknowledged -> attending es legal.
3. El sistema actualiza el estado.
4. El sistema registra la transicion.

**Postcondiciones**: La alerta esta en estado attending.

## VIG-04 Transicionar alerta a resolved

**Precondiciones**: La alerta existe en estado attending.

**Flujo**:

1. El usuario envia PATCH /api/v1/alerts/{id} con to_status = "resolved".
2. El sistema valida que la transicion attending -> resolved es legal.
3. El sistema actualiza el estado.
4. El sistema registra la transicion.

**Postcondiciones**: La alerta esta en estado resolved.

## VIG-05 Crear entrega de notificacion

**Precondiciones**: La alerta existe.

**Flujo**:

1. El usuario envia POST /api/v1/alerts/{id}/deliveries con recipient_kind, recipient_id, channel.
2. El sistema crea la entrega (append-only).
3. El sistema devuelve la entrega.

**Postcondiciones**: La entrega existe asociada a la alerta.

## VIG-06 Agregar evento a entrega

**Precondiciones**: La entrega existe.

**Flujo**:

1. El usuario envia POST /api/v1/deliveries/{id}/events con kind (sent, acknowledged, failed).
2. El sistema crea el evento (append-only).
3. El sistema devuelve la entrega con sus eventos.

**Postcondiciones**: El evento existe en la entrega. Un retry crea una nueva entrega, no muta la anterior.

## VIG-07 Listar entregas de una alerta

**Precondiciones**: La alerta existe.

**Flujo**:

1. El usuario envia GET /api/v1/alerts/{id}/deliveries.
2. El sistema devuelve las entregas con sus eventos, sent_at, acked_at, failed_reason.

**Postcondiciones**: No modifica estado.

## VIG-08 Auditar acceso a alerta (view)

**Precondiciones**: La alerta existe. El usuario esta autenticado.

**Flujo**:

1. El usuario envia POST /api/v1/alerts/{id}/view.
2. El sistema registra el acceso (en auditoria).
3. El sistema devuelve la alerta.

**Postcondiciones**: El acceso queda registrado en auditoria. No se expone media.
