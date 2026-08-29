# Manual de Configuración: Transformación de Políticas de Calibración en el Scene Engine

## 1. Fundamentos del Scene Engine y el Digital Twin

El **Scene Engine** se posiciona como la primera etapa crítica del pipeline de procesamiento de señales, actuando como el nexo entre la telemetría cruda y la inteligencia operativa. Su función estratégica es mantener una representación digital de alta fidelidad, o **Digital Twin**, que actúe como la "fuente de verdad" absoluta sobre el estado físico del residente. Al procesar las observaciones a través de un modelo de transición configurable, el motor garantiza que el sistema no solo reaccione a datos puntuales, sino que comprenda la evolución del comportamiento humano en un entorno residencial de salud.

La arquitectura del motor implementa el patrón **Facade**, una decisión de diseño fundamental para desacoplar la adquisición de datos de la lógica de negocio compleja. Este patrón coordina tres componentes internos clave definidos en `SceneEngine.kt`: el `SceneInterpreter`, el `ClockSweeper` y el propio `DigitalTwin`. Según la implementación en `DigitalTwin.kt`, este gemelo digital rastrea meticulosamente cuatro dimensiones operativas:

- **Estado (State):** El `PersonState` actual (ej. _Lying_, _BedEdge_, _Standing_).
- **Contexto Temporal:** El registro `stateSince`, esencial para el cálculo de permanencia.
- **Salud de la Señal (Signal Health):** Monitoreo del último latido (_heartbeat_) del sensor.
- **Calibración:** El objeto `SceneCalibration` que individualiza el comportamiento del motor.

Esta estructura de fachada no solo simplifica la API externa, sino que garantiza la escalabilidad del monitoreo al permitir que la lógica de transición y los eventos temporales se ejecuten de forma sincronizada, preparando el terreno para una configuración precisa mediante el mapeo de políticas.

## 2. Mapeo Estratégico: De PolicyCalibration a SceneCalibration

La transformación de una `PolicyCalibration` de alto nivel en una `SceneCalibration` operativa equivale a una fase de "compilación" técnica. En este proceso, las directrices clínicas se traducen en parámetros ejecutables que el motor utiliza para dictar el comportamiento del Digital Twin. El `PolicyCalibrationAdapter.kt` es el encargado de realizar esta transposición de campos hacia las entidades funcionales del motor:

|   |   |   |
|---|---|---|
|Campo de la Política (`Politica`)|Entidad del Scene Engine|Propósito Técnico|
|`scene.hysteresis`|`TransitionTable`|Define rutas válidas entre estados y el costo temporal de transición.|
|`scene.confidence`|`ConfidenceThresholds`|Establece el `minConfidence` requerido por cada estado específico.|
|`scene.dwellThresholds`|`DwellCatalog`|Cataloga las duraciones máximas para alertas de permanencia.|
|`scene.heartbeatTimeout`|Lógica de `SignalHealth`|Previene el "stale state" (estado estancado) ante fallos del sensor.|

Es imperativo entender que valores como "1500ms" para la histéresis no están predefinidos estáticamente, sino que son el resultado directo del mapeo de `scene.hysteresis`. Esta granularidad _resident-specific_ es vital; permite que el sistema se adapte a la movilidad única de cada individuo, evitando errores de interpretación en estados críticos. Una calibración precisa es el único mecanismo que permite al `SceneInterpreter` distinguir entre ruido ambiental y un cambio real en la seguridad del residente.

## 3. Lógica Operativa del SceneInterpreter: Filtrado y Validación

El `SceneInterpreter` ejerce como el guardián de integridad del sistema, evaluando la validez de cada observación entrante antes de permitir cualquier modificación en el Digital Twin. Su flujo lógico se divide en tres etapas de validación técnica:

1. **Verificación de Confianza por Estado:** El intérprete valida la observación frente al `minConfidence` configurado. Es crucial destacar que este umbral es **específico por estado**; por ejemplo, el estado "Standing" (de alto riesgo) requiere una confianza significativamente mayor que "Lying" para ser aceptado. Si no se alcanza, la observación se descarta como `CONFIDENCE_TOO_LOW`.
2. **Validación de Transición y Costo:** Se consulta la `TransitionTable` no solo para verificar si un movimiento es legal, sino para determinar el "costo" o retardo requerido para esa transición específica.
3. **Persistencia por Histéresis:** Para evitar el "flickering" o parpadeo entre estados debido a señales erráticas, el nuevo estado debe mantenerse de forma continua durante el tiempo dictado por la histéresis antes de ser oficializado.

Este equilibrio entre umbrales de confianza per-estado y tiempos de histéresis es lo que reduce la fatiga por alarmas. Mientras que el intérprete es un componente reactivo ante los datos, el sistema requiere una dimensión proactiva para gestionar el tiempo en ausencia de señales.

## 4. Gestión de Eventos Temporales mediante el ClockSweeper

Si el intérprete es el componente "Pasivo/Reactivo", el **ClockSweeper** representa la **Seguridad Activa** del motor. Su importancia estratégica reside en detectar riesgos que no dependen de una nueva señal, sino de la inactividad o el estancamiento del residente. El `ClockSweeper` realiza barridos en intervalos definidos por `sweepIntervalSeconds` (por defecto 60s) para disparar eventos críticos:

- **DwellWarning / DwellExceeded:** Se activan cuando la permanencia en un estado (ej. "Standing") supera los límites de seguridad definidos en el `DwellCatalog`.
- **ComeBackWarning / ComeBackExceeded:** Estos eventos son la defensa principal contra incidentes "silenciosos" donde el residente sale del rango del sensor y no regresa a la cama en el tiempo previsto.
- **SignalLost:** Basado en el `heartbeatTimeout`, asegura que una pérdida de conectividad no sea interpretada erróneamente como un estado estable del residente.

La ejecución regular de estos barridos garantiza la protección del residente incluso cuando no hay observaciones frescas, cerrando la brecha de seguridad durante los periodos de inactividad sensorial.

## 5. El Ciclo de Procesamiento: Flujo de Datos y Sincronización

El método `processWithSweepInternal` constituye el núcleo arquitectónico que garantiza la **continuidad temporal** frente a observaciones que suelen ser discretas o esporádicas. Este bucle de procesamiento resuelve el problema de los gaps de latencia asincrónica mediante una secuencia determinista de cuatro pasos:

1. **Avance y Verificación de Latencia:** El sistema compara el timestamp de la nueva observación (`now`) con el último registro procesado.
2. **Ejecución de Barridos Intermedios:** Si el gap temporal supera el `sweepIntervalSeconds`, se ejecutan llamadas a `sweeper.sweep()` para capturar eventos de seguridad que ocurrieron mientras no llegaban datos.
3. **Interpretación de la Observación:** El `SceneInterpreter` procesa la señal actual para actualizar el estado del Digital Twin.
4. **Barrido Final de Sincronización:** Se realiza un último sweep en el timestamp exacto de la observación, asegurando que todos los temporizadores de estado se actualicen instantáneamente.

Esta estructura de **doble barrido** (pre y post interpretación) es lo que confiere robustez al sistema. Asegura que, sin importar la latencia de la red o la frecuencia de los sensores, el Digital Twin sea un modelo determinista y siempre actualizado, proporcionando a los ingenieros de campo un marco de trabajo altamente confiable para la seguridad residencial.