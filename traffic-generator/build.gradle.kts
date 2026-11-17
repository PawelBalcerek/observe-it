plugins {
    id("conventions.spring")
    id("conventions.opentelemetry")
}

springBoot {
    mainClass.set("com.observeit.trafficgenerator.TrafficGeneratorApplicationKt")
}
