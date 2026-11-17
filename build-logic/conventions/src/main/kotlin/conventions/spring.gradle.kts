package conventions

import libs
import org.springframework.boot.gradle.tasks.bundling.BootBuildImage

plugins {
    id("conventions.kotlin")
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.integration.test)
    `java-test-fixtures`
}

dependencies {
    implementation(libs.bundles.spring.boot)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.kotlin.reflect)
    testFixturesApi(libs.bundles.spring.boot)
    testFixturesApi(libs.spring.boot.starter.test)
    testFixturesApi(libs.kotest.assertions.core)
    testFixturesApi(libs.awaitility.kotlin)
}

tasks.named<BootBuildImage>("bootBuildImage") {
    imageName.set("observe-it/${project.name}:${project.version}")
    tags.set(
        listOf(
            "ghcr.io/pawelbalcerek/observe-it/${project.name}:${project.version}"
        )
    )
    buildpacks.add("urn:cnb:builder:paketo-buildpacks/java")
    environment.put("BP_JVM_VERSION", "25")
}
