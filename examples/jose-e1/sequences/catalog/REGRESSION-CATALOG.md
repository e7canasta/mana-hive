# Catálogo de Regresión Funcional — Sentinel + Hub

> **Objetivo:** Validar que el pipeline completo (Hive → NATS → Hub) funciona correctamente para todos los casos de uso del producto.
> **Referencia:** monitoring-policies-product-spec.md
> **Residente de prueba:** Jose (bed-4, monitor m1)

---

## Matriz de Truth Tables por Escenario

### CLAVE: Cómo leer las tablas

| Columna | Significado |
|---------|-------------|
| **Obs** | Observación de cámara que llega |
| **Postura** | Estado detectado por scene engine |
| **Sentinel evalúa** | Qué hace el motor contra las reglas |
| **Episode** | Estado del episodio (— = ninguno, OPENED/CLOSED/ESCALATED) |
| **ClosureCause** | Por qué se cerró (si aplica) |

---

## GRUPO 1: Apertura de Episodios

### 1.1 — Apertura inmediata por onEntry (SAFE_ONLY)

**Escenario:** Jose se sienta → abre WARNING inmediato
**Perfil:** `jose-v-min.json` (SITTING_IN_BED onEntry WARNING, closure=SAFE_ONLY)

| # | Hora | Obs | Postura | Sentinel evalúa | Episode | ClosureCause |
|---|------|-----|---------|-----------------|---------|--------------|
| 1 | 22:00 | IN_BED | LYING | Sin regla onEntry para LYING | — | — |
| 2 | 23:15 | SITTING_IN_BED | SITTING_IN_BED | onEntry WARNING rule match | OPENED (WARNING) | — |
| 3 | 23:32 | IN_BED | LYING | Safe state + SAFE_ONLY + reversible | CLOSED | AUTO_RECOVERY |

**Verificación:**
- ✅ 1 episodio RESOLVED WARNING
- ✅ 3 scene events (STATE_CHANGED)
- ✅ 2 signals (EPISODE_OPENED + EPISODE_CLOSED)

---

### 1.2 — Apertura inmediata por onEntry (STAFF_OR_SAFE)

**Escenario:** Jose se sienta → abre WARNING → viene enfermera → cierra
**Perfil:** SITTING_IN_BED onEntry WARNING, closure=STAFF_OR_SAFE

| # | Hora | Obs | Postura | Sentinel evalúa | Episode | ClosureCause |
|---|------|-----|---------|-----------------|---------|--------------|
| 1 | 22:00 | IN_BED | LYING | Sin regla | — | — |
| 2 | 22:05 | SITTING_IN_BED | SITTING_IN_BED | onEntry WARNING | OPENED (WARNING) | — |
| 3 | 22:10 | STAFF_ENTERED | — | Staff present, STAFF_OR_SAFE → canClose=true | CLOSED | STAFF_PRESENT |

**Verificación:**
- ✅ 1 episodio RESOLVED WARNING
- ✅ ClosureCause = STAFF_PRESENT (no AUTO_RECOVERY)

---

### 1.3 — Apertura inmediata por onEntry (STAFF_AND_SAFE)

**Escenario:** Jose se sienta → abre WARNING → viene enfermera → NO cierra (falta safe state)
**Perfil:** SITTING_IN_BED onEntry WARNING, closure=STAFF_AND_SAFE, reversible=false

| # | Hora | Obs | Postura | Sentinel evalúa | Episode | ClosureCause |
|---|------|-----|---------|-----------------|---------|--------------|
| 1 | 22:00 | IN_BED | LYING | Sin regla | — | — |
| 2 | 22:05 | SITTING_IN_BED | SITTING_IN_BED | onEntry WARNING | OPENED (WARNING) | — |
| 3 | 22:10 | STAFF_ENTERED | — | Staff present, STAFF_AND_SAFE → canClose=false (falta safe) | OPENED | — |
| 4 | 22:15 | IN_BED | LYING | Safe state + STAFF_AND_SAFE + staffPresent=true → canClose=true | CLOSED | STAFF_AND_SAFE |

**Verificación:**
- ✅ 1 episodio RESOLVED WARNING
- ✅ Episode permanece abierto hasta que staff + safe state
- ✅ ClosureCause = STAFF_AND_SAFE

---

## GRUPO 2: Cierre por Safe State (AUTO_RECOVERY)

### 2.1 — Cierre reversible (SAFE_ONLY)

**Escenario:** Jose se sienta y vuelve solo → cierra automáticamente
**Perfil:** SITTING_IN_BED onEntry WARNING, closure=SAFE_ONLY, reversible=true

| # | Hora | Obs | Postura | Sentinel evalúa | Episode | ClosureCause |
|---|------|-----|---------|-----------------|---------|--------------|
| 1 | 22:00 | IN_BED | LYING | — | — | — |
| 2 | 22:05 | SITTING_IN_BED | SITTING_IN_BED | onEntry WARNING | OPENED (WARNING) | — |
| 3 | 22:22 | IN_BED | LYING | handleSafeState → canClose=true → reversible=true | CLOSED | AUTO_RECOVERY |

**Verificación:**
- ✅ 1 episodio RESOLVED WARNING
- ✅ ClosureCause = AUTO_RECOVERY
- ✅ Sin intervención de staff

---

### 2.2 — Cierre no reversible (STAFF_AND_SAFE)

**Escenario:** Jose se sienta → viene enfermera → NO cierra (reversible=false, necesita safe state)
**Perfil:** SITTING_IN_BED onEntry WARNING, closure=STAFF_AND_SAFE, reversible=false

| # | Hora | Obs | Postura | Sentinel evalúa | Episode | ClosureCause |
|---|------|-----|---------|-----------------|---------|--------------|
| 1 | 22:00 | IN_BED | LYING | — | — | — |
| 2 | 22:05 | SITTING_IN_BED | SITTING_IN_BED | onEntry WARNING | OPENED (WARNING) | — |
| 3 | 22:10 | STAFF_ENTERED | — | Staff present, STAFF_AND_SAFE → canClose=false | OPENED | — |
| 4 | 22:15 | IN_BED | LYING | Safe state + staffPresent → canClose=true | CLOSED | STAFF_AND_SAFE |

**Verificación:**
- ✅ Episode permanece abierto hasta que AMBAS condiciones se cumplen
- ✅ ClosureCause = STAFF_AND_SAFE (no AUTO_RECOVERY)

---

## GRUPO 3: Escalado de Severidad

### 3.1 — Escalado WARNING → CRITICAL

**Escenario:** Jose se sienta (WARNING) → se para (CRITICAL) → enfermera lo acosta
**Perfil:** SITTING_IN_BED onEntry WARNING, STANDING dwell 2m/3m CRITICAL

| # | Hora | Obs | Postura | Sentinel evalúa | Episode | ClosureCause |
|---|------|-----|---------|-----------------|---------|--------------|
| 1 | 22:00 | IN_BED | LYING | — | — | — |
| 2 | 22:05 | SITTING_IN_BED | SITTING_IN_BED | onEntry WARNING | OPENED (WARNING) | — |
| 3 | 22:06 | STANDING | STANDING | Dwell 2m rule not yet | OPENED (WARNING) | — |
| 4 | 22:08 | — | STANDING | Dwell 2m exceeded → CRITICAL | ESCALATED (CRITICAL) | — |
| 5 | 22:10 | STAFF_ENTERED | — | Staff present, STAFF_AND_SAFE | OPENED (CRITICAL) | — |
| 6 | 22:12 | IN_BED | LYING | Safe state + staffPresent | CLOSED | STAFF_AND_SAFE |

**Verificación:**
- ✅ 1 episodio que escala de WARNING a CRITICAL
- ✅ Mismo episode ID throughout
- ✅ ClosureCause = STAFF_AND_SAFE

---

### 3.2 — Escalado sin cierre (reversible=false)

**Escenario:** Jose se sienta → se para → NO viene staff → episode queda abierto
**Perfil:** SITTING_IN_BED onEntry WARNING, STANDING dwell 2m/3m CRITICAL, reversible=false

| # | Hora | Obs | Postura | Sentinel evalúa | Episode | ClosureCause |
|---|------|-----|---------|-----------------|---------|--------------|
| 1 | 22:00 | IN_BED | LYING | — | — | — |
| 2 | 22:05 | SITTING_IN_BED | SITTING_IN_BED | onEntry WARNING | OPENED (WARNING) | — |
| 3 | 22:06 | STANDING | STANDING | — | OPENED (WARNING) | — |
| 4 | 22:08 | — | STANDING | Dwell exceeded → CRITICAL | ESCALATED (CRITICAL) | — |
| 5 | 22:15 | IN_BED | LYING | Safe state, reversible=false → canClose=false | OPENED (CRITICAL) | — |

**Verificación:**
- ✅ Episode queda abierto aunque vuelva a LYING
- ✅ Necesita staff para cerrar

---

## GRUPO 4: Supresión por Staff Presente

### 4.1 — Staff presente suprime apertura

**Escenario:** Enfermera está en habitación → Jose se sienta → NO abre episodio
**Perfil:** SITTING_IN_BED onEntry WARNING, staffPresent suprime

| # | Hora | Obs | Postura | Sentinel evalúa | Episode | ClosureCause |
|---|------|-----|---------|-----------------|---------|--------------|
| 1 | 22:00 | IN_BED | LYING | — | — | — |
| 2 | 22:02 | STAFF_ENTERED | — | Staff present, no episode open | — | — |
| 3 | 22:05 | SITTING_IN_BED | SITTING_IN_BED | Staff present → suprime apertura | Suppressed | — |

**Verificación:**
- ✅ 0 episodios
- ✅ Signal: SuppressedWithRecord (STAFF_PRESENT)

---

### 4.2 — Staff llega después de abierto → puede cerrar

**Escenario:** Jose se sienta → abre → viene staff → cierra
**Perfil:** SITTING_IN_BED onEntry WARNING, closure=STAFF_OR_SAFE

| # | Hora | Obs | Postura | Sentinel evalúa | Episode | ClosureCause |
|---|------|-----|---------|-----------------|---------|--------------|
| 1 | 22:00 | IN_BED | LYING | — | — | — |
| 2 | 22:05 | SITTING_IN_BED | SITTING_IN_BED | onEntry WARNING | OPENED (WARNING) | — |
| 3 | 22:10 | STAFF_ENTERED | — | Staff present, STAFF_OR_SAFE → canClose=true | CLOSED | STAFF_PRESENT |

**Verificación:**
- ✅ 1 episodio RESOLVED WARNING
- ✅ ClosureCause = STAFF_PRESENT

---

## GRUPO 5: Permanencia Excedida (Dwell)

### 5.1 — Dwell abre después de tiempo

**Escenario:** Jose se sienta 5 min → abre WARNING por dwell
**Perfil:** SITTING_IN_BED dwell 5m/8m WARNING, closure=SAFE_ONLY

| # | Hora | Obs | Postura | Sentinel evalúa | Episode | ClosureCause |
|---|------|-----|---------|-----------------|---------|--------------|
| 1 | 22:00 | IN_BED | LYING | — | — | — |
| 2 | 22:05 | SITTING_IN_BED | SITTING_IN_BED | Dwell 5m not yet | — | — |
| 3 | 22:10 | — | SITTING_IN_BED | Dwell 5m exceeded → WARNING | OPENED (WARNING) | — |
| 4 | 22:12 | IN_BED | LYING | Safe state + SAFE_ONLY | CLOSED | AUTO_RECOVERY |

**Verificación:**
- ✅ 1 episodio RESOLVED WARNING
- ✅ Episodio abre después de 5 min, no inmediatamente

---

### 5.2 — Dwell con preaviso

**Escenario:** Jose se sienta 4 min (preaviso) → 5 min (abre) → vuelve
**Perfil:** SITTING_IN_BED dwell 5m/8m WARNING, preaviso 4m

| # | Hora | Obs | Postura | Sentinel evalúa | Episode | ClosureCause |
|---|------|-----|---------|-----------------|---------|--------------|
| 1 | 22:00 | IN_BED | LYING | — | — | — |
| 2 | 22:05 | SITTING_IN_BED | SITTING_IN_BED | — | — | — |
| 3 | 22:09 | — | SITTING_IN_BED | Preaviso 4m (80% de 5m) | — | — |
| 4 | 22:10 | — | SITTING_IN_BED | Dwell 5m exceeded | OPENED (WARNING) | — |
| 5 | 22:12 | IN_BED | LYING | Safe state | CLOSED | AUTO_RECOVERY |

**Verificación:**
- ✅ Preaviso registrado pero no genera episodio
- ✅ 1 episodio RESOLVED WARNING

---

## GRUPO 6: Retorno (ComeBack)

### 6.1 — Retorno excedido

**Escenario:** Jose se levanta → no vuelve en 10 min → abre WARNING
**Perfil:** LYING comeBack 5m/10m WARNING, closure=SAFE_ONLY

| # | Hora | Obs | Postura | Sentinel evalúa | Episode | ClosureCause |
|---|------|-----|---------|-----------------|---------|--------------|
| 1 | 22:00 | IN_BED | LYING | — | — | — |
| 2 | 22:05 | SITTING_IN_BED | SITTING_IN_BED | — | — | — |
| 3 | 22:10 | STANDING | STANDING | — | — | — |
| 4 | 22:15 | — | STANDING | Preaviso retorno 5m (80% de 10m) | — | — |
| 5 | 22:20 | — | STANDING | Retorno excedido 10m | OPENED (WARNING) | — |
| 6 | 22:25 | IN_BED | LYING | Safe state | CLOSED | AUTO_RECOVERY |

**Verificación:**
- ✅ 1 episodio RESOLVED WARNING
- ✅ Episodio abre por retorno excedido, no por permanencia

---

### 6.2 — Retorno a tiempo (no abre)

**Escenario:** Jose se levanta → vuelve en 8 min → NO abre
**Perfil:** LYING comeBack 5m/10m WARNING

| # | Hora | Obs | Postura | Sentinel evalúa | Episode | ClosureCause |
|---|------|-----|---------|-----------------|---------|--------------|
| 1 | 22:00 | IN_BED | LYING | — | — | — |
| 2 | 22:05 | SITTING_IN_BED | SITTING_IN_BED | — | — | — |
| 3 | 22:10 | STANDING | STANDING | — | — | — |
| 4 | 22:13 | IN_BED | LYING | Retorno antes de 10m → no abre | — | — |

**Verificación:**
- ✅ 0 episodios
- ✅ No se genera alerta por retorno a tiempo

---

## GRUPO 7: Casos Edge

### 7.1 — Sentada corta no abre (observeOnly)

**Escenario:** Jose se sienta 3 min → vuelve → NO abre
**Perfil:** SITTING_IN_BED observeOnly (sin regla onEntry/dwell)

| # | Hora | Obs | Postura | Sentinel evalúa | Episode | ClosureCause |
|---|------|-----|---------|-----------------|---------|--------------|
| 1 | 22:00 | IN_BED | LYING | — | — | — |
| 2 | 22:05 | SITTING_IN_BED | SITTING_IN_BED | observeOnly → no regla | — | — |
| 3 | 22:08 | IN_BED | LYING | — | — | — |

**Verificación:**
- ✅ 0 episodios
- ✅ Ledger limpio

---

### 7.2 — Múltiples episodios en secuencia

**Escenario:** Jose se sienta → vuelve → se sienta otra vez → vuelve
**Perfil:** SITTING_IN_BED onEntry WARNING, closure=SAFE_ONLY

| # | Hora | Obs | Postura | Sentinel evalúa | Episode | ClosureCause |
|---|------|-----|---------|-----------------|---------|--------------|
| 1 | 22:00 | IN_BED | LYING | — | — | — |
| 2 | 22:05 | SITTING_IN_BED | SITTING_IN_BED | onEntry WARNING | OPENED (WARNING) | — |
| 3 | 22:15 | IN_BED | LYING | Safe state | CLOSED | AUTO_RECOVERY |
| 4 | 23:00 | SITTING_IN_BED | SITTING_IN_BED | onEntry WARNING (nuevo) | OPENED (WARNING) | — |
| 5 | 23:10 | IN_BED | LYING | Safe state | CLOSED | AUTO_RECOVERY |

**Verificación:**
- ✅ 2 episodios RESOLVED WARNING
- ✅ Ledger limpio entre episodios
- ✅ Cada episodio tiene su propio ID

---

### 7.3 — Caída (siempre CRITICAL)

**Escenario:** Jose cae → abre CRITICAL inmediato → siempre avisa
**Perfil:** ON_FLOOR onEntry CRITICAL, alwaysActive

| # | Hora | Obs | Postura | Sentinel evalúa | Episode | ClosureCause |
|---|------|-----|---------|-----------------|---------|--------------|
| 1 | 22:00 | IN_BED | LYING | — | — | — |
| 2 | 22:05 | ON_FLOOR | ON_FLOOR | onEntry CRITICAL (siempre activo) | OPENED (CRITICAL) | — |
| 3 | 22:10 | STAFF_ENTERED | — | Staff present | OPENED (CRITICAL) | — |
| 4 | 22:15 | IN_BED | LYING | Safe state + staff | CLOSED | STAFF_AND_SAFE |

**Verificación:**
- ✅ 1 episodio RESOLVED CRITICAL
- ✅ La regla de caída nunca se puede apagar
- ✅ Siempre abre inmediatamente

---

### 7.4 — 48h robustez

**Escenario:** 48 horas con patrones mixtos
**Perfil:** SITTING_IN_BED observeOnly, comeBack 5m/8m

| # | Hora | Obs | Postura | Sentinel evalúa | Episode | ClosureCause |
|---|------|-----|---------|-----------------|---------|--------------|
| Día 1 | 22:00-06:00 | Varias | Varias | Cortas no abren, 1 larga sí | 1 episode | — |
| Día 2 | 22:00-06:00 | Varias | Varias | Cortas no abren, 1 larga sí | 1 episode | — |

**Verificación:**
- ✅ Ledger limpio entre noches
- ✅ No hay memory leak o state corruption
- ✅ 2 episodios totales

---

## GRUPO 8: Pipeline End-to-End

### 8.1 — Hive → NATS → Hub completo

**Escenario:** Verificar que la señal llega al hub y se persiste correctamente

| Paso | Componente | Acción | Resultado esperado |
|------|------------|--------|-------------------|
| 1 | Hive | Sentinel produce EPISODE_CLOSED | Signal emitida por NATS |
| 2 | NATS | Señal viaja a hub | Hub recibe señal |
| 3 | Hub | IntegrationService procesa | Episode actualizado a RESOLVED |
| 4 | Hub | GET /api/v1/episodes | Devuelve episodio con status=RESOLVED |

### 8.2 — Verificación de Scene Events

| Campo | Valor esperado |
|-------|----------------|
| residentId | "jose" |
| bedId | "bed-4" |
| eventType | STATE_CHANGED |
| created_at | Presente (no null) |

### 8.3 — Verificación de Sentinel Signals

| Campo | Valor esperado |
|-------|----------------|
| residentId | "jose" |
| bedId | "bed-4" |
| signalType | EPISODE_OPENED o EPISODE_CLOSED |
| created_at | Presente (no null) |

---

## Resumen: Qué cubre cada grupo

| Grupo | Casos | Qué valida |
|-------|-------|------------|
| 1. Apertura | 3 | onEntry con los 3 closure conditions |
| 2. Cierre Safe State | 2 | AUTO_RECOVERY reversible vs no reversible |
| 3. Escalado | 2 | Rampa severidad + cierre con staff |
| 4. Supresión Staff | 2 | Staff presente suprime + staff llega después |
| 5. Dwell | 2 | Permanencia excedida + preaviso |
| 6. Retorno | 2 | ComeBack excedido vs a tiempo |
| 7. Edge Cases | 4 | observeOnly, múltiples, caída, 48h |
| 8. Pipeline E2E | 3 | Hive→NATS→Hub completo |
| **TOTAL** | **22** | |

---

## Perfiles necesarios

| Perfil | ClosureCondition | reversible | Reglas | Escenarios |
|--------|------------------|------------|--------|------------|
| `jose-v-min.json` | SAFE_ONLY | true | SITTING onEntry WARNING | 1.1, 2.1, 5.1, 5.2, 7.2 |
| `jose-staff-or-safe.json` | STAFF_OR_SAFE | true | SITTING onEntry WARNING | 1.2, 4.2 |
| `jose-staff-and-safe.json` | STAFF_AND_SAFE | false | SITTING onEntry WARNING | 1.3, 2.2 |
| `jose-escalation.json` | SAFE_ONLY | true | SITTING onEntry WARNING + STANDING dwell 2m/3m CRITICAL | 3.1, 3.2 |
| `jose-staff-suppress.json` | — | — | SITTING onEntry WARNING + staff suppress | 4.1 |
| `jose-comeback.json` | SAFE_ONLY | true | LYING comeBack 5m/10m WARNING | 6.1, 6.2 |
| `jose-observe-only.json` | — | — | SITTING observeOnly | 7.1 |
| `jose-fall.json` | STAFF_AND_SAFE | false | ON_FLOOR onEntry CRITICAL alwaysActive | 7.3 |
| `jose-48h.json` | SAFE_ONLY | true | SITTING observeOnly + comeBack 5m/8m | 7.4 |

---

## Estrategia de ejecución

1. **Primero:** Correr grupo 1 (apertura) para validar pipeline básico
2. **Segundo:** Correr grupo 2 (cierre) para validar AUTO_RECOVERY
3. **Tercero:** Correr grupo 3-6 (escenarios clínicos)
4. **Cuarto:** Correr grupo 7 (edge cases)
5. **Quinto:** Correr grupo 8 (validación E2E)

**Comando para correr todo:**
```bash
./gradlew :examples:scenario-simulator:run --args="examples/jose-e1/sequences/catalog/"
```

**Comando para correr uno:**
```bash
./gradlew :examples:scenario-simulator:run --args="examples/jose-e1/sequences/catalog/01-e1-vuelve-solo.yaml"
```
