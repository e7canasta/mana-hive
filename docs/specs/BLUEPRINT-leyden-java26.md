# Blueprint: Java 26 + Project Leyden (AOTCache) para 8Gi

**Stack validado:** `mana-hive:18081` + `mana-hub:8080` + `bridge:8090` + `nats:4222` + `postgres:5432` = `~832M RSS` en `i7 15Gi` (objetivo 8Gi). Medición real `docker stats` con `openjdk 26.0.1`.

## 1. Principios

*   **Leyden != Spring AOT != GraalVM.** Leyden es caché JVM (`-XX:AOTCacheOutput`/`-XX:AOTCache`) - sigue JVM, startup -30% y metaspace -81% ( `54M→9.7M` hive). Spring AOT es codegen, GraalVM es binario cerrado (requiere `reflect-config`).
*   **Heap manda más que Leyden.** Sin `-Xmx`, JVM reserva `25% RAM` = `4Gi` en 15Gi host. `Xmx256m` baja `hive 556M→341M`, `hub 1.1Gi→508M` sin Leyden. Combo `Xmx+Leyden+Metaspace` clava `hive 170M/400M`, `hub 352M/700M`.

## 2. Tuning por servicio (validado)

| Servicio | JAR | Clases | Xmx | MaxMetaspace | AOT cache | RSS | Límites Docker |
|---|---|---|---|---|---|---|---|
| `mana-hive` (`engines/night-watch-runtime`) | `40M` | `12527` | `256m` | `96m` | `77M` | `170M` | `400M` |
| `mana-hub` (`bootstrap`) | `80M` | `22819` | `512m` | `128m` | `153M` | `352M` | `700M` |
| `bridge` (`event-bridge`) | `53M` | `~12k` | `256m` | `96m` | `70M` | `223M` | `350M` |

*   **Hikari:** `bootstrap/src/main/resources/application.yml:13` `maximum-pool-size:20→5` `minimum-idle:5→2` para 1-4 residentes (ahorro `~80M` heap).
*   **WebFlux eliminado:** `bootstrap/build.gradle.kts:36` `spring-boot-starter-webflux` → `RestClient` (`HubPolicyPublisher.kt:8`, `NatsIngestService.kt:12`). `web+webflux` duplica Netty/Reactor y rompe regiones `ro` Leyden (`Unable to allocate from 'ro' region` con 23k clases).

## 3. Parche Gradle (copiar a nuevos servicios)

`build-logic/.../hub.spring-service.gradle.kts:11` y `bootstrap/build.gradle.kts:36`:
```kts
// JNA solo es CLI batch (mordant) - nunca en runtime
configurations.named("runtimeClasspath") {
    exclude(group = "net.java.dev.jna", module = "jna")
    exclude(group = "net.java.dev.jna", module = "jna-platform")
}
tasks.withType<org.springframework.boot.gradle.tasks.bundling.BootJar> {
    requiresUnpack("**/bcprov-*.jar") // BouncyCastle firmado bloquea Leyden "Signed JAR"
}
```
Y en dependencia si arrastra batch:
```kts
implementation(project(":engines:recorder:recorderBatch")) {
    exclude(group = "net.java.dev.jna", module = "jna")
}
```
Para `mana-hive` mismo parche en `engines/night-watch-runtime/build.gradle.kts:13`.

## 4. Docker Leyden (plantilla `aot-jvm/`)

`Dockerfile.aotgen:37` y `Dockerfile.deploy:35`:
```dockerfile
# training
RUN java -Xmx256m -XX:MaxMetaspaceSize=96m -XX:AOTCacheOutput=/cache/app.aot -Dspring.context.exit=onRefresh -jar app.jar
# deploy hub necesita 512m/256m por JPA
CMD ["java","-Xmx512m","-XX:MaxMetaspaceSize=128m","-XX:AOTCache=/cache/app.aot","-jar","app.jar"]
```
`aot-jvm/compose.three.yml:52` generadores `aotgen-*` con `user: "0:0"` + `volumes: ./cache:/cache` + `depends_on: service_completed_successfully` y `SPRING_DATASOURCE_URL` inyectada también en `aotgen-hub:60` (si no, `onRefresh` falla `ConnectionRefused` y no genera cache `153M`). Fix `chmod 777 ./cache*`.

`compose.three.yml:22` infra `postgres:17-alpine 5433:5432` + `nats:latest -js 4223:4222` (evita colisión con `5432` host).

## 5. Checklist nuevo servicio

1.  `build.gradle.kts` usa `hub.spring-service` con `requiresUnpack bcprov` + `exclude jna`.
2.  `application.yml` Hikari `5/2`, `jackson` `non_null`, `open-in-view:false`.
3.  Solo un starter web (`web` XOR `webflux`), usar `RestClient` no `WebClient`.
4.  `Dockerfile.*` con `Xmx` acorde (`256m` stateless, `512m` JPA) + `MaxMetaspace`.
5.  `compose` con `aotgen` + `deploy.limits.memory` (`400M/700M/350M` total `<1.6Gi`).
6.  CI: `rm -rf ./cache*/app.aot && ./gradlew bootJar && docker compose -f aot-jvm/compose.three.yml build && docker compose up aotgen-*` - regenera siempre `.aot` si cambia código.

## 6. Telemetría validada

`java -XX+AOTCacheOutput` `onRefresh` `76-77M` hive, `160M` hub con `Xmx512m`. `jcmd VM.metaspace` `54M→9.7M`. `docker stats` final `hive 170M hub 352M bridge 223M nats 17M pg 66M = 832M`.

No usar `GraalVM nativeCompile` con `jna`/`bcprov` sin `reflect-config` - Leyden da 80% beneficio sin reescribir.
