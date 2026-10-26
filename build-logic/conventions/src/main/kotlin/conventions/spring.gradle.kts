@file:Suppress("UnstableApiUsage")

package conventions

import libs
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.springframework.boot.gradle.tasks.bundling.BootBuildImage

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.integration.test)
    `java-test-fixtures`
}

group = "com.observeit"
version = "0.1.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

// Ktlint 1.5.0 (used by jlleitschuh ktlint-gradle 14.2.0) requires Kotlin compiler 2.1.x embeddable APIs.
// When Gradle or Kotlin plugin manages Kotlin dependencies at 2.4.x, Ktlint configurations fail at runtime
// with 'java.lang.IllegalStateException: Extensions storage is not registered' if upgraded past 2.1.0.
configurations.matching { it.name.startsWith("ktlint") }.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin" && requested.name == "kotlin-compiler-embeddable") {
            useVersion("2.1.0")
        }
    }
}

dependencies {
    implementation(libs.bundles.spring.boot)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.kotlin.reflect)
    testFixturesApi(libs.spring.boot.starter.test)
    testFixturesApi(libs.kotest.assertions.core)
    testFixturesApi(libs.awaitility.kotlin)
}

tasks.named<BootBuildImage>("bootBuildImage") {
    imageName.set("observe-it/${project.name}:${project.version}")
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
