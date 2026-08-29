plugins {
    id("manahive.pure-domain")
    `maven-publish`
}

// Este modulo es el contrato que implementa el sistema de registro externo.
// Se publica como jar para que ese equipo compile contra el, en vez de
// deducir la estructura de un ejemplo de JSON.
publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "com.manahive"
            artifactId = "profile-api"
            version = "1.0.0-SNAPSHOT"
            from(components["java"])
        }
    }
}
