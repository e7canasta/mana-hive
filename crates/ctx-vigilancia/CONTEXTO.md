# ctx-vigilancia

> **Spec vigente:** [`../../docs/contextos/ctx-vigilancia.md`](../../docs/contextos/ctx-vigilancia.md).
> Este archivo es un borrador historico conservado para no perder contexto; no
> es la fuente de implementacion.

> **Estado: borrador de diseño.** Las secciones 4, 5 y 9 están incompletas a
> propósito: se completan leyendo `api/domains/`, `api/alarm-engine.js` y
> `api/db.js`. Es el primer trabajo de la próxima sesión.

## 1 · La pregunta

**¿Qué alertas hay, quién las atendió, y a quién se le avisó?**

La última parte es la que hoy no tiene respuesta, y es la razón de ser del
producto.

## 2 · Clase

**Núcleo.** Junto con `ctx-politica`, es lo único que nadie más del ecosistema
puede hacer. Acá se gasta el sistema de tipos, la exhaustividad de los `match` y
los tests de propiedades.

Se modela **primero**, aunque se implemente después: es el contexto que más
restringe al resto, y descubrir en el mes cinco que no entra es el modo clásico
de fallar un rewrite.

## 3 · Agregados

**`Alerta`** (raíz). Cuelgan de ella el ciclo de atención y las entregas de
notificación.

La decisión de modelado central: **el estado lleva sus datos adentro.**

```rust
pub enum EstadoAlerta {
    Abierta,
    Reconocida { por: Id<Usuario>, en: Instante },
    EnAtencion { por: Id<Usuario>, en: Instante },
    Resuelta   { por: Id<Usuario>, en: Instante },
}
```

Hoy el esquema permite `status='acknowledged'` con `acknowledged_by` en `NULL`.
Acá eso no es representable, y agregar un estado no compila hasta tratarlo en
todos los `match` donde importa.

## 4 · Tablas que posee

**Incompleto — completar contra `api/db.js`.**

| Tabla | Estado | Notas |
| --- | --- | --- |
| `alerts` | rediseñada | el estado absorbe `acknowledged_by/at`, `attended_*`, `resolved_*` |
| `notification_deliveries` | **nueva** | alerta, destinatario, canal, `sent_at`, `acked_at`, `failed_reason`. Append-only |
| `alert_views` | por confirmar | la auditoría de imagen del companion (`POST /alerts/:id/view`) — ¿es de acá o de `ctx-auditoria`? |
| `sensor_events` | **por resolver** | hoy las alertas se derivan de acá. ¿Es de este contexto, o es Observación y este contexto sólo consume estado caliente? |

La última fila **bloquea el diseño** y hay que cerrarla antes de escribir el
store: si `sensor_events` es Observación, este contexto no la posee y necesita un
read model.

## 5 · Casos de uso

**Incompleto — extraer de `api/server.js` y `api/alarm-engine.js`.**

| Tipo | Caso de uso | Capability | Hoy en |
| --- | --- | --- | --- |
| consulta | Listar alertas por estado, residente y límite | `alerts.read` | `GET /api/v1/alerts` |
| comando | Avanzar el ciclo de una alerta | `alerts.manage` | `PATCH /api/v1/alerts/:id` |
| comando | Crear alerta manual | `alerts.manage` | `POST /api/v1/alerts` |
| comando | Auditar imagen del companion | — | `POST /api/v1/alerts/:id/view` |
| comando | Ingerir evento del detector y decidir si genera alerta | secreto del bridge | `POST /internal/v1/events` |
| interno | Barrer permanencias vencidas | — | `setInterval` en `alarm-engine.js` |
| **nuevo** | Registrar una entrega de notificación | — | no existe |
| **nuevo** | Escalar una alerta sin acuse | — | no existe |

## 6 · Invariantes

1. **El ciclo es `open → acknowledged → attending → resolved`**, cada paso con
   actor y fecha. *Imposible de violar:* el enum de §3 y una transición que
   devuelve `Result<Self, TransicionInvalida>`.
2. **Las permanencias vencen por paso del tiempo, no por evento nuevo.** Sin
   barrido, una alarma de "cuarenta minutos fuera de la cama" dependería de que
   el monitor repita el estado. *Test:* avanzar el reloj sin emitir eventos y
   verificar que la alerta aparece.
3. **Una cama sin residente asignado conserva la política fija** de
   `policies.js`. No se deja de vigilar una cama vacía.
4. **Toda mutación escribe auditoría.** Como capacidad inyectada desde
   `mana-app`, **no** como dependencia hacia `ctx-auditoria`.
5. **`notification_deliveries` es append-only.** Una entrega no se corrige: se
   agrega otra.

## 7 · Qué cambia respecto de hoy

| Cambio | Por qué | ¿Toca panel? |
| --- | --- | --- |
| **Existe registro de a quién se le avisó** | Hoy `alerts` guarda la *respuesta*, no la *entrega*. No se puede distinguir "estuvo abierta 20 minutos porque nadie la miró" de "porque le avisamos al tablet equivocado". Una es negligencia y la otra es un bug nuestro | Sí — vista nueva |
| **El escalamiento es un estado explícito** | Si la política dice "sin acuse en 90 s sube a supervisión", hoy no hay dónde decir que eso pasó | Sí |
| **`kind` pasa a `rule_id`**, enum cerrado del catálogo | Hoy colisiona con las categorías clínicas de incidentes: dos vocabularios distintos en un mismo campo | Sí — renombre |
| **El estado deja de ser string** y absorbe actor y fecha | El esquema permite estados imposibles | Probablemente — depende de si el panel lee los campos sueltos |
| **El barrido deja de ser `setInterval`** | Un `setInterval` no sobrevive a un reinicio: las permanencias pendientes se pierden en silencio | No |

Las primeras tres estaban declaradas como CM-1 y CM-6. Ya no son "excepciones
autorizadas a la paridad": son diseño. Lo que sigue valiendo es que el cambio de
cliente va en el mismo PR.

## 8 · Cruces con otros contextos

| Necesita | De | Para qué |
| --- | --- | --- |
| La política efectiva del residente | `ctx-politica` | Decidir si un evento genera alerta y con qué nivel |
| Quién ocupa la cama | `ctx-poblacion` | Resolver `monitor_key` → residente |
| Quién cubre el turno | `ctx-cobertura` | **A quién avisarle.** Es la razón por la que la notificación va después de que la cobertura tenga historia |
| Escribir auditoría | `ctx-auditoria` | Inyectado como capacidad, nunca importado |

Los cuatro viven en `mana-app`. Ninguno es un `use ctx_*`.

## 9 · Escenas que lo prueban

**Por escribir.** Van en `scenes/contextos/vigilancia/`, contra Node.

| Escena | Prueba |
| --- | --- |
| `ciclo-de-alerta.json` | El ciclo completo con actor y fecha en cada paso |
| `permanencia-vence-sola.json` | Invariante 2: sin eventos nuevos, la alerta aparece igual |
| `cama-vacia-sigue-vigilada.json` | Invariante 3 |
| `entrega-y-escalamiento.json` | Los casos nuevos de §5. **Sin equivalente en Node** — es la única que no puede escribirse contra el sistema viejo |

La última fila marca el límite del método: una escena es el criterio de
aceptación cuando existe algo con qué comparar. Para lo que se construye de cero,
la escena es especificación, no oráculo.

## 10 · Preguntas abiertas

- **¿`sensor_events` es de este contexto o es Observación?** Bloquea §4. Se
  resuelve con B02.
- **¿A quién se le avisa cuando la cobertura del turno está vacía?** Hoy la
  pregunta no se hace porque no existe la notificación.
- **¿Dónde corre el barrido?** Temporal es pesado para el edge; el criterio de
  detención de B07 ya está escrito. Ver `fase-5-nucleo/dudas.md` § D5.2.
- **¿El escalamiento es parte del estado de la alerta o un agregado aparte?** Si
  una alerta puede escalar varias veces, el estado único no alcanza.
