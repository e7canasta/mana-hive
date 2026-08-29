¡Me sumo con entusiasmo a esa mesa de diseño junto a Martin Fowler y los demás gigantes de la arquitectura de software! Diseñar la especificación del modelo de vistas **4+1 de UML** para el **Scene Engine** bajo un enfoque de **Domain-Driven Design (DDD)** es una excelente manera de formalizar la rigidez técnica de Hisso.

Considerando que para esta sesión de diseño **hemos aislado estrictamente la documentación del Scene Engine**, aquí está la propuesta de estructura para el documento arquitectónico:

---

### Propuesta de Estructura: Modelo de Vistas 4+1 (Scene Engine)

#### 1. **Vista Lógica (Logical View)**

- **Enfoque**: Estructura estática y diseño de clases orientado al dominio.
- **Contenido**:
    - El **patrón Facade** como punto de entrada unificado a través de la clase `SceneEngine`.
    - Colaboración y encapsulación de los tres componentes core: `DigitalTwin` (estado), `SceneInterpreter` (transiciones) y `ClockSweeper` (tiempo).
    - El modelo de estados de la persona (`PersonState`): `Lying`, `BedEdge`, `Standing` y `Unknown`.
    - La descomposición de la configuración inmutable de `SceneCalibration` en sus primitivas: `TransitionTable`, `ConfidenceThresholds`, `DwellCatalog` y lógicas de `SignalHealth`.

#### 2. **Vista de Proceso (Process View)**

- **Enfoque**: Dinámica temporal, concurrencia y flujo de datos reactivo.
- **Contenido**:
    - El bucle de ejecución secuencial dentro de `processWithSweepInternal`.
    - El paso a paso de las **4 fases de procesamiento**:
        1. _Avance del tiempo_ en base al timestamp de la nueva observación.
        2. _Barridos intermedios (Sweeps)_ a intervalos regulares (por defecto cada 60s) para capturar eventos basados en tiempo sin telemetría activa.
        3. _Interpretación de la observación_ a través de filtros de confianza mínima (`minConfidence`) y descarte por ruido (`DiscardCause.CONFIDENCE_TOO_LOW`).
        4. _Filtrado por histéresis_ (ej. retraso de transición de 1500ms) para mitigar el "flickeo" de sensores.
        5. _Barrido final_ sincronizado con el timestamp exacto del evento para actualizar temporizadores reactivos de manera inmediata.

#### 3. **Vista de Desarrollo (Development View)**

- **Enfoque**: Organización del código, empaquetado y modularidad.
- **Contenido**:
    - Estructura de módulos en Kotlin, separando el núcleo de dominio puro de las especificaciones de comportamiento.
    - Mapeo de submódulos clave: `scene-domain` (modelo lógico, intérprete, sweeper) y `scene-bdd` (escenarios de prueba de comportamiento).

#### 4. **Vista Física / Despliegue (Physical View)**

- **Enfoque**: Topología física y distribución de nodos.
- **Contenido**:
    - Dado el alcance acotado de nuestras fuentes, modelaremos la instanciación de un `DigitalTwin` por cada par único de cama/residente, operando como un núcleo de cálculo completamente agnóstico de la infraestructura (independiente de bases de datos o brokers de mensajería).

#### 5. **Escenarios (El "+1" - Scenarios View)**

- **Enfoque**: Casos de uso e historias de comportamiento que validan el diseño.
- **Contenido**:
    - Mapeo de los flujos críticos de negocio representados en el motor mediante pruebas BDD:
        - **Transición de Estado**: Detección física exitosa con validación de histéresis.
        - **Permanencia Excesiva**: Disparo de `DwellWarning` y `DwellExceeded` en estados inseguros (ej. Standing prolongado).
        - **Ausencia de Retorno**: Alerta de abandono de cama mediante `ComeBackWarning` y `ComeBackExceeded`.
        - **Pérdida de Señal**: Emisión de `SignalLost` si se excede el `heartbeatTimeout` del monitor.

---

### ¿Cómo procedemos?

Puedo estructurar este documento de dos formas para la mesa de arquitectura:

1. **Reporte de Studio (Tailored Report)**: Un documento técnico formal, auto-contenido y altamente pulido que se guardará directamente en tu panel de Studio para que lo puedas exportar o compartir.
2. **Redacción iterativa en el chat**: Desarrollamos sección por sección aquí mismo para que puedas hacer preguntas o ajustar detalles con el equipo en tiempo real.

**¿Qué opción prefieres para empezar a trabajar el documento?**