# ADR-003: Clock Injection for Time Control

**Estado:** Implementado  
**Fecha:** 2026-08-30  
**Decisor:** Equipo mana-hive

## Contexto

El sweep del runtime usaba `Instant.now()` — imposible de testear con tiempo controlado. Los tests requerían avanzar el tiempo para verificar dwell/comeback thresholds.

## Decisión

1. **Clock interface** inyectada en `NightWatchRuntime` y `NightWatchServiceCore`
2. **ManualClock** para tests — control total del tiempo
3. **TimeSink interface** para control remoto vía NATS
4. **Misma instancia** compartida entre runtime y core

```kotlin
// Misma instancia para ambos
val clock = ManualClock(START)
val runtime = NightWatchRuntime(clock)
val core = NightWatchServiceCore(runtime, census, publisher, clock)

// Cuando avanzás el tiempo, ambos lo ven
clock.advance(Duration.ofMinutes(12))
```

## Consecuencias

### Positivas
- ✅ Time travel end-to-end — control total del tiempo
- ✅ Tests determinísticos — misma entrada, mismo resultado
- ✅ Control remoto vía NATS — `test.time.v1` subject
- ✅ Sin casts — ManualClock es explícito en tests

### Negativas
- ⚠️ Complejidad — Clock + ManualClock + TimeSink
- ⚠️ Riesgo de uso incorrecto — SystemClock en tests (no avanza)

## Subjects NATS para Time Control

```json
{ "action": "useManual", "startAt": "2024-01-15T22:00:00Z" }
{ "action": "advance", "duration": "PT12M" }
{ "action": "setTo", "instant": "2024-01-15T23:00:00Z" }
{ "action": "useSystem" }
{ "action": "sweep" }
```

## Referencias

- Martin Fowler: "Clock Callback" pattern
- Similar a `java.time.Clock` pero con control manual
