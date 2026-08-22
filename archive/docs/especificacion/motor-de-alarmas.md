# Especificacion: motor de alarmas

Que convierte una observacion en una alerta, a quien se le avisa, y cuando sube.
Es la spec de F11. El diseño y el porque estan en
[`../arquitectura/lazo-operativo.md`](../arquitectura/lazo-operativo.md).

---

## 1 · Entradas

| Entrada | De donde | Forma |
| --- | --- | --- |
| Observacion | `POST /internal/v1/events` | `SensorEvent` con `monitor_key`, `state`, `occurred_at` |
| Estado previo de la cama | proyeccion | `BedState` con `state` y `state_since` |
| Perfil efectivo del residente | `ctx-politica` | nivel, plantilla, overrides, con la capa que fijo cada valor |
| Catalogo | `config/alarm-catalog.toml` | reglas con sus parametros y rangos |
| Cobertura del turno | `ctx-cobertura` | grupo de staff vigente en el instante |

**El catalogo es dato.** Cambiar un umbral clinico es un diff de datos, nunca un
deploy. Es la razon por la que se saco de codigo en su momento y hay que
sostenerla.

---

## 2 · Clases de regla

El catalogo tiene reglas de dos clases, y el motor las trata distinto.

### 2.1 · Transicion

Se disparan **por el evento**. `bed_exit`, `bed_edge`, `sitting_in_bed`,
`bed_entry`.

```text
estado_previo -> estado_nuevo  coincide con la transicion de la regla
                 y la regla esta habilitada en el perfil efectivo
              => nace una alerta
```

Con un `delay` configurado, la regla no dispara al instante: **agenda** una
confirmacion. Si antes de vencer el estado vuelve, la confirmacion se cancela.
Esa es la diferencia entre "se levanto" y "se levanto y sigue afuera".

### 2.2 · Permanencia

Se disparan **por el reloj**. `out_of_bed_dwell`, `in_bed_dwell`,
`room_absence_dwell`.

```text
el estado se mantiene durante N minutos  =>  nace una alerta
```

`N` sale del perfil. El reloj arranca en `state_since`, **no** en la llegada del
ultimo evento: un evento que repite el mismo estado no reinicia nada. Esa
invariante ya esta implementada en la proyeccion y es la que hace posible esta
regla.

### 2.3 · Las que el detector todavia no permite

El catalogo tiene reglas que necesitan eventos que hoy no llegan: baño, area
comun, exterior, piso, silla, apoyo, sueño fuera de la cama.

**Se configuran y no disparan.** El motor tiene que poder decir cuales son —una
lista explicita de reglas pendientes— y no fingir que estan activas. Una regla
que el panel muestra encendida y que nunca puede sonar es una mentira operativa.

---

## 3 · Evaluacion

```text
1. Resolver la cama y el residente.        (ya lo hace la ingesta)
2. Sin residente asignado -> politica fija de la cama. No se deja de vigilar.
3. Traer el perfil efectivo vigente en `occurred_at`, no el de ahora.
4. Para cada regla habilitada de clase transicion:
     coincide la transicion -> con delay: agendar; sin delay: emitir
5. Para cada regla habilitada de clase permanencia:
     agendar el vencimiento en `state_since + N`
     cancelar los agendados de la cama que ya no aplican
6. Emitir = crear alerta, salvo que ya haya una abierta de la misma
   `(cama, rule_id)`.
```

El paso 3 es el que se olvida y el que importa: **la politica vigente cuando
ocurrio**, no la de ahora. Por eso los perfiles son temporales. Evaluar una
caida de hace tres semanas con la politica de hoy es reescribir la historia.

El paso 6 es la deduplicacion. Sin ella, un detector que repite estado produce
una alerta por evento y el tablet se vuelve inusable — que en la practica es lo
mismo que no tener alarmas.

**Correccion de la implementacion:** la deduplicacion es **por episodio** —las
reglas que ya avisaron desde `state_since`— y no "mientras haya una alerta
abierta". La diferencia importa: que alguien resuelva la alerta no puede hacerla
sonar de nuevo mientras el residente sigue fuera de la cama. Node lo hacia asi y
tenia razon.

---

## 4 · El nivel

Sale del perfil efectivo, en capas, y **cada valor recuerda que capa lo fijo**:

```text
preset del nivel de riesgo  ->  plantilla  ->  ajuste manual
```

`fall` no se puede desactivar en ninguna capa. Es la unica regla bloqueada del
catalogo y esa condicion es del dominio, no de la UI.

`day` y `night` de los overrides son **momentos del dia del residente**, no
turnos laborales. Los dos ejes no se mezclan: la grilla de turnos es de la
residencia y vive en `ctx-cobertura`.

---

## 5 · A quien se le avisa

```text
alerta -> cama -> ala -> cobertura vigente en el instante -> grupo -> miembros
```

Una entrega (`notification_deliveries`) por destinatario y canal. **Append-only:
una entrega no se corrige, se agrega otra.**

**El caso que define el diseño:** que pasa cuando la cobertura del turno esta
vacia. No es un error del sistema — es una residencia sin nadie asignado a esa
ala en ese turno, que es un hecho operativo real y grave. La alerta **nace
igual** y la entrega se registra como no dirigida. Silenciarla porque no hay a
quien avisarle seria exactamente el bug que este sistema existe para eliminar.

---

## 6 · Escalamiento

```text
alerta abierta sin acuse durante N segundos  ->  sube un nivel
```

Es tiempo de reloj: se agenda al crear la alerta y se cancela al acusar.

`N` y el destino de cada nivel salen del perfil. El escalamiento es un agregado
append-only (`alert_escalations`), no un campo: una alerta puede subir mas de
una vez y hay que poder contar la escalera entera.

**Cuando la presencia de staff llegue al evento** (§7 del documento de
arquitectura), el escalamiento deja de depender solo del acuse: si alguien
esta fisicamente en la habitacion, subir la alerta es ruido. Hasta entonces,
sube por acuse.

---

## 7 · Los timers

Una sola tabla, que sirve a los tres casos: confirmacion de transicion,
vencimiento de permanencia, escalamiento.

```text
timers
  id           TEXT PRIMARY KEY
  kind         TEXT NOT NULL      -- confirm | dwell | escalate
  key          TEXT NOT NULL      -- (cama, regla) o (alerta, nivel)
  fire_at      TEXT NOT NULL
  payload_json TEXT NOT NULL
  claimed_at   TEXT NULL          -- lo toma un solo ejecutor
  created_at   TEXT NOT NULL
```

Reglas:

1. **`key` es unica entre los no vencidos.** Reagendar reemplaza; no acumula.
2. **Tomar es transaccional**: `UPDATE ... SET claimed_at WHERE claimed_at IS
   NULL AND fire_at <= now RETURNING`. Dos ejecutores no pueden tomar el mismo.
3. **Ejecutar es idempotente.** Un timer que se ejecuta dos veces por un reinicio
   entre el claim y el commit no puede producir dos alertas: lo impide la
   deduplicacion de §3.6.
4. **Un timer vencido y no ejecutado se ejecuta tarde, no se descarta.** Llegar
   tarde a una permanencia de cuarenta minutos sigue siendo informacion; no
   llegar nunca es el bug de Node.

---

## 8 · Que hay que poder medir

Sin esto la fase no esta terminada, porque la falla que importa es silenciosa.

| Metrica | Por que |
| --- | --- |
| Eventos evaluados / eventos ingeridos | Si divergen, hay evidencia que el motor no mira |
| Eventos sin regla aplicable | Una cama configurada que nunca puede sonar |
| Alertas por regla y por ala | La tormenta se ve antes de que la reporten |
| Timers vencidos pendientes | El reloj se atraso |
| Retraso real: `ejecutado_at - fire_at` | Cuanto tarde llega una permanencia |
| Entregas sin destinatario | Cobertura vacia: alguien tiene que enterarse |

---

## 9 · Criterio de aceptacion

Una escena, contra base limpia, que:

1. configura un residente con `out_of_bed_dwell` a 2 minutos;
2. ingiere una salida de cama;
3. **avanza el reloj sin emitir un solo evento mas**;
4. verifica que la alerta aparecio;
5. verifica que se registro una entrega al grupo que cubre el turno;
6. no acusa, avanza el reloj de nuevo, y verifica que escalo.

El paso 3 es el que prueba que el sistema tiene reloj propio. Si la escena
necesita mandar un evento para que la alarma salte, el motor no esta hecho.
