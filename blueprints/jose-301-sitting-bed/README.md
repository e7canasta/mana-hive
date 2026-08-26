# Blueprint: José 301 — Sitting Bed

> **"Solo quiero saber cuándo José se sienta en la cama."**
> — Susan, enfermera de guardia nocturna

---

## Residente

| Campo | Valor |
|-------|-------|
| **Nombre** | José |
| **Cama** | bed-4 |
| **Monitor** | m1 |
| **Perfil** | No caedor. Se sienta cada 1-2h. A veces va al baño, a veces no. |
| **Problema actual** | Radar de piso genera falsas alarmas. Enfermera va a la habitación y José ya está acostado o solo está en el baño. |

## Patrón Nocturno de José

```
22:00  ─── LYING ─────────────────────────────────────────── dormido
              │
23:15  ─── SITTING_IN_BED ─── 17 min ─── LYING ─────────── se sienta, se acuesta
              │
00:47  ─── SITTING → STANDING → BATHROOM → ROOM → LYING ── baño 15 min
              │
02:32  ─── SITTING → STANDING → BATHROOM → ROOM → LYING ── baño 31 min ⚠️
              │
03:50  ─── SITTING_IN_BED ─── 4 min ─── LYING ──────────── se sienta, se acuesta
              │
05:20  ─── SITTING → STANDING → BATHROOM → ROOM → LYING ── baño 26 min ⚠️
              │
06:35  ─── SITTING_IN_BED ─── 3 min ─── LYING ──────────── se sienta, se acuesta
              │
07:00  ─── SITTING_IN_BED ───────────────────────────────── se levanta para el día
```

**7 episodios. 3 con baño, 4 solo sentado. 9 horas de noche.**

---

## Las 3 Configuraciones

### Config 1: Solo Sentarse

> "Notificame cuando José se sienta en la cama. Nada más."

- **Fact de interés:** `TransitionDetected(LYING → SITTING_IN_BED)`
- **Resultado:** 7 notificaciones (una por episodio)
- **Complejidad:** mínima — solo detección de transición

### Config 2: Viaje Completo

> "Quiero ver todo: se sienta, se levanta, entra al baño, sale del baño, se acuesta."

- **Facts de interés:** todas las transiciones
- **Resultado:** 23 notificaciones (cada cambio de estado)
- **Complejidad:** media — trazabilidad sin alertas

### Config 3: Viaje + Alertas de Dwell

> "Todo lo anterior, más alertame si se pasa mucho rato en el baño o si no vuelve a la cama."

- **Facts de interés:** transiciones + `DwellExceeded`
- **Dwell normal:** `IN_BATHROOM > 5m` → E3 (31m) y E5 (26m) disparan alerta
- **Come-back:** `fuera de LYING > 15m` → E1 (17m), E3 (33m), E5 (27m) disparan alerta
- **Complejidad:** alta — requiere come-back

---

## Archivos

| Archivo | Descripción |
|---------|-------------|
| `events.dat` | Stream de 23 observaciones — la noche completa de José |
| `config1-run.yaml` | Config 1: solo SITTING_IN_BED |
| `config2-run.yaml` | Config 2: viaje completo |
| `config3-run.yaml` | Config 3: viaje + dwell alerts |
| `expected1.out` | 7 hechos esperados (Config 1) |
| `expected2.out` | 23 hechos esperados (Config 2) |
| `expected3.out` | 28 hechos esperados (Config 3) |

---

## ComeBack (Dwell Inverso) — Implementado (SPEC-05)

### El Problema

El dwell normal responde: **"¿Cuánto tiempo lleva EN este estado?"**

ComeBack responde: **"¿Cuánto tiempo lleva FUERA de este estado?"**

### La Mina

```
Normal:   planta al ENTRAR al estado    → explota si permanece >= threshold
ComeBack: planta al SALIR del estado    → explota si no regresa >= threshold
```

### Ejemplo con José (E1)

```
23:15  José sale de LYING → SITTING_IN_BED
       💣 mina plantada (leftStateAt = 23:15)

23:16  sweep → 1 min fuera de LYING < 15 min → no explota
23:29  sweep → 14 min < 15 min → no explota
23:30  sweep → 15 min >= 15 min → 💥 ComeBackExceeded(LYING)
       mina gastada.

23:32  José vuelve a LYING
       (la mina ya explotó, no vuelve a disparar)
```

### Ejemplo con José (E4 — mina se desarma)

```
03:50  José sale de LYING → SITTING_IN_BED
       💣 mina plantada (leftStateAt = 03:50)

03:53  sweep → 3 min < 15 min → no explota

03:54  José vuelve a LYING
       mina DESARMADA (volvió al estado)
       No hubo ComeBackExceeded.
```

### Cadena de punta a punta (SPEC-05)

| Componente | Cambia | No Cambia |
|------------|--------|-----------|
| `DagDsl` | `comeBackTo(baseline) { alertAfter(...) }` | — |
| `PolicyCalibration` | `ScenePolicy.comeBackThresholds` | — |
| `PolicyResolver` | `resolveComeBackThresholdsFromDag()` | — |
| `PolicyAdapters` | Wire `comeBackThresholds` → scene | — |
| `SentinelCalibration` | `comeBackRules`, `comeBackRuleFor()` | — |
| `SentinelEvaluatorImpl` | `evaluateComeBackExceeded()`, `evaluateComeBackWarning()` | — |
| `ClockSweeper` | — | ✅ (ya existía) |
| `DigitalTwin` | — | ✅ (`leftStateAt`, `baselineState`) |
| `Harbor` | — | ✅ (ve otro SceneFact) |
| `Hub` | — | ✅ (ingesta normal) |

**Sentinel distingue ComeBackExceeded de DwellExceeded: son tipos distintos en el stream.**

---

## Verificación

```bash
# Blueprint completo (via scene directa + via policy).
# Sale distinto de cero si algun check queda rojo.
LANG=C.UTF-8 LC_ALL=C.UTF-8 ./gradlew :blueprints:jose-301-sitting-bed:run

# Tests unitarios. OJO: `check` NO ejecuta los blueprints — ningun blueprint
# tiene source set de test, asi que `check` solo los compila.
LANG=C.UTF-8 LC_ALL=C.UTF-8 ./gradlew check
```
