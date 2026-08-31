FROM gradle:8.10-jdk25 AS build
WORKDIR /app
COPY . .
RUN ./gradlew :examples:scenario-simulator:installDist --console=plain

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/examples/scenario-simulator/build/install/scenario-simulator /app
ENV HUB_URL=http://mana-hub:8080 NATS_URL=nats://mana-nats:4222 CATALOG=/app/catalog/01-e1-vuelve-solo.yaml
ENTRYPOINT ["/app/bin/scenario-simulator"]
