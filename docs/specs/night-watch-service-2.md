Now I have the complete picture. Here is the full NightWatch application boot sequence and observation ingestion flow.

---

## NightWatch Boot Sequence and Observation Ingestion

### File Inventory (all absolute paths)

| Role | Path |
|------|------|
| **Spring Boot entry point** | `/home/visiona/workspace/mana-hive/engines/night-watch-runtime/src/main/kotlin/com/manahive/runtime/NightWatchApplication.kt` |
| **NATS service wiring / lifecycle** | `/home/visiona/workspace/mana-hive/engines/night-watch-runtime/src/main/kotlin/com/manahive/runtime/NightWatchService.kt` |
| **Per-bed runtime registry** | `/home/visiona/workspace/mana-hive/engines/night-watch-runtime/src/main/kotlin/com/manahive/runtime/NightWatchRuntime.kt` |
| **Per-resident 4-engine pipeline** | `/home/visiona/workspace/mana-hive/engines/night-watch-runtime/src/main/kotlin/com/manahive/runtime/ResidentRuntime.kt` |
| **Bed-to-resident map** | `/home/visiona/workspace/mana-hive/engines/night-watch-runtime/src/main/kotlin/com/manahive/runtime/Census.kt` |
| **Census loader from disk** | `/home/visiona/workspace/mana-hive/engines/night-watch-runtime/src/main/kotlin/com/manahive/runtime/CensusSeed.kt` |
| **Profile loader from disk (cold start)** | `/home/visiona/workspace/mana-hive/engines/night-watch-runtime/src/main/kotlin/com/manahive/runtime/ProfileSeed.kt` |
| **Profile-to-calibration bridge** | `/home/visiona/workspace/mana-hive/engines/night-watch-runtime/src/main/kotlin/com/manahive/runtime/ProfileCalibrator.kt` |
| **4-engine calibrations bundle** | `/home/visiona/workspace/mana-hive/engines/night-watch-runtime/src/main/kotlin/com/manahive/runtime/EngineCalibrations.kt` |
| **Service state machine** | `/home/visiona/workspace/mana-hive/engines/night-watch-runtime/src/main/kotlin/com/manahive/runtime/RuntimeStatus.kt` |
| **Health + REST status endpoint** | `/home/visiona/workspace/mana-hive/engines/night-watch-runtime/src/main/kotlin/com/manahive/runtime/RuntimeHealth.kt` |
| **NATS connection management** | `/home/visiona/workspace/mana-hive/platform/messaging/src/main/kotlin/com/manahive/messaging/BusConnector.kt` |
| **NATS event bridge** | `/home/visiona/workspace/mana-hive/platform/messaging/src/main/kotlin/com/manahive/messaging/BusEvents.kt` |
| **NATS subject taxonomy** | `/home/visiona/workspace/mana-hive/platform/messaging/src/main/kotlin/com/manahive/messaging/Subjects.kt` |
| **JetStream stream declarations** | `/home/visiona/workspace/mana-hive/platform/messaging/src/main/kotlin/com/manahive/messaging/NatsTopology.kt` |

---

### Phase 1: Spring Boot Context Assembly

**File:** `NightWatchApplication.kt`

```kotlin
@SpringBootApplication
@EnableScheduling
class NightWatchApplication {

    @Bean fun objectMapper() = NatsObjectMapper.mapper

    @Bean
    fun census(@Value("\${manahive.profiles.dir:profiles}") profilesDir: String): Census {
        val census = Census()
        CensusSeed(census, java.io.File(profilesDir, "census.json")).load()  // bed->resident map from disk
        return census
    }

    @Bean fun runtime(): NightWatchRuntime = NightWatchRuntime()   // empty registry
    @Bean fun runtimeStatus(): RuntimeStatusHolder = RuntimeStatusHolder()
    @Bean fun busEvents(): BusEvents = BusEvents()

    @Bean
    fun busConnector(
        @Value("\${nats.url:nats://localhost:4222}") url: String,
        events: BusEvents,
    ): BusConnector = BusConnector(url, events)   // starts async connect
}

fun main(args: Array<String>) {
    runApplication<NightWatchApplication>(*args)
}
```

**What happens in order during context startup:**

1. **`Census` bean** -- `CensusSeed` reads `census.json` and populates the `Census` (a `ConcurrentHashMap<BedId, CensusEntry>` mapping each bed to its resident/night/monitor). This is the bed-to-resident routing table.
2. **`NightWatchRuntime` bean** -- created empty (no resident runtimes yet; they are registered later when policies arrive).
3. **`BusEvents` bean** -- the volatile event bridge between the NATS thread and the Spring context.
4. **`BusConnector` bean** -- its `@PostConstruct connect()` fires an async NATS connection attempt (non-blocking). On CONNECTED or RECONNECTED it calls `events.fireConnected()`.

---

### Phase 2: Service Initialization (`NightWatchService.start()`)

**File:** `NightWatchService.kt`, lines 70-89

```kotlin
@Component
class NightWatchService(
    private val runtime: NightWatchRuntime,
    private val census: Census,
    private val status: RuntimeStatusHolder,
    private val events: BusEvents,
    ...
) {
    private val calibrator = ProfileCalibrator(runtime, census)

    @PostConstruct
    fun start() {
        // 1. Cold start: load profiles from disk BEFORE waiting for bus
        ProfileSeed(calibrator, File(profilesDir)).load()

        // 2. Transition state
        status.transition(RuntimeState.WAITING_FOR_BUS, "esperando al bus")

        // 3. Hook into bus lifecycle
        events.onConnected { onBusAvailable() }
        events.onLost { onBusLost(it) }

        // 4. If bus is already connected (race), subscribe immediately
        onBusAvailable()
    }
}
```

**Key design insight (from comments):** Cold start from disk happens **before** the bus arrives. This means a resident with a profile on disk is monitored even if NATS is still down. Without this, after a restart the system would be blind until someone manually triggers a policy change.

**`ProfileSeed` (disk cold start):** reads every `.json` file in the profiles directory (excluding `census.json`), deserializes each as `ResidentProfileDto`, and calls `calibrator.accept(dto)`. This triggers the full path: validate -> project profile for the current time window -> produce `EngineCalibrations` -> `runtime.register()`.

---

### Phase 3: Bus Connection and NATS Subscription

**File:** `BusConnector.kt`

```kotlin
public class BusConnector(url: String, events: BusEvents) {
    @PostConstruct
    public fun connect() {
        NatsConfig.connectAsync(url) { conn, type ->
            events.connection = conn
            when (type) {
                CONNECTED, RECONNECTED -> events.fireConnected()
                DISCONNECTED, CLOSED   -> events.fireLost(type.name)
                else -> Unit
            }
        }
    }
}
```

**File:** `NightWatchService.kt`, lines 96-118

When the bus becomes available, `onBusAvailable()` runs:

```kotlin
@Synchronized
fun onBusAvailable() {
    val connection = events.connection
    if (connection == null || connection.status != Connection.Status.CONNECTED) {
        status.transition(RuntimeState.WAITING_FOR_BUS, "bus no disponible")
        return
    }
    try {
        // 1. Declare JetStream streams (idempotent)
        NatsTopology(connection.jetStreamManagement()).ensureAll()

        // 2. Get JetStream handle for publishing
        jetStream = connection.jetStream()

        // 3. Close stale dispatchers from a previous connection
        dispatchers.forEach { d -> runCatching { connection.closeDispatcher(d) } }
        dispatchers.clear()

        // 4. Subscribe to three NATS subjects
        subscribeToObservations(connection)
        subscribeToPolicyChanges(connection)
        subscribeToProfiles(connection)

        status.transition(RuntimeState.RUNNING, "consumiendo del bus")
    } catch (e: Exception) {
        status.transition(RuntimeState.DEGRADED, "no se pudo suscribir: ${e.message}")
    }
}
```

`NatsTopology.ensureAll()` declares seven JetStream streams (PERCEPTION, SCENE, SENTINEL, ALARM, POLICY, RECORDER, EVIDENCE) -- all with 7-day limits-based retention.

**Three NATS subscriptions are created:**

| Subscription | NATS Subject | Handler |
|---|---|---|
| **Observations** | `perception.observation.v1.>` (wildcard) | `handleObservation()` |
| **Policy changes** | `hub.policy.change.v1` | `handlePolicyChange()` |
| **Profile updates** | `hub.policy.profile.v1` | `calibrator.accept()` |

---

### Phase 4: Observation Ingestion -- NATS to Processing

This is the critical path. Each incoming NATS message flows through this chain:

#### Step 4a: NATS message arrives

**File:** `NightWatchService.kt`, lines 156-169

```kotlin
private fun subscribeToObservations(connection: Connection) {
    val dispatcher = connection.createDispatcher { msg ->
        val envelope = mapper.readValue<EventEnvelope>(String(msg.data))
        val obs = mapper.readValue<Observation>(envelope.payloadJson)
        handleObservation(obs)
    }
    dispatcher.subscribe(Subjects.PERCEPTION_WILDCARD)  // "perception.observation.v1.>"
}
```

#### Step 4b: Bed-to-resident routing via Census

**File:** `NightWatchService.kt`, lines 171-185

```kotlin
private fun handleObservation(obs: Observation) {
    val entry = census.lookup(obs.bed)           // BedId -> CensusEntry (resident, night, monitor)
    if (entry == null) {
        log.debug("No census entry for bed {}, ignoring", obs.bed.value)
        return
    }
    val out = runtime.onObservation(entry.resident, obs)   // route to correct resident
    publish(obs.bed, out)                                    // emit results to NATS
}
```

The observation arrives keyed by **bed** (`perception.observation.v1.<bed>`), but the runtime is keyed by **resident**. The `Census` is the bridge that maps one to the other.

#### Step 4c: Per-resident locking and dispatch

**File:** `NightWatchRuntime.kt`, lines 55-62

```kotlin
fun onObservation(residentId: ResidentId, obs: Observation): Outbound {
    val rt = runtimes[residentId]
        ?: error("No runtime registered for resident ${residentId.value}")
    synchronized(rt) {                   // per-resident lock; different residents run in parallel
        return rt.onObservation(obs)
    }
}
```

#### Step 4d: The four-engine pipeline

**File:** `ResidentRuntime.kt`, lines 116-166

```kotlin
fun onObservation(obs: Observation): Outbound {
    // Deduplicate: discard observations older than the last one applied
    val last = lastObservedAt
    if (last != null && obs.observedAt.isBefore(last)) return Outbound.empty()
    lastObservedAt = obs.observedAt

    val now = obs.observedAt

    // Stage 1: SCENE -- interpret raw observation into scene facts
    val sceneResult = sceneInterpreter.interpret(twin, obs, now)
    twin = sceneResult.value.twin           // update the DigitalTwin
    val sceneFacts = sceneResult.value.facts

    // Stage 2: SENTINEL -- evaluate scene facts against rules, produce signals
    val signals = mutableListOf<SentinelSignal>()
    for (fact in sceneFacts) {
        val result = sentinel.evaluate(fact, episodes, fact.at)
        episodes = result.value.episodes
        signals.addAll(result.value.signals)
    }

    // Stage 3: HARBOR -- decide dispatch/notice commands for each signal
    val commands = mutableListOf<NoticeFor>()
    for (signal in signals) {
        val result = harborEngine.evaluate(signal, harborState, signal.at)
        harborState = result.value.state
        result.value.commands.forEach { commands += NoticeFor(signal, it) }
    }

    // Stage 4: RECORDER -- decide recording commands for scene facts and signals
    val recorderCommands = mutableListOf<RecordingCommand>()
    for (fact in sceneFacts) {
        val result = recorderEngine.evaluate(SceneEventTrigger(fact, bed, fact.at), recordingLedger, fact.at)
        recordingLedger = result.value.ledger
        recorderCommands.addAll(result.value.commands)
    }
    for (signal in signals) {
        val result = recorderEngine.evaluate(SentinelSignalTrigger(signal, bed, signal.at), recordingLedger, signal.at)
        recordingLedger = result.value.ledger
        recorderCommands.addAll(result.value.commands)
    }

    return Outbound(sceneFacts, signals, commands, recorderCommands)
}
```

#### Step 4e: Publishing outbound events back to NATS

**File:** `NightWatchService.kt`, lines 211-244

```kotlin
private fun publish(bed: BedId, out: Outbound) {
    // Scene events -> "scene.fact.v1.<bed>"
    for (fact in out.sceneFacts) {
        emit(Subjects.sceneEvent(bed), "SceneEvent", fact.at, SceneEventSerializer.toJson(fact))
    }
    // Sentinel signals -> "sentinel.signal.v1.<bed>" (with real JetStream seq for tracing)
    val seqOf = mutableMapOf<SentinelSignal, EventRef>()
    for (signal in out.signals) {
        val ref = emit(Subjects.sentinelSignal(bed), "SentinelSignal", signal.at,
            SentinelSignalSerializer.toJson(signal))
        if (ref != null) seqOf[signal] = ref
    }
    // Alarm events -> "alarm.event.v1.<alert>" (links signal to episode for traceability)
    for ((signal, command) in out.harborCommands.map { it.signal to it.command }) {
        val event = toAlarmEvent(signal, command, seqOf[signal]) ?: continue
        emit(Subjects.alarmEvent(event.alert), "AlarmEvent", event.at, mapper.writeValueAsString(event))
    }
    // Recording commands -> "recorder.command.v1.<bed>"
    for (command in out.recorderCommands) {
        emit(Subjects.recordingCommand(bed), "RecordingCommand", Instant.now(), mapper.writeValueAsString(command))
    }
}
```

---

### Phase 5: Periodic Sweep (Timer-Driven Observations)

**File:** `NightWatchService.kt`, lines 135-154

```kotlin
@Scheduled(fixedRate = 30_000)
fun sweep() {
    calibrator.reprojectOnWindowEdge()      // re-calibrate if time window changed (e.g., 22:00 night rules)
    if (runtime.size == 0) return
    val now = Instant.now()
    val results = runtime.tickAll(now)      // tick every resident under its own lock
    for ((residentId, out) in results) {
        runtime.get(residentId)?.let { publish(it.bed, out) }
    }
}
```

The sweep runs every 30 seconds and calls `ResidentRuntime.onTick(now)`, which runs the **same four-engine pipeline** but driven by wall-clock time (for dwell detection, signal-lost checks, come-back detection) rather than by an external observation. This is how the system detects "no observation for X minutes" even when a sensor goes silent.

---

### Complete Lifecycle Summary

```
STARTUP
  |
  v
[Spring Context assembles beans]
  |-- Census loaded from disk (census.json)
  |-- NightWatchRuntime created empty
  |-- BusEvents created
  |-- BusConnector kicks off async NATS connect
  |
  v
[NightWatchService.start() -- @PostConstruct]
  |-- ProfileSeed.load() -- reads resident profiles from disk
  |   |-- For each profile: ProfileCalibrator.accept()
  |       |-- Validate -> Project for current time window
  |       |-- EngineCalibrations.from(projected)
  |       |-- runtime.register(resident, bed, night, monitor, calibrations)
  |       |   --> creates ResidentRuntime with 4 engines
  |
  |-- Hook busEvents.onConnected / onLost
  |-- onBusAvailable() (optimistic: bus may already be up)
  |
  v
[BUS AVAILABLE -- onBusAvailable()]
  |-- NatsTopology.ensureAll()  (declare 7 JetStream streams)
  |-- Subscribe to "perception.observation.v1.>"  (wildcard, all beds)
  |-- Subscribe to "hub.policy.change.v1"         (policy changes)
  |-- Subscribe to "hub.policy.profile.v1"        (profile updates)
  |-- Status -> RUNNING
  |
  v
[OBSERVATION ARRIVES on NATS]
  |
  |-- Dispatcher callback:
  |     Deserialize EventEnvelope -> Observation
  |
  |-- handleObservation(obs):
  |     census.lookup(obs.bed) -> CensusEntry (resident)
  |     runtime.onObservation(resident, obs)
  |       |
  |       v
  |     [ResidentRuntime.onObservation]
  |       |-- Deduplicate (discard if older than last applied)
  |       |-- Stage 1: SceneInterpreter.interpret(twin, obs) -> scene facts
  |       |-- Stage 2: SentinelEvaluator.evaluate(facts)    -> signals
  |       |-- Stage 3: HarborEngine.evaluate(signals)       -> notice commands
  |       |-- Stage 4: RecorderEngine.evaluate(facts/signals) -> recording commands
  |       |=> Outbound(sceneFacts, signals, harborCommands, recorderCommands)
  |
  |-- publish(bed, out):
  |     -> scene.fact.v1.<bed>
  |     -> sentinel.signal.v1.<bed>
  |     -> alarm.event.v1.<alert>
  |     -> recorder.command.v1.<bed>
  |
  v
[EVERY 30s -- @Scheduled sweep]
  |-- calibrator.reprojectOnWindowEdge()  (time-window rollover)
  |-- runtime.tickAll(now)                (dwell, signal-lost, come-back)
  |     same 4-engine pipeline, driven by wall clock
  |-- publish results to NATS
```

### Key Architectural Decisions

1. **No bus between engines.** `ResidentRuntime` composes all four engines as direct function calls in a single JVM. No serialization, no deserialization between Scene -> Sentinel -> Harbor -> Recorder.

2. **Observations arrive by bed; policies arrive by resident.** The `Census` is the sole bridge. A policy for a resident that has no census entry is logged as an error and rejected.

3. **Bus-independent cold start.** Profiles and census are loaded from disk before the bus connects. A resident with a profile on disk is monitored immediately, even if NATS is still down.

4. **Re-subscription on reconnect.** NATS subscriptions do not survive a connection drop. `BusEvents.fireConnected()` triggers `onBusAvailable()` which tears down old dispatchers and creates fresh ones.

5. **Per-resident locking.** `synchronized(rt)` in `NightWatchRuntime` means different residents process observations in parallel while the same resident's observations and ticks are serialized. With 1-4 residents this is contention-free.

6. **Out-of-order protection.** `ResidentRuntime.lastObservedAt` rejects observations older than the most recently applied one, preventing durable-delivery replays from corrupting the DigitalTwin's clock.