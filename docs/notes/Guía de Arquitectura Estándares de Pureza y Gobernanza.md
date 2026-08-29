# Guía de Arquitectura: Estándares de Pureza y Gobernanza

Esta guía establece los estándares técnicos y mandatos de ingeniería para **Hisso**, una plataforma de alta integridad para el monitoreo de seguridad de residentes. Como arquitectura fundamentada en el diseño orientado al dominio (DDD) y la arquitectura hexagonal, el sistema exige una separación absoluta entre la lógica de decisión clínica y la infraestructura técnica.

### 1. Filosofía de Diseño: Pure Domain y Arquitectura Hexagonal

La integridad estratégica de Hisso reside en su **Pure Domain**. El núcleo del sistema es agnóstico a frameworks, mecanismos de persistencia o protocolos de transporte. Esta pureza garantiza la **portabilidad total**: el código de dominio puede ejecutarse indistintamente en microservicios reactivos sobre NATS, procesos de análisis forense por lotes (Batch) o arneses de prueba de comportamiento (BDD).

Para asegurar esta pureza, segregamos las responsabilidades mediante una arquitectura hexagonal estricta:

- **Interfaces de Dominio (**`**Engine**`**,** `**Decider**`**):** Contratos funcionales puros. Operan exclusivamente en memoria, ignoran el tiempo real (utilizando un `ClockSweeper` para inyectar temporalidad) y prohíben el uso de I/O o reflexión.
- **Adaptadores de Infraestructura (**`**SceneNatsEgress**`**, Servicios Spring Boot):** La periferia del sistema. Gestionan la serialización, el enrutamiento de NATS JetStream y la orquestación de frameworks.

Esta separación se materializa en el `ResidentRuntime`, que orquesta los motores de dominio mediante llamadas directas a funciones, eliminando penalizaciones de performance por serialización innecesaria entre etapas del pipeline.

### 2. El Domain Kernel: Contratos y Primitivas Universales

El **Domain Kernel** actúa como el árbitro de la consistencia estructural y el lenguaje ubicuo en Hisso.

#### Identificadores Fuertemente Tipados

Para erradicar el "Antipatrón de Obsesión por Primitivos", utilizamos _value classes_ de Kotlin. Esto garantiza que un identificador nunca sea intercambiado por otro a nivel de compilación.

|   |   |
|---|---|
|Tipo|Propósito|
|`BedId`|Identidad única de la ubicación física (cama).|
|`ResidentId`|Identidad única del residente asignado.|
|`EpisodeId`|Referencia a un incidente de seguridad continuo.|
|`RuleId`|Referencia a una regla clínica específica en el catálogo de políticas.|
|`EventRef`|Correlación para rastrear señales hasta su origen sensorial exacto.|

#### El Patrón "Functional Core"

La ejecución en Hisso se estandariza mediante la interfaz `Engine<S, I, C, O>`, que procesa una entrada y una calibración para producir un resultado de tipo `**Discardable<Explained<T>>**`.

- `**Discardable<T>**`**:** Es fundamental para la integridad del sistema; permite al motor ignorar explícitamente datos tardíos o irrelevantes sin romper el flujo reactivo.
- `**Explained<T>**`**:** Proporciona un rastro de auditoría humano. No solo transporta el resultado técnico, sino también una lista de razones (`Reason`) que justifican por qué se activó un umbral clínico, permitiendo la transparencia total en decisiones críticas.

### 3. El Sistema de Motores: Lógica Funcional Pura

El pipeline de procesamiento se organiza en cuatro motores orquestados secuencialmente, cada uno libre de efectos secundarios y dependiente únicamente de su entrada y su `PolicyCalibration`.

#### I. Scene Engine (Motor de Escena)

- **Propósito:** Mantener el **Digital Twin** (Gemelo Digital) del estado físico del residente en tiempo real.
- **Transformación:** `Observation` (crudo) → `SceneEvent`.
- **Lógica Crítica:** Implementa **Hysteresis** para filtrar el ruido de sensores y gestiona el **Dwell** (tiempo de permanencia en estados como `BedEdge` o `Sitting`).

#### II. Sentinel Engine (Motor Centinela)

- **Propósito:** Aplicar juicio clínico para gestionar el **Ciclo de Vida del Episodio**.
- **Transformación:** `SceneEvent` → `SentinelSignal`.
- **Lógica Crítica:** Gestiona la apertura y cierre de episodios de seguridad, detecta **Umbrella Events** (eventos contextuales dentro de un incidente activo) y emite señales de **Auto-Recovery** si el residente regresa a un estado seguro.

#### III. Harbor Engine (Vigia)

- **Propósito:** Orquestar la logística de entrega y mitigar la fatiga de alarmas.
- **Transformación:** `SentinelSignal` → `NoticeCommand`.
- **Lógica Crítica:** Administra el **Notification Budget** (Presupuesto de Notificaciones), asegurando que solo las alertas críticas (`Severity.CRITICAL`) ignoren los límites de despacho por turno para proteger la atención del personal.

#### IV. Recorder Engine (Motor de Grabación)

- **Propósito:** Capturar evidencia visual de incidentes para revisión clínica.
- **Transformación:** (`SceneEvent`, `SentinelSignal`) → `RecordingCommand`.
- **Lógica Crítica:** Opera con **Recording Windows**, aplicando buffers de _pre-roll_ y _post-roll_ para contextualizar el video antes y después de la activación del disparador.

### 4. Gobernanza del Código: Purity Guard y Gradle Plugins

La arquitectura de Hisso no es una sugerencia; es una restricción técnica impuesta por el sistema de construcción Gradle (v9.6.0) bajo Java 25.

1. `**manahive.kotlin-common**`**:** Establece el entorno base y el _Test-dependency guard_, prohibiendo que módulos de producción dependan de artefactos de prueba (`-bdd`, `-test-data`).
2. `**manahive.pure-domain**`**:** Activa el **Purity Guard**. Este plugin **falla la compilación** si detecta dependencias externas (Spring, NATS, JDBC) en el runtime classpath de los módulos de dominio.
3. `**manahive.spring-service**`**:** Configura los adaptadores de infraestructura para los microservicios.

**Convención de Nomenclatura:** La estructura de módulos (`-domain`, `-service`, `-batch`) no es cosmética. El sufijo `-domain` actúa como disparador de las reglas de firewall del sistema de construcción, protegiendo la lógica de negocio de la contaminación tecnológica.

### 5. Calibración Orientada a Políticas: El Contrato Maestro

El comportamiento de Hisso se deriva de un `PolicyCalibration`, eliminando cualquier lógica rígida (_hard-coded_).

- **DagCatalog:** Las reglas de la casa se definen mediante un Grafo Acíclico Dirigido (DAG) que traduce el "Lenguaje del Director" (ej. "Avisar si Jose se sienta por 15 min") en umbrales técnicos consumibles por los motores.
- **Componentes de Calibración:**
    - `EffectiveRules`: Set resuelto de reglas y severidades.
    - `SceneCalibration`: Umbrales de histéresis y latidos de salud (`heartbeat`).
    - `HarborCalibration`: Canales de despacho y reglas de escalación.
    - `RecorderCalibration`: Calidad y duración de los clips de video.
- **Auditabilidad:** Cada calibración genera un `**Fingerprint**` (hash único). Este identificador permite auditar exactamente qué versión de la política clínica produjo una decisión del sistema en un momento determinado.

### 6. Infraestructura y Mensajería: El Perímetro del Sistema

El dominio habita dentro de un perímetro donde **NATS JetStream** gestiona la comunicación asíncrona y persistente.

- **EventEnvelope:** El estándar universal para el bus de datos. Incluye campos obligatorios: `id`, `source`, `type`, `at` y `payload`.
- **Aislamiento de Residentes:** La `NatsTopology` garantiza la segregación estricta de datos mediante la taxonomía de sujetos. Las alarmas siguen el patrón de enrutamiento: `alarm.event.v1.<severity>`.
- **Serialización:** El `NatsObjectMapper` asegura que los tipos `Instant` se serialicen como ISO-8601, manteniendo la auditabilidad fuera del dominio puro y garantizando la compatibilidad entre servicios.

La adherencia innegociable a estos estándares garantiza que Hisso permanezca como un sistema escalable, auditable y, por encima de todo, centrado en la seguridad incondicional del residente.