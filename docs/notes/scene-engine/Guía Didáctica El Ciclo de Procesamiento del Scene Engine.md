# Guía Didáctica: El Ciclo de Procesamiento del Scene Engine

## 1. Bienvenida y el Concepto del "Gemelo Digital"

Bienvenido al equipo técnico. Como primer paso en tu formación, es esencial comprender el **Scene Engine**. Este componente representa la primera etapa crítica de nuestro pipeline de procesamiento, donde las señales brutas de los sensores se transforman en información clínica accionable. Su propósito fundamental es mantener y gestionar lo que denominamos el **Digital Twin** (Gemelo Digital).

**Definición de Gemelo Digital:** Es la "fuente de la verdad" absoluta sobre el estado físico del residente en tiempo real. No es un simple registro estático, sino una entidad virtual dinámica que modela el comportamiento del residente basándose en reglas de transición lógica y datos históricos.

Para garantizar la seguridad y una supervisión efectiva, el Digital Twin rastrea cuatro elementos clave:

- **Estado (State):** Representado por la entidad `PersonState`. Este enum rastrea si el residente se encuentra en posturas como _Lying_ (Acostado), _BedEdge_ (Borde de cama), _Standing_ (De pie) o incluso _Unknown_ (Desconocido) si la señal es ambigua.
- **Contexto Temporal (Temporal Context):** Utiliza el campo `stateSince` para marcar el momento exacto en que se inició el estado actual. Esto es vital para calcular riesgos por inmovilidad o permanencia prolongada.
- **Salud de la Señal (Signal Health):** Monitorea el último "latido" o _heartbeat_ del sensor. Saber si el sistema está "escuchando" es el primer requisito para la seguridad del paciente.
- **Calibración (Calibration):** Aplica la `SceneCalibration` específica del residente. Cada persona es única, y el Digital Twin ajusta sus umbrales de sensibilidad y tiempos de respuesta según el perfil configurado.

Esta identidad virtual es la base sobre la cual operan los componentes lógicos que dan vida al sistema.

## 2. Los Tres Actores Principales del Sistema

Para gestionar la complejidad del procesamiento, el Scene Engine utiliza un patrón **Facade** (Fachada). Este diseño permite que tú, como desarrollador, interactúes con una API simplificada mientras tres actores internos coordinan sus esfuerzos con precisión quirúrgica.

|   |   |   |
|---|---|---|
|Componente|Responsabilidad Principal|Impacto en el Proceso|
|**SceneInterpreter**|Cerebro lógico de transiciones. Evalúa si la nueva observación justifica un cambio de estado.|Filtra el ruido y asegura que los movimientos reportados sean físicamente coherentes.|
|**ClockSweeper**|Vigilante del tiempo y eventos. Gestiona lo que sucede "en el silencio", entre observaciones.|Genera alertas proactivas basadas en la duración de los estados (Dwell) y tiempos de retorno.|
|**DigitalTwin**|Contenedor de estado y salud. Mantiene la persistencia del estado actual y la calibración.|Provee el contexto necesario para que el Interpreter y el Sweeper tomen decisiones informadas.|

Estos actores se sincronizan en un ciclo continuo que garantiza que el sistema nunca pierda de vista al residente.

## 3. El Bucle de Procesamiento: Desglose de las 4 Etapas

Toda la lógica de procesamiento central reside en el método `processWithSweepInternal`. Este bucle está diseñado para que el tiempo nunca se detenga, procesando no solo lo que el sensor envía, sino también lo que el tiempo dicta.

1. **Avance del Tiempo:** El sistema compara el timestamp de la observación entrante (`now`) con el final del último proceso exitoso (`lastTime`). Esto permite reconstruir la línea de tiempo del residente sin huecos de información.
2. **Barridos Intermedios (Intermediate Sweeps):** Si el intervalo entre observaciones es largo, el sistema ejecuta barridos automáticos cada 60 segundos (`sweepIntervalSeconds`). Esto es vital para detectar eventos que dependen solo del reloj (como una alerta de caída o ausencia) que ocurrieron mientras no llegaban datos del sensor.
3. **Interpretación de la Observación:** El `SceneInterpreter` toma la entrada actual y la valida contra el estado actual del Digital Twin. Aquí se decide si el residente ha cambiado de postura o si la señal debe descartarse.
4. **Barrido Final:** Se realiza un barrido final en el timestamp exacto de la observación. Esta etapa es crítica porque **dispara de inmediato el inicio de temporizadores** (como los de Histéresis o Dwell) para el nuevo estado detectado. Sin este paso, el sistema tendría que esperar al próximo ciclo para empezar a contar el tiempo en la nueva posición.

Este flujo garantiza que cada milisegundo sea contabilizado, filtrando inteligentemente las señales antes de que se conviertan en alertas.

## 4. Reglas de Validación: Filtrando el Ruido con Inteligencia

En el entorno de Health-Tech, la precisión es sinónimo de seguridad. El `SceneInterpreter` aplica reglas estrictas para evitar "falsas alarmas" que generan **fatiga por alertas** en el personal de enfermería. Antes de aceptar un cambio, se verifica lo siguiente:

- [ ] **Chequeo de Confianza:** ¿La señal es clara? Se verifica que la observación supere el umbral `minConfidence` definido para **ese estado específico** en la `SceneCalibration`. Si no llega al nivel (ej. 0.9 para _BedEdge_), se marca como `DiscardCause.CONFIDENCE_TOO_LOW`.
- [ ] **Validación de Transición:** ¿Es un movimiento lógico? Se consulta la `TransitionTable` para confirmar que el paso del estado actual al nuevo es "legal" según el modelo físico configurado.
- [ ] **Filtro de Histéresis:** ¿Es un cambio real o momentáneo? El nuevo estado debe persistir durante un tiempo mínimo (típicamente 1500ms). Solo cuando el movimiento se estabiliza, el Digital Twin actualiza oficialmente el `PersonState`.

Una vez validada la información, el sistema puede reaccionar de forma autónoma ante situaciones de riesgo.

## 5. El Reloj en Acción: Eventos Generados por el ClockSweeper

Incluso cuando el sensor no reporta cambios, el `ClockSweeper` permanece activo, generando hechos de tipo `SceneEvent` basados exclusivamente en el paso del tiempo. Estos eventos son la base de la vigilancia proactiva:

- **Dwell (Permanencia):** Controla cuánto tiempo pasa el residente en una postura de riesgo.
    - **DwellWarning:** Aviso preventivo de que se está alcanzando un límite.
    - **DwellExceeded:** El residente ha permanecido demasiado tiempo en una posición (ej. demasiado tiempo de pie sin moverse).
- **ComeBack (Retorno):** Específico para cuando el residente sale de la cama.
    - **ComeBackWarning:** El sistema notifica que el residente aún no ha regresado.
    - **ComeBackExceeded:** Se ha superado el tiempo de seguridad configurado para el retorno a la cama.
- **SignalLost (Pérdida de Señal):** Si el sistema no recibe un "latido" antes de que expire el `heartbeatTimeout`, se emite este evento de seguridad crítica para informar que el monitor ha perdido conexión.

La precisión en cada una de estas etapas es lo que nos permite ofrecer una herramienta confiable para el cuidado y la seguridad de los residentes.