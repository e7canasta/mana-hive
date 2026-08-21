plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        allWarningsAsErrors = true
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

// nested modules get fully-qualified artifact names (engines-vigia-vigia-domain…)
base.archivesName = project.path.removePrefix(":").replace(":", "-")

tasks.withType<Test>().configureEach { useJUnitPlatform() }
