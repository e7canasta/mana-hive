# Guía Conceptual: El Digital Twin en el Scene Engine

## 1. Introducción al Digital Twin: El Reflejo Digital de la Realidad

Dentro de la arquitectura del Scene Engine, el Digital Twin es el componente encargado de mantener una representación virtual sincronizada de la situación física de un residente. Actúa como el modelo de estado que permite al sistema procesar la telemetría de sensores y transformarla en información accionable para el cuidado de la salud.

"The DigitalTwin is the state container representing the resident's current status and signal health."

Esta definición técnica subraya su rol no solo como un registro de posición, sino como un monitor de la integridad de la comunicación entre el hardware y el software. Antes de profundizar en su funcionamiento, es imperativo entender su posición como el eje central de validación en el sistema.

## 2. La Única Fuente de Verdad (Single Source of Truth)

El Digital Twin es la "fuente única de verdad" para cada par cama/residente dentro del ecosistema. Arquitectónicamente, el Scene Engine implementa un **Facade pattern**, lo que permite encapsular la complejidad del Digital Twin y aislarlo de los consumidores de la API, garantizando que el estado del residente sea consistente y esté protegido de interferencias externas.

Sus responsabilidades principales como fuente de verdad son:

- **Centralización de la identidad cama/residente:** Vincula de forma unívoca los datos de los sensores con un perfil específico, asegurando que las observaciones se atribuyan correctamente.
- **Punto de referencia para el estado actual:** Actúa como el repositorio definitivo del `PersonState`, eliminando ambigüedades sobre la situación física del residente en un momento dado.
- **Base para la toma de decisiones del sistema:** Provee la estructura de datos necesaria para que los motores de reglas y sistemas de notificación operen sobre información validada.

Para que esta "verdad" sea robusta, el modelo debe rastrear dimensiones específicas de datos que definen la realidad del entorno monitorizado.

## 3. Anatomía del Digital Twin: ¿Qué datos rastreamos?

El Digital Twin integra cuatro pilares de datos fundamentales. Estos no son meros campos estáticos; son las variables que permiten al Scene Engine realizar cálculos complejos de seguridad.

|   |   |   |
|---|---|---|
|Categoría de Dato|Descripción Técnica|Valor para el Monitoreo|
|**State (Estado)**|Registra el `PersonState` actual (Lying, BedEdge, Standing, Unknown).|Define la postura física inmediata para determinar el nivel de riesgo.|
|**Temporal Context**|Utiliza el atributo `stateSince` para marcar el inicio del estado actual.|Permite el cálculo de métricas de permanencia (_dwell_) fundamentales para protocolos de prevención.|
|**Signal Health**|Monitorea el flujo de "heartbeats" y el cumplimiento del `heartbeatTimeout`.|Detecta fallos de conectividad o hardware, evitando el "monitoreo a ciegas".|
|**Calibration**|`SceneCalibration` "compilada" (incluye `TransitionTable`, `DwellCatalog` y `ConfidenceThresholds`).|Personaliza el comportamiento del gemelo según la política de seguridad específica del residente.|

Estos datos estáticos se transforman en una representación dinámica de la realidad mediante la lógica de interpretación de señales.

## 4. El Proceso de Actualización: De Sensores a la Realidad Física

El **SceneInterpreter** es el encargado de actualizar el Digital Twin, transformando observaciones crudas y potencialmente ruidosas en estados válidos. Este proceso sigue un flujo lógico estricto para garantizar la fidelidad del Gemelo Digital:

1. **Check de Confianza:** Se evalúa la observación contra el `minConfidence` definido para el estado detectado. Si el nivel de certeza es insuficiente, la observación se descarta con la causa técnica `DiscardCause.CONFIDENCE_TOO_LOW`.
2. **Validación de Transición:** El intérprete consulta la `TransitionTable` para verificar si el movimiento desde el estado actual al observado es una "ruta legal" según el modelo físico configurado.
3. **Histéresis:** Antes de confirmar el cambio en el Digital Twin, el sistema exige persistencia temporal (típicamente 1500ms). Esto verifica la legalidad y estabilidad de la transición, evitando fluctuaciones erráticas por ruido momentáneo.

Sin embargo, el Digital Twin no solo depende de estímulos externos; el paso del tiempo es un factor que altera la realidad incluso en ausencia de nuevas observaciones.

## 5. El Factor Tiempo: ClockSweeper y Eventos Temporales

El Digital Twin evoluciona de forma autónoma mediante el **ClockSweeper**. Este componente analiza el tiempo transcurrido entre observaciones y genera eventos críticos si se alcanzan umbrales de seguridad predefinidos.

Los eventos generados por este proceso de "barrido" temporal son:

- [ ] **DwellWarning:** **Trigger:** El residente permanece en un estado (ej. Standing) superando el umbral de advertencia definido en el `DwellCatalog`.
- [ ] **DwellExceeded:** **Trigger:** El tiempo de permanencia en un estado supera la duración máxima permitida en la configuración de seguridad.
- [ ] **ComeBackWarning:** **Trigger:** El residente ha abandonado la cama y no se ha detectado su regreso dentro del margen de advertencia inicial.
- [ ] **ComeBackExceeded:** **Trigger:** El tiempo de ausencia del residente supera el límite de seguridad establecido para su retorno.
- [ ] **SignalLost:** **Trigger:** El intervalo de tiempo desde el último mensaje del monitor supera el valor de `heartbeatTimeout`.

Al integrar el estado físico validado, el contexto temporal y la salud de la señal, el Digital Twin proporciona una representación técnica precisa y confiable de la seguridad del residente en todo momento.