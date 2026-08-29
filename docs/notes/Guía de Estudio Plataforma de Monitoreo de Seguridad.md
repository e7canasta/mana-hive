# Guía de Estudio: Plataforma de Monitoreo de Seguridad

Esta guía de estudio proporciona un marco exhaustivo para comprender la arquitectura, el funcionamiento y los principios de dominio de la plataforma Hisso, un sistema diseñado para el monitoreo de seguridad de residentes en centros de cuidado.

## Cuestionario de Repaso

El siguiente cuestionario consta de diez preguntas de respuesta corta diseñadas para evaluar la comprensión de los componentes centrales del sistema.

1. **¿Cuál es la función principal de la plataforma Hisso y qué tipo de datos procesa?**
2. **Explique el concepto de "Dominio Puro" y cómo se aplica a la arquitectura hexagonal del sistema.**
3. **¿Qué funciones desempeñan el "Scene Engine" y el "Digital Twin" dentro de la tubería de datos?**
4. **¿Cómo define el "Sentinel Engine" el inicio de un episodio de seguridad?**
5. **Describa el rol del "Harbor Engine" (Vigia) y su importancia en la gestión del personal.**
6. **¿Qué mecanismo utiliza el "Recorder Engine" para asegurar que los incidentes tengan contexto visual?**
7. **¿De qué manera el motor "Politica" transforma los requisitos de cuidado en configuraciones técnicas?**
8. **¿Qué es el envoltorio "Explained" y por qué es crítico para la auditabilidad del sistema?**
9. **¿Cómo garantiza el sistema la integridad del estado cuando procesa múltiples residentes simultáneamente?**
10. **¿Qué papel juegan los "Blueprints" en el ciclo de vida del desarrollo de Hisso?**

## Clave de Respuestas

1. **Función y Datos:** Hisso es una plataforma de monitoreo de seguridad que procesa observaciones en tiempo real provenientes de sensores (como cámaras o radares de piso). Su objetivo es detectar la actividad de los residentes, gestionar episodios de seguridad y despachar notificaciones al personal cuando se superan ciertos umbrales de cuidado.
2. **Dominio Puro y Arquitectura Hexagonal:** El principio de "Dominio Puro" establece que la lógica central no realiza operaciones de E/S, no usa reflexión ni depende de frameworks externos. La arquitectura hexagonal utiliza este núcleo para separar la lógica de negocio de la infraestructura (como NATS o APIs REST), permitiendo que el sistema funcione igual en producción, pruebas BDD o procesos por lotes.
3. **Scene Engine y Digital Twin:** El Scene Engine es la primera etapa de la tubería; traduce datos brutos de sensores (_Observations_) en eventos semánticos (_SceneEvents_). Para ello, mantiene un _Digital Twin_ (gemelo digital), que es el contenedor de estado que representa el estatus físico actual del residente (ej. acostado, sentado, en el borde de la cama).
4. **Sentinel Engine y Episodios:** Este motor aplica juicio clínico para gestionar el ciclo de vida de un "Episodio". Un episodio se abre cuando un evento de escena coincide con una regla de alerta específica (basada en entrada inmediata, duración o falta de retorno) y no hay otro episodio activo para esa cama.
5. **Harbor Engine (Vigia):** Actúa como un enrutador de comunicaciones que transforma señales clínicas en comandos de notificación (_NoticeCommands_). Su importancia radica en la gestión de la logística de entrega y la mitigación de la "fatiga por alarmas" mediante la aplicación de presupuestos de notificación por turno.
6. **Contexto del Recorder Engine:** El motor utiliza un sistema de "ventanas" de grabación que incluyen _pre-roll_ (_recordBefore_) y _post-roll_ (_recordAfter_). Esto asegura que, cuando ocurre un evento significativo, el video capturado incluya los momentos previos y posteriores para una revisión clínica completa.
7. **Motor Politica:** Este motor resuelve el "lenguaje del director" (ej. "Avísame si José se sienta por 15 minutos") y lo traduce en una _PolicyCalibration_. Este objeto contiene los umbrales técnicos y configuraciones específicas que se inyectan en los otros motores para dictar su comportamiento.
8. **Envoltorio Explained:** Es un contenedor que transporta el resultado de un cálculo junto con una lista de razones (_Reasons_). Proporciona un rastro de auditoría integrado que explica paso a paso por qué el sistema tomó una decisión específica, como la asignación de una gravedad o el disparo de una alerta.
9. **Integración y Paralelismo:** El _NightWatchRuntime_ utiliza un _ConcurrentHashMap_ para gestionar múltiples instancias de residentes. Mientras que diferentes residentes se procesan en paralelo para mayor eficiencia, las observaciones de un solo residente se procesan secuencialmente para mantener la integridad de su estado.
10. **Blueprints:** Son escenarios de ejecución de extremo a extremo que sirven como especificaciones ejecutables. Se utilizan para verificar el comportamiento del sistema frente a requisitos de seguridad específicos (ej. el caso "José 301") antes de su despliegue en entornos de producción.

## Temas de Ensayo Sugeridos

_Los siguientes temas requieren un análisis profundo basado en la arquitectura y los principios detallados en el contexto de la fuente._

1. **Aislamiento y Pureza Arquitectónica:** Analice cómo el uso de tipos personalizados (Value Classes) y la exclusión de frameworks en el núcleo del dominio contribuyen a la robustez y testabilidad del sistema Hisso.
2. **Gestión de la Fatiga por Alarmas:** Discuta las estrategias implementadas en el Harbor Engine para equilibrar la seguridad del residente con la eficiencia operativa del personal de cuidado.
3. **Ciclo de Vida de un Incidente:** Describa el flujo de datos completo desde que un sensor detecta movimiento hasta que se cierra un episodio, detallando la transformación de la información en cada una de las cuatro etapas de la tubería.
4. **Flexibilidad mediante la Calibración de Políticas:** Evalúe cómo el desacoplamiento de las reglas de negocio (Politica) de la ejecución lógica (Engines) permite que Hisso se adapte a diferentes niveles de riesgo y perfiles de residentes.
5. **Auditabilidad en Sistemas de Misión Crítica:** Reflexione sobre la importancia de estructuras como `Explained<T>` y `EventEnvelope` en el contexto de un sistema de seguridad donde cada decisión debe ser justificable y rastreable.

## Glosario de Términos Clave

|   |   |
|---|---|
|Término|Definición|
|**Auto-Recovery**|Señal emitida cuando un residente regresa a un estado seguro sin intervención del personal, permitiendo que la alerta se atenúe visualmente.|
|**BedId / ResidentId**|Identificadores fuertemente tipados (Value Classes) que evitan errores accidentales de intercambio de parámetros en el código.|
|**Calibration**|Configuración técnica específica para un motor, derivada de una política de alto nivel.|
|**ClockSweeper**|Componente del Scene Engine que genera eventos basados en el paso del tiempo (como el exceso de permanencia) en ausencia de nuevas observaciones.|
|**Digital Twin**|Representación digital en tiempo real del estado físico y la salud de la señal de un residente.|
|**Dwell**|La duración que un residente permanece en un estado específico (ej. sentado o fuera de la cama).|
|**Episode**|Periodo de preocupación de seguridad activa que comienza con una señal de apertura y termina con una de cierre.|
|**EventEnvelope**|Formato de cable estándar para todos los mensajes NATS que incluye metadatos como ID, fuente, tipo y marca de tiempo.|
|**Hysteresis**|Búfer de tiempo configurado para evitar alertas intermitentes o "parpadeos" durante las transiciones de estado.|
|**NATS / JetStream**|Backbone de mensajería asíncrona y su capa de persistencia utilizados para la comunicación entre servicios.|
|**Observation**|Datos brutos de telemetría (ej. presión de cama o sensor virtual) que inician la tubería de procesamiento.|
|**NoticeCommand**|Instrucción de entrega generada por el Harbor Engine para canales como tabletas, consolas o notificaciones push.|
|**SceneEvent**|Interpretación semántica de un movimiento (ej. `TransitionDetected` o `DwellExceeded`).|
|**SentinelSignal**|Salida del Sentinel Engine que indica cambios en el ciclo de vida de un episodio (ej. apertura, escalada o cierre).|
|**Umbrella Event**|Eventos contextuales que ocurren mientras un episodio ya está abierto, proporcionando información adicional sin disparar nuevas alertas.|