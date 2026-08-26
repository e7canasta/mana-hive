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
- **Dwell inverso:** `fuera de LYING > 15m` → E1 (17m), E3 (33m), E5 (27m) disparan alerta
- **Complejidad:** alta — requiere inverse dwell

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

## Inverse Dwell — Feature Necesario

### El Problema

El dwell normal responde: **"¿Cuánto tiempo lleva EN este estado?"**

Necesitamos que responda: **"¿Cuánto tiempo lleva FUERA de este estado?"**

### La Mina

```
Normal:   planta al ENTRAR al estado    → explota si permanece >= threshold
Inverso:  planta al SALIR del estado    → explota si no regresa >= threshold
```

### Ejemplo con José (E1)

```
23:15  José sale de LYING → SITTING_IN_BED
       💣 mina inversa plantada (leftStateAt = 23:15)

23:16  sweep → 1 min fuera de LYING < 15 min → no explota
23:29  sweep → 14 min < 15 min → no explota
23:30  sweep → 15 min >= 15 min → 💥 DwellExceeded(LYING)
       mina gastada.

23:32  José vuelve a LYING
       (la mina ya explotó, no vuelve a disparar)
```

### Ejemplo con José (E4 — mina se desarma)

```
03:50  José sale de LYING → SITTING_IN_BED
       💣 mina inversa plantada (leftStateAt = 03:50)

03:53  sweep → 3 min < 15 min → no explota

03:54  José vuelve a LYING
       mina DESARMADA (volvió al estado)
       No hubo DwellExceeded.
```

### Impacto en Código

| Componente | Cambia | No Cambia |
|------------|--------|-----------|
| `PolicyCalibration` | `DwellThreshold + returnTo` | — |
| `ClockSweeper` | `checkInverseDwell()` | — |
| `DigitalTwin` | `leftStateAt: Instant?` | — |
| `SceneInterpreter` | — | ✅ |
| `Sentinel` | — | ✅ (ve otro DwellExceeded) |
| `Harbor` | — | ✅ (ve otro SceneFact) |
| `Hub` | — | ✅ (ingesta normal) |

**Sentinel no distingue dwell normal de inverso. Es otro `DwellExceeded` en el stream.**

---

## Verificación

```bash
# Config 1: solo sentarse
scene-batch verify config1-run.yaml expected1.out

# Config 2: viaje completo
scene-batch verify config2-run.yaml expected2.out

# Config 3: viaje + dwell (inverse dwell pendiente)
scene-batch verify config3-run.yaml expected3.out
```
