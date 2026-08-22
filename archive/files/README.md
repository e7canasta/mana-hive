# Registro v3 — Paquete de diseño de la sala

**Sesión:** re-diseño desde el problema, no desde la implementación heredada · Agosto 2026
**Reemplaza:** `registro-v2-diseno-arquitectura.md` en su parte estratégica y táctica. El veredicto de stack de v2 (Kotlin + Spring Boot 4 + Modulith, Postgres, DuckDB) **sobrevive a esta revisión** y no se repite aquí; todo lo demás se re-deriva.

## Qué pasó en esta sesión

El documento v2 cometió el pecado que la propia sala señaló al revisarlo: transcribió la estructura del sistema existente (sus once contextos, su orquestador, su bus) en lugar de volver al dominio y preguntarse si esa estructura era la correcta. Esta versión se enamora del problema — la noche de una residencia, el silencio como síntoma, el lazo que se cierra con una persona llegando a una habitación — y deja que el diseño emerja de ahí. Seis objeciones se levantaron en la pizarra; las seis produjeron decisiones nuevas.

## Cómo leer el paquete

| Artefacto | Contenido | Léelo si preguntás… |
| --- | --- | --- |
| `01-acta-sala-diseno.md` | Las seis objeciones al v2, su diagnóstico y su resolución; qué sobrevive | ¿Qué cambió y por qué? |
| `02-dominio-estrategico.md` | El problema re-derivado: lenguaje ubicuo, event storming, puntos calientes, mapa de contextos por capacidades, mapeo viejo→nuevo | ¿Cuáles son los bounded contexts correctos? |
| `03-modelo-tactico.md` | Agregados como Deciders, flujos event-sourced con criterio explícito, process managers con nombre, ciclo de vida de la Alerta | ¿Por qué event sourcing y por qué no un orquestador? |
| `04-motores.md` | Carta de responsabilidad de cada motor, la complejidad que cada uno debe dominar, interfaces completas, registro de decisión y explicabilidad | ¿Los motores, en serio? |
| `05-plataforma-ejecucion.md` | Ledger-first: el event store como bus interno, NATS degradado a la frontera, garantías, simulacros de fallo, topologías de despliegue | ¿Por qué no NATS JetStream como columna vertebral? |
| `06-evaluacion-e-implementacion.md` | Replay dorado, modo sombra, simulador de noches, banco de conformidad de puertos, métricas clínicas con la revisión humana como etiqueta, rebanadas de implementación | ¿Cómo lo evalúo, inspecciono e implemento? |
| `07-workspace-gradle.md` | La forma física: roles de módulo como convention plugins, guardián de pureza, grafo permitido, DSLs Kotlin (escenarios, dado-cuando-entonces, plantillas de criterio), suites de test | ¿Cómo se escribe esto en Gradle y Kotlin? |
| `08-acta-segunda-sesion.md` | El v3 revisado con sus propias armas: mapa como hipótesis (D7), cierre de libros por jornada (D8), workspace evolutivo de seis módulos (D9), XP normativo (D10) | ¿Qué objeciones sobrevivió el propio v3? |
| `09-bigpicture-y-sprint-1.md` | Big picture UML por componentes e interfaces, contratos en seudocódigo, modelo de datos del release, épicas E1–E3 y el Sprint 1 completo con historias XP y protocolo de revisión | ¿Por dónde empiezo el lunes? |
| `10-diseno-c4-modelo-dominio.md` | El diseño entregable: C4 niveles 1–4, modelo de dominio por contexto con estereotipos, casos de uso formales UC-01..08 con corte de implementación, catálogo de patrones Kotlin/Spring | ¿Cuál es EL diseño que implemento? |
| `11-acta-workspace-real.md` | La topología adoptada: componentes + hub SoR + bus NATS (ia-cell → scene-engine → sentinel → vigia); código en inglés; el workspace REAL construido y compilando en la raíz del repo | ¿Dónde está el código y qué manda ahora? |

Orden recomendado: 01 → 02 → 03 → 04 → 05 → 06 → 07 → 08 → 09 → 10. Para implementar: 11 manda sobre la topología y el workspace; 10 sobre el modelo de dominio y los UCs; 09 sobre el orden de historias. El acta 08 enmienda a 02 (§5), 03 (§3) y 07 (§2); esas enmiendas prevalecen. Cada documento es autosuficiente pero referencia a los demás por número.

## La frase que ordena el paquete

> El sistema existe para una sola cosa: **que la persona correcta llegue a la habitación correcta a tiempo, con la menor cantidad de falsas alarmas posible, y que después podamos demostrar por qué cada decisión se tomó**. Todo lo que no sirve a esa frase es accidental.
