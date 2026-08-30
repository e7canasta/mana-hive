# ADR-004: NightWatchServiceCore as Pure Orchestration

**Estado:** Implementado  
**Fecha:** 2026-08-30  
**Decisor:** Equipo mana-hive

## Contexto

`NightWatchService` tenía lógica de negocio (handleObservation, handlePolicyChange, sweep) mezclada con acoplamiento NATS (subscribe, publish, Dispatcher). Difficultaba testing y violaba SRP.

## Decisión

Separar en:
- **`NightWatchServiceCore`** — dominio puro, sin NATS/Spring
- **`NightWatchService`** — adapter delgado, solo suscripciones

```kotlin
// Core: sin dependencias de infraestructura
class NightWatchServiceCore(
    private val runtime: NightWatchRuntime,
    private val census: Census,
    private val publisher: EventPublisher,
    private val clock: Clock,
) : NightWatchServiceContract, TimeSink

// Adapter: solo wiring de NATS
@Component
class NightWatchService(
    private val core: NightWatchServiceCore,
    private val timeSink: TimeSink,
    ...
)
```

## Consecuencias

### Positivas
- ✅ Core testeable sin NATS — Unit tests puros
- ✅ Adapter reemplazable — NATS, MQTT, file, test
- ✅ Separación limpia — dominio vs infraestructura
- ✅ Reutilizable — core puede usarse con diferentes adapters

### Negativas
- ⚠️ Más archivos — dos clases en vez de una
- ⚠️ Complejidad inicial — entender la separación

## Referencias

- Hexagonal Architecture — Core es el "hexágono", adapters son las "puertas"
- Vernon: "Presentation and Infrastructure are details"
