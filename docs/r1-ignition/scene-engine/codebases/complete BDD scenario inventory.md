Here is the **complete BDD scenario inventory** across all 20 spec files:

---

## 1. ClockSweeperExceededSpec (SE-10)
**Pattern:** Domain Event + Threshold Specification
**Tests:** DwellExceeded is emitted when duration >= threshold

| # | Given | And | When | Then |
|---|-------|-----|------|------|
| 1 | un reloj | un gemelo en STANDING desde hace 5 min | umbral STANDING = 5 min | sweep a las 03:05:00 |
|   | | | | → emite DwellExceeded |
|   | | | | → marks contiene la marca de exceeded |
| 2 | un reloj | un gemelo en STANDING desde hace 4 min | umbral STANDING = 5 min | sweep a las 03:04:00 |
|   | | | | → no emite DwellExceeded |

---

## 2. ClockSweeperIdempotentSpec (SE-11)
**Pattern:** Idempotency via Marks
**Tests:** Sweeping twice with same timestamp does not duplicate DwellExceeded

| # | Given | And | When | Then |
|---|-------|-----|------|------|
| 1 | un reloj | un gemelo en STANDING desde hace 5 min | umbral STANDING = 5 min | sweep dos veces con el mismo now |
|   | | | | → solo 1 DwellExceeded en total |
|   | | | | → marks tiene 1 sola marca |

---

## 3. ClockSweeperSignalLostSpec (SE-12)
**Pattern:** Domain Event
**Tests:** SignalLost emitted when heartbeat timeout exceeded

| # | Given | And | When | Then |
|---|-------|-----|------|------|
| 1 | un reloj | un gemelo con lastHeartbeat = hace 2 min | heartbeatTimeout = 90s | sweep a las 03:00:00 |
|   | | | | → emite SignalLost |
|   | | | | → signal.lost es true |
| 2 | un reloj | un gemelo con lastHeartbeat = hace 30s | heartbeatTimeout = 90s | sweep a las 03:00:00 |
|   | | | | → no emite SignalLost |

---

## 4. ClockSweeperWarningSpec (SE-9)
**Pattern:** Domain Event + Threshold Specification
**Tests:** DwellWarning emitted when duration >= warningThreshold

| # | Given | And | When | Then |
|---|-------|-----|------|------|
| 1 | un reloj | un gemelo en STANDING desde hace 4 min | umbral STANDING = 5 min | sweep a las 03:04:00 |
|   | | | | → emite DwellWarning |
|   | | | | → marks contiene la marca de warning |
| 2 | un reloj | un gemelo en STANDING desde hace 3 min | umbral STANDING = 5 min | sweep a las 03:03:00 |
|   | | | | → no emite DwellWarning |

---

## 5. DigitalTwinEvolutionSpec
**Pattern:** Immutable data class + copy()
**Tests:** DigitalTwin evolves immutably with different event types

| # | Given | When | Then |
|---|-------|------|------|
| 1 | un gemelo en LYING desde 03:00:00 | evoluciona con TransitionDetected a BED_EDGE | → el estado cambia a BedEdge |
|   | | | → stateSince se actualiza |
|   | | | → el gemelo original no cambia |
| 2 | un gemelo con sensor vivo | evoluciona con SignalLost | → signal.lost es true |
|   | | | → el gemelo original no cambia |
| 3 | un gemelo con sensor perdido | evoluciona con SignalRecovered | → signal.lost es false |
| 4 | un gemelo en STANDING | evoluciona con DwellWarning | → el gemelo no cambia |

---

## 6. DigitalTwinWithCalibrationSpec (SE-15)
**Pattern:** Inmutabilidad + Derived Value
**Tests:** Calibration is preserved through evolution

| # | Given | And | Then/When |
|---|-------|-----|-----------|
| 1 | un PolicyCalibration para María | convertido a SceneCalibration via adaptador | |
|   | | un gemelo con calibración | → el gemelo tiene calibración |
|   | | | → el gemelo tiene ocupante |
|   | | | → la calibración tiene dwell thresholds |
|   | | | **When:** evoluciona con TransitionDetected |
|   | | | → la calibración se conserva |
|   | | | → el estado cambia |

---

## 7. DwellCatalogSpec (SE-18 + SE-20)
**Pattern:** Derived Value + Observer
**Tests:** Per-resident calibration overrides default catalog

| # | Given | When | Then |
|---|-------|------|------|
| 1 | dos residentes con diferentes umbrales de dwell | ambos llevan 4 minutos de pie | → Maria recibe warning (threshold 3 min) |
|   | | | → Jose recibe exceeded (threshold 3 min) |
| 2 | un residente con calibration y otro sin calibration | ambos llevan 4 minutos de pie | → Maria usa su calibration (warning 3 min) |
|   | | | → Jose usa el default (warning 4 min) |
| 3 | un residente sin calibration | sweep a las 03:04:00 (4 min) | → usa default catalog (warning 4 min) |
|   | | sweep a las 03:05:00 (5 min) | → usa default catalog (exceeded 5 min) |
| 4 | un residente con calibration actualizada | sweep a las 03:03:00 (exceeded threshold) | → recibe exceeded (threshold 3 min) |
|   | | sweep a las 03:02:30 (after warning threshold) | → recibe warning (threshold 2 min) |
| 5 | dos residentes con calibraciones diferentes | sweep a las 03:04:00 (4 min) | → Maria recibe exceeded (calibration threshold 3 min) |
|   | | | → Jose NO recibe exceeded (default threshold 5 min) |

---

## 8. LaCaidaDeLas03Spec (SE-13)
**Pattern:** Use Case (end-to-end integration)
**Tests:** Full interpreter + clock sweeper pipeline

| # | Given | When | Then |
|---|-------|------|------|
| 1 | Maria en cama 3, dormida | sensor ve borde de cama y luego de pie, y el reloj corre 5 minutos | → se emiten exactamente 4 hechos |
|   | | | → facts[0] = TransitionDetected(LYING, BED_EDGE) |
|   | | | → facts[1] = TransitionDetected(BED_EDGE, STANDING) |
|   | | | → facts contiene DwellExceeded(STANDING) |
|   | | | → el gemelo queda en STANDING |
|   | | | → stateSince es 03:00:02 |

---

## 9. ObservationKindMappingSpec
**Pattern:** Value mapping
**Tests:** ObservationKind → PersonState translation

| # | Given | When | Then |
|---|-------|------|------|
| 1 | un ObservationKind | es IN_BED | → traduce a Lying |
| 2 | | es BED_EDGE | → traduce a BedEdge |
| 3 | | es STANDING | → traduce a Standing |
| 4 | | es OUT_OF_ROOM | → traduce a Absent |
| 5 | | es HEARTBEAT | → traduce a Lying (no cambia estado) |
| 6 | | es STAFF_IN_ROOM | → traduce a Lying (staff no afecta persona) |
| 7 | | es UNCLASSIFIED | → traduce a Unknown(SCENE) |

---

## 10. PersonStateElevenSpec (SE-17)
**Pattern:** Value Object
**Tests:** All 13 clinical states with RELEASE_2 table

| # | Given | When | Then |
|---|-------|------|------|
| 1 | un interprete con la tabla RELEASE_2 | gemelo en LYING + llega SITTING_IN_BED | → estado cambia a SittingInBed |
| 2 | | gemelo en LYING + llega ATTEMPTING_EXIT | → estado cambia a AttemptingExit |
| 3 | | gemelo en LYING + llega BED_EDGE | → estado cambia a BedEdge |
| 4 | | gemelo en LYING + llega STANDING directamente | → transicion es ilegal (ILLEGAL_TRANSITION) |
| 5 | | gemelo en BED_EDGE + llega STANDING | → estado cambia a Standing |
| 6 | | gemelo en STANDING + llega IN_BATHROOM | → estado cambia a InBathroom |
| 7 | | gemelo en STANDING + llega IN_ROOM | → estado cambia a InRoom |
| 8 | | gemelo en STANDING + llega IN_HALLWAY | → estado cambia a InHallway |
| 9 | | gemelo en STANDING + llega OUTDOOR | → estado cambia a Outdoor |
| 10 | | gemelo en STANDING + llega IN_CHAIR | → estado cambia a InChair |
| 11 | | gemelo en STANDING + llega IN_WHEELCHAIR | → estado cambia a InWheelchair |
| 12 | | gemelo en STANDING + llega OUT_OF_ROOM | → estado cambia a Absent |

---

## 11. PoliticaToSceneIntegrationSpec
**Pattern:** Integration Test (adapter boundary)
**Tests:** PolicyCalibration → SceneCalibration bridging

| # | Given | When | Then |
|---|-------|------|------|
| 1 | a PolicyCalibration from Politica Engine for María | converting to SceneCalibration via adapter | → correct hysteresis |
|   | | | → correct confidence |
|   | | | → correct heartbeat timeout |
|   | | | → correct dwell thresholds |
| 2 | | creating a SceneInterpreter with converted calibration | → interpreter discards observation below confidence (0.85 < 0.9) |
|   | | | → interpreter accepts observation above confidence (0.95 >= 0.9) |
| 3 | two residents with different PolicyCalibrations | converting both to SceneCalibrations | → they have different confidence thresholds |
|   | | And: creating interpreters for each | |
|   | | And: both receive BED_EDGE observation with confidence 0.8 | → María's interpreter discards (threshold 0.9) |
|   | | | → José's interpreter accepts (threshold 0.7) |

---

## 12. SceneCalibrationReceivedSpec (SE-14)
**Pattern:** Observer Pattern
**Tests:** Low-risk vs high-risk calibration acceptance

| # | Given | And | When | Then |
|---|-------|-----|------|------|
| 1 | un PolicyCalibration low risk para María | un PolicyCalibration high risk para María | ambos convertidos a SceneCalibration | |
|   | | un gemelo en LYING | low risk recibe BED_EDGE con confianza 0.8 | → acepta la transicion (0.8 >= 0.7) |
|   | | | high risk recibe BED_EDGE con confianza 0.8 | → descarta la transicion (0.8 < 0.9) |

---

## 13. SceneInterpreterConfidenceSpec (SE-3)
**Pattern:** Specification Pattern
**Tests:** Confidence filtering on observations

| # | Given | And | When | Then |
|---|-------|-----|------|------|
| 1 | un interprete con minConfidence BED_EDGE = 0.8 | un gemelo en LYING | llega BED_EDGE con confianza 0.7 | → descarta por CONFIDENCE_TOO_LOW |
|   | | | | → el gemelo no cambia |
|   | | | | → no hay hechos |
| 2 | | un gemelo en LYING | llega BED_EDGE con confianza 0.9 | → no hay discards por confianza |

---

## 14. SceneInterpreterDuplicateSpec (SE-4)
**Pattern:** Idempotency Check
**Tests:** Same-state observation is discarded as DUPLICATE

| # | Given | And | When | Then |
|---|-------|-----|------|------|
| 1 | un interprete | un gemelo en LYING | llega IN_BED (mismo estado) | → descarta como DUPLICATE |
|   | | | | → el gemelo no cambia |

---

## 15. SceneInterpreterHysteresisSpec (SE-6)
**Pattern:** Temporal Specification
**Tests:** Hysteresis enforces minimum time in state before transition

| # | Given | And | When | Then |
|---|-------|-----|------|------|
| 1 | un interprete | un gemelo en LYING desde hace 1s | llega BED_EDGE (histeresis = 1500ms) | → descarta por HYSTERESIS_NOT_MET |
|   | | | | → el gemelo no cambia |
| 2 | | un gemelo en LYING desde hace 2s | llega BED_EDGE (histeresis = 1500ms) | → no hay discards |
|   | | | | → el estado cambia a BedEdge |
|   | | | | → se emite TransitionDetected |

---

## 16. SceneInterpreterIllegalSpec (SE-5)
**Pattern:** Strategy Pattern
**Tests:** Illegal transitions are rejected

| # | Given | And | When | Then |
|---|-------|-----|------|------|
| 1 | un interprete con tabla RELEASE_1 | un gemelo en LYING | llega OUT_OF_ROOM (LYING → ABSENT no existe) | → descarta como ILLEGAL_TRANSITION |
|   | | | | → el gemelo no cambia |

---

## 17. SceneInterpreterPerResidentSpec (SE-16)
**Pattern:** Factory Pattern
**Tests:** Per-resident interpreters with different confidence thresholds

| # | Given | And | When | Then |
|---|-------|-----|------|------|
| 1 | dos PolicyCalibrations con diferentes confianzas | ambos convertidos a SceneCalibration | SceneInterpreters separados | |
|   | | ambos gemelos en LYING | llega BED_EDGE con confianza 0.8 | → interprete de María descarta (0.8 < 0.9) |
|   | | | | → interprete de José acepta (0.8 >= 0.7) |

---

## 18. SceneInterpreterSensorRecoverySpec (SE-8)
**Pattern:** Chain of Responsibility
**Tests:** Signal recovery on observation reception

| # | Given | And | When | Then |
|---|-------|-----|------|------|
| 1 | un interprete | gemelo con signal.lost = true, estado STANDING | llega STANDING (mismo estado) | → se emite SignalRecovered |
|   | | | | → signal.lost es false |
|   | | | | → descarta como DUPLICATE |
| 2 | | gemelo con signal.lost = true, estado STANDING | llega BED_EDGE (cambio de estado) | → se emite SignalRecovered |
|   | | | | → se emite TransitionDetected(STANDING, BED_EDGE) |
|   | | | | → el estado es BED_EDGE |
|   | | | | → signal.lost es false |

---

## 19. SceneInterpreterTransitionSpec (SE-7)
**Pattern:** Domain Event
**Tests:** Valid transition produces correct event and state

| # | Given | And | When | Then |
|---|-------|-----|------|------|
| 1 | un interprete | un gemelo en LYING desde 03:00:00 | llega BED_EDGE con confianza 0.9 a las 03:00:02 | → el estado es BED_EDGE |
|   | | | | → stateSince es 03:00:02 |
|   | | | | → se emite TransitionDetected(LYING, BED_EDGE) |
|   | | | | → la explicacion contiene transition-table |
|   | | | | → no hay discards |

---

## 20. TransitionTableThirteenSpec (SE-19)
**Pattern:** Specification
**Tests:** DSL-built transition table covers all 13 states

| # | Given | Then |
|---|-------|------|
| 1 | una tabla construida con el DSL | → todas las transiciones de bed son legales (15 assertions) |
| 2 | | → todas las transiciones de out-of-bed son legales (12 assertions) |
| 3 | | → todas las transiciones de furniture son legales (9 assertions) |
| 4 | | → unknown recovery es legal |
| 5 | | → transiciones ilegales fallan (5 assertions) |
| 6 | | → cada transicion legal tiene hysteresis (6 assertions) |

---

**Summary:** 20 spec files, **~55 distinct BDD scenarios** covering:
- **ClockSweeper:** threshold exceeded, warning, idempotency, signal lost (4 specs)
- **DigitalTwin:** evolution, calibration persistence (2 specs)
- **SceneInterpreter:** confidence, duplicate, hysteresis, illegal transitions, valid transitions, sensor recovery, per-resident (7 specs)
- **Domain mapping:** ObservationKind → PersonState, 13-state transition table, 11-state transition spec (3 specs)
- **Integration:** Politica→Scene bridging, calibration reception, end-to-end "La caida de las 03", per-resident dwell catalog (4 specs)