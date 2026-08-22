# Overview

Relevant source files

- [](https://github.com/pbaalerta-wq/hubp/blob/cc2edd14/HANDOFF.md?plain=1)
- [](https://github.com/pbaalerta-wq/hubp/blob/cc2edd14/Justfile)
- [](https://github.com/pbaalerta-wq/hubp/blob/cc2edd14/README.md?plain=1)
- [](https://github.com/pbaalerta-wq/hubp/blob/cc2edd14/bins/mana-hub/src/main.rs)
- [](https://github.com/pbaalerta-wq/hubp/blob/cc2edd14/docs/arquitectura/memoria-diseno.md?plain=1)
- [](https://github.com/pbaalerta-wq/hubp/blob/cc2edd14/docs/contextos/README.md?plain=1)
- [](https://github.com/pbaalerta-wq/hubp/blob/cc2edd14/docs/contextos/ctx-identidad.md?plain=1)
- [](https://github.com/pbaalerta-wq/hubp/blob/cc2edd14/docs/funcional/README.md?plain=1)
- [](https://github.com/pbaalerta-wq/hubp/blob/cc2edd14/docs/funcional/casos-uso/ctx-identidad.md?plain=1)
- [](https://github.com/pbaalerta-wq/hubp/blob/cc2edd14/docs/funcional/contextos/ctx-identidad.md?plain=1)
- [](https://github.com/pbaalerta-wq/hubp/blob/cc2edd14/docs/funcional/modelo-dominio/README.md?plain=1)
- [](https://github.com/pbaalerta-wq/hubp/blob/cc2edd14/docs/funcional/modelo-dominio/ctx-identidad.md?plain=1)
- [](https://github.com/pbaalerta-wq/hubp/blob/cc2edd14/docs/funcional/modulos/README.md?plain=1)
- [](https://github.com/pbaalerta-wq/hubp/blob/cc2edd14/docs/funcional/modulos/mana-hub.md?plain=1)
- [](https://github.com/pbaalerta-wq/hubp/blob/cc2edd14/docs/funcional/north-star.md?plain=1)
- [](https://github.com/pbaalerta-wq/hubp/blob/cc2edd14/docs/reference/architecture.md?plain=1)
- [](https://github.com/pbaalerta-wq/hubp/blob/cc2edd14/docs/reference/crate-map.md?plain=1)
- [](https://github.com/pbaalerta-wq/hubp/blob/cc2edd14/just/hub.just)
- [](https://github.com/pbaalerta-wq/hubp/blob/cc2edd14/just/justfile)
- [](https://github.com/pbaalerta-wq/hubp/blob/cc2edd14/var/log/hub.log)

The **mana-hub** System of Record (SoR) is a modular, transactional, and verifiable backend architecture built in Rust. It serves as the authoritative source of truth for identity, facility structure, population, and clinical evidence within the Virtual Rounds ecosystem [HANDOFF.md8-10](https://github.com/pbaalerta-wq/hubp/blob/cc2edd14/HANDOFF.md?plain=1#L8-L10)

The system is designed to be a **Modular Monolith** first, where domain logic is partitioned into 11 distinct bounded contexts (`ctx-*`) to ensure strict ownership and maintainable growth without the overhead of microservices [docs/arquitectura/memoria-diseno.md98-110](https://github.com/pbaalerta-wq/hubp/blob/cc2edd14/docs/arquitectura/memoria-diseno.md?plain=1#L98-L110)

### Purpose and Scope

The Hub is the sole owner of the Registry. It receives commands, conserves state, and determines the clinical meaning of sensor events [README.md39-41](https://github.com/pbaalerta-wq/hubp/blob/cc2edd14/README.md?plain=1#L39-L41)

- **It IS:** The source of truth for RBAC, facility planograms, resident assignments, alarm policies, and audit logs.
- **It IS NOT:** A video server (streams flow directly from IA cells to panels) or an analytical layer (handled by Parquet/DuckDB) [README.md43-45](https://github.com/pbaalerta-wq/hubp/blob/cc2edd14/README.md?plain=1#L43-L45)

### Four-Binary Architecture

The system follows a distributed topology powered by **NATS JetStream** for asynchronous event mesh communication and **SQLite** for persistent storage [docs/reference/architecture.md3-4](https://github.com/pbaalerta-wq/hubp/blob/cc2edd14/docs/reference/architecture.md?plain=1#L3-L4)

|Binary|Role|State|
|---|---|---|
|`mana-hub`|HTTP API server, SQLite persistence, and event storage [docs/reference/architecture.md31](https://github.com/pbaalerta-wq/hubp/blob/cc2edd14/docs/reference/architecture.md?plain=1#L31-L31)|Persistent (SQLite)|
|`mana-engine`|DigitalTwin and FSM evaluation engine for sensor data [docs/reference/architecture.md32](https://github.com/pbaalerta-wq/hubp/blob/cc2edd14/docs/reference/architecture.md?plain=1#L32-L32)|Stateless|
|`mana-sentinel`|Rule evaluation, incident management, and alert creation [docs/reference/architecture.md33](https://github.com/pbaalerta-wq/hubp/blob/cc2edd14/docs/reference/architecture.md?plain=1#L33-L33)|Stateless|
|`mana-vigilancia`|Notification processing and delivery lifecycle [docs/reference/architecture.md34](https://github.com/pbaalerta-wq/hubp/blob/cc2edd14/docs/reference/architecture.md?plain=1#L34-L34)|Stateless|

**System Event Flow** The following diagram illustrates how raw sensor data (`evt_perception`) is transformed into clinical alerts through the binary mesh.

Sources: [docs/arquitectura/memoria-diseno.md28-50](https://github.com/pbaalerta-wq/hubp/blob/cc2edd14/docs/arquitectura/memoria-diseno.md?plain=1#L28-L50) [docs/reference/architecture.md7-25](https://github.com/pbaalerta-wq/hubp/blob/cc2edd14/docs/reference/architecture.md?plain=1#L7-L25) [README.md7-33](https://github.com/pbaalerta-wq/hubp/blob/cc2edd14/README.md?plain=1#L7-L33)

### Logical Layers and Code Entities

The codebase is organized into layers that enforce a strict Directed Acyclic Graph (DAG) dependency model.

|Layer|Components|Role|
|---|---|---|
|**Layer 0**|`mana-kernel`, `mana-storage`|Shared primitives and DB pooling.|
|**Layer 1**|`ctx-*` (e.g., `ctx-identidad`)|Bounded contexts containing domain logic and Diesel schemas.|
|**Layer 2**|`mana-nats`, `mana-observation`|Messaging and sensor event tracking.|
|**Layer 3**|`mana-app`, `mana-http`|Use case orchestration and Axum transport.|
|**Layer 4**|`mana-hub` (bin)|The final binary assembly and startup logic.|

**Code Entity Mapping** This diagram maps the natural language "Request Lifecycle" to specific Rust modules and crates.

Sources: [docs/arquitectura/memoria-diseno.md150-165](https://github.com/pbaalerta-wq/hubp/blob/cc2edd14/docs/arquitectura/memoria-diseno.md?plain=1#L150-L165) [docs/reference/crate-map.md81-112](https://github.com/pbaalerta-wq/hubp/blob/cc2edd14/docs/reference/crate-map.md?plain=1#L81-L112) [bins/mana-hub/src/main.rs133-145](https://github.com/pbaalerta-wq/hubp/blob/cc2edd14/bins/mana-hub/src/main.rs#L133-L145)

### Wiki Navigation

- **[Getting Started](https://deepwiki.com/pbaalerta-wq/hubp/1.1-getting-started)**: Instructions for local development using `just` tasks, managing `hub.sqlite`, and running the `mana-cli` [README.md54-75](https://github.com/pbaalerta-wq/hubp/blob/cc2edd14/README.md?plain=1#L54-L75)
- **[System Architecture](https://deepwiki.com/pbaalerta-wq/hubp/1.2-system-architecture)**: Deep dive into the four-binary topology, NATS JetStream topics (`evt_scene`, `evt_notif`), and the "no cross-context" rule [docs/reference/architecture.md68-85](https://github.com/pbaalerta-wq/hubp/blob/cc2edd14/docs/reference/architecture.md?plain=1#L68-L85)
- **[Crate Map and Dependency Graph](https://deepwiki.com/pbaalerta-wq/hubp/1.3-crate-map-and-dependency-graph)**: A comprehensive reference of all 32 workspace crates and the `xtask verificar-contextos` architectural enforcement [docs/reference/crate-map.md3-79](https://github.com/pbaalerta-wq/hubp/blob/cc2edd14/docs/reference/crate-map.md?plain=1#L3-L79)

### Core Rules

1. **Context Isolation:** No `ctx-*` crate may depend on another `ctx-*` crate [README.md158-159](https://github.com/pbaalerta-wq/hubp/blob/cc2edd14/README.md?plain=1#L158-L159)
2. **Stateless Workers:** All workers must persist state exclusively via the Hub's HTTP API; they do not have direct DB access [docs/reference/architecture.md70-71](https://github.com/pbaalerta-wq/hubp/blob/cc2edd14/docs/reference/architecture.md?plain=1#L70-L71)
3. **Kernel Purity:** `mana-kernel` must remain free of business logic (e.g., no `Resident` or `Alert` types) [README.md160-162](https://github.com/pbaalerta-wq/hubp/blob/cc2edd14/README.md?plain=1#L160-L162)
4. **Audit Integrity:** Mutations and audit logs must be recorded within the same SQLite transaction [docs/funcional/README.md66](https://github.com/pbaalerta-wq/hubp/blob/cc2edd14/docs/funcional/README.md?plain=1#L66-L66) via `mana-app` coordination.

Sources: [HANDOFF.md68-75](https://github.com/pbaalerta-wq/hubp/blob/cc2edd14/HANDOFF.md?plain=1#L68-L75) [docs/arquitectura/memoria-diseno.md125-147](https://github.com/pbaalerta-wq/hubp/blob/cc2edd14/docs/arquitectura/memoria-diseno.md?plain=1#L125-L147) [README.md156-168](https://github.com/pbaalerta-wq/hubp/blob/cc2edd14/README.md?plain=1#L156-L168) [docs/funcional/north-star.md70-88](https://github.com/pbaalerta-wq/hubp/blob/cc2edd14/docs/funcional/north-star.md?plain=1#L70-L88)

Dismiss

Refresh this wiki

This wiki was recently refreshed. Please wait 8 days to refresh again.

### On this page

- [Overview](https://deepwiki.com/pbaalerta-wq/hubp/1-overview#overview)
- [Purpose and Scope](https://deepwiki.com/pbaalerta-wq/hubp/1-overview#purpose-and-scope)
- [Four-Binary Architecture](https://deepwiki.com/pbaalerta-wq/hubp/1-overview#four-binary-architecture)
- [Logical Layers and Code Entities](https://deepwiki.com/pbaalerta-wq/hubp/1-overview#logical-layers-and-code-entities)
- [Wiki Navigation](https://deepwiki.com/pbaalerta-wq/hubp/1-overview#wiki-navigation)
- [Core Rules](https://deepwiki.com/pbaalerta-wq/hubp/1-overview#core-rules)

Ask Devin about pbaalerta-wq/hubp

Fast

pbaalerta-wq/hubp | DeepWiki

Syntax error in textmermaid version 11.7.0

Syntax error in textmermaid version 11.7.0

Syntax error in textmermaid version 11.7.0

Syntax error in textmermaid version 11.7.0

Syntax error in textmermaid version 11.7.0

Syntax error in textmermaid version 11.7.0

Syntax error in textmermaid version 11.7.0

Add to ContextPress Q