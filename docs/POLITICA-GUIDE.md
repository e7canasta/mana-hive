# Guía de Uso — mana-hive Politica Engine

## Para el Director Médico

### ¿Qué es esto?

Es el sistema que traduce lo que usted configura en el Hub a reglas que cada engine entiende. Usted habla en idioma de monitoreo, el sistema traduce a configuración técnica.

### El Diagrama de Estados (DAG)

Piense en el residente como un punto que se mueve entre estados:

```
                        ┌─────────┐
                        │ UNKNOWN │
                        └────┬────┘
                             │
                             ▼
              ┌──────────────────────────────┐
              │           LYING              │
              │      (acostado en cama)      │
              └──────┬───────────────┬───────┘
                     │               │
            ┌────────▼─────┐   ┌─────▼────────┐
            │  SITTING_    │   │  BED_EDGE    │
            │  IN_BED      │   │ (borde cama) │
            │ (sentado)    │   └──────┬───────┘
            └──────┬───────┘          │
                   │                  │
                   ▼                  ▼
              ┌──────────────────────────────┐
              │          STANDING            │
              │        (parado)              │
              └──────┬───────────────┬───────┘
                     │               │
            ┌────────▼─────┐   ┌─────▼────────┐
            │  IN_BATHROOM │   │   IN_ROOM    │
            │  (baño)      │   │ (habitación) │
            └──────────────┘   └──────────────┘
```

### ¿Qué configura usted en cada estado?

En cada nodo del DAG, usted dice:

> "Si el residente se queda en este estado más de X minutos, avísenme"

Ejemplo:
- **SITTING_IN_BED**: "Si se sienta más de 15 minutos, avísenme"
- **IN_BATHROOM**: "Si está en el baño más de 10 minutos, avísenme"
- **STANDING**: "Si está parado más de 10 minutos, avísenme"

### ¿Qué configura en las transiciones?

En las aristas del DAG, usted dice:

> "Cuando el residente pasa de un estado a otro, haga X"

Ejemplo:
- **LYING → STANDING**: "Si se levanta de golpe, grabe 2 minutos antes y 5 después"
- **Staff entra**: "Cuando entre staff, cierre la alerta"

---

## Para el Ingeniero

### DSL para el Catálogo (reglas maestras)

```kotlin
val catalog = buildDagCatalog {
    version("2.1.0")

    resident {
        sitting {
            warningAfter(Duration.ofMinutes(30))   // Warning a los 30 min
            alertAfter(Duration.ofMinutes(45))     // Alerta a los 45 min
            severity(Severity.WARNING)
            closure(ClosureCondition.SAFE_ONLY)
        }
        bathroom {
            warningAfter(Duration.ofMinutes(20))
            alertAfter(Duration.ofMinutes(30))
            severity(Severity.WARNING)
            closure(ClosureCondition.SAFE_ONLY)
        }
    }

    room {
        staffEnters {
            closeEpisode()  // "Cuando staff entra, cierra alerta"
        }
    }

    transitions {
        from(StateKind.LYING) {
            to(StateKind.STANDING) {
                hysteresis(Duration.ofMillis(2000))
                record(before = Duration.ofMinutes(2), after = Duration.ofMinutes(5))
            }
        }
    }
}
```

### DSL para el Perfil del Residente

```kotlin
val joseProfile = buildResidentProfile("jose") {
    risk(RiskLevel.HIGH)
    mobility(MobilityAid.NONE)
    template("standard")

    resident {
        sitting {
            alertAfter(Duration.ofMinutes(15))  // Override: 15 min en vez de 45
        }
        bathroom {
            alertAfter(Duration.ofMinutes(10))  // Override: 10 min en vez de 30
        }
    }

    transitions {
        lyingToStanding {
            hysteresis(Duration.ofMillis(1000))  // Override: 1s en vez de 2s
        }
    }
}
```

### Cómo funciona la resolución

```
Catálogo (reglas base)
    +
Perfil del residente (overrides)
    ↓
PolicyResolver.resolve()
    ↓
PolicyCalibration (lo que cada engine necesita)
    ↓
┌─────────────────────────────────────────────────────────┐
│  Scene Engine:                                          │
│    - Hysteresis: LYING→STANDING = 1000ms              │
│    - Dwell: SITTING_IN_BED warning=15min, exceeded=30min│
│    - Confidence: SITTING_IN_BED >= 0.90                │
├─────────────────────────────────────────────────────────┤
│  Sentinel:                                              │
│    - Rule: SITTING_IN_BED → WARNING, STAFF_OR_SAFE    │
│    - Rule: IN_BATHROOM → WARNING, SAFE_ONLY            │
├─────────────────────────────────────────────────────────┤
│  Harbor:                                                │
│    - Budget: 5 warnings por turno                      │
│    - Channels: PUSH + TABLET                           │
│    - Escalation: 30min                                 │
│  > **Objetivo, no implementado.** Harbor devuelve        │
│  > emptyMap() para channels y escalationTimeouts.       │
├─────────────────────────────────────────────────────────┤
│  Recorder:                                              │
│    - Trigger: LYING→STANDING → grabar 2min+5min       │
│    - Trigger: IN_BATHROOM dwell → grabar 3min+10min   │
│  > **Objetivo, no implementado.** Recorder devuelve      │
│  > 0 transition windows.                                │
└─────────────────────────────────────────────────────────┘
```

### Templates disponibles

| Template | Descripción | Cuándo usar |
|----------|-------------|-------------|
| `standard` | Defaults del catálogo | Residentes sin riesgo especial |
| `night-wandering` | Detección más rápida | Residentes que se levantan de noche |
| `fall-risk` | Alertas tempranas | Residentes con historial de caídas |
| `low-mobility` | Tiempos extendidos | Residentes con movilidad reducida |

### Closure Conditions

| Condición | Cuándo cierra el episodio |
|-----------|---------------------------|
| `SAFE_ONLY` | Cuando el residente vuelve a un estado seguro |
| `STAFF_AND_SAFE` | Cuando hay staff Y el residente está seguro |
| `STAFF_OR_SAFE` | Cuando hay staff O el residente está seguro |

### Escenarios E2E (Blueprint)

| # | Escenario | Qué prueba |
|---|-----------|------------|
| 1 | José se sienta | Pipeline completa: Scene→Sentinel |
| 2 | José va al baño y tarda | Dwell exceeded |
| 3 | José se sienta 3 veces | Come-back exceeded |
| 4 | Camina al baño sin sitting | Transiciones sin regla |
| 5 | LYING→STANDING | **Objetivo:** Recorder graba |
| 6 | Staff asiste | **Objetivo:** Episodio cierra con STAFF_OR_SAFE |
| 7 | 20:00 Staff deja solo | **Objetivo:** Ciclo completo staff |
| 8 | 08:00 Staff se lleva | **Objetivo:** Habitación vacía |

### Comandos útiles

```bash
# Correr el blueprint E2E
./gradlew :blueprints:jose-301-e2e-pipeline:run

# Correr tests del Politica Engine
./gradlew :engines:politica-engine:politica-domain:test

# Correr tests del Sentinel
./gradlew :engines:sentinel:sentinel-domain:test

# Correr tests del Scene Engine
./gradlew :engines:scene-engine:scene-domain:test
```

### Archivos clave

| Archivo | Contenido |
|---------|-----------|
| `DagDsl.kt` | DSL para catálogo y perfil DAG-centric |
| `ProductionDagCatalog.kt` | Catálogo maestro con templates (en fuentes de test) |
| `PolicyResolver.kt` | Traduce DAG → calibraciones |
| `Main.kt` (blueprint) | 8 escenarios E2E |
| `SentinelEvaluatorImpl.kt` | Lógica de episodios |
| `EpisodeLedger.kt` | Event sourcing de episodios |
| `SceneInterpreterImpl.kt` | FSM de estados |

### Gaps cerrados

- ✅ `PolicyService` lee capas event-sourced, resuelve con `catalogFor(nivel)` (SPEC-02, SPEC-06)
- ✅ Catálogo conectado al PoliticaApplication via DAG (SPEC-03)
- ✅ API REST para escritura de política (SPEC-06)

### Próximos pasos

1. ~~Conectar `PolicyService` al event-sourced history~~ ✅ (SPEC-06)
2. ~~Crear API REST para templates~~ ✅ (SPEC-06)
3. Agregar business language assertions a Sentinel BDD
4. Agregar tests E2E de catálogo → engines
