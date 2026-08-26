# Guía de Estudio: Sistema de Monitoreo de Cuidados mana-hive

Esta guía de estudio proporciona una revisión exhaustiva de la plataforma **mana-hive**, un sistema distribuido y orientado a eventos para el monitoreo de cuidados nocturnos. El contenido se basa en el análisis técnico de su arquitectura, subsistemas y principios de diseño.

## Cuestionario de Repaso (Preguntas de Respuesta Corta)

Responda a las siguientes preguntas basándose en la documentación técnica del sistema. Cada respuesta debe tener una extensión de 2 a 3 oraciones.

1. **¿Cuál es la misión principal de la plataforma mana-hive?**
2. **¿Qué funciones diferenciadas cumplen NATS JetStream y el Hub (Postgres) dentro del sistema?**
3. **¿En qué consiste el principio de "Pureza en el Núcleo" (Purity at the Core)?**
4. **¿Qué es un** `**DecisionRecord**` **y cuál es su importancia para la auditoría clínica?**
5. **¿Cómo gestiona el Scene Engine el ruido de los datos de los sensores para evitar falsas alarmas?**
6. **¿Cuál es el rol del Sentinel Engine en la evaluación de incidentes?**
7. **¿Qué es el "Presupuesto de Fatiga" (Fatigue Budget) y quién lo administra?**
8. **¿Cuál es el propósito del componente** `**ClockSweeper**` **dentro del Scene Engine?**
9. **Explique la jerarquía de los módulos de un motor (Engine) en el sistema de construcción Gradle.**
10. **¿Cómo permite la taxonomía de sujetos en NATS realizar implementaciones de tipo "blue-green"?**

## Clave de Respuestas

1. **Misión principal:** La misión es garantizar que la persona adecuada llegue a la habitación correcta a tiempo, minimizando las falsas alarmas. Además, busca mantener un rastro de auditoría completo y reproducible por máquina de cada decisión tomada por el sistema.
2. **NATS vs. Hub:** NATS JetStream actúa como el sistema nervioso central y buffer de alto rendimiento para la coordinación de eventos en tiempo real con retención basada en límites. Por otro lado, el Hub sirve como el Sistema de Registro (SoR) inmutable, almacenando permanentemente todos los eventos en una base de datos Postgres para auditoría y reconstrucción de estados.
3. **Pureza en el Núcleo:** Este principio dicta que la lógica central de los motores debe estar aislada de la infraestructura (como bases de datos o red), utilizando un enfoque de "dominio puro". Esto se impone mediante plugins de Gradle que restringen las dependencias de los módulos de dominio a funciones puras y lógica determinista.
4. **DecisionRecord:** Es un rastro duradero de la invocación de un motor que captura las "huellas dactilares" de la versión del motor, las reglas aplicadas y el estado de entrada. Permite que cualquier decisión de alarma sea reproducida exactamente por una máquina para responder por qué una alarma sonó o no en un momento dado.
5. **Gestión de ruido en Scene Engine:** El motor utiliza histéresis, umbrales de confianza y detección de permanencia temporal (dwell detection) para filtrar señales erráticas. Esto asegura que solo las transiciones de estado estables y semánticamente significativas sean enviadas a los componentes de toma de decisiones.
6. **Rol del Sentinel Engine:** El Sentinel actúa como un juez clínico que evalúa los hechos de la escena contra las políticas de seguridad específicas de cada residente. Transforma los cambios de estado físico en eventos clínicos, gestionando el ciclo de vida de los episodios de riesgo.
7. **Fatigue Budget:** Es un mecanismo administrado por el Sentinel Engine para prevenir el agotamiento del personal debido a notificaciones excesivas. Rastrea el número de interrupciones permitidas por turno y puede suprimir señales si se excede el límite configurado.
8. **ClockSweeper:** Es un proceso periódico que activa hechos basados en el paso del tiempo en lugar de estímulos externos, como cuando un residente permanece demasiado tiempo fuera de la cama. Si se alcanza un tiempo de espera definido en la calibración, el `ClockSweeper` emite un evento de transición al Digital Twin.
9. **Jerarquía de módulos:** Los motores se dividen en tres sub-módulos: `*-domain` (lógica pura y transiciones de estado), `*-service` (aplicación Spring Boot y conectividad NATS) y `*-batch` (herramienta CLI para replaying de eventos y verificación offline).
10. **Implementaciones Blue-Green:** El sistema utiliza una taxonomía de sujetos estrictamente versionada (ej. `v1`, `v2`). Los cambios incompatibles en los esquemas generan nuevas versiones de sujetos, permitiendo que los motores antiguos y nuevos coexistan en el bus durante la migración sin interrupciones.

## Temas de Ensayo Sugeridos

_Los siguientes temas requieren una síntesis profunda de los conceptos del sistema. No se proporcionan respuestas para estos temas._

1. **La Arquitectura Pipe-and-Filter en Sistemas de Cuidado Crítico:** Analice cómo el flujo de datos desde la percepción sensorial hasta la alerta humana en mana-hive garantiza la integridad de la información y la especialización de los componentes.
2. **Determinismo y Reproducibilidad como Pilares de la Responsabilidad Clínica:** Discuta cómo el uso de motores funcionales puros y el almacenamiento de registros de decisión (`DecisionRecord`) transforman la auditoría técnica en una herramienta de revisión clínica confiable.
3. **El Concepto de Digital Twin en el Monitoreo de Residentes:** Evalúe la eficacia de utilizar una abstracción de "Gemelo Digital" dentro del Scene Engine para representar estados físicos complejos y temporales a partir de datos de sensores ruidosos.
4. **Gestión de la Complejidad mediante Lenguajes Publicados y Contratos:** Explique cómo el módulo de contratos define un "Lenguaje Publicado" que permite la interoperabilidad entre contextos delimitados (Bounded Contexts) y facilita la evolución del sistema.
5. **Estrategias de Configuración y Calibración Distribuida:** Compare el uso de archivos TOML locales para desarrollo frente a la carga dinámica de definiciones de escenas (DAGs) desde el Hub en entornos de producción.

## Glosario de Términos Clave

|   |   |
|---|---|
|Término|Definición|
|**AlertRule**|Regla que define un activador (ej. intento de salida), severidad y condición de cierre para una alarma.|
|**CensusSnapshot**|"Fotografía" de solo lectura de las asignaciones de residentes a camas, utilizada para vincular observaciones a personas.|
|**Decider**|Interfaz central de event-sourcing que separa la lógica de "qué debería pasar" de "cómo cambia el estado".|
|**Digital Twin**|Agregado orientado a eventos que representa el modelo inmutable y el estado actual de una cama monitoreada.|
|**Dwell Time**|Tiempo mínimo que un residente debe permanecer en un estado para que este se considere confirmado.|
|**EpisodeLedger**|Contenedor de todos los episodios activos y el presupuesto de fatiga para un residente específico.|
|**Explained****<T>**|Contenedor que envuelve un resultado junto con una traza del proceso de razonamiento y las reglas aplicadas.|
|**Hysteresis**|Amortiguación temporal o de conteo utilizada para evitar cambios rápidos y erráticos entre estados (flickering).|
|**NATS JetStream**|Infraestructura de mensajería asíncrona que actúa como buffer persistente y tolerante a fallos para el transporte de eventos.|
|**Pure Domain**|Enfoque de diseño donde la lógica de negocio está aislada de efectos secundarios, I/O o dependencias externas.|
|**SceneFact**|Hecho de alto nivel producido por el Scene Engine que representa estados como "Ocupante Presente" o "Salida de Cama".|
|**SentinelSignal**|Juicio clínico emitido por el Sentinel que categoriza un evento como Incidente, Ocurrencia o Supresión.|
|**System of Record (SoR)**|El Hub de Postgres, que actúa como la verdad administrativa definitiva y crónica inmutable de todos los eventos.|
|**Vigia / Harbor**|Motor responsable de gestionar el ciclo de vida de la entrega de alertas, incluyendo su enrutamiento y escalación.|