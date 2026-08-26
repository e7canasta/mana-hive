plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        allWarningsAsErrors = true
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

// nested modules get fully-qualified artifact names (engines-vigia-vigia-domain…)
base.archivesName = project.path.removePrefix(":").replace(":", "-")

tasks.withType<Test>().configureEach { useJUnitPlatform() }

/**
 * Test-dependency guard: production CODE must not depend on test modules.
 *
 * A module whose name ends in `-bdd` or `-test-data` is a test-support module.
 * It exists precisely so that other modules' TESTS can use it — so the guard
 * looks only at the production classpath. `testImplementation(sceneBdd)` is the
 * intended use and must stay legal; forbidding it would push people to copy the
 * helpers into production modules, which is the very thing this guard prevents.
 *
 * Blueprints are excluded: they are executable scenarios, and driving the
 * engines through the BDD harness is their job.
 *
 * Uses afterEvaluate to ensure all dependencies are declared before checking.
 */
val moduleName = project.name
val isProductionModule = !moduleName.endsWith("-bdd") &&
    !moduleName.endsWith("-test-data")
val isBlueprint = project.path.startsWith(":blueprints:")

if (isProductionModule && !isBlueprint) {
    afterEvaluate {
        val testDeps = configurations
            // Only the production classpath. Anything declared for a test source
            // set (testImplementation, testCompileOnly, …) is legitimate.
            .filterNot { it.name.startsWith("test") }
            .flatMap { config ->
                config.dependencies
                    .filterIsInstance<org.gradle.api.artifacts.ProjectDependency>()
                    .filter { it.name.endsWith("-bdd") || it.name.endsWith("-test-data") }
                    .map { "${config.name} -> ${it.name}" }
            }.distinct()

        if (testDeps.isNotEmpty()) {
            val projectPath = project.path
            val verifyNoTestDeps by tasks.registering {
                doLast {
                    check(false) {
                        "Production module $projectPath depends on test module(s): ${testDeps.joinToString()}. " +
                            "Move the production code out of the test module instead. " +
                            "(If the dependency is only needed by tests, declare it as testImplementation.)"
                    }
                }
            }
            tasks.named("check") { dependsOn(verifyNoTestDeps) }
        }
    }
}
