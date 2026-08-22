# 12 · Scene Engine — Diseño en lenguaje de geriátrico

**Para quién:** Director del geriátrico, jefa de enfermería, enfermeros de turno.
**Qué lee:** cómo funciona el "ojo que interpreta la habitación", qué hace, qué no hace, y qué pasa cada noche.

---

## 1. El Scene Engine y sus vecinos — quién hace qué

```mermaid
C4Component
    title Scene Engine — qué hace y con quién habla

    Container_Boundary(scene, "Scene Engine") {
        Component(interpreter, "Intérprete de observaciones", "MotorDeSituacion", "Traduce lo que el sensor ve en hechos claros: transiciones, descartes, duplicados")
        Component(sweeper, "Reloj de permanencia", "MotorDeReloj", "Cuenta cuánto tiempo lleva cada persona en un estado y avisa cuando se pasa")
        Component(twins, "Gemelos digitales", "Estado de cada cama", "Ficha viva: quién está, en qué estado, desde cuándo, si el sensor vive")
    }

    System_Ext(sensor, "Sensor / Celda IA", "Ve lo que pasa en la habitación")
    System_Ext(hub, "Hub", "Sistema central — recibe hechos y decide qué hacer")
    System_Ext(sentinel, "Sentinel", "Juzga si hay que alertar según reglas clínicas")

    Rel(sensor, interpreter, "Dice: 'vi a alguien en el borde de la cama'")
    Rel(interpreter, twins, "Actualiza la ficha de la cama")
    Rel(interpreter, hub, "Emite: 'pasó esto' o 'descarté esto por esto'")
    Rel(sweeper, twins, "Mira todas las fichas y cuenta el tiempo")
    Rel(sweeper, hub, "Emite: 'lleva X minutos en este estado'")
    Rel(hub, sentinel, "Le pasa los hechos para que juzgue")
```

### Tabla de responsabilidades

| Componente | Qué hace | Qué NO hace | Ejemplo |
|------------|----------|-------------|---------|
| **Intérprete** | Traduce sensor → hecho | No decide si alertar | "María pasó de acostada a de pie" |
| **Reloj** | Cuenta tiempo en estado | No conoce reglas clínicas | "Lleva 5 minutos de pie" |
| **Gemelo** | Guarda el estado actual de una cama | No guarda historial | "Cama 3: María, de pie, desde 03:00:02" |

---

## 2. Ciclo de vida de una noche — de punta a punta

```mermaid
flowchart TD
    START[🌙 Anochece] --> OPEN["Director dice: 'Abrir jornada'<br/>Cada cama tiene su gemelo"]
    OPEN --> WAIT[Esperando sensores]
    
    WAIT --> OBS["Sensor ve algo<br/>Observación llega"]
    OBS --> INTERPRET{"¿Qué ve el sensor?"}
    
    INTERPRET -->|"Algo normal"| UPDATE["Intérprete actualiza el gemelo<br/>Emite el hecho"]
    INTERPRET -->|"Ruido / temblor"| DISCARD["Intérprete descarta<br/>Registra por qué"]
    INTERPRET -->|"Duplicado"| NOP["No-op<br/>Ya lo vio"]
    
    UPDATE --> SWEEP["El reloj mira todas las camas<br/>¿Alguna lleva mucho tiempo?"]
    DISCARD --> SWEEP
    NOP --> SWEEP
    
    SWEEP -->|"Nada nuevo"| WAIT
    SWEEP -->|"Tiempo vencido"| FACT["Hecho de permanencia<br/>Se envía al Hub"]
    
    FACT --> WAIT
    
    WAIT -->|"30 min sin señales"| LOST["Sensor perdido<br/>Hecho: señal caída"]
    LOST --> WAIT
    
    WAIT -->|" turno cambia"| CLOSE["Cerrar jornada<br/>Resumen de la noche"]
    
    style START fill:#1a1a2e,color:#fff
    style CLOSE fill:#1a1a2e,color:#fff
    style DISCARD fill:#e74c3c,color:#fff
    style NOP fill:#95a5a6,color:#fff
    style FACT fill:#e67e22,color:#fff
    style LOST fill:#c0392b,color:#fff
```

---

## 3. Secuencia: la caída de María a las 03:00

```mermaid
sequenceDiagram
    autonumber
    participant S as 📷 Sensor
    participant I as 🧠 Intérprete
    participant G as 📋 Gemelo (Cama 3)
    participant R as ⏰ Reloj
    participant H as 🏥 Hub
    participant SN as 🚨 Sentinel

    Note over H: 03:00 — María duerme en cama 3

    S->>I: "BED_EDGE, confianza 0.9, 03:00:00"
    I->>I: ¿Es legal LYING → BED_EDGE? SÍ<br/>¿Confianza ≥ mínima? SÍ<br/>¿Tiempo mínimo superado? SÍ (primera vez)
    I->>G: Actualizo: estado = BED_EDGE, desde = 03:00:00
    I->>H: Hecho: TransiciónDetectada(LYING → BED_EDGE)
    H->>SN: Le paso el hecho

    S->>I: "STANDING, confianza 0.95, 03:00:02"
    I->>I: ¿Es legal BED_EDGE → STANDING? SÍ
    I->>G: Actualizo: estado = STANDING, desde = 03:00:02
    I->>H: Hecho: TransiciónDetectada(BED_EDGE → STANDING)

    Note over R: 03:00:07 — Tick del reloj (cada 5s)

    R->>G: ¿Cuánto tiempo lleva de pie? 5 segundos
    R->>R: 5s < 5min → No hago nada

    Note over R: 03:05:02 — Han pasado 5 minutos

    R->>G: ¿Cuánto tiempo lleva de pie? 5 minutos
    R->>R: 5min ≥ 5min → ¡Hecho!
    R->>H: Hecho: DwellExceeded(STANDING, 5min, desde 03:00:02)
    H->>SN: "María lleva 5 min de pie a las 3 de la mañana"

    SN->>SN: Regla: "Noche + DePie + 5min = CRÍTICO"
    SN->>H: IncidentDeclared → Alerta

    Note over H: 03:05:10 — Enfermera acude, María vuelve a la cama

    S->>I: "IN_BED, confianza 0.98, 03:05:10"
    I->>I: ¿Es legal STANDING → LYING? SÍ (vía BED_EDGE o directo)
    I->>G: Actualizo: estado = LYING, desde = 03:05:10
    I->>H: Hecho: TransiciónDetectada(STANDING → LYING)
```

---

## 4. Secuencia: el sensor pierde señal

```mermaid
sequenceDiagram
    autonumber
    participant S as 📷 Sensor
    participant I as 🧠 Intérprete
    participant G as 📋 Gemelo (Cama 3)
    participant R as ⏰ Reloj
    participant H as 🏥 Hub

    Note over S,H: María está de pie, sensor funcionando

    S->>I: "STANDING, 03:00:02"
    I->>G: estado = STANDING, desde = 03:00:02

    Note over S: 03:03:00 — El sensor se apaga

    Note over R: 03:03:05 — Tick del reloj

    R->>G: ¿Cuándo fue el último latido del sensor?
    R->>R: 03:00:02 → hace 3 minutos
    R->>R: 3min > 90s (umbral) → Señal perdida
    R->>G: Actualizo: sensor perdido = true
    R->>H: Hecho: SignalLost(monitor, último latido 03:00:02)

    Note over R: 03:05:00 — Sensor vuelve

    S->>I: "STANDING, 03:05:00"
    I->>G: Actualizo: sensor recuperado
    I->>H: Hecho: SignalRecovered(monitor)
    I->>H: Hecho: TransiciónDetectada(UNKNOWN → STANDING)
```

---

## 5. Secuencia: ruido del sensor (temblor)

```mermaid
sequenceDiagram
    autonumber
    participant S as 📷 Sensor
    participant I as 🧠 Intérprete
    participant G as 📋 Gemelo (Cama 3)
    participant H as 🏥 Hub

    Note over S,H: María está tranquila en la cama

    S->>I: "BED_EDGE, confianza 0.7, 03:01:00"
    I->>I: ¿Confianza 0.7 ≥ mínima (0.8)? NO
    I->>H: Descarte: CONFIDENCE_TOO_LOW (0.7 < 0.8)

    S->>I: "IN_BED, confianza 0.95, 03:01:01"
    I->>I: ¿Es legal LYING → LYING? Es el mismo estado
    I->>H: Descarte: DUPLICATE (ya está en LYING)

    Note over S: 03:01:03 — El sensor titubea

    S->>I: "BED_EDGE, confianza 0.9, 03:01:03"
    I->>I: ¿Es legal LYING → BED_EDGE? SÍ<br/>¿Histéresis? 1500ms mínimos
    I->>G: Anoto: transición pendiente desde 03:01:03

    S->>I: "IN_BED, confianza 0.95, 03:01:04"
    I->>I: ¿Han pasado 1500ms? NO (solo 1s)
    I->>H: Descarte: HYSTERESIS_NOT_MET (1000ms < 1500ms)
    I->>G: Limpio la transición pendiente
```

---

## 6. Pseudocódigo del Intérprete — cómo piensa

```
función interpretar(gemelo, observación, ahora, calibración):

    # 1. ¿La observación es confiable?
    si observación.confianza < calibración.confianzaMínima(observación.tipo):
        return Explicado(
            gemelo sin cambios,
            [],
            [Descarte("confianza baja", CONFIDENCE_TOO_LOW)]
        )

    # 2. ¿El sensor está vivo?
    si gemelo.sensor.perdido:
        # La observación recuperó el sensor
        gemelo = gemelo.conSensorRecuperado()
        hechos = [SignalRecovered(gemelo.sensor)]

    # 3. ¿Es el mismo estado? → no-op
    si observación.tipo == gemelo.estado.kind:
        return Explicado(gemelo, [], [Descarte("duplicado", DUPLICATE)])

    # 4. ¿La transición es legal en la tabla?
    si NO tabla.esLegal(de: gemelo.estado.kind, a: observación.tipo):
        return Explicado(
            gemelo sin cambios,
            [],
            [Descarte("transición ilegal", ILLEGAL_TRANSITION)]
        )

    # 5. ¿Pasó el tiempo mínimo de histéresis?
    tiempoEnEstado = ahora - gemelo.estadoDesde
    mínimoRequerido = tabla.histeresis(de: gemelo.estado.kind, a: observación.tipo)
    si tiempoEnEstado < mínimoRequerido:
        return Explicado(
            gemelo sin cambios,
            [],
            [Descarte("histéresis no superada", HYSTERESIS_NOT_MET)]
        )

    # 6. Todo bien → transición
    nuevoEstado = observación.tipo.aEstado()
    gemelo = gemelo.conEstado(nuevoEstado, desde: ahora)
    hechos = [TransicionDetectada(de: gemelo.estadoAnterior, a: nuevoEstado)]

    return Explicado(gemelo, hechos, [])
```

---

## 7. Pseudocódigo del Reloj — cómo cuenta

```
función barrer(gemelos, ahora, catálogo, marcasEmitidas):

    hechos = []
    marcasNuevas = marcasEmitidas

    para cada gemelo en gemelos:

        # ¿El sensor está vivo?
        si gemelo.sensor.perdido:
            # Ya se emitió el SignalLost, no duplicar
            continuar

        # ¿Cuánto tiempo lleva en este estado?
        duración = ahora - gemelo.estadoDesde

        # ¿Hay un umbral para este estado?
        umbral = catálogo.porEstado[gemelo.estado.kind]
        si umbral == null:
            continuar  # No se vigila permanencia en LYING

        # ¿Ya emití un hecho para este (cama, estado, desde)?
        marca = DwellMarkKey(cama: gemelo.cama, estado: gemelo.estado.kind, desde: gemelo.estadoDesde)
        si marca in marcasEmitidas:
            continuar  # Ya lo reporté

        # ¿Está en pre-aviso? (80% del umbral)
        preUmbral = umbral * 0.8
        si duración >= preUmbral Y duración < umbral:
            hechos.add(DwellWarning(gemelo.estado, umbral, gemelo.estadoDesde))
            marcasNuevas = marcasNuevas + marca(con warning: true)

        # ¿Superó el umbral?
        si duración >= umbral:
            hechos.add(DwellExceeded(gemelo.estado, umbral, gemelo.estadoDesde))
            marcasNuevas = marcasNuevas + marca(con warning: false)

    return Explicado(SweepResult(hechos, marcasNuevas))
```

---

## 8. Los tres escenarios del Sprint 1

### Escenario A: La caída de las 03:00 ✅
```
DADO: María en cama 3, dormida, sensor vivo
CUANDO: sensor ve borde de cama → de pie → 5 minutos de pie
ENTONCES:
  1. TransiciónDetectada(LYING → BED_EDGE)
  2. TransiciónDetectada(BED_EDGE → STANDING)
  3. DwellExceeded(STANDING, 5min)
  → El Hub recibe 3 hechos y puede alertar
```

### Escenario B: La noche tranquila ✅
```
DADO: 30 camas, todas con residentes dormidos
CUANDO: pasan 8 horas sin novedad
ENTONCES:
  1. Cero hechos de transición
  2. Cero hechos de permanencia
  3. El sistema no grita de más
  → Silencio = normalidad
```

### Escenario C: El sensor se apaga ✅
```
DADO: María de pie, sensor vivo
CUANDO: sensor pierde señal por 90 segundos
ENTONCES:
  1. SignalLost(monitor, último latido)
  2. Gemelo pasa a UNKNOWN(SIGNAL_LOST)
  → El sistema dice "no sé qué pasó" en vez de asumir que todo está bien
```

---

## 9. Lo que el Scene Engine NO necesita saber

| No necesita | Por qué |
|-------------|---------|
| El nombre de María | Solo sabe "cama 3 tiene a alguien de pie" |
| Que es de noche | Solo sabe "la observación llegó a las 03:00" |
| Que 5 minutos es grave | Solo sabe "lleva 5 minutos en este estado" — otro decide si es grave |
| Si hay enfermera en el pasillo | Eso lo ve el sensor de staff, y lo suprime el Sentinel |
| Si ya se alertó antes | Eso lo lleva el Sentinel con sus episodios |

---

## 10. Diagrama de componentes con pseudocódigo integrado

```mermaid
flowchart LR
    subgraph INPUT["Lo que entra"]
        OBS["Observation<br/>传感器看到的"]
        CEN["CensusSnapshot<br/>谁住在哪张床"]
    end
    
    subgraph SCENE["Scene Engine"]
        direction TB
        INT["🧠 Intérprete<br/>─────────────<br/>¿Qué ve el sensor?<br/>¿Es legal?<br/>¿Es real o ruido?"]
        CLK["⏰ Reloj<br/>─────────────<br/>¿Cuánto tiempo lleva?<br/>¿Ya lo reporté?"]
        TWIN["📋 Gemelos<br/>─────────────<br/>Estado actual de<br/>cada cama"]
        
        INT -->|"actualiza"| TWIN
        CLK -->|"lee"| TWIN
    end
    
    subgraph OUTPUT["Lo que sale"]
        FACT["SceneFact<br/>Hecho claro para el Hub"]
        DISC["Discard<br/>Descarte con causa"]
    end
    
    OBS --> INT
    CEN --> INT
    INT --> FACT
    INT --> DISC
    CLK --> FACT
    
    style INT fill:#3498db,color:#fff
    style CLK fill:#e67e22,color:#fff
    style TWIN fill:#2ecc71,color:#fff
```

---

*Documento de diseño — nivel de geriátrico, no de arquitecto. Cada Diagrama responde a una pregunta que el director o la jefa de enfermería haría.*
