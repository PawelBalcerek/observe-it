package conventions

import libs
import org.springframework.boot.gradle.tasks.bundling.BootBuildImage

plugins {
    id("conventions.kotlin")
    `java-test-fixtures`
}

extra["opentelemetry.version"] = libs.versions.opentelemetry.get()

dependencies {
    implementation(libs.opentelemetry.api)
    testFixturesApi(libs.opentelemetry.sdk.testing)
}

val verifyOtelApiBoundary = tasks.register<VerifyOtelApiBoundaryTask>("verifyOtelApiBoundary") {
    description = "Verifies runtimeClasspath depends only on the OpenTelemetry API without SDK or instrumentation."
    group = "verification"
    projectPath.set(project.path)
    runtimeModules.set(
        configurations.named("runtimeClasspath").map { cfg ->
            cfg.incoming.resolutionResult.allComponents.mapNotNull { component ->
                component.moduleVersion?.let { mv -> "${mv.group}:${mv.name}" }
            }
        }
    )
}

tasks.named("check") {
    dependsOn(verifyOtelApiBoundary)
}

pluginManager.withPlugin("org.springframework.boot") {
    tasks.named<BootBuildImage>("bootBuildImage") {
        buildpacks.add("docker.io/paketobuildpacks/opentelemetry:${libs.versions.paketo.opentelemetry.get()}")
        environment.put("BP_OPENTELEMETRY_ENABLED", "true")
    }
}
