# Protocolo de Auditoría y Trazabilidad: Reconstrucción Determinista de Decisiones Clínicas en mana-hive

### 1. Fundamentos del Sistema de Registro Inmutable

En el ecosistema de **mana-hive**, la capacidad de auditar cada evento no es un añadido secundario, sino una misión central. El sistema está diseñado bajo una arquitectura de "Dominio Puro" y un patrón de "Tuberías y Filtros" (_Pipe-and-filter_), lo que garantiza que la lógica clínica esté aislada de las complejidades de la infraestructura. Esta separación no es solo una preferencia técnica; constituye la base estratégica para la seguridad del residente y la responsabilidad legal de las instituciones. Al procesar datos a través de motores especializados que actúan como filtros inmutables, el sistema asegura el cumplimiento de su promesa fundamental: que la persona correcta llegue a la habitación correcta a tiempo, minimizando la fatiga por falsas alarmas.

La integridad de esta misión descansa sobre tres pilares arquitectónicos:

- **Arquitectura Orientada a Eventos (Event-Driven):** Toda la comunicación entre subsistemas se realiza mediante contratos de eventos versionados sobre NATS JetStream. Esto elimina el acoplamiento y permite que cada mensaje sea un registro histórico autocontenido.
- **Pureza en el Núcleo (Purity at the Core):** Los motores de decisión (Engines) están aislados mediante el plugin de Gradle `manahive.pure-domain`. Esta restricción impide que la lógica dependa de frameworks (como Spring) o infraestructura, garantizando funciones puras donde el mismo estímulo siempre produce el mismo veredicto.
- **Reproducibilidad por Máquina (Machine Reproducibility):** Cada decisión captura una "huella digital" que incluye la versión exacta del software (build fingerprint) y las reglas aplicadas. Esto permite que el sistema responda con precisión forense por qué una notificación fue emitida o suprimida.

Estos pilares convergen en la necesidad de un registro centralizado de verdad administrativa, capaz de transformar ráfagas efímeras de datos en una crónica legalmente vinculante.

### 2. El Hub como "System of Record" (SoR) y Crónica Global

Dentro de la arquitectura de mana-hive, existe una distinción crítica entre el transporte de datos y su preservación. Mientras que **NATS JetStream** funciona como el sistema nervioso central, facilitando la coordinación en tiempo real, el **Hub** actúa como el libro mayor o _ledger_ definitivo del sistema.

El Hub ingiere cada evento que circula por el bus y lo persiste en una base de datos Postgres de forma permanente. Mientras que NATS está configurado con una retención basada en límites (7 días y `StorageType.File` para persistencia en reinicios), el Hub trasciende estas limitaciones para consolidarse como la "Verdad Administrativa". Un detalle vital para la auditoría es la ventana de **deduplicación de 10 minutos** configurada en el `NatsTopology`, la cual permite gestionar reintentos de los productores sin corromper la integridad del ledger.

|   |   |   |
|---|---|---|
|Característica|NATS JetStream (Transporte)|Hub (Postgres Ledger)|
|**Propósito**|Buffer de alto rendimiento y coordinación.|Registro permanente y auditoría clínica.|
|**Persistencia**|Temporal (7 días, `StorageType.File`).|Permanente e inmutable.|
|**Rol en Auditoría**|Reconstrucción de estado de motores activos.|Fuente de verdad para reconstrucción forense.|
|**Integridad**|Ventana de deduplicación de 10 minutos.|Integridad referencial y auditoría SQL.|

Esta estructura asegura que, aunque los datos cumplan su ciclo de vida en el bus, la crónica global de lo ocurrido permanezca intacta para revisiones legales.

### 3. Anatomía del DecisionRecord: La Huella Digital de la Decisión

La trazabilidad en mana-hive no solo registra el "qué", sino el razonamiento clínico o "por qué". El `DecisionRecord` es la pieza fundamental para la defensa legal y la mejora continua de algoritmos, situando cada veredicto dentro de su contexto lógico exacto.

#### Componentes Críticos para la Reproducibilidad

1. **EngineVersion:** Captura la versión semántica y la huella digital del _build_. Esto garantiza que un auditor pueda identificar la lógica exacta activa durante un incidente.
2. **Explained****<T>****:** Estructura de telemetría que desglosa el razonamiento:
    - `ExplanationStep`: Registra la regla evaluada, la observación y la conclusión.
    - `DiscardCause`: Identifica por qué se descartó una acción, citando causas como `HYSTERESIS_NOT_MET` (histéresis), `FATIGUE_BUDGET_EXCEEDED` (presupuesto de fatiga) o `**CONFIDENCE_TOO_LOW**` (baja confianza del sensor).
3. **Huellas de Entrada:** Vincula el estímulo de entrada con el veredicto final para trazar la cadena de causalidad.

#### El EventEnvelope: Contexto y Idempotencia

El `EventEnvelope` es el contenedor que permite que el flujo dinámico sea rastreable. Sus campos incluyen:

1. **eventId:** Clave única de idempotencia que duplica su función como `**Nats-Msg-Id**`, mecanismo técnico que previene el doble conteo de eventos clínicos.
2. **version:** Versión del esquema del contrato para asegurar compatibilidad.
3. **occurredAt:** Marca de tiempo del suceso en el mundo físico.
4. **source:** Identificación del componente emisor.

### 4. Trazabilidad Extremo a Extremo: Del Sensor a la Alerta Humana

La taxonomía de sujetos y el versionado de contratos aseguran una trazabilidad ininterrumpida a través de las capas del sistema.

**Taxonomía de Sujetos:** `{dominio}.{type}.{version}.{discriminator}` El uso de **Wildcard Patterns** (ej. `scene.fact.v1.>`) permite que el Hub y los auditores se suscriban a flujos completos de datos. Las versiones (v1, v2) garantizan que cambios disruptivos requieran nuevos sujetos, manteniendo la integridad histórica.

#### Ciclo de Vida de un Incidente y Persistencia del Riesgo

En un incidente de caída, el sistema gestiona una transición crítica de identidades:

- **Percepción (ia-cell):** Emite una `Observation`.
- **Escena (DigitalTwin):** El motor rastrea **Camas (BedId)** a través del `DigitalTwin`. Si se detecta una caída, el `ClockSweeper` emite un `SceneFact` tras cumplirse el tiempo de permanencia.
- **Sentinel (SentinelEvaluator):** Juzga el hecho contra la política del residente. A diferencia de la Escena, el Sentinel rastrea **Residentes (ResidentId)** mediante el `**EpisodeLedger**`. Esto es vital para la auditoría clínica: permite que un episodio de riesgo persista incluso si el residente es movido entre camas durante la noche. Genera un `SentinelSignal` (Incident, Occurrence o Suppression).
- **Harbor (NoticeLifecycle):** Gestiona el ciclo de vida de la notificación interna (Notice) en el `**NoticeRegistry**`, registrando desde su creación hasta su resolución por presencia física.

### 5. Metodología de Reproducción: El "Golden Replay" y Auditoría Técnica

La reproducibilidad por máquina permite a un auditor recrear el estado exacto del sistema. Esta capacidad es posible gracias al plugin `manahive.pure-domain`, que al restringir el uso de frameworks externos, garantiza que los motores se comporten de forma determinista fuera del entorno de producción.

Para la auditoría técnica se utilizan las **Batch Tools** (`scene-batch`, `harbor-batch`):

- **Run:** Procesa datos históricos para observar resultados.
- **Verify:** Valida la lógica actual contra un "archivo de oro" histórico.
- **Diff:** Resalta discrepancias en transiciones de estado por cambios de lógica.

#### Flujo de Trabajo para Auditoría de Decisión

1. **Identificación:** Localizar el `eventId` o `EpisodeId` en el ledger de Postgres del Hub.
2. **Extracción de Contexto:** Recuperar los datos de entrada y la `EngineVersion` del `DecisionRecord`.
3. **Configuración:** Cargar la versión correspondiente del motor y la calibración activa en la fecha del evento.
4. **Ejecución del "Golden Replay":** Rebotar los datos de entrada a través del motor puro usando la herramienta de _batch_.
5. **Validación:** Comparar resultados y analizar `DiscardCause` si la alerta fue suprimida.

### 6. Diccionario de Identidades y Lenguaje Publicado

El uso de **Strongly-Typed IDs** elimina la ambigüedad, asegurando que un `ResidentId` nunca sea procesado donde se espera un `BedId`.

|   |   |   |
|---|---|---|
|Entidad de Código|Concepto Clínico|Rol en la Trazabilidad|
|**BedId**|Ubicación física|Vincula el evento a un punto geográfico (cama).|
|**ResidentId**|Identidad del paciente|Eje de la persistencia del riesgo clínico.|
|**EpisodeId**|Incidente clínico|Agrupa eventos de un mismo suceso (ej. caída).|
|**EventRef**|Puntero de auditoría|Referencia al evento en el bus (**Stream + Seq**).|

#### Categorización de Estados de Riesgo

Para simplificar la auditoría, el sistema mapea el `PersonState` a grupos de riesgo específicos:

|   |   |   |
|---|---|---|
|PersonState|RiskGroup|Descripción Clínica|
|**Lying**|**SAFE**|Residente en posición segura.|
|**SittingInBed**|**AT_RISK**|Potencial inicio de salida.|
|**AttemptingExit**|**AT_RISK**|Acción inminente de riesgo.|
|**InBathroom**|**AT_RISK**|Supervisión temporal requerida.|
|**Unknown**|**UNKNOWN**|Pérdida de señal o estado no determinado.|

La inmutabilidad de los contratos y este lenguaje publicado actúan como la API permanente que asegura la integridad histórica de mana-hive frente a cualquier requerimiento legal o clínico futuro.