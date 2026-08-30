# ADR-005: Interface Naming Convention

**Estado:** Implementado  
**Fecha:** 2026-08-30  
**Decisor:** Equipo mana-hive

## Contexto

Interfaces usaban `handleX()` vs `onX()` — inconsistencia en el naming.

## Decisión

- **`onX()`** — para event handlers (reaccionamos a eventos)
- **`handleX()`** — para command processing (procesamos comandos)

En nuestro caso, los inputs son eventos del bus:
```kotlin
interface ObservationSink { fun onObservation(obs: Observation) }
interface PolicyChangeSink { fun onPolicyChange(change: PolicyChangeDetected) }
interface ProfileSink { fun onProfileChanged(profile: ResidentProfileDto) }
```

## Consecuencias

### Positivas
- ✅ Semántica clara — `onX()` indica "reacciono a esto"
- ✅ Consistencia — patrón uniformly aplicado
- ✅ Alineado con frameworks — `onClick()`, `onConnected()`

## Referencias

- Observer pattern: `onEvent()`
- Spring: `@EventListener`, `onApplicationEvent()`
