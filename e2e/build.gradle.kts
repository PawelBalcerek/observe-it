plugins {
    id("conventions.kotlin")
}

dependencies {
    testImplementation(platform(libs.jackson.bom))
    testImplementation(libs.jackson.module.kotlin)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.awaitility.kotlin)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.named<Test>("test") {
    enabled = false
}

tasks.named("check") {
    dependsOn("detektTest")
}

tasks.register<Test>("e2eTest") {
    description = "Runs end-to-end verification tests against the live Docker Compose stack."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    outputs.upToDateWhen { false }
    systemProperty("observeIt.projectVersion", project.version.toString())
    testLogging {
        events("passed", "skipped", "failed")
    }
}
