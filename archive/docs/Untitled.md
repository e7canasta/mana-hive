El sistema **mana-hub** es una arquitectura de software desarrollada en Rust que funciona como el núcleo central de registro para el ecosistema Virtual Rounds. Su diseño se basa en un **monolito modular** compuesto por once contextos delimitados y cuatro binarios especializados que coordinan la persistencia de datos y el procesamiento de eventos mediante NATS JetStream. El sistema prioriza la **integridad de los datos** y la trazabilidad, empleando un modelo de persistencia en SQLite con un esquema de migraciones descentralizado y un registro de auditoría inmutable. La estructura sigue un gráfico acíclico de dependencias estricto para asegurar el aislamiento entre dominios, permitiendo que trabajadores sin estado gestionen alertas clínicas y lógica de sensores de forma eficiente. Además, el proyecto implementa un control de acceso basado en **capacidades**, garantizando que cada acción sobre la infraestructura o los residentes sea autorizada y debidamente documentada. En conjunto, esta infraestructura técnica proporciona una base sólida, transaccional y verificable para la gestión de cuidados y seguridad clínica.

El flujo de datos en la plataforma sigue un pipeline reactivo a través de una **arquitectura de cuatro binarios independientes** coordinados por un bus de mensajería asíncrona basado en **NATS JetStream**.

A continuación, se detalla el recorrido paso a paso que realiza la señal, desde la detección física en el sensor hasta que se genera y distribuye la alerta:

### 1. Ingesta en el Hub (Edge → Hub)

- El dispositivo o cámara en el borde (celda de IA) detecta un cambio físico y realiza una petición HTTP `POST` al endpoint `/internal/v1/events` de **mana-hub** enviando un evento de percepción.
- **mana-hub** recibe el evento, valida el formato y verifica su idempotencia utilizando el identificador único `source_event_id` para evitar procesar duplicados.
- Dado que el sensor solo conoce su propia identidad de hardware (`monitor_key`), el Hub cruza contextos para **resolver a qué cama (`bed_id`) y residente (`resident_id`) corresponde**.
- Una vez resuelto, persiste el evento de manera inmutable en la tabla de evidencias `sensor_events` y publica un mensaje en el tema de NATS **`evt_perception`**.

### 2. Actualización del Gemelo Digital (Engine)

- El binario **mana-engine** (que mantiene un modelo vivo en memoria de las camas, llamado **DigitalTwin**) está suscrito al canal de eventos **`evt_perception`**.
- Al recibir el evento de percepción, el motor busca el gemelo de cama correspondiente (`BedTwin`) y evalúa la **máquina de estados finitos del residente** (`PersonState` FSM), detectando cambios de postura o ubicación (por ejemplo, una transición de "acostado" a "de pie").
- Si el estado cambia, el motor **cancela los temporizadores activos del estado anterior** y **arranca nuevos temporizadores** correspondientes al nuevo estado (por ejemplo, temporizadores de permanencia o _dwell_).
- El motor emite un evento de escena (**`SceneEvent`**), el cual representa una transición de estado en la habitación o el vencimiento de algún temporizador, y lo publica en el canal de NATS **`evt_scene`**.

### 3. Persistencia de Escena (Hub)

- El **mana-hub** escucha en el canal **`evt_scene`** para actuar como el único Sistema de Registro persistente.
- Al recibir el `SceneEvent`, lo guarda en el histórico persistente de eventos y actualiza inmediatamente la **proyección en tiempo real de la cama** en la tabla `current_bed_states` (lo que alimenta el Tablero de Monitoreo en la interfaz de usuario).

### 4. Evaluación de Reglas Clínicas (Sentinel)

- El binario **mana-sentinel** está suscrito a **`evt_scene`** para evaluar de forma reactiva lo que sucede en el entorno.
- Utilizando la lógica pura del motor de alarmas (**`mana-motores::evaluar`**), Sentinel evalúa la escena confrontando el estado y los tiempos de permanencia del residente contra su **perfil clínico efectivo** y el turno laboral del personal asignado.
- Si se determina que se ha violado una regla de seguridad configurada (por ejemplo, el residente pasó más tiempo del permitido fuera de la cama), Sentinel crea un incidente de manera transaccional a través de la API del Hub y publica una notificación en el tema de NATS **`evt_notif`**.

### 5. Entrega de Alertas y Notificación (Vigilancia)

- El binario **mana-vigilancia** consume los eventos del canal **`evt_notif`**.
- Crea formalmente el registro de la alerta en la base de datos de Hub a través de peticiones HTTP `POST /api/v1/alerts`.
- Acto seguido, inicia el **ciclo de entrega de la notificación** (`NotificationDelivery`). Resuelve a quién notificar basándose en el grupo de staff que cubre esa ala en ese instante específico y distribuye la alerta a través de los canales asignados (pantallas de tablets, notificaciones push, SMS, etc.).

---

🎧 Si lo deseas, puedo generar un resumen de audio de este flujo técnico para que puedas repasar los detalles de "El viaje de la señal" de manera interactiva.


El Engine (`mana-engine` o `mana-engine-v2`) gestiona los temporizadores (**timers**) de manera reactiva y determinista en memoria, actuando de forma similar a un PLC (Controlador Lógico Programable) mediante un ciclo continuo de evaluación.

A continuación se detalla el ciclo de vida, los tipos y las reglas de resolución de los temporizadores según el diseño del sistema:

### 1. Filosofía de Diseño: Estado Derivado e Inmutable

- **Persistencia en memoria:** Los temporizadores **no se persisten en la base de datos**; se consideran un estado derivado del gemelo digital (`DigitalTwin`). En caso de reinicio, el Engine reconstruye los temporizadores del residente rehidratando su estado y utilizando el campo `state_since` de la cama como el instante de inicio (`started_at`) del temporizador.
- **Identificación:** Cada temporizador en un gemelo de cama (`BedTwin`) está indexado bajo una clave única `rule_id` basada en el formato `"dwell_{state:?}"`. No se permiten temporizadores duplicados para un mismo estado.

---

### 2. El Ciclo de Vida del Temporizador

El ciclo de vida transiciona por los estados de **`Active`**, **`Fired`** (Disparado) o **`Cancelled`** (Cancelado) de la siguiente manera:

- **Creación (Transición de entrada):** Cuando llega un evento de percepción (`PerceptionEvent`) que indica un cambio de estado del residente, el Engine llama a `start_timers_for_state()`. Se inicializan los temporizadores para el nuevo estado utilizando los umbrales definidos en las `DwellRules`.
- **Cancelación (Transición de salida):** Al abandonar un estado, el Engine ejecuta `cancel_timers_for_state()`, lo que marca explícitamente los temporizadores del estado anterior como `Cancelled`.
- **Evaluación (`Tick` del Super Loop):** El Engine ejecuta un lazo continuo o _Super Loop_ que invoca la función `tick(now)` a intervalos regulares (por defecto, **cada 5 segundos**). En cada iteración:
    1. Examina todos los temporizadores en estado `Active`.
    2. Compara si el tiempo transcurrido supera el umbral configurado (`now - started_at >= threshold_minutes`).
    3. Si se alcanza el umbral, transiciona el temporizador a `Fired` y emite un evento de escena (**`SceneEvent`**) con el tipo `Dwell` (que incluye el `rule_id`, la duración y el umbral).
- **Limpieza:** Al finalizar el escaneo del `tick`, todos los temporizadores que quedaron en estado `Fired` o `Cancelled` son eliminados del `BedTwin` para evitar que se procesen por duplicado en el siguiente ciclo.

---

### 3. Tipos de Temporizadores

El Engine está preparado para gestionar tres tipos lógicos de temporizadores:

1. **Dwells (Permanencia):** Miden cuánto tiempo pasa un residente en un estado específico (ej. fuera de la cama o en el baño).
2. **Confirmaciones (Transiciones pendientes):** Aseguran que una transición realmente ocurrió y no es un artefacto de ruido del sensor antes de disparar el cambio de estado.
3. **Post-caída (Refuerzo temporal):** Períodos de tiempo en los que se incrementa la sensibilidad de la vigilancia tras la detección de una caída.

---

### 4. Umbrales por Defecto y Calibración de Sensibilidad

Las `DwellRules` mapean cada variante del estado del residente (`PersonState`) a un umbral de tiempo base (en minutos):

|Grupo de Estado|Estados Incluidos|Umbral Base por Defecto|
|:--|:--|:--|
|**In Bed**|`Lying`, `SittingInBed`, `BedEdge`|300 minutos (5 horas)|
|**Standing**|`Standing`|5 minutos|
|**In Bathroom**|`InBathroom`|30 minutos|
|**Out of Bed / Furniture**|`InRoom`, `InHallway`, `Outdoor`, `InChair`, `InWheelchair`|10 minutos|
|**Unknown**|`Unknown`|Sin temporizador (`None`)|

#### Multiplicador por Sensibilidad

El umbral efectivo se calibra dinámicamente según el perfil clínico de riesgo (sensibilidad) asignado al residente:

- **Alta sensibilidad:** Se aplica un multiplicador de **0.5x** sobre la duración base (los temporizadores vencen en la mitad del tiempo).
- **Sensibilidad Estándar:** Multiplicador de **1.0x**.
- **Baja sensibilidad:** Multiplicador de **1.5x**.

---

🧩 ¿Te gustaría que profundicemos en cómo el componente **Sentinel** evalúa estos `SceneEvents` generados por el Engine para contrastarlos con las reglas clínicas del residente y crear alertas?



¿Cómo actúa el Sentinel al recibir una escena?

Cuando el binario **mana-sentinel** recibe un evento de escena (**`SceneEvent`**) a través del canal de NATS JetStream (**`evt_scene`**), se activa su pipeline reactivo de toma de decisiones. Su lazo reactivo principal (`tokio::select!`) ejecuta un flujo de actuación estructurado en cinco fases clave:

### 1. Ingesta y Evaluación de Reglas (`evaluar`)

Al recibir la escena, el componente **RuleEngine** del Sentinel toma el control. Para evaluar si la escena representa una situación de riesgo, el Sentinel recopila el **perfil efectivo de alarmas del residente** (que incluye presets, plantillas y overrides clínicos) y la cobertura del turno laboral del personal en ese instante específico.

Toda esta información es procesada por la función de lógica pura **`mana-motores::evaluar`**, la cual categoriza la escena en uno de tres tipos (`EventCategory`):

- **`Off`**: Representa actividad normal del residente; el sistema no realiza ninguna acción adicional.
- **`Notify`**: Corresponde a un evento de carácter informativo; se registra de manera histórica pero no genera una alarma activa.
- **`Alarm`**: Es un evento crítico que viola una regla clínica (por ejemplo, una transición hacia bipedestación o el vencimiento de un temporizador de permanencia fuera de la cama).

### 2. Agrupación y Ciclo del Incidente (`IncidentManager`)

Si el resultado de la evaluación es **Alarm**, entra en acción el **`IncidentManager`**. Con el objetivo de evitar "tormentas de alertas" que inunden la interfaz de las tablets de los cuidadores, el sistema agrupa eventos relacionados en un único incidente clínico activo a nivel de cama. El incidente se gestiona mediante una máquina de estados definida por `IncidentStatus`:

- **`CollectingEvidence`**: Estado inicial donde se asocian datos y grabaciones del evento.
- **`WaitingForStaff`**: El Sentinel ya ha notificado al personal y el sistema queda a la espera de una presencia física en la habitación.
- **`StaffOnSite`**: Se detecta la llegada del personal a la cabecera del residente.
- **`ClosingIncident` / `Closed`**: El incidente se cierra tras la partida del cuidador.

### 3. Apertura de Ventanas de Evidencia (`ClipWindow`)

Al confirmarse una alerta de tipo **Alarm**, el Sentinel inicializa un **`ClipWindow`** a través del `ClipWindowManager`. Este componente define una ventana de tiempo de interés alrededor del incidente para acumular la secuencia de eventos de escena y el registro multimedia de soporte. Posteriormente, persiste de manera transaccional estos datos como evidencia inmutable consumiendo el endpoint correspondiente del Hub en el contexto **`ctx-evidence`**.

### 4. Seguimiento y Medición de Presencia de Staff

El Sentinel inspecciona el metadato **`accompanied_by`** dentro del `SceneEvent` entrante para rastrear la llegada y salida del personal clínico.

- **Llegada**: Si detecta un identificador de staff (`staff_id`) mientras el incidente está en espera, cambia su estado a `StaffOnSite`. Esto calcula de manera automática los segundos reales de respuesta del staff frente a la alerta (`staff_arrival_seconds`).
- **Salida**: Si el campo vuelve a ser `None`, se inicia la transición del incidente hacia el cierre.

### 5. Publicación en el Canal de Alertas (`evt_notif`)

Una vez creado el incidente a través de la API de persistencia del Hub, el Sentinel emite un evento de notificación (`NotificationEvent`). Para garantizar la total trazabilidad y observabilidad del viaje de la señal, Sentinel hereda y propaga el mismo identificador de traza (**`trace_id`**) del evento de escena original. Este mensaje es publicado en el tema de NATS JetStream **`evt_notif`**, desde donde será consumido por **mana-vigilancia** para realizar la entrega física final a las tablets y dispositivos móviles del staff.

---

🎧 Si te interesa, puedo generar un resumen de audio interactivo para que repases con más profundidad las interacciones del "Lazo Operativo" y el motor de alertas.

Si el **Engine** (`mana-engine` o `mana-engine-v2`) se reinicia, el sistema está diseñado para recuperarse de manera automática, determinista y sin pérdida de datos gracias a su arquitectura desacoplada.

Esto es exactamente lo que sucede en el proceso de recuperación y cómo se comporta cada componente:

### 1. Rehidratación del Gemelo Digital (DigitalTwin)

El Engine es un componente **completamente stateless (sin estado)** que no posee una base de datos persistente propia. Por lo tanto, al reiniciarse:

- **Reconstrucción al 100%:** El `DigitalTwin` de cada cama se vuelve a construir desde cero importando los datos del **Hub**.
- **Replay de Eventos:** El Engine se rehidrata cargando el último snapshot guardado en el Hub y aplicando el _replay_ (reproducción) de los eventos de sensor en un orden estrictamente determinista (por secuencia/rowid, no por timestamp) desde el último punto de control (_watermark_).

### 2. Reconstrucción de los Temporizadores (Timers)

Dado que los temporizadores de permanencia (`dwell`) y confirmación residen únicamente en la memoria dinámica del Engine, **no se guardan en la base de datos**:

- Al reiniciarse, el Engine **reconstruye los temporizadores automáticamente** a partir del estado actual del residente.
- Utiliza el campo **`state_since`** de la cama (el instante exacto en el que el residente entró en su estado actual) como el tiempo de inicio (`started_at`) del temporizador. De esta forma, el tiempo que el residente ya llevaba fuera de la cama o en el baño no se pierde ni se reinicia a cero.

### 3. Garantía de Mensajería con NATS JetStream

- **Sin pérdida de eventos:** Durante el tiempo que el Engine permanezca apagado, no se generarán nuevos eventos de escena (`SceneEvents`). Sin embargo, los eventos de los sensores (`evt_perception`) no se pierden.
- **Reprocesamiento desde el último offset:** Al volver a estar en línea, el Engine se reconecta a NATS JetStream y comienza a consumir y procesar todos los mensajes acumulados desde su última posición confirmada (_watermark_ u _offset_).

---

📊 Si te interesa, podemos explorar las diferencias entre el "Tiempo de Evento" (reactivo) y el "Tiempo de Reloj" (barrido de temporizadores) para ver cómo el sistema asegura que las alertas de permanencia se disparen de manera persistente incluso tras fallos de red.


¿Qué diferencia hay entre Tiempo de Evento y Tiempo de Reloj?


El **Tiempo de Evento** representa la lógica **reactiva** y estimulada por estímulos del sistema.

Sus características fundamentales son:

- **Origen reactivo:** Se dispara exclusivamente cuando llega una observación o evidencia física desde un sensor (como un detector o cámara). No depende del transcurso pasivo del tiempo, sino de que ocurra un hecho concreto en el entorno del residente.
- **Canal de entrada:** Este flujo se inicia mediante una petición HTTP `POST /internal/v1/events` en el Hub.
- **Ciclo de evaluación inmediato:** Al ingresar la observación, el sistema la digiere y el motor de alarmas la evalúa de forma inmediata para determinar si esa acción puntual genera o no una alerta (por ejemplo, transiciones directas como una caída detectada).

A diferencia del _Tiempo de Reloj_ —que se dispara por el simple paso de las horas (como un temporizador de permanencia fuera de la cama)—, el Tiempo de Evento **siempre requiere un estímulo o evento entrante del sensor** para ponerse en marcha.

---

📊 ¿Te gustaría que analicemos un ejemplo práctico del catálogo de reglas (como una caída versus una alarma de permanencia en el baño) para ver cómo interactúan ambos tiempos en el motor?


Para ilustrar cómo funciona una **alarma de caída** bajo esta arquitectura modular, sigamos el recorrido paso a paso de un incidente práctico, desde la detección física en la habitación de un residente de riesgo alto (por ejemplo, "José Pérez") hasta que la alerta llega al personal de enfermería:

### 1. Detección física en la habitación (Edge → Hub)

- El sensor de la habitación detecta que el residente ha pasado bruscamente a una postura en el suelo. El dispositivo del borde envía inmediatamente una petición HTTP `POST /internal/v1/events` al backend **mana-hub** con el evento de percepción.
- El Hub recibe la petición, valida el formato y utiliza el identificador único `source_event_id` para asegurar la **idempotencia** (si el sensor reintenta el envío por un fallo de red, no se duplicará el evento).
- Dado que el hardware solo conoce su propia identidad (`monitor_key`), el Hub resuelve de forma segura que corresponde a la cama de "José Pérez" (`resident_id`). Persiste el hecho inmutable en `sensor_events` y publica el evento resuelto en el canal de NATS **`evt_perception`**.

### 2. Actualización del Gemelo Digital (Engine)

- El componente **mana-engine** (que está escuchando el tema **`evt_perception`**) recibe el evento.
- Busca el gemelo digital (`BedTwin`) en memoria correspondiente a la cama de José. Su máquina de estados finitos (`PersonState` FSM) registra la transición del estado actual (por ejemplo, `Lying` o `Standing`) hacia un estado de caída.
- El Engine **cancela todos los temporizadores activos** del estado anterior (como el temporizador de permanencia fuera de la cama).
- Genera y publica un **`SceneEvent`** de transición en el canal de NATS **`evt_scene`** con toda la telemetría e información del suceso.

### 3. Evaluación de Reglas y Creación del Incidente (Sentinel)

- El componente **mana-sentinel** consume el `SceneEvent` de **`evt_scene`**.
- Ejecuta la evaluación utilizando la lógica pura de **`mana-motores::evaluar`**. El motor confronta el suceso con el catálogo clínico. Cabe destacar que la regla **`fall` (caída)** es la única **regla bloqueada (`locked`)** en todo el sistema: no puede desactivarse en ninguna plantilla, preset o ajuste manual de la interfaz.
- Al confirmarse la caída, Sentinel ejecuta tres acciones:
    1. Abre una ventana de evidencia (**`ClipWindow`**) para empezar a recopilar los eventos de escena y registros multimedia que rodearon la caída.
    2. Crea de manera transaccional un registro de incidente inmutable llamando a la API del Hub (`POST /internal/v1/clinical/incidents`).
    3. Publica una notificación de alerta en el canal de NATS **`evt_notif`**.

### 4. Distribución y Entrega de la Alerta (Vigilancia)

- El binario **mana-vigilancia** recibe el mensaje de **`evt_notif`**.
- Registra formalmente la alerta de negocio persistente en la base de datos del Hub a través del endpoint `POST /api/v1/alerts`.
- El servicio de vigilancia consulta la cobertura del turno en ese ala (`ctx-cobertura`) para **identificar qué cuidadores están asignados a esa unidad** en ese minuto específico.
- Distribuye la alerta mediante notificaciones push y alertas sonoras prioritarias a las tablets de los cuidadores asignados en ese turno.

### 5. Respuesta en Habitación y Cierre de la Alerta

- Un cuidador corre a la habitación para asistir a José.
- En el momento en que el cuidador entra al rango del sensor, el sistema óptico detecta su presencia en la habitación (`accompanied_by: staff_id`).
- Esta nueva información viaja por el mismo pipeline. Al llegar a Sentinel, el `IncidentManager` cambia el estado del incidente a **`StaffOnSite`**.
- El sistema **calcula automáticamente los segundos reales de respuesta** (`staff_arrival_seconds`), comparando el momento de la caída con la llegada física de la ayuda. Esto cancela la escala de ruidos y notificaciones acústicas (escalamiento) para evitar interrumpir al personal.
- La ventana de evidencia (`ClipWindow`) se cierra y se guarda de forma segura en la base de datos para futuras revisiones médicas.

---

🧩 ¿Te gustaría que analicemos cómo se configuran las regiones de privacidad de la cámara de la habitación en `ctx-streams` para garantizar que la IA detecte estas caídas sin comprometer la intimidad del residente?