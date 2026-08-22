# Informe de Gobernanza: Integridad de Contextos Acotados y Motores Puros

## 1. El Paradigma del Monolito Modular y la Segregación de Responsabilidades

En el diseño de sistemas de misión crítica para entornos clínicos, la arquitectura de **Monolito Modular** no es una preferencia estética, sino una decisión estratégica de gobernanza. Esta estructura permite particionar la lógica de negocio en 11 contextos acotados (_Bounded Contexts_) que operan bajo fronteras de lenguaje y propiedad estrictas. El objetivo fundamental es prevenir la degradación del código hacia un "Big Ball of Mud", permitiendo que cada subdominio evolucione de forma independiente sin incurrir en la latencia operativa o la sobrecarga de infraestructura de los microservicios distribuidos. En nuestro ecosistema, estas fronteras actúan como **invariantes de seguridad** que garantizan que un error en un módulo secundario no comprometa la integridad del núcleo clínico.

La transición desde el sistema heredado en Node.js hacia una topología de cuatro binarios en Rust (`mana-hub`, `mana-engine`, `mana-sentinel` y `mana-vigilancia`) ha permitido centralizar el **System of Record (SoR)** en `mana-hub`. Esta centralización es vital para la coherencia del estado del paciente y la trazabilidad de la evidencia. Los beneficios estructurales de este modelo incluyen:

- **Consistencia Transaccional:** El uso de SQLite bajo el control de `mana-hub` asegura que las mutaciones complejas —como el ingreso de un residente o la asignación de una cama— ocurran en una única transacción atómica, eliminando estados inconsistentes.
- **Durabilidad de Datos y Auditoría:** Al centralizar la persistencia, se garantiza que cada acción deje una huella inmutable, fundamental para la responsabilidad legal y clínica.
- **Seguridad de Tipos en el Borde:** La migración a Rust permite que las restricciones de negocio se verifiquen en tiempo de compilación, minimizando fallos en el _runtime_ del borde (edge).

Esta estructura macro establece el soporte necesario para blindar los subdominios mediante contratos técnicos infranqueables.

## 2. Evaluación de las Fronteras Arquitectónicas (The 11 Bounded Contexts)

La segregación en contextos acotados es la salvaguarda principal contra el acoplamiento accidental. Al definir límites claros, facilitamos el _ownership_ del código y aseguramos que el sistema sea comprensible y mantenible. En este modelo, el acoplamiento no es solo un problema de mantenimiento; es un riesgo de seguridad clínica.

### Clasificación de Contextos Acotados

|                |                 |                                                                          |                                                                       |
| -------------- | --------------- | ------------------------------------------------------------------------ | --------------------------------------------------------------------- |
| Contexto       | Tipo            | Responsabilidad Central                                                  | Impacto de la Frontera                                                |
| **Identidad**  | Genérico        | Gestión de usuarios, roles (RBAC) y sesiones seguras (Argon2id/SHA-256). | Aísla la seguridad del acceso de la lógica de negocio.                |
| **Auditoría**  | Genérico        | Registro inmutable y "append-only" de todas las mutaciones del sistema.  | Garantiza trazabilidad total sin contaminar otros dominios.           |
| **Residencia** | Soporte         | Jerarquía física (Instalación, Ala, Habitación, Cama) y planogramas.     | Estabiliza el modelo del entorno físico y privacidad.                 |
| **Población**  | Soporte         | Ciclo de vida del residente (Ingreso/Egreso) y asignación de camas.      | Protege la integridad de la ocupación 1-a-1.                          |
| **Cobertura**  | Soporte         | Gestión de turnos y asignación de personal a sectores físicos.           | Asegura que siempre haya un responsable identificado por turno.       |
| **Cuidado**    | Operativo       | Ejecución de rondas, tareas y registro de notas de continuidad.          | Separa la operación diaria de los registros históricos.               |
| **Historia**   | Soporte         | Detección de incidentes clínicos y revisiones humanas de evidencia.      | Centraliza la evidencia clínica para análisis posterior.              |
| **Política**   | Núcleo          | Catálogos de alarmas, perfiles y resolución de política efectiva.        | Permite cambiar reglas clínicas sin modificar el código de ejecución. |
| **Vigilancia** | Núcleo          | Máquina de estados de alertas y ciclo de vida de la notificación.        | Controla el flujo crítico de alarmas hacia el personal.               |
| **Evidencia**  | Infraestructura | Metadatos de clips de video y líneas de tiempo de eventos.               | Gestiona volúmenes de datos masivos sin afectar el SoR.               |
| **Streams**    | Infraestructura | Registro de cámaras y configuración de Regiones de Interés (ROI).        | Aísla la configuración de hardware del uso clínico.                   |

Para evitar la "erosión estructural", implementamos la regla de **"No Cross-Context Dependency"**. Un crate `ctx-*` tiene prohibido depender de otro `ctx-*` en su configuración de Cargo. El cumplimiento de esta norma es forzado por la herramienta `xtask verificar-contextos`, la cual actúa como un **build-breaker** en el pipeline de CI/CD. Cualquier orquestación que requiera datos de múltiples contextos debe residir obligatoriamente en `mana-app`, el único coordinador autorizado.

## 3. Independencia de Motores Puros: El Crate `mana-motores`

La estrategia de gobernanza dictamina que la lógica de decisión debe estar separada de los efectos secundarios de Entrada/Salida (IO). El crate `mana-motores` encarna la "pureza por construcción": es un componente agnóstico a la infraestructura que garantiza determinismo absoluto.

A diferencia de `mana-app`, que gestiona la hidratación de datos, `mana-motores` tiene prohibido el uso de Diesel o consultas de red. Esta restricción técnica permite que el motor opere como un **PLC (Super Loop)**, procesando tanto estímulos reactivos (Push) como pulsos de tiempo (Pull/Tick). Las responsabilidades se destilan en APIs específicas:

1. **Motor de Política Efectiva (**`**AlarmCatalog::resolve_rules**`**):** Resuelve la superposición de niveles, plantillas y ajustes manuales para determinar las reglas activas.
2. **Motor de Recomendación (**`**recomendar**`**):** Analiza factores de riesgo y señales para sugerir el nivel de vigilancia óptimo.
3. **Motor de Alarmas (**`**evaluar**`**):** Convierte observaciones del sensor en alertas explicables basándose en reglas de transición y permanencia.
4. **Motor de Autopilot (**`**decidir**`**):** Ejecuta la lógica de cambios de perfil automáticos bajo restricciones de seguridad.
5. **Motor de Reloj:** Evalúa timers de permanencia de forma duradera, incluso en ausencia de eventos nuevos.

Esta independencia se materializa a través del Gemelo Digital, permitiendo que el motor tome decisiones sin consultar constantemente la persistencia.

## 4. El Gemelo Digital (Digital Twin) y la Máquina de Estados Finitos (FSM)

El **Gemelo Digital** es una proyección viva en memoria del estado de cada cama y residente. Para garantizar la resiliencia en un sistema de borde, el Twin **nunca se re-hidrata del Hub en cada evaluación**. En su lugar, utiliza un mecanismo de **snapshot + replay de eventos** para reconstruir su estado tras un reinicio, garantizando que la lógica de la FSM (Máquina de Estados Finitos) sea continua.

Dentro del Twin, la FSM gestiona los estados del residente (`PersonState`) agrupados por nivel de riesgo (In-bed, Out-of-bed, etc.). Los cambios de estado disparan eventos de escena y gestionan automáticamente los **dwell timers** (temporizadores de permanencia).

### Umbrales de Permanencia (Dwell Thresholds) Clínicos

|   |   |   |
|---|---|---|
|Estado / Grupo|Umbral (Threshold)|Propósito Clínico|
|**Lying / Sitting / BedEdge**|5 Horas (300m)|Monitoreo de inmovilidad prolongada o sueño.|
|**Standing**|5 Minutos|Detección de riesgo de caída por bipedestación estática.|
|**InBathroom**|30 Minutos|Alerta de seguridad en zona de alta criticidad.|
|**InRoom / InHallway**|10 Minutos|Seguimiento de deambulación o ausencia de la cama.|
|**Unknown**|N/A|Estado centinela; no dispara timers por falta de certeza.|

Para asegurar que el comportamiento en producción sea idéntico al de desarrollo, utilizamos **"Escenas"** (blueprints JSON). Estos archivos actúan como contratos ejecutables que validan que el razonamiento del motor sea matemáticamente consistente ante secuencias de eventos reales.

## 5. Gobernanza de Decisiones Automáticas: Trazabilidad y Seguridad en Autopilot

En entornos clínicos, Autopilot no es solo un automatismo, es un actor con responsabilidad. Su diseño se basa en una distinción crítica: el **Razonamiento** es un valor derivado y recalculable, mientras que la **Decisión** es un **Hecho Inmutable**.

La gobernanza de Autopilot se rige por una **Política de Seguridad Asimétrica** mandataria:

- **Subidas de Nivel (Auto-up):** Se permiten automáticamente si el puntaje y las señales (`minimum_signals_for_raise`) respaldan el aumento de vigilancia. Priorizamos la seguridad del residente.
- **Bajadas de Nivel (Human-confirm-down):** El sistema solo puede **proponer** una bajada. Se requiere una confirmación humana explícita, bajo la premisa de que un sistema que baja la vigilancia solo puede estar apagándose cuando más se necesita.

Autopilot es tratado como un **"Actor"** con identidad propia en la tabla de auditoría. Cada registro conserva el razonamiento completo (señales evaluadas, puntajes y motivos estables como `increase_allowed`) junto a la decisión. Esto permite que los supervisores clínicos puedan "discutir" o auditar la composición de una decisión automática meses después de haber ocurrido.

## 6. El Lazo Operativo: Sincronización entre Tiempo de Evento y Tiempo de Reloj

El lazo operativo es el mecanismo que cierra la brecha entre los diferentes contextos. Un sistema profesional de alarmas debe ser capaz de detectar la **ausencia de eventos**, lo cual requiere una distinción técnica entre dos tiempos:

- **Tiempo de Evento (Reactivo):** Lógica disparada por la ingesta de una observación (ej. `evt_perception` desde el borde).
- **Tiempo de Reloj (Barrido):** Lógica que detecta permanencias prolongadas sin necesidad de nuevos estímulos del sensor.

Para gestionar el Tiempo de Reloj, hemos implementado un **Scheduler duradero sobre SQLite** (Opción A). Se descartaron soluciones como `setInterval` (volátil) o `Temporal` (costo de recursos) debido al **presupuesto de recursos limitado** del dispositivo de borde. Este scheduler garantiza que los timers sobrevivan a reinicios del sistema, ejecutando tareas pendientes según su `fire_at`.

### Flujo de Datos en la Topología de 4 Binarios:

1. **Percepción:** El borde envía un evento a `mana-hub`, que publica en NATS `evt_perception`.
2. **Escena:** `mana-engine` (Digital Twin) procesa el evento o el Tick del reloj y publica `evt_scene`.
3. **Evaluación:** `mana-sentinel` (Rule Engine) evalúa la escena contra la política y, si corresponde, publica `evt_notif`.
4. **Entrega:** `mana-vigilancia` consume `evt_notif` y gestiona la entrega final de la alerta al personal.

Esta arquitectura garantiza una resiliencia total: si el Hub cae, NATS retiene los mensajes; si el Engine se reinicia, el replay de eventos reconstruye el Twin. La integridad de los contextos y la pureza de los motores aseguran que el sistema sea, por construcción, un entorno seguro para la toma de decisiones clínicas.