plugins {
    id("conventions.spring")
    id("conventions.opentelemetry")
}

springBoot {
    mainClass.set("com.observeit.observedapp.ObservedAppApplicationKt")
}

dependencies {
    implementation(libs.spring.boot.starter.data.mongodb)
    integrationImplementation(libs.spring.boot.testcontainers)
    integrationImplementation(libs.testcontainers.mongodb)
}
