# Scene Engine — Sprint 3: 13 Estados + Dwell por Residente

**Fecha:** 2026-08-22
**Estado:** 🚀 En progreso — SE-17 completado, DSL refactorizado
**Build:** `./gradlew :engines:politica-engine:politica-domain:test :engines:scene-engine:scene-domain:test` → BUILD SUCCESSFUL
**Tests:** 86+ passing

---

## 1. Sprint 2 completado ✅

| Historia | Estado |
|----------|--------|
| PE-1 · PolicyChangeProcessor | ✅ |
| PE-2 · PolicyResolver | ✅ |
| PE-3 · CalibrationProvider | ✅ |
| SE-14 · SceneCalibration received | ✅ |
| SE-15 · DigitalTwin with calibration | ✅ |
| SE-16 · SceneInterpreter per resident | ✅ |

---

## 2. Sprint 3: 13 estados + dwell por residente

### El cambio

Sprint 2 puso la base: cada residente tiene su SceneCalibration. Sprint 3 **expande los estados** de 5 a 13 y **conecta el DwellCatalog por residente**.

### Estados actuales (Sprint 2)

```
Lying, BedEdge, Standing, Absent, Unknown
```

### Estados nuevos (Sprint 3)

```
EN CAMA (in_bed)
├── Lying              → Acostada
├── SittingInBed       → Incorporada en la cama
├── AttemptingExit     → "Gusanito" en el borde, intentando salir (alto riesgo de caida)
└── BedEdge            → Sentada al borde de la cama

FUERA DE CAMA (out_of_bed)
├── Standing           → De pie
├── InBathroom         → En el baño
├── InRoom             → En la habitación
├── InHallway          → En el pasillo
└── Outdoor            → Afuera

MUEBLES
├── InChair            → En la silla
└── InWheelchair       → En la silla de ruedas

UBICACION DESCONOCIDA
├── Absent             → Fuera de habitacion (ubicacion no determinada)

DESCONOCIDO
└── Unknown            → No se sabe
```

### Decisiones clinicas

- **AttemptingExit** = "gusanito" — movimiento en el borde de la cama intentando levantarse, brazos/cara al borde. Alto riesgo de caida.
- **Absent** se mantiene como 12mo estado — mapea desde `OUT_OF_ROOM` de percepcion (ubicacion no determinada, NO es "fuera de la instalacion").
- **BedEdge** se mantiene sin renombrar (decidido: "SittingInEdge" no es mejor).
- **Modelo permisivo** — se permiten saltos de pasos intermedios (SittingInBed→Standing, AttemptingExit→Standing, InBathroom→InHallway).

---

## 3. Historias Sprint 3

### SE-17 · PersonState 13 estados — ✅ COMPLETADO

**Tamano M** → Resuelto en ~1 hora.

**Archivos modificados:**
- `PersonState.kt` — 13 data objects + StateKind enum (13 valores) + `.kind` extension
- `Observation.kt` — 14 ObservationKinds (agregados SITTING_IN_BED, ATTEMPTING_EXIT, IN_BATHROOM, IN_ROOM, IN_HALLWAY, OUTDOOR, IN_CHAIR, IN_WHEELCHAIR)
- `ObservationKindMapping.kt` — ACL para los 14 kinds
- `TransitionTable.kt` — RELEASE_2 con ~48 transiciones (modelo permisivo)
- `DwellCatalogDsl.kt` — 13 constructores de estado
- `CalibrationDsl.kt` — 13 constructores de estado, default RELEASE_2
- `SceneInterpreterDsl.kt` — mapeo completo StateKind → PersonState
- `SceneTestDsl.kt` — mapeo completo en helpers de prueba
- `PolicyCalibrationAdapter.kt` — defaults a RELEASE_2
- `PersonStateElevenSpec.kt` — 12 escenarios de prueba

**Tests:** 12 nuevos + todos los existentes pasando.

### SE-18 · DwellCatalog por residente — Tamano M

**Tarjeta:** como equipo, queremos que ClockSweeper use el DwellCatalog del residente (via DigitalTwin.calibration), para que cada residente tenga sus propios umbrales de dwell.

**Conversacion (Vernon):** ClockSweeper es un Engine. Recibe twins y thresholds. Los thresholds ahora vienen de la calibración del twin.

**Confirmacion (BDD):**

```kotlin
class DwellCatalogPerResidentSpec : BehaviorSpec({
    Given("dos residentes con diferentes umbrales de dwell") {
        val mariaCalibration = calibration {
            dwell {
                STANDING warning 3.minutes exceeded 5.minutes
            }
        }
        val joseCalibration = calibration {
            dwell {
                STANDING warning 2.minutes exceeded 3.minutes
            }
        }

        When("ambos llevan 4 minutos de pie") {
            Then("María recibe warning (threshold 3 min)") {
                // DwellWarning emitted for María
            }

            Then("José NO recibe exceeded (threshold 3 min, 4 min > 3 min)") {
                // DwellExceeded emitted for José
            }
        }
    }
})
```

### SE-19 · TransitionTable 13 estados — Tamano M

**Tarjeta:** como equipo, queremos que TransitionTable soporte las 13 transiciones del catálogo, para que el Scene Engine pueda modelar todos los cambios de estado posibles.

**Conversacion (Vernon):** La tabla de transiciones es TOTAL. Cada par (from, to) tiene una regla. Agregar estados significa agregar filas.

**Confirmacion (BDD):**

```kotlin
class TransitionTableThirteenSpec : BehaviorSpec({
    Given("una tabla con transiciones de 13 estados") {
        val table = transitionTable {
            from(LYING) {
                to(BED_EDGE) after 1500.ms
                to(SITTING_IN_BED) after 1500.ms
                to(ATTEMPTING_EXIT) after 1500.ms
            }
            from(ATTEMPTING_EXIT) {
                to(BED_EDGE) after 1000.ms
                to(LYING) after 1000.ms
                to(STANDING) after 1200.ms
            }
            from(BED_EDGE) {
                to(LYING) after 1000.ms
                to(SITTING_IN_BED) after 1000.ms
                to(ATTEMPTING_EXIT) after 1000.ms
                to(STANDING) after 1200.ms
            }
            from(STANDING) {
                to(BED_EDGE) after 1200.ms
                to(IN_BATHROOM) after 2000.ms
                to(IN_ROOM) after 2000.ms
                to(IN_HALLWAY) after 2000.ms
                to(OUTDOOR) after 2000.ms
            }
            // ... más transiciones
        }

        Then("todas las transiciones son legales") {
            table.isLegal(LYING, BED_EDGE) shouldBe true
            table.isLegal(LYING, SITTING_IN_BED) shouldBe true
            table.isLegal(LYING, ATTEMPTING_EXIT) shouldBe true
            table.isLegal(ATTEMPTING_EXIT, STANDING) shouldBe true
            table.isLegal(STANDING, IN_BATHROOM) shouldBe true
        }

        Then("transiciones ilegales fallan") {
            table.isLegal(LYING, IN_BATHROOM) shouldBe false
            table.isLegal(OUTDOOR, LYING) shouldBe false
        }
    }
})
```

### SE-20 · CalibrationChanged regenera DwellCatalog — Tamano S

**Tarjeta:** como equipo, queremos que cuando llega CalibrationChanged, el ClockSweeper regenere su DwellCatalog con los umbrales del residente, para que los timers se ajusten inmediatamente.

**Conversacion (Vernon):** El DwellCatalog es DERIVED del SceneCalibration. No se almacena; se calcula de los dwellThresholds.

**Confirmacion (BDD):**

```kotlin
class DwellCatalogRegenerationSpec : BehaviorSpec({
    Given("un ClockSweeper con umbrales default") {
        val sweeper = ClockSweeperImpl()

        When("llega CalibrationChanged con nuevos umbrales") {
            val newCalibration = calibration {
                dwell {
                    STANDING warning 2.minutes exceeded 3.minutes
                }
            }

            Then("el sweeper usa los nuevos umbrales") {
                // ClockSweeper uses DwellCatalog from calibration
            }
        }
    }
})
```

---

## 4. Orden de implementacion (TDD)

```
SE-17 (PersonState 13 estados)           ← ✅ COMPLETADO
  ↓
SE-18 (DwellCatalog por residente)       ← scene-domain
  ↓
SE-19 (TransitionTable 13 estados)       ← scene-domain
  ↓
SE-20 (CalibrationChanged regenera)       ← scene-domain
```

---

## 5. Archivos a crear/modificar

### Nuevos

| Archivo | Descripcion |
|---------|-------------|
| `PersonState.kt` | Expandido a 13 estados ✅ |
| `DwellThresholdsDsl.kt` | Builder compartido para dwell thresholds ✅ |

### Existentes (modificar)

| Archivo | Cambio |
|---------|--------|
| `PersonState.kt` | 13 data objects + StateKind ✅ |
| `Observation.kt` | 14 ObservationKinds ✅ |
| `ObservationKindMapping.kt` | ACL completa ✅ |
| `TransitionTable.kt` | RELEASE_2 con ~48 transiciones ✅ |
| `DwellCatalogDsl.kt` | Refactorizado: usa DwellThresholdsBuilder compartido ✅ |
| `CalibrationDsl.kt` | Refactorizado: usa DwellThresholdsBuilder compartido ✅ |
| `SceneInterpreterDsl.kt` | Mapeo completo 13 estados ✅ |
| `SceneTestDsl.kt` | Helpers 13 estados ✅ |
| `PolicyCalibrationAdapter.kt` | Defaults a RELEASE_2 ✅ |
| `ClockSweeperImpl.kt` | Usar DwellCatalog del twin (SE-18) |
| `DigitalTwin.kt` | Sin cambios (ya tiene calibration) |

---

## 6. Invariantes Sprint 3

1. Los 13 estados del catalogo son los estados posibles del mundo.
2. Cada transicion tiene una regla en la tabla (TOTAL).
3. DwellCatalog se deriva de SceneCalibration, no se almacena.
4. CalibrationChanged regenera DwellCatalog inmediatamente.
5. La tabla de transiciones es por residente (via calibration).

---

## 7. Resumen ejecutivo

**Sprint 2** puso la base: cada residente tiene su SceneCalibration.

**Sprint 3** expande: 13 estados, dwell por residente, transiciones completas.

**El cambio**: mas estados, mas transiciones, mas dwell — todo por residente.

**Refactoring DSL** (Fowler): 8 builder classes → 3 shared + 2 specific. Eliminacion de parallel hierarchies, data clumps, y duplicated code.

---

*Sprint 3 definido el 2026-08-22 — SE-17 completado, SE-18 al SE-20 pendientes.*
