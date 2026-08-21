# 11 · Acta de la tercera sesión — el workspace real: componentes, hub y bus

**Objeto:** el dueño del producto plantó su topología y la sala la adoptó: **no es un monolito**. Es un conjunto de componentes que interactúan por un bus NATS, con el hub como System of Record. Esta acta registra las decisiones, qué documentos enmienda, y el hecho más importante de la sesión: **el workspace existe, compila y su guardián de pureza funciona** (raíz del repo `mana-hive/`).

---

## D11 — Topología de componentes (enmienda a 05 §4 y 10 §2)

La cadena de responsabilidad, con nombre propio por componente:

| Componente | Responsabilidad | Consume | Publica |
| --- | --- | --- | --- |
| **ia-cell** (borde, existente) | percibir: sensores + IA en la habitación | — | `perception.observation.v1.<bed>` |
| **scene-engine** | el gemelo digital: digerir percepción en hechos de escena (transiciones con histéresis, dwells derivados, presencia de staff, silencio del propio sensor) | observaciones + censo | `scene.fact.v1.<bed>` |
| **sentinel** | juzgar hechos contra las reglas efectivas de cada residente: incidencia / suceso / supresión con constancia; álgebra de episodios; presupuesto de fatiga | hechos + reglas | `sentinel.signal.v1.<bed>` |
| **vigia** | la conversación con humanos: ciclo de vida de la alerta, enrutamiento, escalada, cierre del lazo por presencia | incidencias + presencia | `alarm.event.v1.<alert>` |
| **hub** | **System of Record**: ingesta de TODO el bus al ledger Postgres; censo (1:1 adentro); política clínica event-sourced + `PolicyResolver`; crónica (veredictos = verdad de terreno); moviola | todo | `hub.policy.effective-rules.v1.<resident>`, `hub.census.snapshot.v1` |

**Modelo de verdad** (concilia la objeción 3 con la topología distribuida): el bus **transporta y amortigua** (retención por límites, ventana de dedupe de 10 min); el ledger del hub es **la única verdad** — auditoría, replay dorado y fuente de re-siembra cuando un engine reconstruye estado. Ningún componente confía en la retención del bus para la verdad. Las decisiones citan huellas (reglas, gemelo, versión de engine): reproducibles por máquina aunque vivan en procesos distintos.

## D12 — El código se escribe en inglés, siempre

Identificadores, paquetes, comentarios, SQL, YAML: inglés. El español queda para la prosa de diseño y el lenguaje hablado con el negocio. El glosario 02 §2 gana una columna de mapeo: Gemelo→`DigitalTwin`, Hecho de escena→`SceneFact`, Criterio→`EffectiveRules`/policy, Alerta→`AlarmEvent`/`AlertLifecycle`, Veredicto→verdict (chronicle), Censo→`CensusSnapshot`. Los nombres propios de componente (vigia, mana-hive) se conservan como marca.

## D13 — El workspace, construido y verificado

13 módulos Gradle, tres roles de convention plugin, build completo en verde con JDK toolchain 21 / Kotlin 2.2 / Spring Boot 4:

```text
mana-hive/
├── build-logic/                     # manahive.pure-domain · kotlin-common · spring-service
├── platform/
│   ├── domain-kernel/               # PURO: Decider, Engine, Explained, DecisionRecord, typed ids
│   ├── contracts/                   # PURO: el lenguaje publicado (Observation, SceneFact,
│   │                                #   SentinelSignal, AlarmEvent, EffectiveRules, CensusSnapshot)
│   └── messaging/                   # taxonomía de subjects + topología JetStream idempotente
├── hub/
│   ├── hub-domain/                  # PURO: PolicyResolver (capas, procedencia, huella)
│   └── hub-service/                 # Boot: ledger SoR (schema.sql), ingesta, censo, crónica, moviola
├── engines/
│   ├── scene-engine/{scene-domain, scene-service}
│   ├── sentinel/{sentinel-domain, sentinel-service}
│   └── vigia/{vigia-domain, vigia-service}
└── simulator/                       # DSL de escenarios + banco inicial (main source)
```

Verificado en esta sesión, no prometido: `gradle build` en verde; `verifyPurity` activo (un módulo puro con dependencia externa **rompe el build**); `:simulator:run` imprime el banco de escenarios con "la caída de las 03:00" ejecutable como datos.

Cada engine repite el patrón del paquete: **núcleo puro + cáscara Boot**. Los invariantes ganados sobreviven intactos y ahora viven en código: dwells derivados jamás cronómetros (`ClockSweeper`), silencio del sensor como hecho (`SignalLost`, `Unknown(SIGNAL_LOST)` ≠ `Unknown(SCENE)`), supresión con constancia (`SuppressedWithRecord`), resuelta absorbente (`AlertLifecycle`), peldaño terminal por tipo (`DeliveryPlan`), cierre de libros por noche (`NightOpened`/`NightClosed`), y `Explained<T>` en toda salida de engine.

## D14 — Qué enmienda esta acta

| Documento | Qué cambia |
| --- | --- |
| 05 (ledger-first) | El principio sobrevive traducido: un solo SoR (el hub). NATS deja de ser "frontera degradada" y es el tejido entre componentes — la topología B pasa de futuro a presente |
| 07/08 (workspace) | Sustituidos por el árbol real de D13; los roles de plugin y el guardián sobreviven tal cual |
| 09/10 (sprint y C4) | Historias y UCs siguen válidos; el corte por componente es ahora físico. S2 (ledger + conformance kit) vive en `hub`; S4/S5 en `scene-engine`; S6 en `vigia`; S7 en `simulator`; el UC-04 se parte entre sentinel (juzgar) y vigia (alertar) |

**Sigue en pie para el Sprint 1:** el objetivo (la caída de las 03:00 de punta a punta, reinicio incluido), el protocolo (vos implementás, la sala revisa contra 09 §8), y el primer PR esperado: **el conformance kit del ledger antes que el adaptador Postgres**. Los puntos de partida están marcados en el código: `LedgerPort` (hub), `SceneInterpreter`/`ClockSweeper` (scene), `SentinelEvaluator` (sentinel), `AlertLifecycle` (vigia) — cada KDoc lleva su carta de responsabilidad.
