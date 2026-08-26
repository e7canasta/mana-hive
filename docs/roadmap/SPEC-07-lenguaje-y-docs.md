# SPEC-07 — Deuda de lenguaje y documentación

**No es una tarea final.** Se aplica **al cerrar cada spec anterior**, en el mismo commit que el código. Un documento que miente cuesta más que un test que falla: el test se ve en rojo, el documento no.

**Depende de:** se ejecuta en paralelo · **Tamaño:** chico por pieza

---

## Por qué es una spec y no una limpieza

En un sistema cuyo argumento central es el lenguaje ubicuo, cada uno de estos puntos es un defecto de dominio, no de prolijidad. La frase de cierre de la sesión con la dirección lo fija como criterio:

> *"En el momento en que aparezca un término en el código que nadie en esta sala pueda pronunciar, o dos términos para la misma cosa, vuelvan a esta reunión."*

Hay cinco casos abiertos.

---

## 1 · `README.md` describe un módulo que no existe

**Estado:** el README nombra `engines/vigia/vigia-domain` y `engines/vigia/vigia-service`, con `vigia` como *"alert lifecycle · routing · escalation"*. El módulo no existe. El código y `CONTEXT-MAP.md` dicen `harbor` — Faro.

También apunta a documentos de diseño en `files/`; viven en `docs/`. Y menciona `files/09-bigpicture-y-sprint-1.md` y `files/11-*.md`, que no están en el repositorio.

**Cambio:**
- `vigia` → `harbor` en el diagrama Mermaid, la tabla de módulos y el texto corrido.
- `files/` → `docs/`, y quitar o reubicar las referencias a documentos inexistentes.
- Revisar el diagrama contra `settings.gradle.kts`: los módulos reales son `scene-engine`, `sentinel`, `harbor`, `recorder`, `politica-engine`, `pipeline`.

**Cuál nombre gana:** `harbor` / Faro. Es el que está en el código, en el mapa de contextos y en las pruebas. `vigia` sobrevive sólo en el README.

**Añadir además:** el requisito de UTF-8 en la sección de build (`SPEC-00` tarea 1).

---

## 2 · "Susan" nombra dos cosas

**Estado:**

| Dónde | Quién es Susan |
|---|---|
| `blueprints/jose-301-sitting-bed/README.md` | *"Susan, enfermera de guardia nocturna"* — es quien **pide** la configuración |
| `blueprints/susan-e2e-standard/` | Residente de `bed-5`, habitación 401, riesgo bajo — es quien **es monitoreada** |

Los dos papeles están en lados opuestos del sistema: una lo configura y recibe avisos, la otra los genera. Que compartan nombre es el peor caso posible de colisión.

**Cambio:** renombrar **la residente**, no la enfermera. La cita de la enfermera es material clínico real y textual; el residente de prueba es inventado.

Nombre sugerido: **Elena**, residente 401. Sin colisión con `jose`, `maria` ni `susan` en el repositorio.

Alcance del renombre:
- directorio `blueprints/susan-e2e-standard/` → `blueprints/elena-401-standard/`
- `settings.gradle.kts`
- paquete `susane2e` → `elena401`
- `ResidentId("susan")`, `ResidentId("susan-dwell")`, `NightId("night-susan-401")`
- los `println` del blueprint
- `docs/POLITICA-GUIDE.md`, tabla de escenarios E2E

**Regla nueva a dejar escrita en el README de `blueprints/`:** los residentes de prueba llevan nombre + número de habitación (`jose-301`, `elena-401`). El personal se nombra por rol, no por nombre propio, salvo en citas textuales.

---

## 3 · Un concepto, tres nombres

**Estado:** el mismo mecanismo se llama *dwell inverso* en los documentos, *la mina* en la metáfora del README, y `ComeBack` en el código (`ComeBackExceeded`, `comeBackByBaseline`, `checkComeBack`).

**Cambio:** **gana `ComeBack`.** Razones: es el nombre que está en el código y en el contrato publicado; y es el que se traduce solo a la frase de la enfermera — *"no volvió a la cama"*. *"Dwell inverso"* es jerga de implementación, y el lenguaje ubicuo no se define desde la implementación.

- En documentación, usar **"come-back"** o **"vuelta a la cama"**; introducir el término una vez explicando que en el código es `ComeBack`.
- **La metáfora de la mina se conserva** en los comentarios donde ya está: explica la mecánica (se planta al salir, explota si no vuelve, se desarma si vuelve) mejor que cualquier definición formal. Es didáctica, no un nombre alternativo.
- Actualizar `blueprints/jose-301-sitting-bed/README.md` y `docs/POLITICA-GUIDE.md`.

---

## 4 · Documentación que promete de más

**Estado:** `docs/POLITICA-GUIDE.md` presenta el diagrama de resolución con Harbor recibiendo *"Budget: 5 warnings por turno · Channels: PUSH + TABLET · Escalation: 30min"* y Recorder recibiendo ventanas de grabación.

Verificado: `PolicyResolver.resolve()` devuelve `HarborPolicy(defaultChannels = emptyMap(), escalationTimeouts = emptyMap())`, y los dos blueprints imprimen `Recorder: 0 transition windows`.

También presenta `ProductionDagCatalog.kt` como *"Catálogo maestro con templates"*; las plantillas están en fuentes de test.

**Cambio:** o se corrige el documento, o se corrige el código (`SPEC-03`, `SPEC-04`). Lo que no puede quedar es la divergencia.

**Convención a adoptar en todos los documentos de diseño:** marcar explícitamente lo que es objetivo y no estado. Un bloque `> **Objetivo, no implementado.**` antes de la sección correspondiente. Barato de escribir y evita exactamente esta clase de error.

---

## 5 · Documentación que promete de menos

**Estado:** `blueprints/jose-301-sitting-bed/README.md` tiene una sección *"Inverse Dwell — Feature Necesario"* con una tabla de *"Impacto en Código"* que lista lo que habría que cambiar. Todo eso ya está hecho.

Peor: la tabla afirma que Sentinel **no cambia** porque *"ve otro DwellExceeded"*. La implementación eligió un tipo de evento propio y Sentinel **sí** tiene que cambiar. Un agente que lea ese README y le crea, no va a tocar Sentinel — y la cadena se queda cortada, que es exactamente lo que pasó.

**Cambio:** reescribir la sección como descripción de lo que existe, con la corrección de `AD-3` explícita: *ComeBack es un tipo de hecho propio, y Vigilancia lo juzga por separado*. Ver `SPEC-05`.

---

## Criterios de aceptación

1. `grep -rn "vigia" README.md docs/` no devuelve nada.
2. `grep -rni "susan" --include="*.kt" .` sólo aparece en la cita textual de la enfermera.
3. Los documentos usan *come-back* / *vuelta a la cama*; *dwell inverso* aparece a lo sumo una vez, como aclaración.
4. Ninguna sección de `POLITICA-GUIDE.md` describe como resuelto algo que devuelve vacío, o lleva la marca de objetivo.
5. `jose-301-sitting-bed/README.md` describe come-back como implementado y dice que Sentinel lo juzga.
6. El README de `blueprints/` fija la convención de nombres de residentes y personal.
7. `LANG=C.UTF-8 ./gradlew check` verde después del renombre.

## Cómo se ejecuta

**No como un commit grande al final.** Cada punto se cierra junto con la spec que toca esa área:

| Punto | Se cierra con |
|---|---|
| 1 · README `vigia`/`files` | `SPEC-00` — es donde se agrega el requisito de UTF-8 al README |
| 2 · Susan | `SPEC-03` — que agrega blueprints nuevos y necesita la convención antes de elegir nombres |
| 3 · ComeBack | `SPEC-05` |
| 4 · Promete de más | `SPEC-03` y `SPEC-04` |
| 5 · Promete de menos | `SPEC-05` |

Si alguna spec se cierra sin su parte de ésta, **no está cerrada**.
