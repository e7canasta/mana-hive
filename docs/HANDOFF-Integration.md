# Handoff: mana-hive ↔ mana-hub Integration

**Fecha:** 2026-08-30  
**Próxima sesión:** Implementar Sprint 1 (Contracts JAR)

## Estado Actual

### Lo que funciona
- ✅ Arquitectura hexagonal completa (interfaces → core → adapters)
- ✅ 6 mains de test funcionando
- ✅ 8 blueprints compilando
- ✅ Time control vía NATS
- ✅ Profile change vía NATS
- ✅ Event recording vía NATS
- ✅ Hub eliminado, PolicyLayers movido a contracts

### Lo que falta
- 🔴 Contracts JAR no publicado
- 🔴 Hub no consume tipos de hive
- 🔴 Bridge no tiene routing inteligente
- 🔴 Profile endpoints no implementados
- 🔴 Outbox pattern no implementado

## Próximos Pasos (Sprint 1)

### 1. Configurar Gradle Publication

**Archivo:** `platform/contracts/build.gradle.kts`

```kotlin
plugins {
    `java-library`
    `maven-publish`
}

group = "com.manahive"
version = "1.0.0"

publishing {
    publications {
        create<MavenPublication>("contracts") {
            from(components["java"])
            artifactId = "contracts"
        }
    }
}
```

### 2. Publicar a Maven Local

```bash
./gradlew :platform:contracts:publishToMavenLocal
```

### 3. Verificar en mana-hub

```kotlin
// mana-hub/build.gradle.kts
repositories {
    mavenLocal()
}

dependencies {
    implementation("com.manahive:contracts:1.0.0")
}
```

### 4. Test de Consumo

```kotlin
// mana-hub test
import com.manahive.contracts.scene.SceneEvent
import com.manahive.contracts.sentinel.SentinelSignal

val event = SceneEvent.TransitionDetected(
    bed = BedId("bed-4"),
    night = NightId("night-1"),
    at = Instant.now(),
    from = PersonState.Lying,
    to = PersonState.SittingInBed,
)
```

## Archivos Clave

```
contracts:    platform/contracts/src/main/kotlin/com/manahive/contracts/
core:         engines/night-watch-runtime/src/main/kotlin/com/manahive/runtime/
tests:        examples/jose-e1/src/main/kotlin/jose301/
blueprints:   blueprints/level-thresholds/
ADRs:         docs/adr/
```

## Comandos Útiles

```bash
# Todos los mains
for main in MainKt MainFromJsonKt MainPipelineKt MainResidentRuntimeKt MainNightWatchRuntimeKt MainNightWatchServiceCoreKt; do
  ./gradlew :examples:jose-e1:run -Pmain=jose301.$main
done

# Blueprints
./gradlew :blueprints:level-thresholds:run

# NATS integration
# Terminal 1: ./gradlew :engines:night-watch-runtime:bootRun
# Terminal 2: ./gradlew :examples:jose-e1:run -Pmain=jose301.MainColdBootProfileKt
```

## Decisiones Pendientes

1. **Versioning strategy** — semver estricto o calendar versioning?
2. **Backward compatibility** — ¿agregar siempre, nunca modificar?
3. **Dependencia en mana-hub** — ¿implementation o api?
4. **Testing del JAR** — ¿tests de compatibilidad automática?

## Contacto

- **mana-hive owner:** Equipo mana-hive
- **mana-hub owner:** Equipo mana-hub
- **Bridge owner:** Equipo mana-hub (consume contratos de hive)
