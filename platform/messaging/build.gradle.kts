plugins { id("manahive.kotlin-common") }

dependencies {
    api(projects.platform.contracts)
    api(libs.jnats)
    api(libs.jackson.kotlin)
    api(libs.jackson.jsr310)

    // Spring solo para NatsClientConfiguration: la declaracion de beans vive
    // junto al codigo NATS que declara, y los seis servicios la importan en vez
    // de copiar el mismo @Bean seis veces. compileOnly porque este modulo no
    // arrastra Spring a quien no lo tenga.
    compileOnly(platform("org.springframework.boot:spring-boot-dependencies:4.0.1"))
    compileOnly("org.springframework.boot:spring-boot-autoconfigure")
    compileOnly("org.springframework:spring-context")
}
