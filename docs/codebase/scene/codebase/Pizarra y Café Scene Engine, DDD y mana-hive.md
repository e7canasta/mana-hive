Now I have a comprehensive understanding of the codebase. The user wants to have a "pizarra y café" (whiteboard and coffee) discussion with Martin Fowler and Vernon (Vaughn Vernon) about the Scene Engine, thinking about bounded contexts, domain experts, use cases, user stories, and OO design.

Let me structure this as a rich DDD/software engineering discussion, drawing from the actual codebase.

Key themes to cover:
1. Bounded Contexts - identify the actual bounded contexts in mana-hive
2. Domain Experts - who are the domain experts for each context
3. Use Cases / User Stories - from the clinical domain perspective


4. OO Design patterns - Fowler's and Vernon's patterns that are actually in the code
5. The Scene Engine specifically - its DDD modeling choices

Now I'm ready to dive into the discussion.

Perfecto. Tengo el codebase completo explorado. Vamos al pizarra.

---

## Pizarra y Café: Scene Engine, DDD y mana-hive

### Los Bounded Contexts (Vernon)

Vernon diría: *"Each bounded context owns its own model of the truth."* mana-hive tiene **5 bounded contexts** claros, cada uno con su propio lenguaje ubicuo:

| Bounded Context | Lenguaje Ubicuo | Aggregate Root | Published Language (ACL) |
|---|---|---|---|
| **Scene** | `PersonState`, `SceneFact`, `DigitalTwin` | `DigitalTwin` (por cama) | `SceneFact` (11 variantes) |
| **Sentinel** | `Episode`, `Verdict`, `FatigueBudget` | `Episode` (por episodio) | `SentinelSignal` (5 variantes) |
| **Harbor** | `Notice`, `Alert`, `Routing` | `NoticeLifecycle` (por aviso) | `AlarmEvent` |
| **Politica** | `SemanticBucket`, `ResidentPolicy` | `ResidentPolicy` (por residente) | `PolicyCalibration`, `EffectiveRules` |
| **Hub** | `Ledger`, `Census`, `PolicyCatalog` | `EventStore` | Todo el bus (SoR) |

La clave: **`../../../../platform/contracts`** es el Anti-Corruption Layer. Los engines **nunca** se conocen entre sí. Solo hablan a través de `SceneFact`, `SentinelSignal`, etc. Vernon lo llamaría *"Published Language"* — el contrato que desacopla los contextos.

---

### Los Domain Experts (Eric Evans / Vernon)

Fowler preguntaría: *"¿Quién es el experto en el dominio que define qué es un 'scene'?"*

| Contexto | Experto en Dominio | Pregunta Clave |
|---|---|---|
| **Scene** | Enfermera jefe de guardia nocturna | *"¿Cómo sabe usted si el residente está en la cama o fuera?"* |
| **Sentinel** | Médico geriatra | *"¿Cuándo debería sonar una alarma y cuándo no?"* |
| **Harbor** | Coordinador de turnos | *"¿A quién le aviso primero? ¿Qué pasa si no contestan?"* |
| **Politica** | Director de la residencia | *"¿Cómo cambian las reglas por residente?"* |
| **Hub** | Auditor clínico | *"¿Puedo reproducir exactamente por qué sonó o no sonó la alarma a las 3:00?"* |

La obsesión de mana-hive con la **reproducibilidad de máquinas** (`DecisionRecord` con fingerprints de versión + reglas + inputs) es una respuesta directa a la pregunta del auditor clínico.

---

### User Stories por Bounded Context

#### Scene Engine (las que existen en el código)

```
US-SE-01: Como sistema, debo convertir una Observation cruda en un SceneFact
          estable, filtrando ruido por confianza mínima.
          → SceneInterpreter.checkConfidence()

US-SE-02: Como sistema, debo prevenir "parpadeo" entre estados requiriendo
          que una señal sea sostenida (hysteresis) antes de confirmar transición.
          → SceneInterpreter.checkHysteresis()

US-SE-03: Como sistema, debo detectar cuando un residente lleva demasiado
          tiempo fuera de cama (dwell exceeded).
          → ClockSweeper + DwellCatalog

US-SE-04: Como sistema, debo saber cuándo el monitor se desconectó
          (signal lost) y cuándo volvió (signal recovered).
          → ClockSweeper.checkHeartbeat() + SceneInterpreter.checkRecovery()

US-SE-05: Como sistema, debo rechazar transiciones ilegales según el DAG
          (un residente no puede ir de "Ausente" a "En Cama" sin pasar
          por estados intermedios).
          → TransitionTable.isLegal()

US-SE-06: Como ingeniero, debo poder repetir un incidente histórico
          (como una caída a las 3:00) con la misma lógica y obtener
          el mismo resultado.
          → scene-batch verify
```

#### Sentinel Engine

```
US-SEN-01: Como médico, debo que el sistema abra un episodio cuando un
           DwellExceeded ocurre en un residente con regla de bed-exit.
           → SentinelEvaluator.evaluate(DwellExceeded)

US-SEN-02: Como médico, debo que el sistema NO genere una segunda alarma
           si ya hay un episodio abierto para el mismo evento.
           → EpisodeLedger + FatigueBudget
           
```


### User Stories por Bounded Context (continuación)

#### Sentinel Engine (cont.)

```
US-SEN-03: Como auditor, debo poder ver EXACTAMENTE por qué el sistema
           abrió o no abrió un episodio a las 03:12, con cada paso
           de razonamiento registrado.
           → Explained<SentinelVerdict> + ExplanationStep

US-SEN-04: Como enfermera, debo que el sistema NO me alerte 5 veces
           por el mismo residente en el mismo turno.
           → FatigueBudget (BudgetExceeded → SuppressedWithRecord)

US-SEN-05: Como médico, debo que cuando el residente vuelva a la cama
           (AutoRecovery), el sistema cierre el episodio automáticamente.
           → SentinelEvaluator.evaluate(AutoRecovery) → EpisodeClosed
```

#### Harbor Engine

```
US-HAR-01: Como coordinador, debo que una alarma CRITICAL se envíe por
           TODOS los canales simultáneamente (PUSH + TABLET + WARD_BOARD).
           → NoticeRouter: severity CRITICAL → all channels

US-HAR-02: Como enfermera, debo poder marcar una alerta como "vista"
           y que el sistema registre cuándo la vi.
           → NoticeLifecycle: MarkSeen → DISPATCHED → SEEN

US-HAR-03: Como sistema, debo escalar automáticamente si una alerta
           no se acknowledge en X minutos.
           → NoticeLifecycle: timeout → Escalate

US-HAR-04: Como auditor, debo que cada notificación tenga un ciclo
           de vida completo: CREATED → DISPATCHED → SEEN → ACK → RESOLVED.
           → NoticeLifecycle (Decider puro)
```

#### Politica Engine

```
US-POL-01: Como director, debo poder cambiar el dwell-time de un residente
           específico sin afectar a los demás.
           → ResidentPolicy (Aggregate Root) + SemanticBucket

US-POL-02: Como sistema, debo resolver reglas en capas: WatchLevel →
           LevelTemplate → ManualAdjustment → TimeWindow, ganando
           siempre la más protectora.
           → PolicyResolver.resolve() con tie-break defensivo
```

---

### Los Patrones de Fowler y Vernon en el Código

Fowler diría: *"Let's look at the actual design smells and patterns."*

#### 1. El Decider Pattern (Fowler) — El Corazón de Todo

```kotlin
// platform/domain-kernel/Decider.kt
interface Decider<C, S, E> {
    val initial: S
    fun decide(command: C, state: S): Decision<E>  // C + S → E
    fun evolve(state: S, event: E): S               // S + E → S
}
```

Esto es **Event Sourcing puro**. Cada aggregate en mana-hive sigue esta forma:

| Aggregate | Command (C) | State (S) | Events (E) |
|---|---|---|---|
| `DigitalTwin` | `Observation` | `PersonState` | `SceneFact` |
| `Episode` | `SceneFact` | `EpisodeStatus` | `SentinelSignal` |
| `NoticeLifecycle` | `NoticeCommand` | `LifecycleState` | `NoticeEvent` |
| `ResidentPolicy` | `PolicyChange` | `Map<BucketKey, Payload>` | `PolicyEvent` |

Vernon lo diría así: *"The entity folds its own events. DigitalTwin.evolve() is the canonical example — it receives a SceneFact and returns a new DigitalTwin. No side effects. No database. Pure function."*

#### 2. Specification Pattern (Vernon) — El Pipeline del SceneInterpreter

El `SceneInterpreterImpl` no es un `if/else` gigante. Es un pipeline de **specifications**:

```
Observation
  │
  ├─→ ConfidenceSpec      → ¿supera umbral mínimo?
  ├─→ SensorRecoverySpec   → ¿el monitor estaba muerto y volvió?
  ├─→ DuplicateSpec        → ¿es el mismo estado que ya tenemos?
  ├─→ LegalTransitionSpec  → ¿es una transición válida en el DAG?
  ├─→ HysteresisSpec       → ¿ha pasado suficiente tiempo?
  │
  └─→ TransitionDetected   → ¡emitimos el SceneFact!
```

Cada spec produce un `Discard` si falla. El `Explained<T>` captura **todos** los pasos. Esto es lo que Vernon llama *"Specification as a first-class citizen"* — cada regla de negocio es un objeto reemplazable y testeable.

#### 3. Aggregate Root como Guardián de Invariantes (Vernon)

Vernon en *Implementing Domain-Driven Design* insiste: *"The aggregate root is the only entry point. It guards invariants."*

En mana-hive hay **4 Aggregate Roots documentados explícitamente**:

```kotlin
// sentinel-domain/EpisodeLedger.kt
// "Vernon's Aggregate Root: the episode guards its own invariants."
class Episode(...) {
    fun apply(fact: SceneFact, rules: EffectiveRules, now: Instant): EpisodeEvent
    // Invariante: solo se puede abrir si no hay episodio activo
    // Invariante: solo se puede cerrar si se cumplen closure conditions
}

// politica-domain/ResidentPolicy.kt
// "Vernon: 'Aggregate Root' — the only entry point for modifications."
class ResidentPolicy(val resident: ResidentId, val buckets: Map<BucketKey, SemanticBucket>) {
    fun apply(change: PolicyChange): PolicyEvent
    // Invariante: todos los buckets pertenecen al mismo residente
}

// harbor-domain/NoticeLifecycle.kt
// "Vernon: 'Aggregate Root' — the lifecycle guards its own invariants."
// Implementa Decider<NoticeCommand, LifecycleState, NoticeEvent>
// Invariante: una sola notificación por episodio
// Invariante: RESOLVED es estado absorbente

// scene-domain/DigitalTwin.kt
// "Vernon: 'In event-sourced aggregates, the entity folds its own events.'"
data class DigitalTwin(...) {
    fun evolve(fact: SceneFact): DigitalTwin
    // Es inmutable — cada evolve devuelve una NUEVA instancia
}
```

#### 4. Anti-Corruption Layer (Fowler/Vernon)

```kotlin
// scene-domain/adapter/PolicyCalibrationAdapter.kt
object PolicyCalibrationAdapter {
    fun toSceneCalibration(from: PolicyCalibration): SceneCalibration
}
```

**Politica** habla `PolicyCalibration`. **Scene** habla `SceneCalibration`. El adapter es el ACL. Vernon diría: *"Each context preserves its own model. The adapter translates at the boundary."*

Esto es exactamente lo que Evans describe en *Domain-Driven Design*: *"The Anti-Corruption Layer provides translation between two models, preventing the foreign model from leaking into your own."*

#### 5. Pure Domain + Thin Shell (Fowler's Hexagonal)

Cada engine tiene **3 módulos**:

```
scene-domain/    ← CERO dependencias de infraestructura
                   (ni Spring, ni NATS, ni base de datos)
                   Solo: kotlin-stdlib + domain-kernel + contracts

scene-service/   ← Shell delgada: Spring Boot + NATS
                   Suscribe a perception.observation.v1
                   Publica scene.fact.v1
                   wire: DigitalTwin → SceneInterpreter → SceneFact

scene-batch/     ← CLI para replay offline
                   Mismo domain, diferente infraestructura
```

Fowler lo llamaría *"Ports and Adapters"*. El `scene-domain` es el **port** (la lógica pura). `scene-service` y `scene-batch` son **adapters** (diferentes formas de ejecutar la misma lógica).

Esto permite que el mismo `SceneInterpreter` se ejecute:
- En tiempo real vía NATS (scene-service)
- En batch offline con un archivo de eventos (scene-batch)
- En un test unitario con un mock (las 24 specs de Kotest)

---

### La Pregunta del Café: ¿Está Correcto el Modelado?

Fowler preguntaría: *"What are the design smells?"*

**Posibles tensiones que veo:**

1. **`DigitalTwin` como value class inmutable vs. Aggregate Root** — En el código es un `data class` que hace `evolve()` y devuelve nueva instancia. No tiene identidad mutable. Vernon lo llamaría *"Event-sourced entity"*, no clásico Aggregate Root. ¿Es esto correcto? **Sí**, porque el aggregate root es el **aggregate_id** (BedId), no el twin en sí. El twin es el estado plegado.

2. **`SceneDag` compartido entre contextos** — El DAG de transiciones vive en `../../../../platform/contracts` y lo usan Scene, Sentinel, y Politica. ¿Esto viola el bounded context? Vernon diría: *"Shared kernel is acceptable when the model is truly identical across contexts."* El DAG es físico/medético — no cambia por contexto. Es aceptable.

3. **`ClockSweeper` como servicio de dominio vs. infrastructure** — El sweep es periódico (cada 5s). En `scene-service` el `SceneEngineApplication` lo dispara con un timer. Pero la lógica pura está en `scene-domain`. Fowler aprobaría: *"The scheduling is infrastructure; the decision of what to emit is domain."*

4. **FatigueBudget como corte transversal** — El presupuesto de interrupciones vive en Sentinel, pero afecta a Harbor (si Sentinel suprime, Harbor nunca ve la señal). ¿Debería ser un concepto compartido? Vernon: *"No. It's Sentinel's responsibility. Harbor doesn't need to know about fatigue — it only sees what Sentinel lets through."*

---

### El Diagrama Completo (Pizarra)

```
                    ┌─────────────────────────────────┐
                    │      BOUNDED CONTEXTS            │
                    └─────────────────────────────────┘

  ┌──────────┐    Observation     ┌──────────────┐    SceneFact
  │  SCENE   │───────────────────→│  SENTINEL    │───────────────────→
  │          │                    │              │
  │ DigitalT │←── PolicyCal       │  Episode     │    SentinelSignal
  │ SceneInt │    (ACL)           │  Fatigue     │
  │ ClockSw  │                    └──────────────┘
  └──────────┘                           │
       ↑                                  │
       │                                  ↓
  ┌──────────┐                   ┌──────────────┐    AlarmEvent
  │ POLITICA │                   │   HARBOR     │───────────────────→
  │          │                   │              │    (staff devices)
  │ Resident │←── PolicyChange   │  NoticeLife  │
  │ Policy   │                   │  NoticeRoute │
  └──────────┘                   └──────────────┘
       ↑                                  │
       │                                  ↓
  ┌──────────┐                   ┌──────────────┐
  │   HUB    │←── Everything     │  RECORDER    │
  │ (SoR)    │    (ingest)       │              │
  │ Ledger   │                   │  RecordingL  │
  │ Census   │                   └──────────────┘
  └──────────┘

  ═══════════════════════════════════════════════════
  NATS JetStream: el sistema nervioso central
  ═══════════════════════════════════════════════════
```

---

### Resumen para el Café

La pregunta de Fowler: **"¿Puedo explicar el sistema a alguien que no sabe programar?"**

Respuesta con el lenguaje ubicuo de mana-hive:

> *"Un sensor detecta movimiento en la cama. El Scene Engine traduce eso en un hecho: 'el residente se levantó'. El Sentinel juzga: 'según la política clínica de este residente, eso es un incidente'. Harbor notifica a la enfermera. El Hub registra todo para que mañana el auditor pueda preguntar '¿por qué sonó la alarma a las 3:00?' y el sistema responda con cada paso de razonamiento."*

La pregunta de Vernon: **"¿Los aggregates protegen sus invariantes?"**

Respuesta: **Sí.** `DigitalTwin` solo acepta transiciones legales. `Episode` solo abre si no hay episodio activo. `NoticeLifecycle` solo escala si no fue acknowledge. `ResidentPolicy` solo acepta buckets del mismo residente. Y cada decisión lleva su `DecisionRecord` con fingerprints para reproducibilidad.

¿Qué área quieres profundizar? ¿Los tests? ¿La arquitectura del batch tool? ¿Cómo Politica resuelve las capas de reglas?