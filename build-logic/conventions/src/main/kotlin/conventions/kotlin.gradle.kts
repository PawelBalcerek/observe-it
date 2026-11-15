package conventions

import libs
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

group = "com.observeit"
version = rootProject.version

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

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.layout.projectDirectory.file("config/detekt/detekt.yml"))
}

// Detekt 2.0.0-alpha.6 is compiled against Kotlin 2.4.10 and refuses to run on the project's newer Kotlin version,
// so its own configurations keep the Kotlin artifacts it was built with. Bump this together with detekt.
// Ktlint 1.5.0 (used by jlleitschuh ktlint-gradle 14.2.0) requires Kotlin compiler 2.1.x embeddable APIs.
// When Gradle or Kotlin plugin manages Kotlin dependencies at 2.4.x, Ktlint configurations fail at runtime
// with 'java.lang.IllegalStateException: Extensions storage is not registered' if upgraded past 2.1.0.
// Registered in afterEvaluate so it runs after io.spring.dependency-management when conventions.spring is applied.
afterEvaluate {
    configurations.matching { it.name.startsWith("detekt") }.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlin") {
                useVersion("2.4.10")
            }
        }
    }

    configurations.matching { it.name.startsWith("ktlint") }.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlin" && requested.name == "kotlin-compiler-embeddable") {
                useVersion("2.1.0")
            }
        }
    }
}
