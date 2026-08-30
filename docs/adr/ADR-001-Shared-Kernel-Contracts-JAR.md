# ADR-001: Shared Kernel via Contracts JAR

**Estado:** Propuesto  
**Fecha:** 2026-08-30  
**Decisor:** Equipo mana-hive

## Contexto

mana-hive publica eventos tipados (SceneEvent, SentinelSignal, AlarmEvent, RecordingCommand, EvidenceRecord). mana-hub necesita consumirlos. Dos opciones:

1. **Traducir en bridge** — bridge deserializa tipos de hive y mapea a DTOs propios de hub
2. **Compartir tipos** — publicar JAR de contracts que ambos usan

## Decisión

Publicar `mana-hive/contracts` como JAR. mana-hub depende de él. Ambos usan las mismas clases Kotlin.

```kotlin
// mana-hub/build.gradle.kts
dependencies {
    implementation("com.manahive:contracts:1.0.0")
}
```

## Consecuencias

### Positivas
- ✅ Sin traducción en bridge — forward directo
- ✅ Type safety compile-time — si hive agrega un subtype, hub lo ve al compilar
- ✅ Single source of truth — un solo set de modelos
- ✅ Menos código — eliminamos DTOs paralelos y translators

### Negativas
- ⚠️ Acoplamiento directo — si hive cambia un tipo, hub debe recompilar
- ⚠️ Versioning estricto — semver obligatorio, breaking changes requieren nueva major version
- ⚠️ Despliegue coordinado — ambas partes deben upgrade en ventanas compatibles

### Mitigaciones
- Usar semver estricto
- Mantener backward compatibility en contracts (agregar, no modificar)
- Documentar cambios en CHANGELOG del JAR

## Alternativas Consideradas

### Alternativa 1: DTOs paralelos con traducción
- ❌ Mantener dos set de modelos es caro
- ❌ Riesgo de desincronización
- ❌ Traducción en runtime (performance)

### Alternativa 2: Code generation desde schema
- ❌ Complejidad innecesaria
- ❌ Kotlin sealed interfaces no se generan fácilmente desde XSD/JSON Schema

## Referencias

- DDD: Shared Kernel pattern
- Similar a SOAP/WSDL pero con código Kotlin en vez de XML
- Ejemplo: Spring Cloud Contract, but without the contract testing overhead
