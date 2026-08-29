# Especificación Técnica: Flujo de Datos Reactivo entre Motores de Dominio Puro

## 1. Arquitectura de Referencia y Principios Fundamentales

La plataforma Hisso ha sido concebida bajo una premisa innegociable: la seguridad de los residentes en entornos de cuidado crítico depende de la predictibilidad y la pureza de la lógica de negocio. Para alcanzar este nivel de robustez, hemos adoptado un diseño de **Dominio Puro (Pure Domain)** sustentado en una Arquitectura Hexagonal. Este enfoque garantiza que el "Core" del sistema —donde residen el juicio clínico y la interpretación de escenas— esté totalmente desacoplado de los detalles de infraestructura, como la mensajería NATS o las APIs REST. Como Arquitecto Principal, mi objetivo es asegurar que una regla de seguridad pueda ejecutarse con el mismo determinismo en un microservicio de producción, en un procesador por lotes (batch) o en un arnés de pruebas BDD.

### Pure Domain y Arquitectura Hexagonal

La separación estricta permite que la lógica central permanezca agnóstica a los efectos secundarios. El stack tecnológico está anclado en **Java 25 (JVM Toolchain)** y **Kotlin 2.4.20-RC**, gestionado por **Gradle 9.6.0**. Esta base técnica nos permite utilizar tipos avanzados y contratos inmutables que son procesados por adaptadores externos, permitiendo que el mismo código de dominio sea orquestado por NATS JetStream en la nube o invocado directamente por una CLI.

### Restricciones de Pureza y Architectural Guards

Para evitar la degradación arquitectónica, implementamos guardias automatizados mediante el plugin de Gradle `manahive.pure-domain`. Este plugin no solo activa la configuración `explicitApi()` para obligar a una visibilidad estricta, sino que impone restricciones de compilación drásticas:

- **Prohibición de Frameworks:** No se permite el uso de Spring Boot, JDBC o cualquier librería de infraestructura en los módulos `-domain`.
- **Aislamiento de Dependencias:** Los módulos de producción tienen prohibido depender de artefactos de prueba (`-bdd`, `-test-data`).
- **Validación del Classpath:** El build falla si se detectan dependencias externas (fuera de la librería estándar de Kotlin) en el runtime de los núcleos.

### Estructura Modular

El sistema se organiza en una jerarquía de módulos que diferencia claramente entre la lógica pura (`*-domain`) y sus adaptadores de servicio (`*-service`):

- `**:platform**`: Contiene el Kernel (`kernel`), contratos compartidos (`contracts`) e infraestructura base.
- `**:hub**`: Sistema de registro para censo y políticas (`hub-service`).
- `**:engines**`: Unidades lógicas individuales:
    - `scene-engine`: (`-domain`, `-service`, `-batch`).
    - `sentinel-engine`: (`-domain`, `-service`).
    - `harbor-engine`: (`-domain`, `-service`).
    - `recorder-engine`: (`-domain`, `-service`, `-batch`).

Estos principios de pureza técnica son el cimiento sobre el cual construimos el flujo de datos reactivo que da vida al sistema.

## 2. El Pipeline Reactivo de Cuatro Etapas

El corazón operativo de Hisso es un pipeline lineal que sigue el patrón **Consume-Evalúa-Publica**. Este diseño es vital para transformar telemetría de sensores en acciones de cuidado en tiempo real, manteniendo un **Gemelo Digital (Digital Twin)** preciso de cada residente sin introducir latencias por bloqueos de I/O.

### Visión General de los Cuatro Motores

1. **Scene Engine**: Recibe un objeto `Observation` y lo traduce en eventos semánticos de alto nivel (`SceneEvent`), como transiciones entre estados (ej. de "Tumbado" a "Sentado en cama").
2. **Sentinel Engine**: Ingiere los `SceneEvent` y aplica juicio clínico para gestionar el ciclo de vida de un `Episode`, emitiendo señales de alerta (`SentinelSignal`).
3. **Harbor Engine (Vigia)**: Transforma las señales clínicas en instrucciones logísticas de notificación (`NoticeCommand`) dirigidas a canales específicos.
4. **Recorder Engine**: Evalúa tanto eventos de escena como señales de Sentinel para emitir comandos de grabación (`RecordingCommand`) al sistema de video.

### Interdependencia y Orquestación

La salida de un motor es la entrada estrictamente tipada del siguiente. Esta interdependencia asegura que el sistema evolucione de lo físico (sensores) a lo clínico (riesgos) y finalmente a lo operativo (notificaciones). En producción, la orquestación se realiza mediante el `ResidentRuntime`, que compone los motores mediante llamadas a funciones estándar en memoria. Esto elimina el overhead de serialización interna y garantiza que el flujo de datos sea atómico para el estado del residente.

## 3. Kernel de Dominio y Contratos Compartidos

Para evitar la "Obsesión por Primitivos", el sistema utiliza un lenguaje ubicuo (Ubiquitous Language) expresado mediante tipos fuertes. No manejamos simples "Strings" o "Longs", sino conceptos de dominio que impiden errores de intercambio de parámetros en tiempo de compilación.

### Identificadores con Tipado Fuerte

Utilizamos clases de valor de Kotlin para todos los IDs críticos:

|   |   |
|---|---|
|Identificador|Propósito Específico|
|`BedId`|Identificador único de la ubicación física de la cama.|
|`ResidentId`|Identificador único del residente asociado.|
|`EpisodeId`|Referencia a un evento de seguridad continuo (ej. una caída).|
|`RuleId`|Referencia a una regla clínica en el catálogo de políticas.|
|`EventRef`|ID de correlación para rastrear señales hasta el hecho original del sensor.|

### Interfaces Engine y Decider

La lógica se estandariza bajo dos abstracciones:

- **Decider<S, I, C, O>**: Componente puramente funcional que evalúa una entrada (I) con una calibración (C) y un estado (S), retornando un `Effect` (nuevo estado y eventos).
- **Engine<S, I, C, O>**: Envoltorio estatal que utiliza el `Decider`. Implementa el tipo `Discardable<O>`, una pieza clave de nuestra arquitectura reactiva que permite manejar explícitamente datos descartados por ruido o latencia de red.

### Auditabilidad y Mensajería

Las decisiones clínicas críticas se encapsulan en el wrapper `Explained<T>`, que transporta tanto el resultado como la lista de razones técnicas que lo justifican. Para la comunicación externa, utilizamos el `EventEnvelope`, que estandariza metadatos como `source`, `type` y la marca de tiempo `at`, facilitando el trazado completo de cada mensaje en el backbone de NATS.

## 4. Análisis Profundo de los Motores de Dominio

### Scene Engine (El Gemelo Digital)

Este motor mantiene el estado humano (`PersonState`) en el `DigitalTwin`. Su lógica de validación es rigurosa:

1. **Confidence Check**: Filtra observaciones basadas en umbrales de confianza por estado.
2. **Transition Validation**: Verifica la legalidad del movimiento en la `TransitionTable`.
3. **Hysteresis**: Aplica un buffer temporal (por defecto **1500ms**) para estabilizar los cambios de estado y evitar el "flickering" de alertas. El `ClockSweeper` actúa como un motor de tiempo interno, generando eventos como `DwellExceeded` (exceso de permanencia) incluso en ausencia de nuevas observaciones.

### Sentinel Engine (Juicio Clínico)

Gestiona el `EpisodeLedger` para dar seguimiento a los riesgos. Soporta la lógica de **Severity Ramp**, que permite elevar la gravedad de un episodio (ej. de WARNING a CRITICAL) si el riesgo aumenta. La finalización de un episodio está sujeta a tres condiciones de cierre estrictas:

- **SAFE_ONLY**: Cierre automático al volver a un estado seguro.
- **STAFF_OR_SAFE**: Cierre por presencia de personal O retorno a seguridad.
- **STAFF_AND_SAFE**: Requiere ambos factores para dar por concluido el incidente.

### Harbor Engine (Logística de Notificación)

Contrasta la urgencia clínica con la capacidad humana. Gestiona canales como `CONSOLE`, `PUSH`, `TABLET` y `WARD_BOARD`. Implementa un **Notification Budget** para mitigar la fatiga por alarmas; no obstante, por diseño de seguridad vital, las alertas de `Severity.CRITICAL` **nunca** son suprimidas por este presupuesto.

### Recorder Engine (Captura de Evidencia)

Traduce eventos en comandos para el NVR. Su valor reside en la gestión de ventanas de grabación: el "pre-roll" y "post-roll" aseguran que el equipo clínico tenga contexto visual antes y después del trigger, con calidades configurables (SD, HD, FULL).

## 5. Infraestructura de Mensajería NATS y Aislamiento

**NATS JetStream** actúa como la columna vertebral asíncrona de la plataforma. La arquitectura garantiza un aislamiento total de los datos mediante una taxonomía de subjects granular.

En el `NightWatchRuntime`, la gestión de la concurrencia es fundamental. Utilizamos un `ConcurrentHashMap` para orquestar múltiples residentes en paralelo, pero aplicamos una restricción de seguridad crítica: **los eventos para un residente específico se procesan de forma secuencial**. Esto garantiza la integridad del estado del `DigitalTwin` y evita condiciones de carrera en la evaluación de reglas. Gracias a `NatsTopology`, el sistema soporta el replay de eventos, permitiendo reconstruir estados históricos o realizar auditorías forenses tras un incidente.

## 6. Resolución de Políticas y Calibración Dinámica

El sistema traduce el lenguaje del "Director de la Instalación" en parámetros técnicos mediante el **Motor Política**.

- **Resolución**: Un `ResidentProfile` y un `DagCatalog` se resuelven en un `PolicyCalibration`.
- **Distribución**: Este objeto se descompone en calibraciones específicas (`SceneCalibration`, `SentinelCalibration`, etc.) para cada motor.
- **Trazabilidad**: Cada calibración genera un `Fingerprint` (hash único), lo que permite auditar exactamente qué versión de las reglas de seguridad estaba activa en el momento de producirse una alerta.

## 7. Verificación del Flujo mediante Blueprints

Los **Blueprints** son nuestras especificaciones ejecutables. No son solo pruebas; son validaciones de extremo a extremo que aseguran que el flujo de datos cumple con los requisitos de seguridad.

### Caso de Estudio: José 301

El blueprint `jose-301-sitting-bed` verifica escenarios críticos como el "ComeBack" (retorno a cama). Rastrea cómo una salida de cama detectada por el Scene Engine activa un temporizador que, al no cumplirse, dispara un `SentinelSignal` de alerta, culminando en un `NoticeCommand` en el Harbor Engine.

### Ejecución de Verificaciones

Para validar este comportamiento de manera determinista, ejecutamos el comando: `./gradlew :blueprints:jose-301-sitting-bed:run`

Esta arquitectura reactiva, basada en motores de dominio puro y un pipeline estrictamente desacoplado, asegura que Hisso sea una plataforma de monitoreo no solo escalable y auditable, sino profundamente confiable para la protección de la vida humana.