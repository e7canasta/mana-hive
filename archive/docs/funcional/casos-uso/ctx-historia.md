# Casos de uso: `ctx-historia`

## Frontera funcional

`ctx-historia` es propietario de las detecciones de incidentes clinicos y las
revisiones humanas sobre ellas. Nunca importa otros `ctx-*`; los IDs de
residente, cama y usuario son opacos y la validacion de existencia vive en
`mana-app`.

## Reglas del contexto

- Un `source_record_id` duplicado devuelve la deteccion existente (idempotencia).
- La ingesta crea una deteccion, pero no inserta una revision.
- La ingesta no puede cerrar un incidente ni fijar un veredicto.
- Una revision siempre tiene actor y timestamp del servidor.
- Las revisiones nunca se pisan; el estado actual es la ultima revision valida.
- `safe_to_ground` no es un veredicto de caida aunque el residente haya tocado
  el floor.
- El vocabulario de deteccion y el de revision son distintos de los IDs de
  reglas de alerta.
- La secuencia y las metricas de respuesta derivadas vienen de read models de
  Observacion y Cuidado, no se copian en la deteccion.

## HIS-01 - Ingestar deteccion de incidente

**Objetivo:** Registrar evidencia clinica inmutable de forma idempotente.

**Actor primario:** Fuente interna (IA, sensor, operador) con `x-clinical-secret`.

**Disparador:** HTTP POST `/internal/v1/clinical/incidents`.

**Precondiciones:** Secret clinico valido.

**Flujo principal:**

1. El cliente envia `source_record_id`, `resident_id`, `kind`, `severity`,
   `occurred_at`, `injury_status`, `source`, `model_version`.
2. El contexto busca si ya existe una deteccion con ese `source_record_id`.
3. Si no existe, crea la deteccion y devuelve 201 con `duplicate: false`.
4. Si ya existe, devuelve 200 con `duplicate: true` y la deteccion existente.

**Postcondiciones:** La deteccion es inmutable; no se puede modificar.

**Excepciones:**

- Secret invalido: 403.
- Campos requeridos faltantes: 422.

## HIS-02 - Listar incidentes de un residente

**Objetivo:** Consultar todos los incidentes detectados de un residente.

**Actor primario:** Staff autenticado con capability `incidents.read`.

**Disparador:** HTTP GET `/api/v1/residents/{residentId}/incidents`.

**Precondiciones:** Sesion autenticada.

**Flujo principal:**

1. El contexto lista detecciones del residente ordenadas por `occurred_at`
   descendente.
2. Para cada deteccion, compone el read model con las revisiones y el estado
   actual.
3. Devuelve la lista de incidentes compuestos.

## HIS-03 - Revisar un incidente

**Objetivo:** Registrar una decision humana sobre un incidente detectado.

**Actor primario:** Staff autenticado con capability `incidents.manage`.

**Disparador:** HTTP POST `/api/v1/incidents/{incidentId}/reviews`.

**Precondiciones:** Sesion autenticada; incidente existe.

**Flujo principal:**

1. El cliente envia `status` (open, under_review, closed), opcionalmente
   `detection_verdict`, `review_note` y `resolved_at`.
2. El contexto crea una nueva revision append-only con actor y timestamp.
3. Devuelve el incidente compuesto con todas las revisiones.

**Postcondiciones:** La revision queda registrada; el historial es inmutable.

**Excepciones:**

- Status invalido: 422.
- Veredicto invalido: 422.
- Incidente no existe: 404.

## HIS-04 - Obtener secuencia de un incidente

**Objetivo:** Consultar el historial completo de revisiones de un incidente.

**Actor primario:** Staff autenticado con capability `incidents.read`.

**Disparador:** HTTP GET `/api/v1/incidents/{incidentId}/sequence`.

**Precondiciones:** Sesion autenticada; incidente existe.

**Flujo principal:**

1. El contexto busca la deteccion por ID.
2. Lista todas las revisiones ordenadas por fecha.
3. Devuelve el incidente compuesto con su historial.

## HIS-05 - Ingestar multiples incidentes

**Objetivo:** Verificar que multiples incidentes se registran correctamente y
se pueden listar.

**Actor primario:** Fuente interna + staff autenticado.

**Disparador:** Secuencia de POST y GET.

**Flujo principal:**

1. Ingestar primer incidente (sr-001) con kind=fall.
2. Ingestar segundo incidente (sr-002) con kind=bed_exit.
3. Listar incidentes del residente: debe retornar 2.
4. Cada incidente tiene su propio historial de revisiones independiente.
