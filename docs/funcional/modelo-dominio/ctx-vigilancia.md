# Modelo de dominio: ctx-vigilancia

## Objetos de dominio

### Alert (raiz)

Posee estado actual, referencia de evidencia, ID de regla, severidad y estado de escalamiento.

**Campos**:
- id: identificador unico
- resident_id: residente asociado (opcional, puede ser null si la cama no tiene residente)
- bed_id: cama monitoreada
- evidence_kind: tipo de evidencia (sensor_event, dwell_window, manual)
- evidence_ref: referencia a la evidencia externa (opcional)
- rule_id: ID de regla del catalogo de alarmas
- level: severidad (low, medium, high, critical)
- status: estado actual (open, acknowledged, attending, resolved)
- status_actor_id: actor que hizo la ultima transicion
- status_at: timestamp de la ultima transicion
- title: titulo de la alerta
- detail: detalle opcional
- occurred_at: timestamp del evento
- escalation_level: nivel de escalamiento actual
- escalated_at: timestamp del ultimo escalamiento
- escalated_to: destino del ultimo escalamiento

### AlertTransition (append-only)

Historia de transiciones de estado de una alerta.

**Campos**:
- id: identificador unico
- alert_id: alerta asociada
- from_status: estado anterior (null para la primera transicion)
- to_status: estado nuevo
- actor_id: actor que hizo la transicion
- occurred_at: timestamp de la transicion
- sequence: numero de secuencia

### NotificationDelivery (append-only)

Intento de entrega de notificacion. Un retry crea otra entrega.

**Campos**:
- id: identificador unico
- alert_id: alerta asociada
- recipient_kind: tipo de destinatario (user, staff_group, service)
- recipient_id: ID del destinatario
- channel: canal de notificacion (push, tablet, sms, other)
- escalation_level: nivel de escalamiento al momento de la entrega

### NotificationDeliveryEvent (append-only)

Resultado de un intento de entrega.

**Campos**:
- id: identificador unico
- delivery_id: entrega asociada
- kind: tipo de evento (sent, acknowledged, failed)
- reason: razon del fallo (opcional)
- occurred_at: timestamp del evento

### AlertEscalation (append-only)

Historia de escalamientos de una alerta.

**Campos**:
- id: identificador unico
- alert_id: alerta asociada
- level: nivel de escalamiento
- target_id: destino del escalamiento
- occurred_at: timestamp del escalamiento

## Invariantes

1. El orden de transicion es open -> acknowledged -> attending -> resolved.
2. Cada estado distinto de open lleva actor y timestamp.
3. rule_id viene del catalogo de alarmas cargado.
4. La evidencia de alerta es explicita: evento, ventana de permanencia o comando manual.
5. Una alerta de permanencia puede crearse por el paso del tiempo, no solo por un evento nuevo del detector.
6. El escalamiento es estado persistido con destino y timestamp, no un efecto invisible de un timer.
7. Entregas y resultados son append-only y pueden contarse independiente del acuse.
8. La historia de escalamiento es append-only aunque la alerta exponga su nivel actual como read model.
9. Una cama sin residente puede seguir vigilada por una politica fija de fallback.
10. POST /alerts/{id}/view audita el acceso a imagen, pero no expone ni persiste media en este contexto.

## Tablas

- alerts: raiz de alerta con estado actual y escalamiento
- alert_transitions: historia append-only de transiciones
- notification_deliveries: intentos de entrega (append-only)
- notification_delivery_events: resultados de entregas (append-only)
- alert_escalations: historia de escalamientos (append-only)
