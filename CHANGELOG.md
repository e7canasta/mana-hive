# Changelog

## [Unreleased] — 2026-08-30

### Fixed
- **Sentinel: supresión por presencia de personal** — `SentinelEvaluatorImpl` ahora no abre episodio si `staffPresent` es true en la cama. Antes `evaluateNewEpisode()` y `evaluateDeadline()` abrían `EPISODE_OPENED` aunque la enfermera ya estuviera (`STAFF_ENTERED` previo), lo que generaba un falso episodio vigilado. Ahora emite `SuppressedWithRecord(STAFF_PRESENT)` (`EpisodeLedger.staffPresentBeds` + `EpisodeLedger.isStaffPresent`).

### Changed
- `EpisodeLedger` guarda `staffPresentBeds: Set<BedId>` incluso sin episodio abierto, para poder suprimir aperturas futuras. `evaluateStaffPresence` / `evaluateStaffLeft` actualizan ese set siempre, no solo cuando hay episodio abierto.

### Added
- Escenarios NATS para validar: `MainNatsScenarioE2` (se queda sentado + enfermera → cierre `STAFF_OR_SAFE`), `MainNatsNormalNight` (noche normal 1 episodio + 2 cortas), `MainNats48hMixed` (48h mixto: cortas / largas / staff sola), `MainNatsStaffPresentNoEpisode` (staff presente desde inicio → 0 episodios, supresión).
- Script `mana-dist/scripts/clean-jose.sh` para dejar `episodes`/`scene_events` en cero entre pruebas.

### Verified
- `SentinelEvaluatorSpec` existente sigue en verde (13 casos `STAFF_OR_SAFE`). Nuevo comportamiento cubierto por `SuppressedWithRecord` y `EpisodeLedger` con `staffPresentBeds`. `E1` (sin staff → abre) sigue abriendo `pending` 23:32; `staff-presente` → 0 episodios.
- Build: `./gradlew :engines:sentinel:sentinel-domain:compileKotlin` y `:engines:night-watch-runtime:bootJar` OK, redeploy `mana-dist/compose.dev.yml` con `aot-jvm/engines-night-watch-runtime.jar`.

