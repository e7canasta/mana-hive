plugins {
    id("manahive.kotlin-common")
}

// every public type in a pure module is API — and therefore a design decision
kotlin { explicitApi() }

/**
 * Purity guard: a pure-domain module may depend only on the Kotlin stdlib and
 * other pure modules of this build. Spring, JDBC, NATS, HTTP: impossible, not
 * discouraged. Fine-grained purity (Instant.now(), mutability) is Konsist's job.
 */
val verifyPurity by tasks.registering {
    val offenders = configurations.named("runtimeClasspath").map { cfg ->
        cfg.allDependencies
            .filterIsInstance<ExternalModuleDependency>()
            .filterNot { it.group == "org.jetbrains.kotlin" }
            .map { "${it.group}:${it.name}" }
    }
    doLast {
        check(offenders.get().isEmpty()) {
            "Pure domain module ${project.path} declares external dependencies: ${offenders.get()}"
        }
    }
}
tasks.named("check") { dependsOn(verifyPurity) }
