@file:Suppress("UnstableApiUsage")

package conventions

import libs
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.springframework.boot.gradle.tasks.bundling.BootBuildImage

plugins {
    id("conventions.kotlin")
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.integration.test)
    `java-test-fixtures`
}

extra["opentelemetry.version"] = libs.versions.opentelemetry.get()

abstract class VerifyOtelApiBoundaryTask : DefaultTask() {
    @get:Input
    abstract val projectPath: Property<String>

    @get:Input
    abstract val runtimeModules: ListProperty<String>

    @TaskAction
    fun verify() {
        val modules = runtimeModules.get().distinct()
        val apiModule = "io.opentelemetry:opentelemetry-api"
        if (apiModule !in modules) {
            throw GradleException(
                "${projectPath.get()} runtimeClasspath must contain $apiModule"
            )
        }

        val disallowed = modules.filter {
            it.startsWith("io.opentelemetry:opentelemetry-sdk") ||
                it.startsWith("io.opentelemetry.instrumentation:")
        }
        if (disallowed.isNotEmpty()) {
            throw GradleException(
                "${projectPath.get()} runtimeClasspath contains disallowed OpenTelemetry dependencies: $disallowed"
            )
        }

        val allowedOtelModules = setOf(
            "io.opentelemetry:opentelemetry-api",
            "io.opentelemetry:opentelemetry-context",
            "io.opentelemetry:opentelemetry-common"
        )
        val unexpected = modules.filter {
            (it.startsWith("io.opentelemetry:") || it.startsWith("io.opentelemetry.instrumentation:")) &&
                it !in allowedOtelModules
        }
        if (unexpected.isNotEmpty()) {
            throw GradleException(
                "${projectPath.get()} runtimeClasspath contains unexpected OpenTelemetry dependencies: $unexpected"
            )
        }
    }
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
    dependsOn("detektMain", "detektTest", "detektTestFixtures", "detektIntegration", verifyOtelApiBoundary)
}

dependencies {
    implementation(libs.bundles.spring.boot)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.kotlin.reflect)
    implementation(libs.opentelemetry.api)
    testFixturesApi(libs.bundles.spring.boot)
    testFixturesApi(libs.spring.boot.starter.test)
    testFixturesApi(libs.kotest.assertions.core)
    testFixturesApi(libs.awaitility.kotlin)
    testFixturesApi(libs.opentelemetry.sdk.testing)
}

tasks.named<BootBuildImage>("bootBuildImage") {
    imageName.set("observe-it/${project.name}:${project.version}")
    tags.set(
        listOf(
            "ghcr.io/pawelbalcerek/observe-it/${project.name}:${project.version}"
        )
    )
    buildpacks.set(
        listOf(
            "urn:cnb:builder:paketo-buildpacks/java",
            "docker.io/paketobuildpacks/opentelemetry:${libs.versions.paketo.opentelemetry.get()}"
        )
    )
    environment.putAll(
        mapOf(
            "BP_JVM_VERSION" to "25",
            "BP_OPENTELEMETRY_ENABLED" to "true"
        )
    )
}
