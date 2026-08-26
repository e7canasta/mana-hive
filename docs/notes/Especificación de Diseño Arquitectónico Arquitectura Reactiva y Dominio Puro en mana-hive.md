# Especificación de Diseño Arquitectónico: Arquitectura Reactiva y Dominio Puro en mana-hive

## 1. Visión General y Propósito del Sistema

La misión crítica de **mana-hive** es garantizar que la persona adecuada llegue a la habitación adecuada a tiempo, minimizando las falsas alarmas que erosionan la respuesta clínica. En un entorno de monitorización nocturna, la seguridad del paciente depende de una arquitectura que priorice el procesamiento determinista de baja latencia sobre la simple entrega de mensajes. No se trata solo de mover datos, sino de garantizar la integridad y la trazabilidad absoluta de cada juicio emitido por el sistema. Esta necesidad de robustez exige que cada decisión sea auditable y reproducible, transformando el flujo sensorial en una crónica de eventos con validez clínica y legal.

El sistema implementa una arquitectura de procesamiento distribuido que transita desde la percepción de bordes (edge) hasta la gestión de alertas humanas. **NATS JetStream** actúa como el sistema nervioso central, proporcionando un buffer persistente y tolerante a fallos para la coordinación en tiempo real. Por otro lado, el **Hub (Postgres)** se consolida como el Sistema de Registro (SoR) inmutable, ingiriendo cada evento para formar una crónica global. Esta estructura de "tuberías y filtros" (pipe-and-filter) permite un escalamiento lineal y, fundamentalmente, habilita la revisión clínica retrospectiva: la capacidad de reconstruir el estado exacto del sistema en cualquier punto del tiempo.

Esta fiabilidad clínica es inalcanzable sin una pureza lógica estricta que aísle las reglas de cuidado de las fluctuaciones de la infraestructura tecnológica.

## 2. Filosofía de Diseño: El Núcleo de Dominio Puro

La importancia estratégica de aislar la lógica de negocio reside en la protección de la propiedad intelectual clínica. Al desacoplar las reglas de decisión de los detalles de implementación (como bases de datos o protocolos de red), garantizamos que el núcleo del sistema sea inmune a la obsolescencia tecnológica. Este aislamiento permite validar la lógica clínica de forma independiente, asegurando que el comportamiento del sistema responda exclusivamente a criterios médicos y operativos definidos.

La arquitectura se asienta sobre tres pilares fundamentales:

- **Arquitectura Dirigida por Eventos (EDA):** Comunicación asíncrona mediante contratos versionados, garantizando un acoplamiento semántico mínimo.
- **Pureza en el Núcleo:** Implementada mediante el patrón "Pure Core + Thin Shell". El plugin de Gradle `manahive.pure-domain` impone restricciones en tiempo de compilación, prohibiendo cualquier dependencia de frameworks (Spring) o infraestructura (NATS, Postgres) en los módulos de dominio.
- **Reproducibilidad Mecánica:** Cada salida del motor incluye "huellas digitales" (fingerprints) de la versión del motor y el estado de entrada.

**Impacto de la Reproducibilidad en la Auditoría:**

- **Auditabilidad Legal:** Capacidad de demostrar ante una revisión clínica o judicial por qué se activó (o se rechazó) una alerta específica.
- **Golden Replay:** Permite re-ejecutar la lógica de producción contra estímulos históricos almacenados en el Hub para verificar correcciones o simular nuevas políticas clínicas.
- **Determinismo Absoluto:** Garantiza que, ante los mismos inputs y versión de motor, el resultado sea idéntico, eliminando efectos secundarios de latencia o relojes de sistema.

Esta pureza se manifiesta técnicamente a través de las interfaces matemáticas definidas en el Kernel.

## 3. El Kernel del Dominio y el Patrón 'Decider'

El Kernel constituye la base matemática del sistema. Aquí, las decisiones se toman sin efectos secundarios, garantizando que el razonamiento clínico sea independiente del entorno de ejecución. Esta determinación es vital para la seguridad del paciente: un sistema que decide de forma distinta basándose en la carga de red o el tiempo de CPU no es apto para un entorno clínico crítico.

### El Patrón Decider

El Kernel define el estado de los agregados mediante la interfaz **Decider**, que utiliza dos funciones puras:

- `decide(command, state)`: Evalúa un comando y produce un resultado de tipo `Decision`, que puede ser **Accepted** (una lista de eventos) o **Rejected** (con una razón de rechazo).
- `evolve(state, event)`: Una función total que aplica un evento al estado previo para producir uno nuevo, reconstruyendo la historia del sistema.

### Telemetría de Decisiones y Descarte

En mana-hive, una decisión sin justificación no existe. El sistema captura no solo lo que ocurrió (Hechos), sino por qué ocurrió (Juicio) y por qué ciertos datos fueron ignorados (Descarte).

|   |   |
|---|---|
|Clase|Propósito Estratégico|
|`Explained<T>`|Contenedor que envuelve el resultado clínico junto con su traza de razonamiento.|
|`ExplanationStep`|Registra la regla aplicada, la observación evaluada y la conclusión derivada.|
|`Discard`|Captura los estímulos filtrados, registrando el motivo mediante un `DiscardCause`.|
|`DiscardCause`|Enum que define razones técnicas de rechazo: `HYSTERESIS_NOT_MET`, `CONFIDENCE_TOO_LOW`, `FATIGUE_BUDGET_EXCEEDED`.|
|`DecisionRecord`|El rastro duradero de una invocación, almacenado en el Hub para auditoría técnica y clínica.|

Para prevenir errores de lógica (como intercambiar identificadores), el Kernel emplea **Identidades Fuertemente Tipadas** mediante `value classes` de Kotlin (ej. `BedId`, `ResidentId`). Esto garantiza seguridad en tiempo de compilación sin penalización de rendimiento en el runtime.

## 4. El Lenguaje Publicado: Contratos y Evolución de Eventos

En un sistema distribuido, el "lenguaje publicado" actúa como una capa de anticorrupción que evita el acoplamiento semántico. Los contratos definen la verdad compartida entre los motores, permitiendo que cada componente evolucione de forma independiente siempre que respete el esquema acordado.

### Estructura del EventEnvelope

Todo mensaje en el bus utiliza el formato `EventEnvelope`, que garantiza un wire format uniforme:

- `**eventId**`: Clave de idempotencia para evitar procesamientos duplicados.
- `**occurredAt**`: Marca de tiempo del hecho en el mundo físico (independiente del procesamiento).
- `**payloadJson**`: El hecho de dominio serializado.

### Jerarquía Clínica y Riesgo

Los contratos fluyen desde la percepción sensorial hasta la alarma gestionada. En el nivel de percepción, cada `Observation` enviada por la **ia-cell** incluye un `confidence score` (0.0 a 1.0), permitiendo que los motores filtren ruido estadístico antes de emitir un juicio.

**Mapeo de** `**PersonState**` **y Grupos de Riesgo:**

|   |   |   |
|---|---|---|
|PersonState|Propósito Clínico|Grupo de Riesgo|
|**Lying**|Residente acostado de forma estable.|Safe|
|**AttemptingExit**|Detección de intención de levantarse.|At Risk|
|**InBathroom**|Residente en área de riesgo fuera de cama.|At Risk|
|**SittingInBed**|Movimiento en cama bajo vigilancia.|At Risk|
|**Unknown**|Estado no determinado por falta de datos.|Unknown|

Para facilitar despliegues blue-green, los sujetos de NATS incluyen la versión (ej. `perception.observation.v1.bed123`). Cualquier cambio incompatible genera un nuevo sujeto (v2), permitiendo la coexistencia de versiones.

## 5. Infraestructura de Mensajería Reactiva con NATS JetStream

NATS JetStream no se utiliza como un archivo histórico, sino como un **buffer persistente de alto rendimiento**. La estrategia de **retención basada en límites (Limits-based retention)** asegura que el bus mantenga la agilidad necesaria para la coordinación en tiempo real, delegando la persistencia a largo plazo al Hub.

### Taxonomía y Configuración técnica

Los sujetos siguen el patrón: `{domain}.{type}.{version}.{discriminator}`. Un ejemplo real sería `perception.observation.v1.bed123`. Los streams están configurados con almacenamiento en **File**, una ventana de deduplicación de 10 minutos y un `Max Age` de 7 días.

**Streams Principales del Sistema:**

|   |   |   |
|---|---|---|
|Stream|Sujetos|Propósito|
|**PERCEPTION**|`perception.observation.v1.>`|Datos brutos y visión artificial desde la **ia-cell**.|
|**SCENE**|`scene.fact.v1.>`|Estado del Gemelo Digital de la habitación.|
|**SENTINEL**|`sentinel.signal.v1.>`|Incidentes clínicos y detecciones de riesgo.|
|**ALARM**|`alarm.event.v1.>`|Ciclo de vida de la alerta (Dispatch, Ack, Resolve).|

El procesamiento es garantizado mediante **Durable Consumers**, lo que permite a cada motor retomar el consumo exactamente donde lo dejó tras un reinicio o fallo de infraestructura.

## 6. Arquitectura de Motores (Engines) y Pipeline de Datos

El sistema se organiza en un pipeline de motores especializados donde cada "filtro" añade valor semántico al flujo de eventos. Una distinción arquitectónica crítica es el foco de seguimiento: el **Scene Engine rastrea la Cama (Bed)** para mantener el Gemelo Digital, mientras que el **Sentinel Engine rastrea al Residente (Resident)**. Esta separación permite que un episodio clínico persista de forma coherente incluso si el residente es trasladado de cama durante la noche.

### Flujo de Trabajo de los Motores

1. **Scene Engine:** Transforma observaciones ruidosas en estados estables mediante el `SceneInterpreter`. El `ClockSweeper` maneja la lógica temporal (ej. transiciones por ausencia de movimiento).
2. **Sentinel Engine:** Es el juez clínico. Evalúa los hechos de la escena contra el `EpisodeLedger` del residente. Gestiona el `FatigueBudget` para asegurar que el personal no sea saturado con alertas redundantes.
3. **Harbor (Vigia):** Gestiona la logística de notificación y el escalamiento basado en tiempos de respuesta.

Cada motor se divide en tres capas: **Pure Domain** (lógica), **Spring Service** (infraestructura) y el **Batch Tool**. El Batch Tool es la pieza clave para la validación offline, permitiendo realizar el "Golden Replay" para certificar que una nueva versión de la lógica clínica mejora la precisión sin introducir regresiones.

## 7. El Hub como Sistema de Registro e Integridad

A pesar de la naturaleza distribuida de los motores, mana-hive requiere un **Hub centralizado (Postgres)** que funcione como la "Crónica" global y Sistema de Registro. Su rol es fundamental para la integridad del ecosistema de cuidados.

El Hub ingiere todos los eventos y almacena el `DecisionRecord`. Gracias a la pureza del dominio, cualquier decisión puede ser reproducida mecánicamente re-ejecutando los mismos inputs en la misma versión del motor que se utilizó en producción. Además, el Hub actúa como el orquestador de lógica dinámica a través de `HubSceneDagSource`, enviando los grafos de interpretación (DAGs) que definen cómo los motores deben procesar las escenas en tiempo de ejecución.

## 8. Build System y Aplicación de Reglas Arquitectónicas

El sistema de construcción basado en Gradle es el guardián proactivo de la arquitectura. Mediante el uso de **Included Builds**, centralizamos la lógica de construcción para prevenir la duplicación de código y, sobre todo, para imponer las fronteras entre el dominio y la infraestructura.

**Plugins de Convención y Gobernanza:**

|   |   |   |
|---|---|---|
|Plugin|Aplicación|Función y Restricción Arquitectónica|
|`manahive.pure-domain`|Módulos `*-domain`|**Prohíbe estrictamente** Spring/NATS/Postgres. Asegura un núcleo determinista.|
|`manahive.spring-service`|Módulos `*-service`|Configura el shell de infraestructura, inyección de dependencias y NATS.|

Para gestionar la diversidad de entornos de despliegue, el patrón `LocalConfig` permite fusionar valores predeterminados (TOML) con overrides específicos del entorno (como credenciales o URLs locales). Esto asegura despliegues de "toque cero" en redes de instalaciones diversas sin comprometer la portabilidad del código.

### Resumen de Cierre

La arquitectura de mana-hive establece un estándar de robustez clínica mediante la convergencia de un dominio funcional puro y una mensajería reactiva persistente. Al garantizar que cada decisión es auditable, reproducible y aislada de la infraestructura, hemos creado un sistema que no solo protege a los residentes hoy, sino que está diseñado para ser verificado y mejorado continuamente ante los retos del futuro del cuidado asistencial.