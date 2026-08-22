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

Orden recomendado: 01 → 02 → 03 → 04 → 05 → 06. Cada documento es autosuficiente pero referencia a los demás por número.

## La frase que ordena el paquete

> El sistema existe para una sola cosa: **que la persona correcta llegue a la habitación correcta a tiempo, con la menor cantidad de falsas alarmas posible, y que después podamos demostrar por qué cada decisión se tomó**. Todo lo que no sirve a esa frase es accidental.
