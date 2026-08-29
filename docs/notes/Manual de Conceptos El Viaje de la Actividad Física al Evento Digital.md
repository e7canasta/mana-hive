# Manual de Conceptos: El Viaje de la Actividad Física al Evento Digital

Como arquitectos de sistemas de misión crítica, nuestra prioridad no es solo que el código compile, sino que la intención clínica detrás de cada línea se preserve intacta. Este manual detalla el pipeline de procesamiento de **Hisso**, un flujo reactivo diseñado para transformar señales físicas en decisiones que salvan vidas.

## 1. La Filosofía Detrás del Código: Pureza y Arquitectura Hexagonal

El núcleo de Hisso se rige por los principios de **Arquitectura Hexagonal** y **Dominio Puro (Pure Domain)**. Nuestra misión es asegurar que la lógica clínica nunca se vea comprometida por el ruido de la infraestructura. Por ello, los motores (engines) son "puros": funciones matemáticas que reciben una entrada, evalúan una regla y devuelven un resultado sin interactuar con el mundo exterior (APIs, bases de datos o NATS).

Esta pureza no es opcional ni un simple deseo; está impuesta por **Architectural Guards** mediante plugins de Gradle (`manahive.pure-domain`). Si un desarrollador intenta introducir una dependencia externa (como Spring o NATS) en un módulo de dominio, el build fallará automáticamente.

**Ventajas de esta pureza para el diseño de software:**

- **Portabilidad Absoluta:** La misma lógica corre en servidores de producción, herramientas de consola o arneses de prueba BDD.
- **Pruebas BDD (Behavior-Driven Development):** Permite verificar comportamientos complejos simulando estados humanos sin necesidad de infraestructura real.
- **Ausencia de Efectos Secundarios:** El sistema es predecible y determinista, lo que facilita la depuración en escenarios de alta concurrencia.

_Establecida la pureza estructural, el siguiente paso es definir el lenguaje común que fluye a través de ella._

## 2. El Alfabeto de Hisso: Identificadores y Contratos Compartidos

Para combatir la "obsesión por los tipos primitivos", Hisso utiliza clases de valor de Kotlin para sus identificadores. Esto garantiza que el sistema no confunda nunca el ID de una cama con el de un residente, asegurando que el dato nunca se mezcle en el pipeline.

|   |   |   |
|---|---|---|
|Concepto|Entidad de Código|Propósito en el Mundo Real|
|**Cama**|`BedId`|Ubicación física exacta y única del monitoreo.|
|**Residente**|`ResidentId`|Identidad de la persona protegida por el sistema.|
|**Episodio**|`EpisodeId`|Agrupador lógico de todos los eventos de un incidente de seguridad.|
|**Regla**|`RuleId`|Referencia a la norma clínica específica dentro del catálogo.|
|**Referencia**|`EventRef`|**Correlación:** Vincula cualquier señal final con el hecho original del sensor.|

_Estos contratos aseguran la integridad del dato antes de entrar en nuestra tubería reactiva._

## 3. La Tubería de Cuatro Etapas (The Pipeline)

El sistema opera bajo el patrón reactivo **Consume-Evaluate-Publish**. Cada etapa es un motor especializado que transforma la información:

1. **Scene Engine:** `Observation` -> **Digital Twin** -> `SceneEvent`.
2. **Sentinel Engine:** `SceneEvent` -> **Clinical Judgment** -> `SentinelSignal`.
3. **Harbor Engine (Vigia):** `SentinelSignal` -> **Delivery Logistics** -> `NoticeCommand`.
4. **Recorder Engine:** `SceneEvent/Signal` -> **Evidence Gathering** -> `RecordingCommand`.

_Hagamos un "zoom" en la primera etapa, donde los datos crudos adquieren significado humano._

## 4. Etapa 1: Scene Engine – Creando el "Gemelo Digital"

El **Scene Engine** mantiene el **DigitalTwin** del residente. Su función es traducir observaciones de sensores en un **Semantic State** (Estado Semántico) comprensible para los médicos (ej. "Sentado", "Cama vacía").

**Flujo Lógico:** `Observation` -> `SceneInterpreter` -> `SceneEvent`.

**Subtareas de este motor:**

- **Verificación de Confianza (**`**minConfidence**`**):** Si un sensor reporta datos con baja fidelidad, el motor los descarta para evitar falsos positivos.
- **Validación de Transición:** Asegura que los movimientos sean físicamente posibles (ej. no se puede pasar de "Estar en la cama" a **Entrar al baño** sin pasar por "Borde de cama").
- **Histéresis:** Implementa buffers de tiempo para evitar que el estado "parpadee" ante ruidos transitorios del sensor.

**Tipos de SceneEvent (Hechos Semánticos):**

|   |   |
|---|---|
|Evento|Representación Física|
|`TransitionDetected`|El residente cambió su posición (ej. de acostado a sentado).|
|`DwellExceeded`|Permanencia excesiva en un estado no seguro.|
|`ComeBackExceeded`|Salida de la cama sin retorno en el tiempo permitido.|
|`SignalLost`|Pérdida de comunicación con el hardware de la habitación.|

_Una vez definido el "qué está haciendo", el sistema debe aplicar un juicio clínico para decidir si existe un riesgo._

## 5. Etapa 2: Sentinel Engine – El Juicio Clínico

El **Sentinel Engine** es el cerebro médico del sistema. Su rol es gestionar el ciclo de vida de un **Episode** (Incidente de seguridad) a través del `EpisodeLedger`.

A diferencia de otros motores, el Sentinel tiene la capacidad única de monitorear la **ausencia** de un estado (lógica `ComeBack`). No solo reacciona a lo que sucede, sino a lo que _deja de suceder_ (ej. el residente no regresó a la cama). Evalúa los eventos contra las `AlertRule` y genera señales:

- **EpisodeOpened:** Apertura de un incidente de seguridad con severidad asignada.
- **UmbrellaEvent:** Eventos de contexto (ej. movimiento continuo) que ocurren mientras el episodio principal sigue activo.
- **AutoRecovery:** Detección de que el residente volvió a la seguridad por sus propios medios.
- **EpisodeClosed:** Resolución formal del incidente.

**Protocolos de Cierre (Closure Conditions):**

1. **SAFE_ONLY:** Cierre automático cuando el residente vuelve a estar a salvo.
2. **STAFF_OR_SAFE:** Cierre por presencia de personal O retorno a la seguridad.
3. **STAFF_AND_SAFE:** Requiere obligatoriamente personal presente Y residente seguro.

_Tras el juicio clínico, pasamos de la teoría del riesgo a la logística práctica de notificación._

## 6. Etapa 3: Harbor Engine (Vigia) – Logística de Notificación

El motor **Harbor** (o **Vigia**) gestiona la entrega de alertas. Su foco principal es mitigar la **fatiga de alarmas**, un fenómeno peligroso donde el personal ignora alertas por saturación.

**Canales de Entrega Disponibles:**

|   |   |
|---|---|
|Canal|Propósito|
|**CONSOLE**|Registro histórico y paneles de control central.|
|**PUSH**|Alertas móviles para personal distribuido en la planta.|
|**TABLET**|Interfaz táctil en dispositivos de mano o pared.|
|**WARD_BOARD**|Pantallas de gran formato en estaciones de enfermería.|

**Gestión de Presupuesto:** Utiliza un **NotificationBudget** que limita cuántos avisos de baja prioridad se emiten por turno. No obstante, las alertas de severidad **CRITICAL** tienen un "Safety Override": nunca se suprimen, pues la seguridad de vida es absoluta.

_Mientras se coordina el aviso humano, el sistema garantiza la captura de evidencia técnica._

## 7. Etapa 4: Recorder Engine – Capturando la Evidencia

El **Recorder Engine** cierra el pipeline ordenando la captura de video para auditoría clínica. Utiliza el `RecordingLedger` para gestionar sesiones de grabación y evitar duplicidad de archivos si varios sensores disparan simultáneamente.

Para entender el origen de un incidente, el motor gestiona ventanas temporales:

- **recordBefore (Pre-roll):** Segundos grabados antes del evento para ver su inicio.
- **recordAfter (Post-roll):** Segundos grabados tras el evento para evaluar la respuesta.

**Estándares de Calidad y Bitrate:**

|   |   |   |
|---|---|---|
|Calidad|Resolución / FPS|Bitrate|
|**SD**|640x480 @ 15fps|**1 Mbps**|
|**HD**|1280x720 @ 30fps|**5 Mbps**|
|**FULL**|1920x1080 @ 30fps|**10 Mbps**|

## 8. Política y Calibración: Traduciendo la Intención a Código

El sistema resuelve el lenguaje humano (ej. "Avísame si José sale de la cama por más de 5 minutos") mediante dos conceptos:

1. **Policy:** La definición de alto nivel del cuidado requerido.
2. **Calibration:** La traducción técnica de esa política en parámetros de motor (ej. `threshold = 300s`).

Para garantizar la auditabilidad total, cada decisión se envuelve en el contenedor **Explained****<T>**. Este no solo lleva el resultado, sino una lista de razones humanas y una **Fingerprint** (Huella Digital). Esta huella es un hash único de las reglas exactas que generaron esa configuración, permitiendo reconstruir meses después por qué el sistema tomó una decisión específica en un segundo determinado.

_Este flujo cierra el círculo de seguridad. Ahora, te invito a explorar los_ _**Blueprints**_ _para ver este pipeline operando en escenarios de monitoreo real._