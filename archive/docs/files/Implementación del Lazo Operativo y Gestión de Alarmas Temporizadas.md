# Protocolo Técnico: Implementación del Lazo Operativo y Gestión de Alarmas Temporizadas

## 1. Fundamentos de la Arquitectura de Doble Tiempo

La transición de una vigilancia puramente reactiva a una arquitectura crítica de seguridad exige la adopción de un modelo de **Arquitectura de Doble Tiempo**. En el ecosistema _mana-engine_, la lógica no puede depender exclusivamente de estímulos externos; debe ser capaz de determinar riesgos basados tanto en la presencia de eventos como en la ausencia de los mismos. Para garantizar la seguridad del residente, es imperativo separar el "Tiempo de Evento" del "Tiempo de Reloj".

- **1.1. Tiempo de Evento (Lógica Reactiva):** Define la respuesta inmediata ante percepciones. El flujo se origina en la ingesta del detector (IA Cell) hacia el Hub (`POST /internal/v1/events`), donde se valida e identifica la cama/residente, publicándose finalmente en el bus NATS bajo el tópico `evt_perception`.
- **1.2. Tiempo de Reloj (Lógica de Barrido/Sweep):** La permanencia (dwell) y el escalamiento no pueden depender de que el sensor envíe nuevos datos. Un detector que visualiza a un residente inmóvil en el suelo podría no generar eventos nuevos. Por ello, el sistema implementa un "barrido persistente" que evalúa condiciones de seguridad de forma determinista basándose en el transcurso del tiempo cronológico.
- **1.3. Determinismo y Secuenciación (Lección F8):** Para mitigar inconsistencias causadas por latencia de red o entrega desordenada, el sistema ignora el `timestamp` de creación para la lógica de estado y se rige estrictamente por la secuencia monotónica (`seq/rowid`). Esto garantiza que el historial del residente sea coherente y reproducible.

[!CAUTION] **Requisitos No Negociables del Barrido**

1. **Ejecución Independiente:** El barrido debe ejecutarse periódicamente (tick) sin requerir latidos o actividad del sensor.
2. **Persistencia Transaccional:** Los temporizadores deben sobrevivir a reinicios del Hub; la pérdida de una ventana de tiempo en curso se considera un fallo crítico de seguridad.

## 2. El Gemelo Digital (Digital Twin) y la FSM

El **DigitalTwin** es la proyección viva y en memoria de cada cama. A diferencia de las arquitecturas tradicionales de bases de datos, el Twin es la **fuente de verdad absoluta para el Engine**; su estado no se re-hidrata desde el Hub para cada evaluación, sino que evoluciona mediante la ingesta secuencial de eventos.

### 2.1. Estructura del BedTwin

El objeto `BedTwin` encapsula el contexto operativo necesario para la toma de decisiones:

- `bed_id` y `resident_id`: Identificadores únicos de la escena.
- `estado_actual`: Variante vigente de `PersonState`.
- `state_since`: Instante exacto del último cambio de estado.
- `timers_armados`: Lista de temporizadores de dwell o confirmación vinculados a la cama.

### 2.2. Máquina de Estados Finitos (FSM)

El sistema modela el comportamiento del residente mediante 11 variantes de estado, clasificadas funcionalmente para la aplicación de umbrales:

|   |   |   |
|---|---|---|
|Grupo|Estados (PersonState)|Propósito|
|**In-Bed**|Lying, SittingInBed, BedEdge|Monitoreo de descanso y riesgo de caída inminente.|
|**Out-of-Bed**|Standing, InBathroom, InRoom, InHallway, Outdoor|Vigilancia de deambulación y permanencia fuera de zona segura.|
|**Furniture**|InChair, InWheelchair|Estados de sedestación asistida o mobiliario.|
|**Sentinel**|Unknown|Estado inicial o pérdida de confianza; mantiene la vigilancia activa.|

Las transiciones entre estos estados son estrictamente legales (ej. de `Lying` a `SittingInBed`); cualquier cambio de estado invalida y cancela automáticamente los temporizadores del estado anterior.

## 3. El Lazo Operativo: El Pipeline de 5 Pasos

El Lazo Operativo es el puente que transforma una observación cruda en una acción clínica efectiva. Este pipeline cruza los contextos de Observación, Política, Vigilancia, Cobertura e Identidad.

1. **Evaluación:** El Engine compara la transición o el dwell contra el perfil efectivo del residente. Es un proceso puro: el Engine **emite** `SceneEvents` que describen la novedad.
2. **Creación:** El Hub recibe el `SceneEvent` y lo **materializa** como un incidente/alerta en el System of Record (SoR) si la política lo requiere.
3. **Direccionamiento:** El sistema resuelve la cobertura en tiempo real (quién está de turno en ese ala) para identificar a los responsables.
4. **Entrega:** Se registra el canal y se despacha la notificación.
5. **Escalamiento:** Respuesta determinista ante la falta de acuse (ACK).

### 3.1. Señal de Presencia de Staff

Un componente crítico es la detección de staff mediante la IA Cell. Esta señal genera la métrica `staff_arrival_seconds`, vital para la auditoría clínica. Al ser una señal que no depende de una acción humana deliberada (como presionar un botón), permite distinguir con precisión si una alerta no fue atendida o si fue atendida sin registro manual.

### 3.2. Matriz de Responsabilidad por Binario

|   |   |   |   |
|---|---|---|---|
|Binario|Responsabilidades|NATS (Sub)|NATS (Pub)|
|**Hub**|Persistencia SoR, Ingesta, Materialización de Escenas.|`evt_scene`, `evt_notif`|`evt_perception`|
|**Engine**|Gestión de DigitalTwin (FSM), Motor de Reloj.|`evt_perception`|`evt_scene`|
|**Sentinel**|Evaluación de reglas, Gestión de incidentes.|`evt_scene`|`evt_notif`|
|**Vigilancia**|Entrega a UI/Push, Ciclo de vida de alerta.|`evt_notif`|-|

## 4. Gestión de Temporizadores y Motor de Reloj

La durabilidad en la gestión de tiempos es el pilar que evita los "fallos silenciosos". El sistema utiliza un **Scheduler Durable** implementado sobre una tabla `timers` en SQLite.

### 4.1. El Mecanismo de Claim Transaccional

Para evitar condiciones de carrera en despliegues con múltiples ejecutores, la tabla `timers` utiliza un campo `claimed_at`. El motor de reloj ejecuta un ciclo de `UPDATE ... SET claimed_at = now WHERE ... RETURNING` para asegurar que cada temporizador sea procesado por una única instancia de ejecución de manera atómica.

### 4.2. Tipología y Umbrales de Dwell (Default)

El sistema aplica umbrales de permanencia predefinidos en el catálogo, ajustables por sensibilidad:

- **In-Bed (Lying, SittingInBed, BedEdge):** 300 minutos (5h) para detección de inactividad extrema.
- **Standing:** 5 minutos (riesgo de caída por bipedestación prolongada).
- **InBathroom:** 30 minutos.
- **Out-of-Bed/Furniture:** 10 minutos.

El flujo de vida de un timer es: **Creación** (en cambio de estado) \rightarrow **Evaluación** (en cada tick) \rightarrow **Disparo** (emisión de `SceneEvent::Dwell`) \rightarrow **Limpieza** (eliminación post-ejecución).

## 5. Estándares de Implementación y Puertos (Traits)

El diseño impone que `mana-motores` sea **Puro por Construcción**. No contiene dependencias de IO (Diesel, Network o NATS). La orquestación y la hidratación de datos se delegan exclusivamente a la capa `mana-app`, que actúa como el **Seam** (costura) del sistema.

- **5.1. Definición de Puertos:**
    - `EngineInput`: Recepción de eventos de percepción o ticks de reloj.
    - `EngineOutput`: Emisión de `SceneEvent` hacia el Hub para su materialización.
    - `TwinStore`: Interfaz de persistencia para el estado del Gemelo Digital.
- **5.2. Log de Eventos y Auditoría:** Todas las entradas y salidas se registran en archivos JSONL append-only. Este "Event Store" es el sustrato para auditorías forenses y se compacta periódicamente a Parquet/DuckDB para análisis clínico de alto rendimiento.

## 6. Modos de Falla y Mitigación de Riesgos

### 6.1. Idempotencia y Deduplicación

Para prevenir "tormentas de alertas", el sistema aplica una deduplicación estricta por la tupla `(cama, regla)`. El uso de `source_event_id` asegura que eventos repetidos del sensor no generen alertas redundantes; el Twin solo reacciona ante cambios de estado reales o vencimiento de tiempos.

### 6.2. Matriz de Fallos Sistémicos

|   |   |   |
|---|---|---|
|Fallo|Impacto Operativo|Estrategia de Mitigación|
|**Edge Offline**|Pérdida de percepción en tiempo real.|Buffering local en el Edge; re-ingesta masiva al reconectar.|
|**Caída del Hub**|Cese de persistencia y materialización.|NATS JetStream retiene eventos; persistencia en el reinicio.|
|**Caída del Engine**|Suspensión de transiciones y dwell timers.|Re-hidratación total del Twin desde el último snapshot del Hub.|
|**Caída de NATS**|Interrupción del lazo operativo.|Failover de cluster; reintento exponencial en clientes.|
|**Caída de DB**|Imposibilidad de registro de incidentes.|El Hub rechaza ingesta; Sentinel reintenta publicación vía backoff.|

La observabilidad del lazo se garantiza mediante métricas expuestas en `/__hub/rutas`, monitoreando específicamente el _delay_ entre `fire_at` y la ejecución real, asegurando que la latencia del sistema no comprometa la seguridad clínica.