# Scenario Simulator — Paquetito de pruebas punta a punta (caja negra)

> **Para arrancar una sesión fresca sin cargar contexto.** Lee esto y tenés todo.

## Qué es

Corredor **caja negra por API + NATS** que explica el negocio. No toca la base directo: va por `hub` (`/api/v1/...`) y `hive` (`NATS`).

* **Lee `sequences/catalog/*.yaml`** con metadata arriba (perfil, limpieza, fecha, verificación).
* **Resuelve `autoDate`** por `GET /api/v1/episodes?residentId=jose&from/to` → `max(occurredAt)+1d` (aisla por `residentId` + `fecha`, no compara JSON crudo).
* **Aplica perfil** por `PUT /api/profiles/jose` (ej. `jose-rampa-v6.json`).
* **Limpia** por `POST /api/v1/admin/clean?residentId=jose` (episodios + `scene_events`).
* **Publica** `test.time.v1` + `perception.observation.v1.bed-4` por NATS (`hive` lo consume).
* **Verifica** por `GET /api/v1/episodes?residentId=jose&from/to` → parsea a `DTO` (`platform/contracts`) y compara **secuencialidad + severidad + status** con tolerancia `±1s` (no `JSON==JSON`).

## Dónde vive

* **Proyecto:** `examples/scenario-simulator` (dentro de `mana-hive`, `C-Grey`).
* **Código:** `src/main/kotlin/simulator/{DateResolver,ProfileApplier,Cleaner,ExpectVerifier,Main}.kt`
* **Catálogo:** `examples/jose-e1/sequences/catalog/*.yaml` (cada uno con comentario de negocio arriba para director/enfermería).
* **Perfiles:** `mana-dist/config/mana-hive/profiles/jose-*.json` y `shared/profiles/` (exportable `GET /api/profiles/jose`).
* **Hub evoluciona:** `observation/IntegrationService.kt` persiste `scene_events` + `signal` `EPISODE_COMPLICATED` → `episodes` (hive/bridge ya estaban bien).

## Cómo usar

```bash
# 1. Levanta dev
docker compose -f mana-dist/compose.dev.yml up -d

# 2. Corre un escenario (ej. noche normal)
./gradlew :examples:scenario-simulator:run --args="examples/jose-e1/sequences/catalog/03-noche-normal-1-episodio.yaml" --console=plain

# 3. Catálogo en yaml (arriba):
profile: "config/mana-hive/profiles/jose-rampa-v6.json"
clean: true
autoDate: true
start: "2024-01-19T22:00:00Z"
expect:
  episodes: [{status: resolved, severity: WARNING, count: 1}]
steps:
  - useManual: "2024-01-19T22:00:00Z"
  - obs: {kind: SITTING_IN_BED}
```

Cada `yaml` tiene arriba `#` con relato para mesa de café con director (qué hizo José, qué debía pasar).

## Qué ya está probado

* `E1` vuelve solo `17m` → `1 pending 23:32` (ComeBack)
* `E2` con enfermera `STAFF_ENTERED` → `resolved STAFF_PRESENT`
* `Staff presente desde inicio` → `0` `SuppressedWithRecord` (no abre)
* `Normal 1 episodio + 2 cortas` → `1 resolved`
* `48h mixto` → `3` mixtos sin colgados
* `Escalado SITTING→STANDING` → `EPISODE_COMPLICATED CRITICAL`

Falla conocida a pulir: `catalog` por `FromFile` dio `0` por reutilizar fecha `2024-01-15` y `v6` (SITTING onEntry). Con `autoDate` + `clean` por API debe dar `1`.

## Próximo paso

Agregar `include(":examples:scenario-simulator")` en `settings.gradle.kts:69`, correr `clean` por API y dejar los 6 `catalog` en verde como regresión punta a punta.
