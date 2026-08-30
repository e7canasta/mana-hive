# ADR-002: Event Publisher Interface

**Estado:** Implementado  
**Fecha:** 2026-08-30  
**Decisor:** Equipo mana-hive

## Contexto

NightWatchService necesitaba publicar eventos (SceneEvent, SentinelSignal, etc.) al bus NATS. El acoplamiento directo a `JetStream.publish()` impedía testing sin NATS.

## Decisión

Crear interfaz `EventPublisher` con implementaciones intercambiables:

```kotlin
interface EventPublisher {
    fun publishSceneEvent(bed: BedId, event: SceneEvent)
    fun publishSentinelSignal(bed: BedId, signal: SentinelSignal)
    fun publishNoticeCommand(bed: BedId, signal: SentinelSignal, command: NoticeCommand)
    fun publishRecordingCommand(bed: BedId, command: RecordingCommand)
}
```

### Implementaciones

| Implementación | Uso |
|----------------|-----|
| `NatsEventPublisher` | Producción — publica a JetStream |
| `FileEventWriter` | Testing — escribe .out + events.jsonl |
| `CompositePublisher` | Fan-out — publica a múltiples destinos |

## Consecuencias

### Positivas
- ✅ Core puro sin I/O — NightWatchServiceCore no sabe de NATS
- ✅ Testable — FileEventWriter captura eventos para verificación
- ✅ Flexible — CompositePublisher permite NATS + file simultáneamente
- ✅ Separación limpia — adapter pattern correcto

### Negativas
- ⚠️ Overhead mínimo — una capa de abstracción más
- ⚠️ Complejidad — más clases que mantener

## Ejemplo de Uso

```kotlin
// Testing
val writer = FileEventWriter(outputDir, startTime)
val core = NightWatchServiceCore(runtime, census, writer, clock)
core.onObservation(obs)
writer.flush() // verificar .out

// Producción
val natsPublisher = NatsEventPublisher(busEvents)
val core = NightWatchServiceCore(runtime, census, natsPublisher, clock)
```

## Referencias

- Martin Fowler: "Ports and Adapters" pattern
- Hexagonal Architecture — EventPublisher es un "port" de salida
