# BLUEPRINT Leyden - quick ref (copiar para nuevos servicios)

Ver `docs/specs/BLUEPRINT-leyden-java26.md` completo.

```bash
# 1. Build
./gradlew :bootstrap:bootJar :event-bridge:bootJar
./gradlew :engines:night-watch-runtime:bootJar

# 2. Leyden warmup (requiere postgres healthy para hub)
rm -rf ./cache* && docker compose -f aot-jvm/compose.three.yml build
docker compose -f aot-jvm/compose.three.yml up aotgen-hive aotgen-hub aotgen-bridge

# 3. Deploy 3 javas
docker compose -f aot-jvm/compose.three.yml up mana-hive mana-hub bridge -d
docker stats --no-stream | grep -E "mana|bridge|pg|nats"
curl http://localhost:18081/actuator/health && curl http://localhost:8080/actuator/health && curl http://localhost:8090/actuator/health
```

Tuning: `hive 256m/96m 400M limit`, `hub 512m/128m 700M`, `bridge 256m/96m 350M`. `requiresUnpack bcprov`, `exclude jna`, `hikari 5/2`, `RestClient` no `WebClient`.
