# El lazo operativo

Documento de arquitectura de F11. El hueco inicial de evaluación por evento se
cerró en F11.1; este documento conserva el diseño completo y marca qué parte
queda pendiente para el reloj, las entregas y el escalamiento.

---

## 1 · El hueco original

El hub guardaba evidencia, politica y alertas, pero inicialmente **nada las
conectaba**. F11.1 ya cerró el camino de evento a alerta; F11.2 debe cerrar el
camino del reloj a permanencia y escalamiento.

Verificable en el arbol de hoy:

| Hecho | Como se comprueba |
| --- | --- |
| Un evento del detector genera una alerta | `mana-app/motor/lazo.rs` hidrata, evalua y persiste en la misma transaccion |
| El catalogo de alarmas se evalua | `mana-motores::alarmas` recibe el perfil efectivo y devuelve decisiones |
| No hay barrido de permanencias | No hay un solo `tokio::time`, `interval` ni tarea de fondo en el workspace |
| Nadie decide a quien avisarle | `create_delivery` solo se llama desde HTTP; `mana-app/vigilancia.rs` nunca menciona cobertura |

Node tenia `api/alarm-engine.js`, 272 lineas que hacian exactamente esto, y se
fue con Node en F9. La migracion porto **el estado** de cada contexto y no porto
**el lazo entre ellos**, porque el lazo no era de ningun contexto: vivia en el
unico lugar que no tenia dueño.

Dicho sin rodeos: ya se puede configurar que una salida de cama a la noche genere
una alarma de nivel alto y el detector puede producirla. Todavia falta que el
reloj dispare permanencias sin otro evento y que el lazo dirija y escale la
entrega.

---

## 2 · El lazo

```text
  detector ──▶ evidencia ──▶ [ politica ] ──▶ alerta ──▶ [ cobertura ] ──▶ entrega
                   │              │                                          │
                   │              │  reglas efectivas del residente          │  sin acuse
                   │              │                                          ▼
                   └──▶ estado ◀──┘                                    escalamiento
                        actual
                          │
                          │  permanencias: vencen por reloj, no por evento
                          ▼
                     [ barrido ]
```

Cinco pasos, y **cada flecha entre corchetes es un cruce de contextos**: viven
en `mana-app` y en ningun otro lado.

| Paso | Pregunta | Contextos que cruza |
| --- | --- | --- |
| Evaluar | ¿esta observacion genera una alerta, y de que nivel? | Observacion + Politica |
| Crear | la alerta con su evidencia y su regla | Vigilancia |
| Dirigir | ¿a quien se le avisa? | Vigilancia + Cobertura + Identidad |
| Entregar | registrar que se aviso, por que canal, si llego | Vigilancia |
| Escalar | sin acuse en N segundos, sube | Vigilancia + Cobertura + **reloj** |

El barrido es el quinto elemento y el que rompe el modelo de los otros cuatro:
**no lo dispara un evento.**

---

## 3 · Los dos tiempos

Es la distincion que ordena toda la fase.

**Tiempo de evento.** Llega una observacion, se evalua, puede nacer una alerta.
Es reactivo, se dispara solo, y ya tiene su camino: `POST /internal/v1/events`.

**Tiempo de reloj.** "Cuarenta minutos fuera de la cama" y "sin acuse en 90
segundos" no los dispara nada: los dispara **que pase el tiempo**. Si el sistema
espera a que llegue otro evento para darse cuenta, la alarma depende de que el
detector repita el estado — y un detector que ve a alguien quieto en el piso
puede no tener nada nuevo que informar.

> Una alarma de permanencia que necesita un evento para dispararse no es una
> alarma de permanencia.

De ahi salen dos requisitos que no son negociables:

1. **El barrido corre aunque no llegue nada.**
2. **El barrido sobrevive un reinicio.** El `setInterval` de Node no lo hacia:
   las ventanas en curso se perdian en silencio, que es la peor forma de
   perderlas.

---

## 4 · Donde vive el reloj

Tres opciones, en orden de peso. La decision es de F11.1 y hay que tomarla con
numeros, no con gusto.

### (a) Scheduler propio sobre SQLite — **recomendado para empezar**

Una tabla `timers` con `fire_at`, `kind`, `payload`, `claimed_at`. Un `tokio`
task despierta cada N segundos, toma los vencidos con `UPDATE ... RETURNING` y
los ejecuta.

- **Durable**: el timer esta en la misma base que el resto. Un reinicio no
  pierde nada; a lo sumo llega tarde.
- **Sin infraestructura nueva.** Es lo unico de F11 que no agrega un proceso.
- Se paga con: hay que escribir el claim sin carreras, y el "llega tarde" hay
  que medirlo.

### (b) Temporal

Durabilidad de verdad, retries, visibilidad. El SDK de Go es de primera; el de
Rust hay que **verificar antes de comprometerse**.

Cuesta un servidor mas su propia base en el edge. El criterio de detencion del
roadmap viejo sigue en pie: **si no entra en el presupuesto de recursos del hub,
no entra.**

### (c) Cron del sistema golpeando un endpoint interno

Lo mas barato. Deja el reloj afuera del producto, que es una forma de no tener
reloj: el dia que la casa reinstala el sistema operativo, nadie se acuerda.

**Recomendacion:** (a) para F11, con la interfaz del scheduler detras de un
trait para que (b) sea un cambio de implementacion y no un rewrite. Es la misma
jugada que el puerto de repositorio: el dominio no sabe quien lo despierta.

---

## 5 · Donde vive el motor

**La decision pura vive en `mana-motores`; el seam vive en `mana-app`.** Evaluar
una observacion contra la politica de un residente cruza Observacion y Politica;
ningun contexto puede importar al otro y ninguno de los dos es dueño de la
decision.

```text
mana-motores/
  alarmas.rs        observacion + perfil efectivo -> ¿alerta?

mana-app/
  motor/
    lazo.rs         hidrata, traduce y persiste
    permanencias.rs las que vencen por reloj
    destino.rs      alerta + cobertura -> a quien avisarle
    escalamiento.rs sin acuse -> sube
```

Lo que **si** es de un contexto y hay que resistirse a mover:

- La **regla** (que parametros tiene, que rangos acepta) es del catalogo, que es
  dato y vive en `ctx-politica`.
- El **perfil efectivo** del residente —preset del nivel, plantilla, ajuste
  manual, con la capa que fijo cada valor— es de `ctx-politica`.
- La **maquina de estados** de la alerta es de `ctx-vigilancia`.
- **Quien cubre el turno** es de `ctx-cobertura`.

El motor no reimplementa ninguna: las compone.

---

## 6 · Por que NATS va despues y no antes

El roadmap viejo ponia NATS antes del Registro para que el SoR "naciera
consumiendo del bus". Ese argumento valia cuando el Registro no existia. Ahora
existe, la ingesta funciona y es idempotente por `source_event_id`.

Hoy el orden correcto es el inverso, por una razon de riesgo: **si el lazo no
funciona, que los eventos lleguen mejor no sirve de nada.** Un bus que entrega
con garantia eventos que nadie convierte en alertas es infraestructura cara
alrededor de un producto que no hace nada.

Y hay una razon tecnica: la idempotencia por `source_event_id` ya esta, y es
justamente lo que hace seguro poner un bus con **at-least-once** delante. El
trabajo dificil ya se hizo; NATS pasa a ser cambio de transporte, no de diseño.

**Consecuencia para el bridge:** sigue hablando HTTP hasta que el bus exista. No
se toca en F11.

---

## 7 · La señal que la IA cell ve y el evento no transporta

**La IA cell detecta presencia de staff.** Lo que falta no es el sensor: es que
el vocabulario de `sensor_events` la lleve.

Eso hoy cuesta caro en un lugar concreto. La secuencia de un incidente expone
`staff_present`, `staff_arrival_at` y `staff_arrival_seconds`, y los tres salen
vacios. **`staff_arrival_seconds` es el numero que convierte la revision de una
caida en una medida de respuesta**: cuanto tardo alguien en llegar. Sin el, la
revision cuenta que paso y no cuenta que tan bien respondimos.

Y hay una segunda consecuencia, mas silenciosa: sin presencia de staff, el motor
no puede distinguir *"nadie fue"* de *"alguien fue y no toco el tablet"*. Toda
la escalera de escalamiento se apoya en el acuse humano, que es una accion
deliberada; la presencia observada es la unica señal que no depende de que
alguien se acuerde de apretar un boton.

**Trabajo de F11:** agregar la presencia al contrato de ingesta —campo tipado,
no dentro de `payload_json`—, proyectarla, y derivar de ahi la llegada. Es
barato y desbloquea lo mas valioso de `incidents/{id}/sequence`.

---

## 8 · Que se rompe si esto sale mal

| Riesgo | Como se ve | Mitigacion |
| --- | --- | --- |
| **Tormenta de alertas** | Un evento repetido genera una alerta por evento | Deduplicacion por `(cama, regla)` mientras haya una alerta abierta |
| **El barrido corre dos veces** | Alertas duplicadas tras un reinicio | El claim del timer es transaccional; ejecutar es idempotente por la misma clave |
| **Silencio total** | Nada suena y nadie se entera | Es la falla que este sistema existe para eliminar: **una metrica de "alertas evaluadas" y "eventos sin regla" en `/__hub/rutas` o su equivalente** |
| **El reloj se atrasa** | Las permanencias llegan tarde | Medir el retraso entre `fire_at` y la ejecucion real, y exponerlo |
| **Politica ausente** | Una cama sin residente deja de vigilarse | Invariante ya escrita: una cama sin asignar conserva la politica fija |
| **Escalamiento ciego** | Sube una alerta que alguien ya esta atendiendo | Presencia de staff observada (§7), no solo el acuse |

La tercera fila es la unica que importa de verdad. Un sistema de alarmas que
falla ruidosamente es un problema; uno que falla en silencio es una demanda.
