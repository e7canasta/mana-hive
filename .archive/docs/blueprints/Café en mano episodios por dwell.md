## Café en mano: episodios por dwell

### Los dos tipos de episodio

```
TIPO 1: TRANSICIÓN (lo que ya teníamos)
═══════════════════════════════════════
  José se sienta → episodio abre INMEDIATAMENTE
  Trigger: la acción de sentarse
  Ejemplo: LYING → SITTING_IN_BED → WARNING


TIPO 2: DWELL (lo que estás pidiendo)
═══════════════════════════════════════
  José entra al baño → nada pasa → pasan 5 minutos → EPISODIO
  Trigger: el TIEMPO en un estado, no la acción
  Ejemplo: IN_BATHROOM por 5+ minutos → WARNING
```

### Escenario 1: José en el baño más de 5 minutos

```
T=0:00  José entra al baño (IN_BATHROOM)
        → Scene: TransitionDetected(STANDING → IN_BATHROOM)
        → DigitalTwin: stateSince = 0:00
        → Sentinel: NO abre episodio (no hay regla de transición para BATHROOM)
        → ClockSweeper: "mine planted" — empezando a contar

T=4:00  ClockSweeper tick (80% de 5 min)
        → DwellWarning(state=IN_BATHROOM, since=0:00)
        → Sentinel: umbrella si hay episodio abierto, o nada si no

T=5:01  ClockSweeper tick (exceeded)
        → DwellExceeded(state=IN_BATHROOM, threshold=5min, since=0:00)
        → Sentinel: NO hay episodio abierto → ABRE EPISODIO
        → EpisodeEvent.Opened(trigger=IN_BATHROOM, at=5:01)
        → Harbor: notifica (PUSH + TABLET)
        → Recorder: graba desde 5:01 (¿o desde 0:00?)

T=7:00  José vuelve al cuarto (STANDING)
        → Scene: TransitionDetected(IN_BATHROOM → STANDING)
        → Sentinel: STANDING no es safe state → episodio sigue abierto

T=8:30  José se acuesta (LYING)
        → Scene: TransitionDetected(STANDING → LYING)
        → Sentinel: LYING es safe state → CAN CLOSE
        → EpisodeEvent.Closed(cause = AUTO_RECOVERY)
        → Harbor: resuelve notificación
```

### El problema que veo

```
HOY:  EpisodeEvent.Opened(at=5:01)  ← el episodio "empieza" en 5:01
      RecordingWindow.before = 5:01 - 2min = 3:01
      → Grabamos desde 3:01 (perdimos 0:00 a 3:01)

PERO: Elsafe state fue 0:00 (cuando estaba STANDING antes del baño)
      El episodio real empezó en 0:00, no en 5:01
```

### Escenario 2: José fuera de la cama más de 10 minutos (comeBack)

```
T=0:00  José se levanta (LYING → SITTING → STANDING)
        → DigitalTwin: leftStateAt = 0:00 (mine planted)
        → Sentinel: abre episodio WARNING (transición a SITTING)

T=10:01 ClockSweeper tick (exceeded comeBack = 10 min)
        → ComeBackExceeded(baseline=LYING, since=0:00)
        → Sentinel: episodio ya abierto → UmbrellaEvent
        → Recorder: graba desde 10:01 (¿o desde 0:00?)

T=12:00 José vuelve a la cama (LYING)
        → leftStateAt = null (mine disarmed)
        → canClose() = true → AUTO_RECOVERY
```

### La pregunta clave

> **¿Desde cuándo debería grabar el recorder?**

```
OPCIÓN A (hoy):    desde trigger.at - before
                   = 5:01 - 2min = 3:01

OPCIÓN B (tu propuesta): desde leftStateAt (safe state)
                        = 0:00 (cuando salió del safe state)

OPCIÓN C (ybrid):  desde min(leftStateAt, trigger.at - before)
                   = min(0:00, 3:01) = 0:00
```

**La Opción B es la más completa** — graba desde que salió del safe state, que es cuando el episodio realmente empezó.

¿Querés que ajustemos el plan del sprint para incluir este escenario de dwell? Porque ahora mismo el `RecordingWindow` no sabe nada de `leftStateAt` — solo sabe de `trigger.at`.