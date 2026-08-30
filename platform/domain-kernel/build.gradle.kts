plugins {
    id("manahive.pure-domain")
    `maven-publish`
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "com.manahive"
            artifactId = "domain-kernel"
            version = "1.0.0"
            from(components["java"])
        }
    }
}
