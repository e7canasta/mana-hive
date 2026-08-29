# Especificación de Arquitectura: Orquestación del Scene Engine

## 1. El Patrón Facade y la Simplificación de la Complejidad

El **Scene Engine** constituye la etapa inicial y crítica del pipeline de procesamiento de observaciones en nuestro ecosistema sociosanitario. Su función estratégica es actuar como un mediador inteligente que transforma los datos brutos provenientes de los sensores en una representación coherente y procesable de la realidad física. Para gestionar esta complejidad, el sistema implementa el patrón de diseño **Facade**, abstrayendo la intrincada lógica de interpretación de sensores y la gestión de estados multivariable bajo una interfaz unificada. Esta abstracción permite que el resto del sistema interactúe con una API simplificada, delegando la coordinación interna de tiempos, transiciones y validaciones al motor de escenas.

La jerarquía de responsabilidades dentro del Scene Engine se articula a través de tres componentes internos especializados, coordinados de manera armónica:

- **SceneInterpreter:** Gestiona la lógica para transicionar entre estados basándose en observaciones entrantes.
- **ClockSweeper:** Administra eventos dependientes del tiempo (temporizadores de permanencia y de retorno) que ocurren entre observaciones o en el momento de las mismas.
- **DigitalTwin:** Actúa como el contenedor de estado persistente que representa el estatus actual del residente y la salud de la señal.

Desde una perspectiva arquitectónica, el patrón Facade no solo simplifica el consumo de datos, sino que protege el dominio central contra la "filtración de observaciones" (Observation Leakage). Al evitar que datos de sensores crudos y no validados contaminen los servicios de nivel superior, garantizamos que el sistema sea escalable y mantenible. Esta orquestación asegura que cualquier interacción con el residente se base en un modelo de estado simplificado y robusto, cuya materialización definitiva se encuentra en el Digital Twin.

## 2. El DigitalTwin: Fuente de Verdad del Estado del Residente

En el núcleo del Scene Engine reside el **DigitalTwin** (Gemelo Digital), un contenedor de estado que funciona como la única fuente de verdad para un binomio cama/residente específico. Su ciclo de vida está intrínsecamente ligado a esta pareja, asegurando que la realidad física representada sea unívoca. En entornos de monitoreo crítico, mantener este gemelo digital es vital para la precisión de los sistemas de alerta temprana, proporcionando la persistencia necesaria para contextualizar cada evento.

El DigitalTwin rastrea cuatro pilares fundamentales para garantizar una visibilidad completa:

1. **Estado (PersonState):** Registra la posición física actual, utilizando categorías precisas del dominio como _Lying_ (Acostado), _BedEdge_ (Borde de cama), _Standing_ (De pie) o _Unknown_.
2. **Contexto Temporal:** A través del campo `stateSince`, el sistema mantiene un registro exacto de cuándo se inició el estado actual, dato indispensable para los cálculos de permanencia (_dwell_).
3. **Salud de la Señal:** Supervisa la integridad de la comunicación mediante el rastreo del último latido de corazón (_heartbeat_) del monitor, detectando proactivamente la pérdida de señal.
4. **Calibración:** Integra la `SceneCalibration` aplicada, lo que garantiza que cada transición y umbral sea específico para el residente en cuestión.

Desacoplar el contenedor de estado de la lógica de decisión permite que el sistema sea un reflejo fiel y, lo más importante, auditable del entorno físico. Esta separación de preocupaciones asegura que el DigitalTwin actúe como un registro histórico y situacional confiable. Sin embargo, para que este estado evolucione, el sistema debe validar primero todas las entradas mediante el intérprete de escenas.

## 3. SceneInterpreter: Lógica de Transición y Filtrado de Ruido

El **SceneInterpreter** asume la responsabilidad de transformar observaciones brutas en transiciones de estado válidas, actuando como el filtro de calidad definitivo. En un entorno de sensores volátil, su función es imponer estabilidad y asegurar que solo los cambios físicos genuinos impacten en el modelo de estado.

El flujo lógico de interpretación se ejecuta mediante tres pasos estrictos:

- **Verificación de Confianza:** Si la confianza de la observación es inferior al `minConfidence` definido en la calibración para ese estado, la observación es descartada. Técnicamente, este descarte se registra bajo la entidad `DiscardCause.CONFIDENCE_TOO_LOW`, facilitando auditorías posteriores.
- **Validación de Transición:** El componente consulta la `TransitionTable` para verificar si el movimiento desde el estado actual al observado es legal según el modelo físico configurado.
- **Histéresis:** Incluso ante transiciones legales, el sistema impone estabilidad temporal. La transición debe persistir durante un periodo configurado (ej. 1500ms) antes de que el DigitalTwin actualice oficialmente su estado.

Este diseño no solo reduce falsos positivos, sino que impone una estabilidad temporal en un entorno de señales inherentemente ruidosas. Mientras que el SceneInterpreter gestiona los cambios de estado **explícitos** derivados de las observaciones, la arquitectura debe también contemplar los cambios de estado **implícitos** que ocurren por el mero paso del tiempo.

## 4. ClockSweeper: Gestión Proactiva de Eventos Temporales

El **ClockSweeper** es el componente encargado de la dimensión temporal del sistema. Su importancia estratégica radica en la generación de hechos (`SceneEvent`) basados exclusivamente en el paso del tiempo, permitiendo que el sistema responda incluso cuando existe una ausencia total de nuevos datos de los sensores.

A continuación, se detallan los eventos críticos generados por este componente:

|   |   |
|---|---|
|Tipo de Evento|Condición de Disparo (Trigger)|
|**DwellWarning**|El residente permanece en un estado (ej. _Standing_) más allá del umbral de advertencia inicial.|
|**DwellExceeded**|La permanencia en un estado ha superado la duración máxima permitida por seguridad.|
|**ComeBackWarning**|El residente ha abandonado la cama y no ha regresado dentro de la ventana de tiempo preventiva.|
|**ComeBackExceeded**|El tiempo de espera para el retorno a la cama ha excedido el límite de seguridad establecido.|
|**SignalLost**|No se ha recibido ningún _heartbeat_ del monitor dentro del tiempo configurado en `heartbeatTimeout`.|

La capacidad del ClockSweeper para "barrer" el tiempo permite una actuación preventiva ante situaciones de riesgo donde el silencio del sensor es, en sí mismo, la información crítica (como una caída fuera del área de detección). Estas reglas de tiempo y confianza se personalizan dinámicamente para cada residente.

## 5. SceneCalibration: El Modelo de Configuración Dinámica

La **SceneCalibration** funciona como el nexo entre las políticas de cuidado de alto nivel y la ejecución técnica. El mecanismo encargado de realizar esta "compilación" de reglas es el `**PolicyCalibrationAdapter**`, que traduce las definiciones de un DSL (_Domain Specific Language_) en entidades operativas dentro del motor.

El mapeo de la política a la ejecución técnica se define de la siguiente manera:

|   |   |   |
|---|---|---|
|Campo de Política (Politica)|Entidad del Scene Engine|Propósito Funcional|
|`scene.hysteresis`|`TransitionTable`|Define rutas válidas entre estados y retardos de confirmación.|
|`scene.confidence`|`ConfidenceThresholds`|Establece el nivel de confianza mínimo requerido por cada estado.|
|`scene.dwellThresholds`|`DwellCatalog`|Define las duraciones para alertas de tipo _Warning_ y _Exceeded_.|
|`scene.heartbeatTimeout`|Lógica de `SignalHealth`|Tiempo máximo permitido antes de emitir un evento de `SignalLost`.|

Esta arquitectura permite una personalización masiva: cada binomio cama/residente posee una configuración optimizada sin necesidad de alterar el código base. Todos estos componentes convergen finalmente en el ciclo operativo de procesamiento.

## 6. El Ciclo de Procesamiento (The Processing Loop)

El flujo operativo dentro de `processWithSweepInternal` es el motor determinista que asegura la sincronización entre observaciones y tiempo. Su naturaleza cíclica garantiza que no existan lagunas informativas en el estado del residente.

El ciclo se desglosa en cuatro pasos técnicos:

1. **Avance de Tiempo:** Verificación de la marca temporal actual (`now`) frente al último momento procesado (`lastTime`).
2. **Barridos Intermedios (Intermediate Sweeps):** Para cada intervalo definido por el parámetro `sweepIntervalSeconds` (por defecto, 60 segundos) entre `lastTime` y `now`, se ejecuta un barrido temporal para capturar eventos del `ClockSweeper` que pudieran haber ocurrido durante el silencio del sensor.
3. **Interpretación de Observaciones:** El intérprete evalúa la observación actual, actualizando potencialmente el DigitalTwin y generando eventos de transición detectada.
4. **Barrido Final:** Se realiza un barrido en el **timestamp exacto de la observación**. Esto asegura que todos los temporizadores relacionados con cambios de estado se actualicen inmediatamente y con precisión temporal absoluta.

Este diseño robusto evita que el estado del residente quede en un limbo informativo ante pérdidas intermitentes de señal. Al combinar la interpretación rigurosa de datos con barridos temporales proactivos, la arquitectura asegura un estado físico preciso y una orquestación desacoplada, cumpliendo con los estándares de alta disponibilidad requeridos en entornos críticos de salud.