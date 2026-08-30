# Handoff: Café - Pipeline José E1

## Dónde quedamos

### ✅ Completado

1. **Arranque en frío**: `jose.json` → `ProfileCalibrator` → calibraciones
2. **Pipeline batch**: `events.dat` + JSON → `scene.out`, `sentinel.out`, `harbor.out`, `recorder.out`, `pipeline.out`
3. **Writer consistente**: `RecordingEventWriter` reescrito con formato `t=<offset> <TYPE> <details>`

### 📋 Archivos relevantes

```
examples/jose-e1/
├── src/main/kotlin/jose301/
│   ├── Main.kt              # DSL: E1 con configBasica
│   ├── MainFromJson.kt      # Arranque en frío: JSON → calibración
│   ├── MainPipeline.kt      # Pipeline completa: JSON + events.dat → .out
│   ├── Calibrations.kt      # configBasica (12/15m)
│   ├── Episodes.kt          # e1 (17 min sentado)
│   └── Shared.kt            # Constantes
├── src/main/resources/profiles/
│   └── jose.json            # Perfil v2 con reglas de grabación
└── output-jose-e1/          # Salidas generadas
    ├── scene.out
    ├── sentinel.out
    ├── harbor.out
    ├── recorder.out
    └── pipeline.out
```

### 🔗 Para profundizar después

**El segundo canal: Bridge → mana-hub**

Nuestros .out = lo que el bridge consume y postea a hub.

| .out | Subject NATS | Tabla hub |
|------|-------------|-----------|
| `scene.out` | `scene.fact.v1.<bed>` | `scene_events` |
| `sentinel.out` | `sentinel.signal.v1.<bed>` | `episodes` |
| `harbor.out` | `alarm.event.v1.<alert>` | `notification_events` |
| `recorder.out` | `recorder.command.v1.<bed>` | `clip_windows` |

**La pregunta clave:** ¿Nuestros .out generados con `MainPipeline.kt` son lo que el bridge consume?

**Documento de referencia:**
- `/home/visiona/workspace/mana-hub/docs/integracion-hive-hub.md`

### 📁 Ruta del bridge

```
/home/visiona/workspace/mana-hub/hive-bridge
```

## Comandos útiles

```bash
# Pipeline completa
./gradlew :examples:jose-e1:run

# Solo arranque en frío
./gradlew :examples:jose-e1:run -Pmain=jose301.MainFromJsonKt

# Solo DSL
./gradlew :examples:jose-e1:run -Pmain=jose301.MainKt
```

## Café ⏸️

Volver a: profundizar en bridge → mana-hub
