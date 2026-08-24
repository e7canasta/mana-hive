# Guía de Trayectoria de Datos: El Viaje de una Alerta en mana-hive

Esta guía, diseñada desde la perspectiva de la arquitectura de aprendizaje técnico, desglosa el ciclo de vida de una alerta dentro de la plataforma **mana-hive**. Veremos cómo un evento físico se transforma, mediante una arquitectura de dominios puros y procesamiento de eventos, en una decisión clínica reproducible y auditable.

## 1. Introducción: La Misión del Sistema

La plataforma **mana-hive** no es simplemente un sistema de notificaciones; es una infraestructura de precisión diseñada bajo un mandato operativo y ético fundamental:

"Garantizar que la persona adecuada llegue a la habitación adecuada a tiempo, con la menor cantidad posible de falsas alarmas, manteniendo un rastro de auditoría reproducible por máquina."

Para cumplir esta misión, el sistema implementa una arquitectura de **"tuberías y filtros" (pipe-and-filter)**. Imagine un río cuya agua (los datos brutos) entra turbia en su origen. A través de una serie de estaciones de filtrado especializadas (Engines), el sedimento del ruido sensorial se elimina, transformando el flujo en "agua potable": información clínica valiosa para el cuidador.

**El viaje comienza aquí:** A las 03:00 AM, un evento físico —un residente que se desliza fuera de la cama— inicia un viaje digital a través de cuatro fases críticas de transformación.

## 2. Fase 1: Percepción (El Origen de los Datos)

En esta etapa inicial, el componente **ia-cell** actúa como la interfaz sensorial del sistema. Los sensores en la habitación generan eventos de tipo `perception.observation.v1`. Estos eventos representan la traducción de señales eléctricas en un vocabulario de dominio primario.

**Aplicación al caso "Caída de Residente":** A las 03:00, el sensor detecta un cambio de presión o movimiento inusual fuera de la cama. En este punto, el sistema emite una observación, pero no existe aún una interpretación del peligro.

**Atributos clave de una observación:**

- **Confianza del sensor:** Un valor entre 0.0 y 1.0 que califica la certidumbre del hardware sobre la detección.
- **Marca de tiempo (occurredAt):** El instante preciso en que el evento ocurrió en el mundo físico.
- **Vocabulario de dominio:** Traducción de señales brutas a términos como `IN_BED` o `MOVEMENT`.

**Narrativa de aprendizaje:** Los sensores solo perciben señales, pero carecen de juicio. Ven movimiento a nivel de suelo, pero en esta fase, el sistema aún no comprende el "qué" o el "quién".

## 3. Fase 2: El Gemelo Digital y los Hechos de Escena (Scene Engine)

El **Scene Engine** actúa como el traductor de estados. Su función es mantener un **Digital Twin** (Gemelo Digital) de cada cama, un modelo virtual que filtra el ruido sensorial para generar estados estables.

Para este proceso, utiliza el **SceneInterpreter** (lógica pura) y el **ClockSweeper** (gestión de tiempos). Los procesos críticos incluyen:

- **Umbrales de confianza:** Descarte automático de señales por debajo del mínimo aceptable.
- **Histéresis:** Requisito de estabilidad temporal para evitar el "flickering" o parpadeo de estados.
- **Vinculación de ocupantes:** El sistema utiliza el **CensusSnapshot** para unir la identidad del residente (`ResidentId`) con el sensor de la cama (`BedId`).
- **Categorización de Riesgo:** Antes de pasar al juicio, el sistema clasifica el hecho en un **PersonState** (ej. `AttemptingExit`) y lo asigna a un **RiskGroup** (`SAFE` vs. `AT_RISK`).

**Aplicación al caso "Caída de Residente":** El sistema detecta un `BedExit`. Tras superar el tiempo de gracia (dwell time) mediante el `ClockSweeper`, el motor concluye que el residente no ha vuelto y genera un hecho estable: "Residente fuera de la cama en estado de riesgo".

### Comparativa de Datos

|   |   |
|---|---|
|Observación Bruta (Entrada)|Hecho de Escena (Salida)|
|Movimiento detectado a las 03:00:05.|`SceneFact`: Resident out of bed (Dwell Exceeded).|
|Confianza: 0.82 (Ruido potencial).|**PersonState**: AttemptingExit|

## 4. Fase 3: Juicio Clínico (Sentinel Engine)

Si el motor anterior es el traductor, el **Sentinel** es el "juez". Este motor de dominio puro evalúa los hechos contra las **EffectiveRules** (políticas clínicas personalizadas).

**Conceptos clave del Sentinel:**

- **EpisodeLedger:** Permite la persistencia del riesgo. Si el residente se mueve entre camas, el historial del incidente le "sigue", manteniendo el contexto clínico.
- **FatigueBudget:** Controla la carga cognitiva del cuidador limitando el número de interrupciones permitidas por turno.
- **Incident vs. Occurrence:** El Sentinel distingue entre un **Incident** (alerta que requiere acción inmediata) y una **Occurrence** (datos contextuales que se guardan bajo el mismo "paraguas" para revisión posterior sin saturar al personal).

**Aplicación al caso "Caída de Residente":** El Sentinel recibe el hecho, consulta la política de riesgo de caídas del residente y abre un "Episodio de Riesgo".

### Ciclo de Vida de un Episodio

1. **Abrir:** Se inicia al coincidir un hecho con una regla de alerta (ej. `Incident`).
2. **Escalar:** Si el residente pasa de "sentado en el borde" a "en el suelo", la severidad aumenta.
3. **Eventos sombrilla:** Movimientos posteriores se registran como hechos relacionados sin disparar nuevas alarmas.
4. **Cerrar:** El episodio finaliza por una `ClosureCondition` (retorno a estado seguro o presencia del personal).

## 5. Fase 4: Entrega y Gestión de la Alerta (Harbor / Harbor)

El componente **Harbor** (Harbor) gestiona la entrega física de la notificación. Su objetivo es asegurar que la alerta llegue a través de los canales adecuados: **Tablet** (lado de cama), **Push** (móvil) y **Ward Board** (estación de enfermería).

**Aplicación al caso "Caída de Residente":** Se despacha una notificación **CRÍTICA**. El sistema monitorea si el cuidador ve el mensaje y cuánto tarda en responder, activando escalaciones automáticas si es necesario.

### Estados de la Alerta (Máquina de Estados - FSM)

|   |   |
|---|---|
|Estado|Disparador / Acción|
|**Created**|El Sentinel identifica un incidente clínico legítimo.|
|**Dispatched**|El Harbor envía la señal a través de los canales configurados.|
|**Seen**|El dispositivo confirma que la alerta se ha renderizado en pantalla.|
|**Acknowledged**|El cuidador interactúa físicamente con la notificación.|
|**Escalated**|El tiempo de respuesta expira sin confirmación humana.|
|**Resolved**|El residente vuelve a estar seguro o el sensor detecta presencia del staff.|

## 6. El Registro de la Verdad (The Hub)

Al final del camino, cada bit de información aterriza en el **Hub**, nuestro Sistema de Registro (SoR). Aquí se almacena el **DecisionRecord**.

**La Reproducibilidad como Pilar:** Cada decisión en mana-hive es "machine-reproducible". Esto es posible porque el `DecisionRecord` guarda tanto el **estímulo de entrada** como el **build fingerprint** (la versión exacta del motor y las reglas). Esto permite la "Moviola": reconstruir meses después exactamente por qué una alarma sonó o se suprimió.

## 7. Resumen Visual de la Trayectoria

Este cuadro resume el flujo completo desde el evento físico hasta la resolución clínica:

|   |   |   |   |
|---|---|---|---|
|Etapa / Subsistema|Evento de Entrada|Transformación Clave|Resultado de Salida|
|**Perception (ia-cell)**|Movimiento físico (03:00)|Traducción a señales digitales brutas|`Observation` (Observación v1)|
|**Scene Engine**|`Observation` + `CensusSnapshot`|Filtrado, Hysteresis y `RiskGroup`|`SceneFact` (Hecho de Escena)|
|**Sentinel Engine**|`SceneFact` + `EffectiveRules`|Juicio contra `FatigueBudget` y Ledger|`Incident` (Apertura de Episodio)|
|**Harbor / Harbor**|`Incident`|Gestión de FSM y ruteo multicanal|`AlarmEvent` (Notificación Activa)|