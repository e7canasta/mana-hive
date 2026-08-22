# Guía de Estudio: Sistema HubP y Arquitectura de Motores de Vigilancia

Esta guía de estudio proporciona una revisión exhaustiva de los conceptos, la arquitectura y los mecanismos operativos del sistema HubP, basada en la documentación técnica analizada. Está diseñada para facilitar la comprensión de la estructura de microservicios, el flujo de datos y la lógica de los motores de decisión clínica.

## Cuestionario de Repaso

### Preguntas de Respuesta Corta

1. **¿Cuál es el propósito de la arquitectura de cuatro binarios en el sistema HubP?** La arquitectura divide las responsabilidades en cuatro procesos independientes: `mana-hub` (persistencia y API), `mana-engine` (estado del residente/Digital Twin), `mana-sentinel` (evaluación de reglas e incidentes) y `mana-vigilancia` (entrega de notificaciones). Esta separación permite que cada componente sea escalado, actualizado o reiniciado de forma independiente sin interrumpir el flujo completo del sistema.
2. **¿En qué se diferencia el "Tiempo de Evento" del "Tiempo de Reloj" dentro del lazo operativo?** El Tiempo de Evento es reactivo y ocurre cuando llega una observación del detector (como una salida de cama), activando una evaluación inmediata. Por el contrario, el Tiempo de Reloj es disparado por el paso del tiempo absoluto para gestionar permanencias (dwells) o escalamientos que no dependen de un nuevo mensaje del sensor.
3. **¿Cuál es la función de NATS JetStream en la comunicación inter-binaria?** NATS JetStream actúa como el eje central de comunicación (backbone), proporcionando una entrega de mensajes garantizada de tipo "al menos una vez" (at-least-once). Esto permite que los trabajadores (workers) sean apátridas (stateless), ya que pueden recuperar su estado o re-procesar eventos desde el último punto confirmado si se reinician.
4. **¿Qué establece la regla de "No Cross-Context" (Sin Dependencias entre Contextos)?** Es una regla de diseño estricta donde un crate de contexto limitado (`ctx-*`) no puede declarar dependencia de otro crate `ctx-*` en su configuración de Cargo. Toda coordinación que requiera datos de múltiples contextos debe realizarse exclusivamente en la capa de aplicación (`mana-app`), garantizando la modularidad y evitando el acoplamiento excesivo.
5. **¿Qué es el "Digital Twin" (Gemelo Digital) en el contexto de** `**mana-engine**`**?** Es una proyección viva en memoria de cada cama o residente que mantiene el estado actual (por ejemplo, acostado, de pie) y los temporizadores activos. No se re-hidrata de la base de datos en cada evaluación, sino que se reconstruye a partir de eventos y es la fuente de verdad para las transiciones de la Máquina de Estados Finitos (FSM).
6. **¿Cómo maneja el motor de Autopilot la asimetría entre subir y bajar niveles de vigilancia?** La política de seguridad permite subir niveles automáticamente si existe evidencia suficiente (señales mínimas), pero prohíbe bajar niveles sin confirmación humana. Esto evita que el sistema reduzca la vigilancia de un residente simplemente porque ha tenido un periodo de estabilidad, lo cual podría ser resultado de la vigilancia actual efectiva.
7. **¿Qué papel cumple el archivo** `**rutas.toml**` **en el binario** `**mana-hub**`**?** Funciona como el contrato único de la superficie de la API, definiendo todos los puntos de acceso (endpoints) válidos, su método HTTP y el contexto dueño. Durante el arranque, el sistema valida que cada ruta marcada para Rust tenga un controlador (handler) registrado, de lo contrario, el binario se niega a iniciar.
8. **¿Cuál es el objetivo de que** `**mana-motores**` **sea un crate "puro" sin entrada/salida (I/O)?** Al prohibir dependencias de Diesel, redes o almacenamiento, se garantiza que los motores de decisión sean funciones puras que solo dependen de la entrada proporcionada. Esto facilita las pruebas unitarias deterministas (que corren en milisegundos) y asegura que la lógica clínica no se vea afectada por fallos en la infraestructura.
9. **¿Cómo funciona el "Patrón de Retiro" (Retirement Pattern) en lugar de la eliminación física de datos?** En lugar de borrar filas de la base de datos SQLite, las entidades se marcan con campos `retired_at` y `retired_by`. Esto preserva la integridad referencial y permite mantener un historial de auditoría completo, mientras que los modelos de lectura filtran automáticamente los registros retirados.
10. **¿Qué información compone el "Lazo Operativo" de cinco pasos?** El lazo cruza varios contextos para transformar una observación en una acción: Evaluar (¿qué nivel de alerta?), Crear (persistencia de la alerta), Dirigir (¿a quién avisar?), Entregar (registro del canal de aviso) y Escalar (subir prioridad si no hay acuse humano).

## Clave de Respuestas

1. Separar responsabilidades (Hub, Engine, Sentinel, Vigilancia) para escalabilidad y tolerancia a fallos.
2. Evento es reactivo a estímulos externos; Reloj es proactivo basado en intervalos de tiempo (permanencias).
3. Backbone de mensajería con persistencia que permite trabajadores apátridas y recuperación de mensajes.
4. Prohibición de dependencias directas entre crates `ctx-*` para mantener la modularidad.
5. Proyección en memoria del estado del residente que gestiona transiciones de la FSM y temporizadores.
6. Sube niveles con evidencia, pero bajar niveles requiere intervención humana (seguridad clínica).
7. Registro centralizado de endpoints que garantiza que el contrato de la API coincida con la implementación.
8. Garantizar determinismo clínico y facilidad de pruebas al eliminar efectos secundarios de I/O.
9. Uso de marcas de tiempo y actor para "borrado lógico", protegiendo la trazabilidad del sistema.
10. Evaluar, Crear, Dirigir, Entregar y Escalar.

## Temas para Ensayo

1. **Evolución Arquitectónica:** Analice las ventajas de pasar de un motor de alarmas monolítico (como el antiguo sistema en Node.js) a una topología de cuatro binarios desacoplados mediante NATS JetStream.
2. **Seguridad y Autopilot:** Discuta las implicaciones clínicas de la política de asimetría en el motor de Autopilot y por qué la "ausencia de evidencia" no debe interpretarse automáticamente como una mejora en el estado del residente.
3. **Integridad y Auditoría:** Explique cómo el patrón de mutación atómica junto con el sistema de auditoría en `mana-app` garantiza que ninguna acción de negocio ocurra sin dejar un rastro verificable y cómo esto se apoya en las transacciones de SQLite.
4. **El Gemelo Digital y la FSM:** Evalúe la importancia de mantener una máquina de estados finitos (FSM) determinista para modelar el comportamiento del residente y cómo la gestión de temporizadores (dwells) influye en la reducción de falsas alarmas.
5. **Modularidad mediante Layers:** Describa la estructura de capas (Kernel, Contextos, Aplicación, Binarios) del sistema HubP y cómo el uso de tipos de identidad fuertemente tipados (`Id<K>`) previene errores de programación comunes.

## Glosario de Términos Clave

|   |   |
|---|---|
|Término|Definición|
|**Bounded Context**|Frontera lógica que delimita un dominio de negocio, con su propio lenguaje, reglas e integridad de datos (ej. `ctx-poblacion`).|
|**Digital Twin**|Modelo virtual en tiempo real que representa el estado físico y temporal de un residente en una cama.|
|**Dwell**|Tiempo de permanencia de un sujeto en un estado específico (ej. estar fuera de la cama por más de 10 minutos).|
|**FSM (Finite State Machine)**|Modelo matemático de computación que cambia de un estado a otro en respuesta a entradas (Perception Events).|
|**Idempotencia**|Propiedad de una operación que permite que sea ejecutada múltiples veces sin cambiar el resultado más allá de la aplicación inicial.|
|**Instante**|Primitiva temporal del sistema que garantiza normalización a UTC y formato ISO-8601 con precisión de milisegundos.|
|**MonitorKey**|Identificador único de un hardware o sensor vinculado a una cama para la ingesta de datos.|
|**NATS JetStream**|Sistema de mensajería distribuida que añade persistencia y flujos de datos (streams) a NATS básico.|
|**System of Record (SoR)**|La fuente de verdad autoritativa para un conjunto de datos dado (en este sistema, `mana-hub`).|
|**WAL (Write-Ahead Logging)**|Modo de SQLite que permite que varios lectores y un escritor operen simultáneamente de forma eficiente.|