# Especificación Arquitectónica: Modelo de Vistas 4+1 del Scene Engine

## 1. Introducción y Propósito del Documento

El **Scene Engine** constituye la etapa primaria del pipeline de procesamiento de observaciones dentro del ecosistema Hisso. Su función estratégica es crítica para la monitorización de pacientes en entornos de salud: actúa como el motor de interpretación de datos sensoriales encargado de mantener y actualizar el **Digital Twin** (Gemelo Digital) del residente. Este componente transforma datos crudos en una representación persistente y fidedigna de la realidad física, permitiendo una supervisión proactiva y segura.

Para cumplir con esta misión, el motor se diseña bajo tres objetivos técnicos fundamentales:

- **Filtrado de ruido y validación de señal:** Eliminación de interferencias mediante umbrales de confianza y análisis de integridad.
- **Gestión de transiciones de estado:** Supervisión estricta de cambios posturales para asegurar la coherencia clínica.
- **Generación de eventos temporales:** Activación de alertas basadas en cronómetros internos, capturando riesgos incluso ante la ausencia de nuevas entradas sensoriales.

## 2. Vista Lógica (Logical View)

La Vista Lógica describe la estructura estática del sistema, diseñada bajo principios de **Domain-Driven Design (DDD)**. Se emplea el patrón **Facade** para exponer una interfaz simplificada (`SceneEngine`) que oculta una lógica de dominio pura y compleja, garantizando que el núcleo del negocio permanezca desacoplado de la infraestructura.

### El Coordinador Central: SceneEngine

La clase `SceneEngine` es el punto de entrada único y coordinador de la lógica de negocio. Su responsabilidad es orquestar el flujo de datos entre los componentes internos y asegurar que cada observación sea procesada contra el modelo de calibración vigente.

### Componentes Internos Fundamentales

|   |   |   |
|---|---|---|
|Entidad|Responsabilidad Técnica|Impacto en la Integridad del Sistema|
|**SceneInterpreter**|Procesa la lógica de transición basándose en observaciones entrantes y reglas de dominio.|Garantiza la integridad de la máquina de estados aplicando invariantes de transición clínica.|
|**ClockSweeper**|Gestiona eventos dependientes del tiempo y cronómetros de permanencia (dwell timers).|Asegura que las alertas temporales se disparen con precisión, independientemente del flujo de datos del sensor.|
|**DigitalTwin**|Contenedor de estado que mantiene el estatus actual, el latido de señal y el contexto temporal via `stateSince`.|Actúa como la "fuente de verdad" única; `stateSince` permite cálculos de permanencia exactos para la seguridad del paciente.|

### Modelo de Estados (PersonState)

El sistema clasifica la situación del residente en cuatro estados exhaustivos:

1. **Lying (Acostado):** Estado base de descanso.
2. **BedEdge (Borde de cama):** Estado crítico de transición.
3. **Standing (De pie):** Incorporación detectada.
4. **Unknown (Desconocido):** Incertidumbre por falta de datos concluyentes.

### Parametrización: SceneCalibration

El motor se configura dinámicamente a través de la entidad `SceneCalibration`, compuesta por:

1. **TransitionTable:** Define las rutas legales entre estados, impidiendo transiciones físicamente imposibles (ej. de _Lying_ a _Standing_ sin pasar por _BedEdge_).
2. **ConfidenceThresholds:** Umbrales mínimos de confianza por estado para filtrar falsos positivos.
3. **DwellCatalog:** Catálogo de tiempos de advertencia y exceso de permanencia configurables.
4. **SignalHealth:** Lógica vinculada al parámetro `heartbeatTimeout` para monitorizar la integridad del hardware.

## 3. Vista de Procesos (Process View)

El Scene Engine es un motor reactivo y secuencial donde el tiempo se trata como una dimensión de procesamiento igual de crítica que los datos sensoriales.

### El Bucle de Procesamiento: `processWithSweepInternal`

El sistema gestiona el avance del tiempo mediante una lógica de barrido sistemático. Al recibir una observación, el motor evalúa la brecha temporal respecto al último registro para asegurar que no existan intervalos de supervisión desatendidos.

### Mecanismo de Filtrado y Validación Activa

Para validar cualquier cambio de estado, el sistema ejecuta estrictamente el siguiente flujo:

1. **Verificación de confianza mínima:** Se evalúa la observación contra el `minConfidence`. Si es inferior, se descarta bajo la causa `CONFIDENCE_TOO_LOW`.
2. **Validación de legalidad clínica:** Se consulta la `TransitionTable` para confirmar que el movimiento es permitido por el modelo de seguridad.
3. **Aplicación de histéresis:** Para evitar oscilaciones espurias, el nuevo estado debe persistir un mínimo de **1500ms** antes de que el `DigitalTwin` lo oficialice.

### Especialización del ClockSweeper

El componente `ClockSweeper` opera en dos modalidades para maximizar la reactividad:

- **Barridos intermedios:** Ejecutados cada 60 segundos para capturar eventos de tiempo (como excesos de permanencia) que ocurren específicamente durante periodos de silencio de los sensores.
- **Barrido final:** Se realiza en el timestamp exacto de la observación entrante para sincronizar los temporizadores de cambio de estado de forma inmediata.

## 4. Vista de Desarrollo (Development View)

La implementación se realiza en **Kotlin**, priorizando la modularización para facilitar la testabilidad y el mantenimiento evolutivo de la lógica de escena.

### Jerarquía de Submódulos

- `**scene-domain**`**:** Núcleo de la lógica, entidades puras y motor de interpretación. No posee dependencias externas de infraestructura.
- `**scene-bdd**`**:** Especificaciones de comportamiento y pruebas de aceptación que validan la lógica de negocio frente a casos clínicos reales.

### Compilación de Configuración y Mapeo

El componente `PolicyCalibrationAdapter` realiza una transformación crítica: traduce la **Política** de alto nivel (campos de negocio como `scene.dwellThresholds` o `scene.heartbeatTimeout`) en la entidad ejecutable `SceneCalibration`. Este diseño permite el "hot-swapping" de reglas de negocio y umbrales clínicos sin necesidad de recompilar el motor, facilitando ajustes dinámicos en el entorno asistencial.

## 5. Vista de Despliegue (Physical View)

La arquitectura favorece una topología descentralizada y escalable, protegiendo la disponibilidad del sistema mediante el aislamiento de estados.

### Modelo de Instanciación e Inmutabilidad

La regla de despliegue dicta la creación de un **DigitalTwin único por cada par residente/cama**. Cada instancia mantiene su propio contexto temporal y de calibración de forma aislada.

### Beneficios de la Arquitectura Aislada

- **Resiliencia Sistémica:** Un error de datos o una condición anómala en la monitorización de un residente no impacta en el resto de la población monitorizada.
- **Integridad de Datos:** Al eliminar estados compartidos, se previenen colisiones de datos y se garantiza que cada alerta emitida sea vinculada unívocamente a la entidad física correcta.

## 6. Escenarios (Scenarios - El "+1")

Los escenarios BDD actúan como el mecanismo de validación final que unifica las vistas técnica y de negocio, asegurando que el sistema responda correctamente a las situaciones críticas del mundo real.

### Catálogo de Eventos Críticos

El motor emite hechos técnicos basados en condiciones de disparo rigurosas:

- **Transition Detected:** Cambio de estado validado tras superar filtros de confianza, legalidad y el periodo de histéresis.
- **DwellWarning / DwellExceeded:** El residente permanece en un estado sensible (como _BedEdge_) más allá de los límites de seguridad definidos.
- **ComeBackWarning / ComeBackExceeded:** Fallo crítico en el retorno al estado _Lying_ (acostado) tras una salida de cama detectada, dentro de la ventana de seguridad.
- **SignalLost:** Superación del umbral `heartbeatTimeout` sin recibir señales de vida del monitor, indicando pérdida de supervisión técnica.

La convergencia de estos escenarios garantiza que el Scene Engine proporcione una monitorización de alta fidelidad, permitiendo una intervención clínica oportuna y basada en datos íntegros.