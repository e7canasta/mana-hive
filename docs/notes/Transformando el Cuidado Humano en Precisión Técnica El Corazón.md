# Transformando el Cuidado Humano en Precisión Técnica El Corazón

## 1. El Viaje del Cuidado: De la Necesidad a la Respuesta Automática

Hisso nace como una plataforma de monitoreo de seguridad diseñada para centros de cuidado, actuando como un puente inteligente y vigilante. Su propósito fundamental es transformar las observaciones silenciosas de los sensores en una red de protección activa que resguarda la integridad de los residentes en tiempo real. Al procesar datos crudos, el sistema no solo observa, sino que comprende el contexto clínico para actuar cuando el residente más lo necesita.

Los tres objetivos primordiales que guían cada proceso dentro de Hisso son:

- **Detectar actividad:** Interpretar datos de sensores para identificar movimientos humanos significativos.
- **Gestionar episodios de seguridad:** Evaluar si una actividad representa un riesgo clínico mediante reglas personalizadas.
- **Notificar al personal de salud:** Despachar alertas precisas cuando se superan los umbrales de cuidado.

Para que esta visión se convierta en una realidad infalible, el sistema requiere una arquitectura que garantice que la lógica de cuidado nunca se vea comprometida por las limitaciones de la tecnología subyacente.

## 2. Los Pilares de la Confianza: Arquitectura y Puntos de Verdad

La confiabilidad de Hisso reside en la separación estricta entre la lógica de decisión y la infraestructura. Basado en los principios de **Pure Domain** y **Hexagonal Architecture**, el sistema asegura que el juicio clínico sea totalmente agnóstico. Esta pureza permite que la misma lógica de negocio se ejecute con idéntica precisión en un microservicio basado en NATS, en un procesador de lotes (CLI batch) o en un entorno de pruebas BDD (Behavior Driven Development).

|   |   |   |
|---|---|---|
|Capa|Componentes Clave|Propósito|
|**Núcleo de Dominio**|Interfaces `Engine`, `Decider`, Lógica Pura|Contiene reglas de negocio y algoritmos de seguridad; libre de I/O o frameworks externos.|
|**Infraestructura**|NATS JetStream, REST APIs, Spring Boot|Adaptadores que permiten la persistencia, la mensajería asíncrona y el soporte técnico del sistema.|

Para que esta arquitectura funcione con la versatilidad prometida, es indispensable establecer un lenguaje común que traduzca el cuidado en código inquebrantable.

## 3. El Kernel del Dominio: El Idioma de la Seguridad

Hisso elimina la ambigüedad técnica mediante el uso de **Kotlin value classes** para definir "Identificadores Fuertemente Tipados". Esta técnica previene la "Obsesión por Primitivos" al asegurar, a nivel de compilador, que un `BedId` nunca pueda ser intercambiado accidentalmente por un `ResidentId`.

Los identificadores clave que sostienen esta estructura son:

1. **BedId:** Identificador único de la ubicación física (cama).
2. **ResidentId:** Identificador del residente asignado.
3. **EpisodeId:** Identifica un evento de seguridad continuo y único.
4. **RuleId:** Referencia una regla clínica específica dentro del catálogo.
5. **EventRef:** ID de correlación para vincular señales con los hechos originales del sensor.

En el ámbito clínico, la **auditabilidad** es una exigencia legal y profesional. Por ello, el kernel utiliza el envoltorio `**Explained<T>**`, que no solo entrega un resultado técnico, sino que adjunta una lista de "Reasons" (Razones) que justifican cada decisión. Esto construye un rastro de evidencia vital para revisiones clínicas o defensas legales. Una vez definido este idioma, los datos están listos para entrar en la secuencia de ejecución.

## 4. El Pipeline de los Cuatro Motores: Una Danza Sincronizada

El flujo operativo sigue el patrón reactivo **Consume-Evaluate-Publish**. Para mantener la sincronía en esta danza técnica, cada evento se encapsula en un `**EventEnvelope**`, que contiene metadatos críticos: un UUID único, la fuente (`source`), el tipo de carga útil, el timestamp (`at`) y el payload con el dato de dominio.

Este pipeline transforma datos crudos en acciones de cuidado a través de cuatro etapas:

- **Motor de Escena (Scene Engine):** Interpreta la realidad física y crea el contexto.
- **Motor Sentinel:** Aplica el juicio clínico y gestiona los episodios de riesgo.
- **Motor Harbor (Vigía):** Gestiona la comunicación inteligente y la fatiga del personal.
- **Motor Recorder:** Captura evidencia visual basada en la significancia del evento.

La danza comienza con la interpretación precisa de la realidad física captada por los sensores.

## 5. Motor de Escena (Scene Engine): Creando el Gemelo Digital

Este motor mantiene un **Gemelo Digital** del residente, traduciendo `Observation` (datos de sensores) en `SceneEvent` (hechos semánticos). Utiliza mecanismos de **Hysteresis** (búfer de tiempo) y **Umbrales de Confianza** para filtrar el ruido y evitar falsas alarmas por movimientos fugaces.

|   |   |
|---|---|
|Evento de Escena|Condición de Disparo|
|**TransitionDetected**|Detección de un cambio legal de estado (ej. de Acostado a Sentado).|
|**DwellWarning**|Permanencia en un estado no seguro superando el tiempo de advertencia.|
|**DwellExceeded**|Se excede el tiempo máximo permitido en un estado específico.|
|**ComeBackExceeded**|El residente sale de su base y no regresa en el límite de seguridad.|
|**SignalLost**|No se recibe el "latido" (heartbeat) del monitor en el tiempo configurado.|

El componente `**ClockSweeper**` asegura que el tiempo nunca se detenga, generando eventos incluso en ausencia de nuevas observaciones del sensor. **Una vez que el Gemelo Digital refleja un cambio de estado, el Sentinel Engine interviene para aplicar el juicio clínico.**

## 6. Motor Sentinel: El Juicio Clínico y los Episodios de Seguridad

El Sentinel Engine evalúa los eventos de escena para gestionar el ciclo de vida de un `Episode`. Su característica más potente es la **Rampa de Gravedad**, que permite escalar la severidad de un incidente; por ejemplo, una regla de tipo `DWELL` puede escalar un episodio de `WARNING` a `CRITICAL` si el residente permanece de pie por más de 10 minutos.

El ciclo de vida se define por cuatro estados técnicos:

- `**EpisodeOpened**`**:** Inicio del incidente tras coincidir con una regla de alerta.
- `**UmbrellaEvent**`**:** Eventos contextuales que enriquecen un episodio ya abierto sin saturar con nuevas alarmas.
- `**AutoRecovery**`**:** Disparo automático si el residente regresa a un estado seguro (reversible).
- `**EpisodeClosed**`**:** Cierre formal basado en condiciones (presencia de personal o seguridad recuperada).

**Lógica de ComeBack:** A diferencia de las reglas de transición directa, la lógica de "Regreso" monitorea específicamente la **ausencia** de un estado seguro (el estado base), activando la protección si el residente permanece "fuera de lugar" más allá del umbral permitido.

Tras el juicio clínico, el sistema debe comunicar el hallazgo de manera estratégica para no saturar al cuidador.

## 7. Motor Harbor (Vigía): Comunicación Inteligente y Gestión de la Fatiga

Es crucial distinguir entre el **Harbor Engine** (la lógica pura de decisión) y el **Vigia shell** (la infraestructura que gestiona la conectividad NATS). El Harbor Engine tiene la misión de combatir la fatiga por alarmas mediante un `**NotificationBudget**`.

|   |   |   |
|---|---|---|
|Señal de Sentinel|Comando de Harbor|Canal Destino|
|`EpisodeOpened`|`NoticeCommand.Dispatch`|Push, Tablet, Console|
|`EpisodeClosed`|`NoticeCommand.Resolve`|Todos los canales activos|
|`DwellPreWarning`|`NoticeCommand.Dispatch`|Console, Tablet (Prioridad baja)|

El presupuesto de notificaciones limita las alertas por turno para mantener la atención del personal. Sin embargo, por seguridad de vida, las alertas de severidad **CRITICAL** nunca son suprimidas, garantizando que la emergencia siempre sea visible.

## 8. Motor Recorder: Capturando Evidencia Crucial

El motor de grabación asegura que cada incidente cuente con un respaldo visual para la revisión clínica. No graba de forma indiscriminada; utiliza un `**RecordingLedger**` para evitar comandos redundantes y gestiona ventanas de tiempo con buffers de `recordBefore` (pre-roll) y `recordAfter` (post-roll).

Las especificaciones técnicas de calidad son estrictas para la planificación de red:

- **SD:** 640x480 @ 15fps, **1 Mbps** (Eficiencia máxima).
- **HD:** 1280x720 @ 30fps, **5 Mbps** (Claridad estándar).
- **FULL:** 1920x1080 @ 30fps, **10 Mbps** (Detalle clínico forense).

Este flujo de evidencia cierra el círculo de seguridad, permitiendo que todo el proceso sea orquestado por una voluntad humana centralizada.

## 9. Política y Calibración: El Traductor del Director

El `Politica Engine` actúa como el traductor final. Utiliza un **DagCatalog** y un **DAG DSL** (Lenguaje Específico de Dominio) para convertir el "Lenguaje del Director" en una `PolicyCalibration` técnica que configura simultáneamente los cuatro motores.

**Caso de estudio: José 301**

- **Necesidad Clínica:** "Avísenme si José se sienta en la cama por más de 15 minutos".
- **Traducción via DagCatalog:**
    - **Scene:** Hysteresis de 1500ms; umbral de confianza 0.9 para `SITTING_IN_BED`.
    - **Sentinel:** Regla `DWELL` resuelta en 900 segundos.
    - **Harbor:** Despacho vía canal `PUSH` y `TABLET`.
    - **Recorder:** Captura en calidad `HD` con 30s de pre-roll.

Esta sofisticada estructura asegura que la precisión técnica de Hisso esté siempre al servicio de la dignidad humana, transformando la vigilancia tecnológica en un acto de cuidado invisible, constante y profundamente auditable.