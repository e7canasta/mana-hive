# Glosario de Conceptos de Dominio: Ecosistema mana-hive

Bienvenido a la base del conocimiento de **mana-hive**. Como mentor senior en esta travesía de ingeniería, mi objetivo no es solo que memorices términos, sino que comprendas la sofisticada arquitectura que permite que una señal eléctrica se convierta en una acción que salva vidas. mana-hive es una plataforma distribuida y dirigida por eventos, diseñada para el monitoreo de cuidados nocturnos, donde la precisión técnica es la base de la seguridad clínica.

### 1. El Corazón de mana-hive: Filosofía y Arquitectura

La misión de **mana-hive** es simple pero vital: **"asegurar que la persona adecuada llegue a la habitación correcta a tiempo, con la menor cantidad de falsas alarmas posible"**. Para sostener esta promesa, nos regimos por tres pilares fundamentales:

- **Arquitectura Dirigida por Eventos (Event-Driven):** Toda la comunicación entre subsistemas se realiza mediante contratos de eventos inmutables y versionados sobre **NATS JetStream**. _Beneficio:_ Permite un desacoplamiento total y una escalabilidad elástica.
- **Pureza en el Núcleo (Pure Domain):** La lógica de negocio está aislada de la infraestructura. Utilizamos el plugin de Gradle `manahive.pure-domain` para garantizar que el núcleo no dependa de frameworks como Spring o bases de datos. _Beneficio:_ Facilita pruebas deterministas y asegura que el juicio clínico sea independiente de la tecnología subyacente.
- **Reproducibilidad Maquinal:** Cada decisión del sistema incluye "huellas digitales" (fingerprints) de la versión del motor y las reglas aplicadas. _Beneficio:_ Permite que el **Hub** reconstruya exactamente por qué una alarma sonó (o se descartó), algo esencial para la responsabilidad ética y clínica.

Esta filosofía se materializa a través de motores especializados que procesan el flujo de información de forma secuencial y coordinada.

### 2. Los Subsistemas: Los Motores del Cuidado

En mana-hive, la información no solo se mueve; se transforma. Cada subsistema actúa como un eslabón en una cadena de valor donde los datos crudos ganan contexto clínico.

|   |   |   |
|---|---|---|
|Subsistema|Responsabilidad Clave|Entidades de Código Principales|
|**Scene Engine**|Actúa como un **Digital Twin** (Gemelo Digital). Traduce observaciones ruidosas en estados estables de la escena.|`DigitalTwin`, `SceneInterpreter`, `ClockSweeper`|
|**Sentinel**|Representa el **Juicio Clínico**. Evalúa los hechos de la escena contra políticas de seguridad para identificar incidentes.|`SentinelEvaluator`, `EpisodeLedger`, `FatigueBudget`|
|**Harbor (Harbor)**|Gestiona la **Entrega de Alertas**. Controla el ciclo de vida de la notificación y su escalación a dispositivos humanos.|`NoticeLifecycle`, `NoticeRouter`, `HarborEngine`|
|**Hub**|Es el **Sistema de Registro (SoR)**. Actúa como el libro mayor (ledger) inmutable de todos los eventos y políticas.|`EventStore`, `LedgerController`, `NatsIngestListener`|

Para que estos motores hablen el mismo idioma sin errores, el sistema utiliza identidades fuertemente tipadas que constituyen el ADN de nuestra plataforma.

### 3. Identidades Fuertemente Tipadas: El ADN del Ecosistema

En mana-hive, evitamos la "obsesión por los primitivos". No usamos simples cadenas de texto o enteros para los IDs; en su lugar, implementamos **Kotlin value classes** con la anotación `@JvmInline`. Esto nos otorga seguridad de tipos en tiempo de compilación con **cero costo de rendimiento (runtime overhead)**.

#### BedId

Representa la ubicación física de una cama. Es crucial no confundirlo con el residente; una cama es estática, pero quien la ocupa puede cambiar. Confundir un `BedId` con un `ResidentId` resultaría en enviar ayuda a la habitación equivocada.

#### ResidentId

Identifica de forma única a la persona bajo cuidado. Al mantenerlo separado de la ubicación física, el sistema puede rastrear la historia clínica de un residente incluso si es trasladado de habitación durante su estadía.

#### EpisodeId

Identifica un **episodio clínico de riesgo** (ej. una caída). El riesgo aquí es confundir el "episodio" (el incidente de larga duración) con un solo evento de sensor. El `EpisodeId` agrupa toda la narrativa de un incidente desde que se abre hasta que se resuelve.

#### NoticeId

Es el identificador de una **notificación enviada al personal**. Es vital distinguirlo del `EpisodeId`: un solo episodio clínico puede generar múltiples `NoticeId` si la alerta debe escalarse a diferentes cuidadores o dispositivos. Confundirlos impediría rastrear quién respondió a qué alerta específica.

Estas identidades son las que viajan a través de nuestro pipeline, permitiendo una trazabilidad total desde el sensor hasta la alarma.

### 4. El Pipeline de Datos: De la Percepción a la Alarma

El flujo de datos en mana-hive es una metamorfosis inmutable. Ningún evento se sobrecribe; cada etapa emite un nuevo contrato que añade inteligencia:

1. **Perception (Observation):** Datos crudos de sensores (ej. "presión detectada"). Contrato: `perception.observation.v1`.
2. **Scene Fact:** El Scene Engine genera estados de alto nivel (ej. `BedExit`). Contrato: `scene.fact.v1`. Gracias al `ClockSweeper`, detectamos incluso la ausencia de movimiento (dwell detection).
3. **Sentinel Signal:** El Sentinel juzga el hecho. Si se rompe una regla, emite un **Incident**. Contrato: `sentinel.signal.v1`.
4. **Alarm Event:** El Harbor gestiona la notificación humana, registrando si fue entregada, vista o aceptada (`Acknowledged`). Contrato: `alarm.event.v1`.

Este pipeline no es una "caja negra"; la inmutabilidad de estos contratos permite que el sistema reconstruya la lógica de cada decisión, vinculando directamente la percepción inicial con la alarma final.

### 5. Conceptos de Transparencia y Auditoría

La arquitectura de mana-hive está diseñada para responder al "porqué" de cada acción. No basta con que el sistema funcione; debe ser auditable.

**DecisionRecord:** Es el rastro duradero de cada invocación de los motores. Contiene "huellas digitales" (fingerprints) de la versión del software y las reglas exactas aplicadas. Esto permite que una decisión tomada hace meses sea **maquinalmente reproducible** hoy mismo.

**Explained****<T>****:** Es un contenedor que envuelve el resultado (T) de cualquier proceso. No solo lleva la conclusión, sino también una lista de `ExplanationStep` (por qué se decidió algo) y una lista de `Discard` (las razones de rechazo, como `HYSTERESIS_NOT_MET`). En mana-hive, una decisión sin explicación no existe.

**FatigueBudget:** Una herramienta ética para el personal de cuidado. Gestiona la carga cognitiva limitando las interrupciones por turno (por defecto, un máximo de **12 notificaciones por turno por residente**). Si un incidente ocurre pero el presupuesto se ha agotado, el sistema genera un registro de supresión para análisis posterior, protegiendo al cuidador de la fatiga por alarmas.

Dominar este glosario es comprender la columna vertebral de mana-hive. Estos conceptos aseguran que estemos construyendo no solo software, sino una herramienta de confianza para la seguridad humana.